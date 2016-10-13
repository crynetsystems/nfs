// Copyright (c) 2015 D1SM.net

package net.fs.cap;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import net.fs.rudp.Route;
import net.fs.utils.ByteShortConvert;
import net.fs.utils.MLog;

import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PacketListener;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.PcapNetworkInterface;
import org.pcap4j.core.PcapNetworkInterface.PromiscuousMode;
import org.pcap4j.core.PcapStat;
import org.pcap4j.core.Pcaps;
import org.pcap4j.core.BpfProgram.BpfCompileMode;
import org.pcap4j.packet.EthernetPacket;
import org.pcap4j.packet.EthernetPacket.EthernetHeader;
import org.pcap4j.packet.IllegalPacket;
import org.pcap4j.packet.IllegalRawDataException;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV4Packet.IpV4Header;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.TcpPacket.TcpHeader;
import org.pcap4j.util.MacAddress;


/**
 * 选择出口接口，并进行包捕获，自行管理 TCP 连接
 * 
 * @author hackpascal
 *
 */
public class CapEnv {

	/**
	 * 默认出口接口对面的网关 MAC 地址
	 */
	public MacAddress gateway_mac;

	/**
	 * 默认出口接口的 MAC 地址
	 */
	public MacAddress local_mac;

	/**
	 * 默认出口接口的 IP 地址
	 */
	Inet4Address local_ipv4;
	
	/**
	 * 选中的接口的 pcap 句柄
	 */
	public PcapHandle sendHandle;
	
	VDatagramSocket vDatagramSocket;
	
	/**
	 * 当前正在测试的外网 IP 地址
	 */
	String testIp_tcp="";
	
	String testIp_udp="5.5.5.5";
	
	/**
	 * 选中的接口名
	 */
	String selectedInterfaceName=null;
	
	/**
	 * 选中的接口描述
	 */
	String selectedInterfaceDes="";

	/**
	 * 选中的接口对象
	 */
	PcapNetworkInterface nif;

	private  final int COUNT=-1;

	private  final int READ_TIMEOUT=1; 

	private  final int SNAPLEN= 10*1024; 

	HashMap<Integer, TCPTun> tunTable=new HashMap<Integer, TCPTun>();
	
	Random random=new Random();

	/**
	 * 是否为客户端
	 */
	boolean client=false;
	
	/**
	 * 监听端口
	 */
	short listenPort;
	
	TunManager tcpManager=null;
	
	CapEnv capEnv;
	
	Thread versinMonThread;
	
	boolean detect_by_tcp=true;
	
	public boolean tcpEnable=false;
	
	/**
	 * 是否成功修改了防火墙
	 */
	public boolean fwSuccess=true;
	
	boolean ppp=false;
	
	{
		capEnv=this;
	}
	
	/**
	 * 构造函数
	 * 
	 * @param isClient		是否是客户端
	 * @param fwSuccess		防火墙设置是否成功
	 */
	public CapEnv(boolean isClient,boolean fwSuccess){
		this.client=isClient;
		this.fwSuccess=fwSuccess;
		tcpManager=new TunManager(this);
	}

