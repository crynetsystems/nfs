// Copyright (c) 2015 D1SM.net

package net.fs.cap;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.concurrent.LinkedBlockingQueue;

import net.fs.rudp.Route;

/**
 * 这个类的作用是将通过 UDP 发送的数据重定向到 fs 自己实现的 TCP 连接中
 * 重载 DatagramSocket 类以便于和 UDP 模式兼容
 * 
 * @author hackpascal
 *
 */
public class VDatagramSocket extends DatagramSocket{
	
	boolean useTcpTun=true;
	
	boolean client=true;
	
	LinkedBlockingQueue<TunData> packetList=new LinkedBlockingQueue<TunData> ();
	
	CapEnv capEnv;
	
	int localPort;
	
	Object syn_tun=new Object();
	
	boolean tunConnecting=false;

	public VDatagramSocket() throws SocketException {
		
	}

	public VDatagramSocket(int port) throws SocketException {
		localPort=port;
	}
	
	 public int getLocalPort() {
		 return localPort;
	 }

	 /**
	  * 发送数据
	  * 
	  * @param p	要发送的数据
	  */
	public void send(DatagramPacket p) throws IOException  {
		TCPTun tun=null;
		if(client){
			// 如果是客户端，那么首先要判断是否已经创建了和服务端通信的连接
			tun=capEnv.tcpManager.getDefaultTcpTun();
			if(tun!=null){
				// 如果修改了服务器地址或者端口，那么需要重新创建
				if(!tun.remoteAddress.getHostAddress().equals(p.getAddress().getHostAddress())
						||CapEnv.toUnsigned(tun.remotePort)!=p.getPort()){
						capEnv.tcpManager.removeTun(tun);
						capEnv.tcpManager.setDefaultTcpTun(null);
					}
			}else {
				// 没有就创建
				tryConnectTun_Client(p.getAddress(),(short) p.getPort());
				tun=capEnv.tcpManager.getDefaultTcpTun();
			}
		}else {
			// 如果是服务端，那么就获取对应的连接
			tun=capEnv.tcpManager.getTcpConnection_Server(p.getAddress().getHostAddress(), (short) p.getPort());
		}
		
		if(tun!=null){
			// 发送数据
			if(tun.preDataReady){
				tun.sendData(p.getData());
			}else{
				throw new IOException("隧道未连接!");
			}
		}else{
			
			throw new IOException("隧道不存在! "+" thread "+Route.es.getActiveCount()+" "+p.getAddress()+":"+p.getPort());
		}
	}
	
	
	/**
	 * 客户端发起 TCP 连接
	 * 需要线程同步，防止多次连接
	 * 创建好的连接作为和服务端的通信连接
	 * 
	 * @param dstAddress		目的 IP 地址
	 * @param dstPort			目的端口
	 */
	void tryConnectTun_Client(InetAddress dstAddress,short dstPort){
		synchronized (syn_tun) {
			if(capEnv.tcpManager.getDefaultTcpTun()==null){
				if(tunConnecting){
					try {
						syn_tun.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}else {
					tunConnecting=true;
					try {
						capEnv.createTcpTun_Client(dstAddress.getHostAddress(), dstPort);
					} catch (Exception e) {
						e.printStackTrace();
					}
					tunConnecting=false;
				}
			}
		}
	}
	
	
	/**
	 * 接收数据
	 * 
	 * @param p		存放接收到的数据
	 */
	public synchronized void receive(DatagramPacket p) throws IOException {
		TunData td=null;
		try {
			td=packetList.take();
			p.setData(td.data);
			p.setLength(td.data.length);
			p.setAddress(td.tun.remoteAddress);
			p.setPort(CapEnv.toUnsigned(td.tun.remotePort));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 存放从 TCPTun 中接收到的数据
	 * 
	 * @param td	来自自己实现的 TCP 连接的数据
	 */
	void onReceinveFromTun(TunData td){
		packetList.add(td);
	}

	public boolean isClient() {
		return client;
	}

	public void setClient(boolean client) {
		this.client = client;
	}

	public CapEnv getCapEnv() {
		return capEnv;
	}

	public void setCapEnv(CapEnv capEnv) {
		this.capEnv = capEnv;
		capEnv.vDatagramSocket=this;
	}

}
