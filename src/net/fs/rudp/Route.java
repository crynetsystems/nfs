// Copyright (c) 2015 D1SM.net

package net.fs.rudp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.fs.cap.CapEnv;
import net.fs.cap.VDatagramSocket;
import net.fs.rudp.message.MessageType;
import net.fs.utils.ByteIntConvert;
import net.fs.utils.MLog;
import net.fs.utils.MessageCheck;


/**
 * 核心功能，用于处理各个客户端连接，并将传入的数据包进行调度
 * 
 * @author hackpascal
 *
 */
public class Route {

	private DatagramSocket ds;
	public HashMap<Integer, ConnectionUDP> connTable;
	Route route;
	Thread mainThread;
	Thread reveiveThread;

	public static ThreadPoolExecutor es;
	
	public AckListManage delayAckManage;

	Object syn_ds2Table=new Object();

	Object syn_tunTable=new Object();

	Random ran=new Random();

	public int localclientId=Math.abs(ran.nextInt());

	LinkedBlockingQueue<DatagramPacket> packetBuffer=new LinkedBlockingQueue<DatagramPacket>();
	
	/**
	 * Route 模式：服务端
	 */
	public static int mode_server=2;
	
	/**
	 * Route 模式：客户端
	 */
	public static int mode_client=1;

	/**
	 * Route 实际模式
	 */
	public int mode=mode_client;//1客户端,2服务端
	
	String pocessName="";

	HashSet<Integer> setedTable=new HashSet<Integer>();

	static int vv;

	HashSet<Integer> closedTable=new HashSet<Integer>();

	public static int localDownloadSpeed,localUploadSpeed;

	ClientManager clientManager;
	
	HashSet<Integer> pingTable=new HashSet<Integer>();
	
	public CapEnv capEnv=null;
	
	public ClientControl lastClientControl;
	
	/**
	 * 是否使用  TCP 模式
	 */
	public boolean useTcpTun=true;
	
	public HashMap<Object, Object> contentTable=new HashMap<Object, Object>();
	
	private static List<Trafficlistener> listenerList=new Vector<Trafficlistener>();
	
	{
		
		delayAckManage = new AckListManage();
	}
	
	static{
		SynchronousQueue queue = new SynchronousQueue();
		ThreadPoolExecutor executor = new ThreadPoolExecutor(100, Integer.MAX_VALUE, 10*1000, TimeUnit.MILLISECONDS, queue); 
		es=executor;
	}
	
