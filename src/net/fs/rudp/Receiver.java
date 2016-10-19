// Copyright (c) 2015 D1SM.net

package net.fs.rudp;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;

import net.fs.rudp.message.AckListMessage;
import net.fs.rudp.message.CloseMessage_Conn;
import net.fs.rudp.message.CloseMessage_Stream;
import net.fs.rudp.message.DataMessage;
import net.fs.utils.MessageCheck;


public class Receiver {
	ConnectionUDP conn;
	Sender sender;
	public InetAddress dstIp;
	public int dstPort;
	HashMap<Integer, DataMessage> receiveTable=new HashMap<Integer, DataMessage>();
	/**
	 * 上次已经成功接收到并处理了的有效数据包的序号
	 * 类比 TCP 的 ACK 字段
	 */
	int lastRead=-1;
	int lastReceive=-1;
	Object availOb=new Object();

	boolean isReady=false;
	Object readyOb=new Object();
	byte[] b4=new byte[4];
	int lastRead1=0;
	int maxWinR=10;
	int lastRead2=-1;
	UDPInputStream uis;

	float availWin=RUDPConfig.maxWin;

	int currentRemoteTimeId;

	int closeOffset;

	boolean streamClose=false;

	boolean reveivedClose=false;

	static int m=0,x,x2,c;

	boolean b=false,b2;

	public int nw;
	
	long received;

	Receiver(ConnectionUDP conn){
		this.conn=conn;
		uis=new UDPInputStream(conn);
		this.sender=conn.sender;
		this.dstIp=conn.dstIp;
		this.dstPort=conn.dstPort;
	}

	//接收流数据
	public byte[] receive() throws ConnectException {
		DataMessage me=null;
		if(conn.isConnected()){
			me=receiveTable.get(lastRead+1);
			synchronized (availOb){
				if(me==null){
					//MLog.println("等待中 "+conn.connectId+" "+(lastRead+1));

					try {
						availOb.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					me=receiveTable.get(lastRead+1);
					//MLog.println("等待完成aaa "+conn.connectId+" "+(lastRead+1));
				}
			}

		}else{
			throw new ConnectException("连接未建立");
		}

		if(!streamClose){
			checkCloseOffset_Remote();
			if(me==null){
				throw new ConnectException("连接已断开ccccccc");
			}else {
			}
			conn.sender.sendLastReadDelay();

			lastRead++;
			synchronized (availOb){
				receiveTable.remove(me.getSequence());
			}

			received+=me.getData().length;
			//System.out.println("received "+received/1024/1024+"MB");
			return me.getData();
		}else{
			throw new ConnectException("连接已断开");
		}
	}

	public void onReceivePacket(DatagramPacket dp){
		DataMessage me;
		if(dp!=null){
			if(conn.isConnected()){
				int ver=MessageCheck.checkVer(dp);
				int sType=MessageCheck.checkSType(dp);
				if(ver==RUDPConfig.protocal_ver){
					conn.live();
					if(sType==net.fs.rudp.message.MessageType.sType_DataMessage){
						// 处理传入的数据
						me=new DataMessage(dp);
						int timeId=me.getTimeId();	// 获取 timeId
						// 查找 timeId 对应的接收记录
						SendRecord record=conn.clientControl.sendRecordTable_remote.get(timeId);
						if(record==null){
							// 没有就创建
							record=new SendRecord();
							record.setTimeId(timeId);
							// TODO: 这个用处待分析
							conn.clientControl.sendRecordTable_remote.put(timeId, record);
						}
						// 将接收到的数据包添加到记录
						record.addSended(me.getData().length);

						// 更新对方时间
						if(timeId>currentRemoteTimeId){
							currentRemoteTimeId=timeId;
						}

						// 数据包序号
						int sequence=me.getSequence();

						conn.sender.sendAckDelay(me.getSequence());		// 延迟 ACK
						if(sequence>lastRead){
							// 新数据到达
							synchronized (availOb){
								receiveTable.put(sequence, me);	// 放入接收数据队列
								if(receiveTable.containsKey(lastRead+1)){
									availOb.notify();	// 唤醒等待接收数据的线程
								}
							}
						}
					}else if(sType==net.fs.rudp.message.MessageType.sType_AckListMessage){
						// 处理传入的应答
						AckListMessage alm=new AckListMessage(dp);
						int lastRead3=alm.getLastRead();
						if(lastRead3>lastRead2){
							lastRead2=lastRead3;
						}
						ArrayList ackList=alm.getAckList();

						// 挨个删除已经应答了的数据包
						for(int i=0;i<ackList.size();i++){
							int sequence=(Integer) ackList.get(i);
							conn.sender.removeSended_Ack(sequence);
						}
						
						// 以下代码为处理最近三组 timeId 的接手清空
						// 因为每个组都可能有多个数据包
						// 这里处理的是每个租的数据包是否都被应答了
						// 没有的话，如果有新的应答，就更新应答状态
						SendRecord rc1=conn.clientControl.getSendRecord(alm.getR1());
						if(rc1!=null){
							if(alm.getS1()>rc1.getAckedSize()){
								rc1.setAckedSize(alm.getS1());
							}
						}

						SendRecord rc2=conn.clientControl.getSendRecord(alm.getR2());
						if(rc2!=null){
							if(alm.getS2()>rc2.getAckedSize()){
								rc2.setAckedSize(alm.getS2());
							}
						}

						SendRecord rc3=conn.clientControl.getSendRecord(alm.getR3());
						if(rc3!=null){
							if(alm.getS3()>rc3.getAckedSize()){
								rc3.setAckedSize(alm.getS3());
							}
						}

						// 更新发送窗口
						if(checkWin()){
							conn.sender.play();
						}
					}else if(sType==net.fs.rudp.message.MessageType.sType_CloseMessage_Stream){
						CloseMessage_Stream cm=new CloseMessage_Stream(dp);
						reveivedClose=true;
						int n=cm.getCloseOffset();
						closeStream_Remote(n);
					}else if(sType==net.fs.rudp.message.MessageType.sType_CloseMessage_Conn){
						CloseMessage_Conn cm2=new CloseMessage_Conn(dp);
						conn.close_remote();
					}else{
						////#MLog.println("未处理数据包 "+sType);
					}
				}

			}
		}

	}

	public void destroy(){
		//#MLog.println("destroy destroy destroy");
		synchronized (availOb) {
			receiveTable.clear();
		}
	}

	boolean checkWin(){
		nw=conn.sender.sendOffset-lastRead2;
		boolean b=false;
		if(nw<availWin){
			b= true;
		}else {
		}
		return b;
	}

	void closeStream_Remote(int closeOffset){
		this.closeOffset=closeOffset;
		if(!streamClose){
			checkCloseOffset_Remote();
		}
	}

	void checkCloseOffset_Remote(){
		if(!streamClose){
			if(reveivedClose){
				if(lastRead>=closeOffset-1){
					streamClose=true;
					synchronized (availOb){
						availOb.notifyAll();
					}
					conn.sender.closeStream_Remote();
				}
			}
		}
	}

	void closeStream_Local(){
		if(!streamClose){
			c++;
			streamClose=true;
			synchronized (availOb){
				availOb.notifyAll();
			}
			conn.sender.closeStream_Local();
		}
	}

	public int getCurrentTimeId() {
		return currentRemoteTimeId;
	}

	public void setCurrentTimeId(int currentTimeId) {
		this.currentRemoteTimeId = currentTimeId;
	}


	public int getCloseOffset() {
		return closeOffset;
	}


	public void setCloseOffset(int closeOffset) {
		this.closeOffset = closeOffset;
	}

}