	/**
	 * CapEnv 初始化，主要是选择出口接口
	 * 
	 * @throws Exception
	 */
	public void init() throws Exception{
		// 初始化接口
		initInterface();
		
		// 针对系统休眠后可能出现的接口状态变化进行处理
		// 你不用理会
		Thread systemSleepScanThread=new Thread(){
			public void run(){
				long t=System.currentTimeMillis();
				while(true){
					if(System.currentTimeMillis()-t>5*1000){
						for(int i=0;i<10;i++){
							MLog.info("休眠恢复... "+(i+1));
							try {
								boolean success=initInterface();
								if(success){
									MLog.info("休眠恢复成功 "+(i+1));
									break;
								}
							} catch (Exception e1) {
								e1.printStackTrace();
							}
							
							try {
								Thread.sleep(5*1000);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
						
					}
					t=System.currentTimeMillis();
					try {
						Thread.sleep(1*1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		};
		systemSleepScanThread.start();
	}
	
	/**
	 * 处理捕获到的数据包
	 * 
	 * @param packet		捕获到的数据包
	 * @throws Exception
	 */
	void processPacket(Packet packet) throws Exception{
		EthernetPacket packet_eth=(EthernetPacket) packet;
		EthernetHeader head_eth=packet_eth.getHeader();
		
		// 获取 IPv4 数据
		IpV4Packet ipV4Packet=null;
		if(ppp){
			ipV4Packet=getIpV4Packet_pppoe(packet_eth);
		}else {
			if(packet_eth.getPayload() instanceof IpV4Packet){
				ipV4Packet=(IpV4Packet) packet_eth.getPayload();
			}
		}
		
		if(ipV4Packet!=null){
			IpV4Header ipV4Header=ipV4Packet.getHeader();
			if(ipV4Packet.getPayload() instanceof TcpPacket){
				// 如果 IPv4 的载荷是 TCP
				TcpPacket tcpPacket=(TcpPacket) ipV4Packet.getPayload();
				TcpHeader tcpHeader=tcpPacket.getHeader();
				
				if(client){
					// 如果是客户端，那么检查 tcpManager 里面是否记录了此 TCP 连接 (类似于 conntrack)
					// 检查项目包含 源IP地址、源端口、目的端口
					TCPTun conn=tcpManager.getTcpConnection_Client(ipV4Header.getSrcAddr().getHostAddress(),tcpHeader.getSrcPort().value(), tcpHeader.getDstPort().value());
					if(conn!=null){
						// 如果匹配到 TCP 连接，那么就进行 TCP 协议处理
						conn.process_client(capEnv,packet,head_eth,ipV4Header,tcpPacket,false);
					}
				}else {
					// 如果是客户端，那么检查 tcpManager 里面是否记录了此 TCP 连接
					// 检查项目包含 源IP地址、源端口
					TCPTun conn=null;conn = tcpManager.getTcpConnection_Server(ipV4Header.getSrcAddr().getHostAddress(),tcpHeader.getSrcPort().value());
					if(tcpHeader.getDstPort().value()==listenPort){
						// 需要目的端口等于监听端口，否则不是发给fs的数据
						if(tcpHeader.getSyn()&&!tcpHeader.getAck()&&conn==null){
							// 如果是 SYN 握手包，标明创建了一个新连接
							// 那么创建新的 TCP 连接记录，并添加到 tcpManager
							conn=new TCPTun(capEnv,ipV4Header.getSrcAddr(),tcpHeader.getSrcPort().value());
							tcpManager.addConnection_Server(conn);
						}
						// 完全不做优化么。。。
						conn = tcpManager.getTcpConnection_Server(ipV4Header.getSrcAddr().getHostAddress(),tcpHeader.getSrcPort().value());
						if(conn!=null){
							// 类似
							conn.process_server(packet,head_eth,ipV4Header,tcpPacket,true);
						}
					}
				}
			}else if(packet_eth.getPayload() instanceof IllegalPacket){
				// 因为设置了过滤条件，所以这个不应该发生
				MLog.println("IllegalPacket!!!");
			}
		}
	
	}
	
	PromiscuousMode getMode(PcapNetworkInterface pi){
		PromiscuousMode mode=null;
		String string=(pi.getDescription()+":"+pi.getName()).toLowerCase();
		if(string.contains("wireless")){
			mode=PromiscuousMode.NONPROMISCUOUS;
		}else {
			mode=PromiscuousMode.PROMISCUOUS;
		}
		return mode;
	}
	
	/**
	 * 初始化接口
	 * 
	 * @return
	 * @throws Exception
	 */
	boolean initInterface() throws Exception{
		boolean success=false;
		// 选择出口接口
		detectInterface();
		List<PcapNetworkInterface> allDevs = Pcaps.findAllDevs();
		
		// 打印出 pcap 所支持的接口
		MLog.println("Network Interface List: ");
		for(PcapNetworkInterface pi:allDevs){
			String desString="";
			if(pi.getDescription()!=null){
				desString=pi.getDescription();
			}
			MLog.info("  "+desString+"   "+pi.getName());
			// 如果是之前选择出的出口接口，那么就保存接口信对象
			if(pi.getName().equals(selectedInterfaceName)
					&&desString.equals(selectedInterfaceDes)){
				nif=pi;
				//break;
			}
		}
		
		if(nif!=null){
			// 如果找到了选中的接口，就输出提示，并且支持 TCP 协议
			String desString="";
			if(nif.getDescription()!=null){
				desString=nif.getDescription();
			}
			success=true;
			MLog.info("Selected Network Interface:\n"+"  "+desString+"   "+nif.getName());
			if(fwSuccess){
				tcpEnable=true;
			}
		}else {
			// 否则提示只支持 UDP 协议
			// 协议的拼写还错了
			tcpEnable=false;
			MLog.info("Select Network Interface failed,can't use TCP protocal!\n");
		}
		
		if(tcpEnable){
			// 如果支持 TCP 协议，那么打开选中的接口
			sendHandle = nif.openLive(SNAPLEN, getMode(nif), READ_TIMEOUT);
//			final PcapHandle handle= nif.openLive(SNAPLEN, getMode(nif), READ_TIMEOUT);
			
			// 设置过滤器
			String filter="";
			if(!client){
				//服务端
				filter="tcp dst port "+toUnsigned(listenPort);
			}else{
				//客户端
				filter="tcp";
			}
			sendHandle.setFilter(filter, BpfCompileMode.OPTIMIZE);

			// 抓包函数，抓到的包传递给 processPacket 函数
			final PacketListener listener= new PacketListener() {
				@Override
				public void gotPacket(Packet packet) {

					try {
						if(packet instanceof EthernetPacket){
							processPacket(packet);
						}
					} catch (Exception e) {
						e.printStackTrace();
					}

				}
			};

			// 开启抓包线程
			Thread thread=new Thread(){

				public void run(){
					try {
						sendHandle.loop(COUNT, listener);
						PcapStat ps = sendHandle.getStats();
						sendHandle.close();
					} catch (Exception e) {
						e.printStackTrace();
					}
				}

			};
			thread.start();
		}
		
		if(!client){
			MLog.info("FinalSpeed server start success.");
		}
		return success;
	
	}

	/**
	 * zz函数之一：通过抓包的方式获取每个接口的 MAC地址、网关、IP地址
	 * 
	 * zz般的接口选择算法：
	 * 监听所有接口
	 * 然后创建一个TCP连接，尝试在所有接口上捕获TCP握手数据包
	 * 利用系统默认路由表规则，获取出口接口
	 * 检测算法极不可靠
	 * 完全不考虑线程同步可能导致的数据被覆盖
	 */
	void detectInterface() {
		List<PcapNetworkInterface> allDevs = null;
		HashMap<PcapNetworkInterface, PcapHandle> handleTable=new HashMap<PcapNetworkInterface, PcapHandle>();
		try {
			// 获取所有接口信息
			allDevs = Pcaps.findAllDevs();
		} catch (PcapNativeException e1) {
			e1.printStackTrace();
			return;
		}
		
		// 遍历每个接口
		for(final PcapNetworkInterface pi:allDevs){
			try {
				final PcapHandle handle = pi.openLive(SNAPLEN, getMode(pi), READ_TIMEOUT); // 打开接口
				handleTable.put(pi, handle);
				final PacketListener listener= new PacketListener() {
					/**
					 * 此函数用于捕获流经指定接口的数据包
					 */
					@Override
					public void gotPacket(Packet packet) {

						try {
							if(packet instanceof EthernetPacket){
								EthernetPacket packet_eth=(EthernetPacket) packet;
								EthernetHeader head_eth=packet_eth.getHeader();
								
								// 判断是否是 PPP 类型的数据包
								if(head_eth.getType().value()==0xffff8864){
									ppp=true;
									PacketUtils.ppp=ppp;
								}
								
								IpV4Packet ipV4Packet=null;
								IpV4Header ipV4Header=null;
								
								// 分离 IPv4 数据
								if(ppp){
									ipV4Packet=getIpV4Packet_pppoe(packet_eth);
								}else {
									if(packet_eth.getPayload() instanceof IpV4Packet){
										ipV4Packet=(IpV4Packet) packet_eth.getPayload();
									}
								}
								if(ipV4Packet!=null){
									// 获取 IPv4 头部
									ipV4Header=ipV4Packet.getHeader();
									
									// 如果是 TCP 入站数据，则获取目的地址作为本接口的 IP 地址，同时获取MAC地址
									// 以 testIp_tcp 为过滤条件
									if(ipV4Header.getSrcAddr().getHostAddress().equals(testIp_tcp)){
										local_mac=head_eth.getDstAddr();
										gateway_mac=head_eth.getSrcAddr();
										local_ipv4=ipV4Header.getDstAddr();
										selectedInterfaceName=pi.getName();
										if(pi.getDescription()!=null){
											selectedInterfaceDes=pi.getDescription();
										}
										//MLog.println("local_mac_tcp1 "+gateway_mac+" gateway_mac "+gateway_mac+" local_ipv4 "+local_ipv4);
									}
									
									// TCP 出站数据，获取源地址作为本接口的 IP 地址
									if(ipV4Header.getDstAddr().getHostAddress().equals(testIp_tcp)){
										local_mac=head_eth.getSrcAddr();
										gateway_mac=head_eth.getDstAddr();
										local_ipv4=ipV4Header.getSrcAddr();
										selectedInterfaceName=pi.getName();
										if(pi.getDescription()!=null){
											selectedInterfaceDes=pi.getDescription();
										}
										//MLog.println("local_mac_tcp2 local_mac "+local_mac+" gateway_mac "+gateway_mac+" local_ipv4 "+local_ipv4);
									}
									
									// UDP 出站数据
									if(ipV4Header.getDstAddr().getHostAddress().equals(testIp_udp)){
										local_mac=head_eth.getSrcAddr();
										gateway_mac=head_eth.getDstAddr();
										local_ipv4=ipV4Header.getSrcAddr();
										selectedInterfaceName=pi.getName();
										if(pi.getDescription()!=null){
											selectedInterfaceDes=pi.getDescription();
										}
										//MLog.println("local_mac_udp "+gateway_mac+" gateway_mac"+gateway_mac+" local_ipv4 "+local_ipv4);
									}
								
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}

					}
				};

				// 开启线程，针对当前接口进行 数据包捕获
				Thread thread=new Thread(){

					public void run(){
						try {
							handle.loop(COUNT, listener);
							PcapStat ps = handle.getStats();
							handle.close();
						} catch (Exception e) {
							//e.printStackTrace();
						}
					}

				};
				thread.start();
			} catch (PcapNativeException e1) {
				
			}
			
		}
		
		// 开始进行外网测试，产生数据包，以便被上面的代码捕获
		//detectMac_udp();
		detectMac_tcp();
	
	
		// 停止所有接口的包捕获
		Iterator<PcapNetworkInterface> it=handleTable.keySet().iterator();
		while(it.hasNext()){
			PcapNetworkInterface pi=it.next();
			PcapHandle handle=handleTable.get(pi);
			try {
				handle.breakLoop();
			} catch (NotOpenException e) {
				e.printStackTrace();
			}
			//handle.close();//linux下会阻塞
		}
	}
	
	/**
	 * 从 PPPoE 包中提取 IPv4 数据
	 * 
	 * @param packet_eth
	 * @return
	 * @throws IllegalRawDataException
	 */
	IpV4Packet getIpV4Packet_pppoe(EthernetPacket packet_eth) throws IllegalRawDataException{
		IpV4Packet ipV4Packet=null;
		byte[] pppData=packet_eth.getPayload().getRawData();
		if(pppData.length>8&&pppData[8]==0x45){
			byte[] b2=new byte[2];
			System.arraycopy(pppData, 4, b2, 0, 2);
			short len=(short) ByteShortConvert.toShort(b2, 0);
			int ipLength=toUnsigned(len)-2;
			byte[] ipData=new byte[ipLength];
			//设置ppp参数
			PacketUtils.pppHead_static[2]=pppData[2];
			PacketUtils.pppHead_static[3]=pppData[3];
			if(ipLength==(pppData.length-8)){
				System.arraycopy(pppData, 8, ipData, 0, ipLength);
				ipV4Packet=IpV4Packet.newPacket(ipData, 0, ipData.length);
			}else {
				MLog.println("长度不符!");
			}
		}
		return ipV4Packet;
	}
	
	
	/**
	 * 打印十六进制字符串
	 * 
	 * @param b
	 * @return
	 */
	public static String printHexString(byte[] b) {
		StringBuffer sb=new StringBuffer();
        for (int i = 0; i < b.length; i++)
        {
            String hex = Integer.toHexString(b[i] & 0xFF);
            hex=  hex.replaceAll(":", " ");
            if (hex.length() == 1)
            {
                hex = '0' + hex;
            }
            sb.append(hex + " ");
        }
        return sb.toString();
    }
	
	public void createTcpTun_Client(String dstAddress,short dstPort) throws Exception{
		Inet4Address serverAddress=(Inet4Address) Inet4Address.getByName(dstAddress);
		TCPTun conn=new TCPTun(this,serverAddress,dstPort,local_mac,gateway_mac);
		tcpManager.addConnection_Client(conn);
		boolean success=false;
		for(int i=0;i<6;i++){
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			if(conn.preDataReady){
				success=true;
				break;
			}
		}
		if(success){
			tcpManager.setDefaultTcpTun(conn);
		}else {
			tcpManager.removeTun(conn);
			tcpManager.setDefaultTcpTun(null);
			throw new Exception("创建隧道失败!");
		}
	}
	
	/**
	 * zz函数之二：判断一个接口是否能够上外网，TCP 版
	 */
	private void detectMac_tcp() {
		InetAddress address=null;
		
		// 找个域名进行 DNS 解析
		try {
			address = InetAddress.getByName("bing.com");
		} catch (UnknownHostException e2) {
			e2.printStackTrace();
			try {
				address = InetAddress.getByName("163.com");
			} catch (UnknownHostException e) {
				e.printStackTrace();
				try {
					address = InetAddress.getByName("apple.com");
				} catch (UnknownHostException e1) {
					e1.printStackTrace();
				}
			}
		}
		if(address==null){
			MLog.println("域名解析失败,请检查DNS设置!");
		}
		final int por=80;
		testIp_tcp=address.getHostAddress(); // 获取解析出的 IP 地址
		
		// 循环操作5次，直至 获取到需要的信息
		for(int i=0;i<5;i++){
			try {
				Route.es.execute(new Runnable() {
					
					/**
					 * 判断是否可以创建此 IP 的 TCP 连接 (即判断是否能够上外网)
					 */
					@Override
					public void run() {
						try {
							// 创建 Socket 然后关闭，出现异常就表明无法连接
							// 理论上3次握手会被前面的代码捕获
							Socket socket=new Socket(testIp_tcp,por);
							socket.close();
						} catch (UnknownHostException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				});
				Thread.sleep(500);
				// 如果前面的抓包函数捕获到了数据包，那么 local_mac 等参数就会被设置
				// 然后就可以停止操作了
				if(local_mac!=null){
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				try {
					Thread.sleep(1);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}
	}

	/**
	 * zz函数之三：判断一个接口是否能够上外网，UDP 版
	 */
	private void detectMac_udp(){
		for(int i=0;i<10;i++){
			try {
				DatagramSocket ds=new DatagramSocket();
				DatagramPacket dp=new DatagramPacket(new byte[1000], 1000);
				dp.setAddress(InetAddress.getByName(testIp_udp));
				dp.setPort(5555);
				ds.send(dp);
				ds.close();
				Thread.sleep(500);
				if(local_mac!=null){
					break;
				}
			} catch (Exception e) {
				e.printStackTrace();
				try {
					Thread.sleep(1);
				} catch (InterruptedException e1) {
					e1.printStackTrace();
				}
			}
		}

	}

	public short getListenPort() {
		return listenPort;
	}

	public void setListenPort(short listenPort) {
		this.listenPort = listenPort;
		if(!client){
			MLog.info("Listen tcp port: "+toUnsigned(listenPort));
		}
	}
	
	public static int toUnsigned(short s) {  
	    return s & 0x0FFFF;  
	}
	
}