	/**
	 * Route 构造函数
	 * 
	 * @param pocessName	处理类的类名
	 * @param routePort		监听端口
	 * @param mode2 		模式 (客户端/服务端)
	 * @param tcp			是否使用  TCP 模式
	 * @param tcpEnvSuccess
	 * @throws Exception
	 */
	public Route(String pocessName,short routePort,int mode2,boolean tcp,boolean tcpEnvSuccess) throws Exception{
		
		this.mode=mode2;
		useTcpTun=tcp;
		this.pocessName=pocessName;
		
		if(useTcpTun){
			// TCP 模式初始化：
			if(mode==Route.mode_server){
				//服务端
				// 创建 UDP 包装对象，用于转发到TCP
				VDatagramSocket d=new VDatagramSocket(routePort);
				d.setClient(false);
				try {
					// 和接口抓包对象进行绑定
					capEnv=new CapEnv(false,tcpEnvSuccess);
					capEnv.setListenPort(routePort);
					capEnv.init();
				} catch (Exception e) {
					//e.printStackTrace();
					throw e;
				} 
				d.setCapEnv(capEnv);
				
				ds=d;
			}else {
				//客户端
				// 略
				VDatagramSocket d=new VDatagramSocket();
				d.setClient(true);
				try {
					capEnv=new CapEnv(true,tcpEnvSuccess);
					capEnv.init();
				} catch (Exception e) {
					//e.printStackTrace();
					throw e;
				} 
				d.setCapEnv(capEnv);
				
				ds=d;
			}
		}else {
			// UDP 模式初始化：
			// 直接创建 UDP 连接
			if(mode==Route.mode_server){
				MLog.info("Listen udp port: "+CapEnv.toUnsigned(routePort));
				ds=new DatagramSocket(CapEnv.toUnsigned(routePort));
			}else {
				ds=new DatagramSocket();
			}
		}
		
		connTable=new HashMap<Integer, ConnectionUDP>();
		clientManager=new ClientManager(this);
		
		// 接收线程，接收到的包存入 packetBuffer
		reveiveThread=new Thread(){
			@Override
			public void run(){
				while(true){
					byte[] b=new byte[1500];
					DatagramPacket dp=new DatagramPacket(b,b.length);
					try {
						ds.receive(dp);
						//MLog.println("接收 "+dp.getAddress());
						packetBuffer.add(dp);
					} catch (IOException e) {
						e.printStackTrace();
						try {
							Thread.sleep(1);
						} catch (InterruptedException e1) {
							e1.printStackTrace();
						}
						continue;
					}
				}
			}
		};
		reveiveThread.start();	// 立即启动线程

		// 主线程
		mainThread=new Thread(){
			public void run() {
				while(true){
					// 获取一个数据包
					DatagramPacket dp=null;
					try {
						dp = packetBuffer.take();
					} catch (InterruptedException e1) {
						e1.printStackTrace();
					}
					if(dp==null){
						continue;
					}
					// t1 = 计时开始
					long t1=System.currentTimeMillis();
					byte[] dpData=dp.getData(); // 数据包的实际数据
					
					int sType=0;
					if(dp.getData().length<4){
						// 不合法的数据，直接退出？？？
						return;
					}
					sType=MessageCheck.checkSType(dp); // 获取报文类型
					//MLog.println("route receive MessageType111#"+sType+" "+dp.getAddress()+":"+dp.getPort());
					if(dp!=null){
						
						final int connectId=ByteIntConvert.toInt(dpData, 4);
						int remote_clientId=ByteIntConvert.toInt(dpData, 8);

						if(closedTable.contains(connectId)&&connectId!=0){
							//#MLog.println("忽略已关闭连接包 "+connectId);
							continue;
						}
						
						if(sType==net.fs.rudp.message.MessageType.sType_PingMessage
								||sType==net.fs.rudp.message.MessageType.sType_PingMessage2){
							// Ping 包处理，顺便发送上传和下载速率
							ClientControl clientControl=null;
							if(mode==mode_server){
								//发起
								clientControl=clientManager.getClientControl(remote_clientId,dp.getAddress(),dp.getPort());
							}else if(mode==mode_client){
								//接收
								String key=dp.getAddress().getHostAddress()+":"+dp.getPort();
								int sim_clientId=Math.abs(key.hashCode());
								clientControl=clientManager.getClientControl(sim_clientId,dp.getAddress(),dp.getPort());
							}
							// TODO:
							clientControl.onReceivePacket(dp);
						}else {
							//发起
							if(mode==mode_client){
								if(!setedTable.contains(remote_clientId)){
									String key=dp.getAddress().getHostAddress()+":"+dp.getPort();
									int sim_clientId=Math.abs(key.hashCode());
									ClientControl clientControl=clientManager.getClientControl(sim_clientId,dp.getAddress(),dp.getPort());
									if(clientControl.getClientId_real()==-1){
										clientControl.setClientId_real(remote_clientId);
										//#MLog.println("首次设置clientId "+remote_clientId);
									}else {
										if(clientControl.getClientId_real()!=remote_clientId){
											//#MLog.println("服务端重启更新clientId "+sType+" "+clientControl.getClientId_real()+" new: "+remote_clientId);
											clientControl.updateClientId(remote_clientId);
										}
									}
									//#MLog.println("cccccc "+sType+" "+remote_clientId);
									setedTable.add(remote_clientId);
								}
							}


							//udp connection
							if(mode==mode_server){
								//接收
								try {
									// 多半只是在干这样一件事：不存在就创建
									getConnection2(dp.getAddress(),dp.getPort(),connectId,remote_clientId);
								} catch (Exception e) {
									e.printStackTrace();
								}
							}
							
							final ConnectionUDP ds3=connTable.get(connectId);
							if(ds3!=null){
								final DatagramPacket dp2=dp;
								ds3.receiver.onReceivePacket(dp2);
								if(sType==MessageType.sType_DataMessage){
									TrafficEvent event=new TrafficEvent("",ran.nextLong(),dp.getLength(),TrafficEvent.type_downloadTraffic);
									fireEvent(event);
								}
							}

						}
					}
				}
			}
		};
		mainThread.start();	// 启动主线程
		
	}

