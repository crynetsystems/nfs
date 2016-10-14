// Copyright (c) 2015 D1SM.net

package net.fs.cap;

import java.util.HashMap;
import java.util.Iterator;

import net.fs.rudp.CopiedIterator;
import net.fs.utils.MLog;

/**
 * 自己实现的 TCP 会话管理功能的      TCP 连接管理器
 * 使用 Hash 表来实现快速查找
 * 
 * 用于服务端时，管理的是来自客户端的连接，即目的端口是 150 的连接
 * 用于客户端时，管理的是客户端创建的连接
 * 
 * @author hackpascal
 *
 */
public class TunManager {
	
	HashMap<String, TCPTun> connTable=new HashMap<String, TCPTun>();
	
	static TunManager tunManager;
	
	{
		tunManager=this;
	}
	
	TCPTun defaultTcpTun;
	
	Thread scanThread;
	
	Object syn_scan=new Object();
	
	CapEnv capEnv;
	
	{
		// 定时扫描所有记录着的TCP连接，清除无效的连接
		scanThread=new Thread(){
			public void run(){
				while(true){
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					 scan();
				}
			}
		};
		scanThread.start();
	}
	
	/**
	 * 构造函数
	 * 
	 * @param canEnv 
	 */
	TunManager(CapEnv capEnv){
		this.capEnv=capEnv;
	}
	
	/**
	 * 扫描 TCP 连接列表，清除无效的连接
	 */
	void scan(){
		Iterator<String> it=getConnTableIterator();
		while(it.hasNext()){
			String key=it.next();
			TCPTun tun=connTable.get(key);
			if(tun!=null){
				if(tun.preDataReady){
					//无数据超时
					long t=System.currentTimeMillis()-tun.lastReceiveDataTime;
					if(t>6000){
						connTable.remove(key);
						if(capEnv.client){
							defaultTcpTun=null;
							MLog.println("tcp隧道超时");
						}
					}
				}else{
					//连接中超时
					if(System.currentTimeMillis()-tun.createTime>5000){
						connTable.remove(key);
					}
				}
			}
		}
	}
	
	/**
	 * 移除一个 TCP 连接
	 * 
	 * @param tun
	 */
	public void removeTun(TCPTun tun){
		connTable.remove(tun.key);
	}
	
	/**
	 * 返回 TCP 连接表
	 * 
	 * @return 连接的特征字符串
	 */
	Iterator<String> getConnTableIterator(){
		Iterator<String> it=null;
		synchronized (syn_scan) {
			it=new CopiedIterator(connTable.keySet().iterator());
		}
		return it;
	}
	
	public static TunManager get(){
		return tunManager;
	}
	
	/**
	 * 查找连接，客户端
	 * 
	 * @param remoteAddress		目的 IP 地址
	 * @param remotePort		目的 IP 地址
	 * @param localPort			本地绑定的端口
	 * @return
	 */
	public TCPTun getTcpConnection_Client(String remoteAddress,short remotePort,short localPort){
		return connTable.get(remoteAddress+":"+remotePort+":"+localPort);
	}
	
	/**
	 * 添加连接，客户端使用
	 * 
	 * @param conn				TCP 连接对象
	 */
	public void addConnection_Client(TCPTun conn) {
		synchronized (syn_scan) {
			String key=conn.remoteAddress.getHostAddress()+":"+conn.remotePort+":"+conn.localPort;
			//MLog.println("addConnection "+key);
			conn.setKey(key);
			connTable.put(key, conn);
		}
	}
	
	/**
	 * 查找连接，服务端
	 * 
	 * @param remoteAddress		源 IP 地址
	 * @param remotePort		源端口
	 * @return
	 */
	public TCPTun getTcpConnection_Server(String remoteAddress,short remotePort){
		return connTable.get(remoteAddress+":"+remotePort);
	}
	
	/**
	 * 添加连接，服务端使用
	 * 
	 * @param conn				TCP 连接对象
	 */
	public void addConnection_Server(TCPTun conn) {
		synchronized (syn_scan) {
			String key=conn.remoteAddress.getHostAddress()+":"+conn.remotePort;
			//MLog.println("addConnection "+key);
			conn.setKey(key);
			connTable.put(key, conn);
		}
	}

	public TCPTun getDefaultTcpTun() {
		return defaultTcpTun;
	}

	public void setDefaultTcpTun(TCPTun defaultTcpTun) {
		this.defaultTcpTun = defaultTcpTun;
	}

}
