// Copyright (c) 2015 D1SM.net

package net.fs.rudp;

import java.net.InetAddress;

/**
 * 封装 Sender 的 sendData 方法
 * 这个类的意义何在
 * 
 * @author hackpascal
 *
 */
public class UDPOutputStream {
	public ConnectionUDP conn;
	InetAddress dstIp;
	int dstPort;
	Sender sender;
	
	boolean streamClosed=false;
	
	/**
	 * 将 Sender 的 sendData 方法封装成一个类
	 * 意义？？
	 * 
	 * @param conn
	 */
	UDPOutputStream (ConnectionUDP conn){
		this.conn=conn;
		this.dstIp=conn.dstIp;
		this.dstPort=conn.dstPort;
		this.sender=conn.sender;
	}
	
	public void write(byte[] data,int offset,int length) throws ConnectException, InterruptedException {
		sender.sendData(data, offset,length);
	}
	
	public void closeStream_Local(){
		sender.closeStream_Local();
	}
	
}