	public static void addTrafficlistener(Trafficlistener listener){
		listenerList.add(listener);
	}

	static void fireEvent(TrafficEvent event){
		for(Trafficlistener listener:listenerList){
			int type=event.getType();
			if(type==TrafficEvent.type_downloadTraffic){
				listener.trafficDownload(event);
			}else if(type==TrafficEvent.type_uploadTraffic){
				listener.trafficUpload(event);
			}
		}
	}

	public void sendPacket(DatagramPacket dp) throws IOException{
		ds.send(dp);
	}

	/**
	 * 创建一个隧道转发对象，解析客户端传入的目的端口，然后开启双向转发
	 * 仅被服务端使用
	 * 
	 * @return
	 */
	public ConnectionProcessor createTunnelProcessor(){
		ConnectionProcessor o=null;
		try {
			Class onwClass = Class.forName(pocessName);
			o = (ConnectionProcessor) onwClass.newInstance();
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (InstantiationException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
		}
		return o;
	}

	void removeConnection(ConnectionUDP conn){
		synchronized (syn_ds2Table){
			closedTable.add(conn.connectId);
			connTable.remove(conn.connectId);
		}
	}

	/**
	 * 根据传入的连接参数获取连接对象，没有就创建
	 * 服务端使用
	 * 
	 * @param dstIp			客户端 IP 地址
	 * @param dstPort		客户端端口
	 * @param connectId		连接Id
	 * @param clientId		客户端Id
	 * @return
	 * @throws Exception
	 */
	public ConnectionUDP getConnection2(InetAddress dstIp,int dstPort,int connectId,int clientId) throws Exception{
		ConnectionUDP conn=connTable.get(connectId);
		if(conn==null){
			ClientControl clientControl=clientManager.getClientControl(clientId,dstIp,dstPort);
			conn=new ConnectionUDP(this,dstIp,dstPort,2,connectId,clientControl);
			synchronized (syn_ds2Table){
				connTable.put(connectId, conn);
			}
			clientControl.addConnection(conn);
		}
		return conn;
	}

	/**
	 * 发起一个连接，创建客户端对象和连接对象
	 * 
	 * @param address		目的 IP 地址
	 * @param dstPort		目的端口
	 * @param password		密码
	 * @return
	 * @throws Exception
	 */
	public ConnectionUDP getConnection(String address,int dstPort,String password) throws Exception{
		InetAddress dstIp=InetAddress.getByName(address);
		int connectId=Math.abs(ran.nextInt());
		String key=dstIp.getHostAddress()+":"+dstPort;
		int remote_clientId=Math.abs(key.hashCode());
		ClientControl clientControl=clientManager.getClientControl(remote_clientId,dstIp,dstPort);
		clientControl.setPassword(password);
		ConnectionUDP conn=new ConnectionUDP(this,dstIp,dstPort,1,connectId,clientControl);
		synchronized (syn_ds2Table){
			connTable.put(connectId, conn);
		}
		clientControl.addConnection(conn);
		lastClientControl=clientControl;
		return conn;
	}

	public boolean isUseTcpTun() {
		return useTcpTun;
	}

	public void setUseTcpTun(boolean useTcpTun) {
		this.useTcpTun = useTcpTun;
	}

}


