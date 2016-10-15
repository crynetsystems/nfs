// Copyright (c) 2015 D1SM.net

package net.fs.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import net.fs.client.Pipe;
import net.fs.rudp.ConnectionProcessor;
import net.fs.rudp.ConnectionUDP;
import net.fs.rudp.Constant;
import net.fs.rudp.Route;
import net.fs.rudp.UDPInputStream;
import net.fs.rudp.UDPOutputStream;
import net.fs.utils.MLog;

import com.alibaba.fastjson.JSONObject;

/**
 * 经分析，本类的作用为处理客户端第一次发送的 DataMessage，其内容即为转发目的端口
 * 仅被服务端调用
 * 
 * @author hackpascal
 *
 */
public class MapTunnelProcessor implements ConnectionProcessor{
	// 实现 ConnectionProcessor 接口，位于 net/fs/rudp/ConnectionProcessor.java

	Socket dstSocket=null;//标准库java.net.Socket

	boolean closed=false;//bool变量

	MapTunnelProcessor pc;//this对象

	ConnectionUDP conn;//net.fs.rudp.ConnectionUDP
	UDPInputStream  tis;//net.fs.rudp.UDPInputStream
	UDPOutputStream tos;//net.fs.rudp.UDPOutputStream

	InputStream sis;//标准库java.io.InputStream
	OutputStream sos;//标准库java.io.OutputStream

	/**
	 * MapTunnelProcessor 接口函数，用于记录参数并启动线程
	 * 
	 * @param conn 传入的连接？
	 */
	public void process(final ConnectionUDP conn){
		this.conn=conn;
		pc=this;
		Route.es.execute(new Runnable(){
			public void run(){
				process();
			}
		});
	}


	/**
	 * 私有函数，线程的主体
	 */
	void process(){

		tis=conn.uis;
		tos=conn.uos;

		byte[] headData;
		try {
			// 解析出目的端口
			headData = tis.read2();
			String hs=new String(headData,"utf-8");
			JSONObject requestJSon=JSONObject.parseObject(hs);
			final int dstPort=requestJSon.getIntValue("dst_port");
			String message="";
			JSONObject responeJSon=new JSONObject();
			int code=Constant.code_failed;
			code=Constant.code_success;
			responeJSon.put("code", code);
			responeJSon.put("message", message);
			byte[] responeData=responeJSon.toJSONString().getBytes("utf-8");
			tos.write(responeData, 0, responeData.length);
			if(code!=Constant.code_success){
				close();
				return;
			}
			
			// 创建到目的端口的连接
			dstSocket = new Socket("127.0.0.1", dstPort);
			dstSocket.setTcpNoDelay(true);
			
			// TODO: 这几个类着重分析
			sis=dstSocket.getInputStream();
			sos=dstSocket.getOutputStream();

			// TCP入站和出站数据的转发
			final Pipe p1=new Pipe();
			final Pipe p2=new Pipe();

			// 具体转发过程待分析
			// TODO:
			Route.es.execute(new Runnable() {

				public void run() {
					try {
						p1.pipe(sis, tos,100*1024,p2);
					}catch (Exception e) {
						//e.printStackTrace();
					}finally{
						close();
						if(p1.getReadedLength()==0){
							MLog.println("端口"+dstPort+"无返回数据");
						}
					}
				}

			});
			Route.es.execute(new Runnable() {

				public void run() {
					try {
						p2.pipe(tis,sos,100*1024*1024,conn);
					}catch (Exception e) {
						//e.printStackTrace();
					}finally{
						close();
					}
				}
			});


		} catch (Exception e2) {
			//e2.printStackTrace();
			close();
		}



	}

	void close(){
		if(!closed){
			closed=true;
			if(sis!=null){
				try {
					sis.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
			if(sos!=null){
				try {
					sos.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
			if(tos!=null){
				tos.closeStream_Local();
			}
			if(tis!=null){
				tis.closeStream_Local();
			}
			if(conn!=null){
				conn.close_local();
			}
			if(dstSocket!=null){
				try {
					dstSocket.close();
				} catch (IOException e) {
					//e.printStackTrace();
				}
			}
		}
	}

}
