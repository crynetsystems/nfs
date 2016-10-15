// Copyright (c) 2015 D1SM.net

package net.fs.rudp;

import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import net.fs.utils.MLog;


/**
 * 客户端管理类
 * 
 * @author hackpascal
 *
 */
public class ClientManager {
	
	HashMap<Integer, ClientControl> clientTable=new HashMap<Integer, ClientControl>();
	
	Thread mainThread;
	
	Route route;
	
	int receivePingTimeout=8*1000;
	
	int sendPingInterval=1*1000;
	
	Object syn_clientTable=new Object();
	
	/**
	 * 构造函数
	 * 开启线程定时扫描客户端列表
	 * 
	 * @param route
	 */
	ClientManager(Route route){
		this.route=route;
		mainThread=new Thread(){
			@Override
			public void run(){
				while(true){
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					scanClientControl();
				}
			}
		};
		mainThread.start();
	}
	
	/**
	 * 扫描客户端，判断客户端是否在线，不在线就删除
	 */
	void scanClientControl(){
		Iterator<Integer> it=getClientTableIterator();
		long current=System.currentTimeMillis();
		//MLog.println("ffffffffffff "+clientTable.size());
		while(it.hasNext()){
			ClientControl cc=clientTable.get(it.next());
			if(cc!=null){
				if(current-cc.getLastReceivePingTime()<receivePingTimeout){
					if(current-cc.getLastSendPingTime()>sendPingInterval){
						cc.sendPingMessage();
					}
				}else {
					//超时关闭client
					MLog.println("超时关闭client "+cc.dstIp.getHostAddress()+":"+cc.dstPort+" "+new Date());
//					System.exit(0);
					synchronized (syn_clientTable) {
						cc.close();
					}
				}
			}
		}
	}
	
	/**
	 * 删除客户端对象
	 * 
	 * @param clientId		客户端Id
	 */
	void removeClient(int clientId){
		clientTable.remove(clientId);
	}
	
	Iterator<Integer> getClientTableIterator(){
		Iterator<Integer> it=null;
		synchronized (syn_clientTable) {
			it=new CopiedIterator(clientTable.keySet().iterator());
		}
		return it;
	}
	
	/**
	 * 获取客户端对象，不存在就创建
	 * 
	 * @param clientId		客户端Id
	 * @param dstIp			源 IP 地址
	 * @param dstPort		源端口
	 * @return
	 */
	ClientControl getClientControl(int clientId,InetAddress dstIp,int dstPort){
		ClientControl c=clientTable.get(clientId);
		if(c==null){
			c=new ClientControl(route,clientId,dstIp,dstPort);
			synchronized (syn_clientTable) {
				clientTable.put(clientId, c);
			}
		}
		return c;
	}
	
}
