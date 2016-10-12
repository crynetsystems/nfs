// Copyright (c) 2015 D1SM.net

// Copyright (c) 2015 D1SM.net

package net.fs.server;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.BindException;

import net.fs.rudp.ConnectionProcessor;
import net.fs.rudp.Route;
import net.fs.utils.MLog;

public class FSServer {

	ConnectionProcessor imTunnelProcessor;

	/**
	 * Route 实例，全局变量，防止因为构造函数执行完毕而被销毁
	 */
	Route route_udp, route_tcp, route;

	/**
	 * 服务端监听端口
	 */
	int routePort = 150;

	/**
	 * 这个变量是程序执行时创建的，是 fs 的根基，UDP 模式
	 */
	static FSServer udpServer;

	/**
	 * 操作系统名称，还转换成了小写
	 */
	String systemName = System.getProperty("os.name").toLowerCase();

	boolean success_firewall_windows=true;

  //主函数负责构造一个FSServer然后就什么都没有做了
	public static void main(String[] args) {
		try {
			FSServer fs = new FSServer();
		} catch (Exception e) {
			e.printStackTrace();
			if(e instanceof BindException){
				MLog.println("Udp port already in use.");
			}
			MLog.println("Start failed.");
			System.exit(0);
		}
	}

	static FSServer get() {
		return udpServer;
	}

	//构造函数起始位置
	public FSServer() throws Exception {
		MLog.info("");
		MLog.info("FinalSpeed server starting... ");
		MLog.info("System Name: " + systemName);
		udpServer = this;
		final MapTunnelProcessor mp = new MapTunnelProcessor();//此处应该是整个服务端的核心，参见./src/net/fs/server/MapTunnelProcessor.java

		String port_s = readFileData("./cnf/listen_port");//读取监听的端口
		if (port_s != null && !port_s.trim().equals("")) {
			port_s = port_s.replaceAll("\n", "").replaceAll("\r", "");
			routePort = Integer.parseInt(port_s);
		}
		
		// Route 才是重点
		/*
		 * 这是我见过最 SB 的设计：
		 * 首先 61 行 final MapTunnelProcessor mp = new MapTunnelProcessor();
		 * 创建了 MapTunnelProcessor 的一个实例  mp
		 * 通过 mp.getClass().getName() 获取这个实例对应的类的完整名称 "net.fs.server.MapTunnelProcessor"
		 * 然后将这个实例的完整类名传递给了 Route 类的构造函数
		 * 然后 Route 类记录下这个类名
		 * 然后在后期 Route 类的 createTunnelProcessor 函数会被调用 (ConnectionUDP 类的构造函数里)
		 * 这个函数的作用就是根据那个类名重新创建这个类 (MapTunnelProcessor) 的实例，并作为返回值返回
		 * 。。。
		 * 这个 mp 变量的作用
		 * 。。。
		 * 就是
		 * 。。。
		 * 获取一个完整的类名 net.fs.server.MapTunnelProcessor 而已
		 * 。。。
		 * 然后
		 * 。。。
		 * 它就没有作用了
		 * 。。。
		 * 最后因为函数执行完毕，离开作用域而被销毁
		 */
		route_udp = new Route(mp.getClass().getName(), (short) routePort, Route.mode_server, false,true);
		
		/* -- 以下代码可忽略 -- */
		if (systemName.equals("linux")) {//根据不同的操作系统配置防火墙
			startFirewall_linux();//打开iptables
			setFireWall_linux_udp();//给iptables开UDP端口
		}else if(systemName.contains("windows")){
			startFirewall_windows();//配置windows防火墙
		}
		/* -- 以上代码可忽略 -- */

		Route.es.execute(new Runnable() {

			/**
			 * 基于 TCP 的连接另开一个线程
			 */
			@Override
			public void run() {
				try {
					route_tcp = new Route(mp.getClass().getName(), (short) routePort, Route.mode_server, true,true);
					
					
					/* -- 以下代码可忽略 -- */
					if (systemName.equals("linux")) {
						setFireWall_linux_tcp();//给iptables开TCP端口
					}else if(systemName.contains("windows")){
						if(success_firewall_windows){
							setFireWall_windows_tcp();
						}else{
							System.out.println("启动windows防火墙失败,请先运行防火墙服务.");
						}
					}
					/* -- 以上代码可忽略 -- */
					
				} catch (Exception e) {
					// e.printStackTrace();
				}
			}
		});

	}
  //构造函数终止位置
	void startFirewall_windows(){

		String runFirewall="netsh advfirewall set allprofiles state on";
		Thread standReadThread=null;
		Thread errorReadThread=null;
		try {
			final Process p = Runtime.getRuntime().exec(runFirewall,null);
			standReadThread=new Thread(){
				public void run(){
					InputStream is=p.getInputStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true){
						String line;
						try {
							line = localBufferedReader.readLine();
							if (line == null){
								break;
							}else{
								if(line.contains("Windows")){
									success_firewall_windows=false;
								}
							}
						} catch (IOException e) {
							e.printStackTrace();
							//error();
							break;
						}
					}
				}
			};
			standReadThread.start();

			errorReadThread=new Thread(){
				public void run(){
					InputStream is=p.getErrorStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true){
						String line;
						try {
							line = localBufferedReader.readLine();
							if (line == null){
								break;
							}else{
								System.out.println("error"+line);
							}
						} catch (IOException e) {
							e.printStackTrace();
							//error();
							break;
						}
					}
				}
			};
			errorReadThread.start();
		} catch (IOException e) {
			e.printStackTrace();
			success_firewall_windows=false;
			//error();
		}

		if(standReadThread!=null){
			try {
				standReadThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(errorReadThread!=null){
			try {
				errorReadThread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

	}

	void setFireWall_windows_tcp() {
		cleanRule_windows();
		try {
			if(systemName.contains("xp")||systemName.contains("2003")){
				String cmd_add1="ipseccmd -w REG -p \"tcptun_fs_server\" -r \"Block TCP/"+routePort+"\" -f *+0:"+routePort+":TCP "+" -n BLOCK -x ";
				final Process p2 = Runtime.getRuntime().exec(cmd_add1,null);
				p2.waitFor();
			}else {
				String cmd_add1="netsh advfirewall firewall add rule name=tcptun_fs_server protocol=TCP dir=out localport="+routePort+" action=block ";
				final Process p2 = Runtime.getRuntime().exec(cmd_add1,null);
				p2.waitFor();
				String cmd_add2="netsh advfirewall firewall add rule name=tcptun_fs_server protocol=TCP dir=in localport="+routePort+" action=block ";
				Process p3 = Runtime.getRuntime().exec(cmd_add2,null);
				p3.waitFor();
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	void cleanRule_windows(){
		try {
			if(systemName.contains("xp")||systemName.contains("2003")){
				String cmd_delete="ipseccmd -p \"tcptun_fs_server\" -w reg -y";
				final Process p1 = Runtime.getRuntime().exec(cmd_delete,null);
				p1.waitFor();
			}else {
				String cmd_delete="netsh advfirewall firewall delete rule name=tcptun_fs_server ";
				final Process p1 = Runtime.getRuntime().exec(cmd_delete,null);
				p1.waitFor();
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	void startFirewall_linux() {
		String cmd1 = "service iptables start";
		runCommand(cmd1);
	}

	void setFireWall_linux_udp() {
		cleanUdpTunRule();
		String cmd2 = "iptables -I INPUT -p udp --dport " + routePort + " -j ACCEPT"
				+ " -m comment --comment udptun_fs_server";
		runCommand(cmd2);
	}

	void cleanUdpTunRule() {
		while (true) {
			int row = getRow("udptun_fs_server");
			if (row > 0) {
				// MLog.println("删除行 "+row);
				String cmd = "iptables -D INPUT " + row;
				runCommand(cmd);
			} else {
				break;
			}
		}
	}

	void setFireWall_linux_tcp() {
		cleanTcpTunRule();
		String cmd2 = "iptables -I INPUT -p tcp --dport " + routePort + " -j DROP"
				+ " -m comment --comment tcptun_fs_server ";
		runCommand(cmd2);

	}

	void cleanTcpTunRule() {
		while (true) {
			int row = getRow("tcptun_fs_server");
			if (row > 0) {
				// MLog.println("删除行 "+row);
				String cmd = "iptables -D INPUT " + row;
				runCommand(cmd);
			} else {
				break;
			}
		}
	}

	int getRow(String name) {
		int row_delect = -1;
		String cme_list_rule = "iptables -L -n --line-number";
		// String [] cmd={"netsh","advfirewall set allprofiles state on"};
		Thread errorReadThread = null;
		try {
			final Process p = Runtime.getRuntime().exec(cme_list_rule, null);

			errorReadThread = new Thread() {
				public void run() {
					InputStream is = p.getErrorStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true) {
						String line;
						try {
							line = localBufferedReader.readLine();
							if (line == null) {
								break;
							} else {
								// System.out.println("erroraaa "+line);
							}
						} catch (IOException e) {
							e.printStackTrace();
							// error();
							break;
						}
					}
				}
			};
			errorReadThread.start();

			InputStream is = p.getInputStream();
			BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
			while (true) {
				String line;
				try {
					line = localBufferedReader.readLine();
					// System.out.println("standaaa "+line);
					if (line == null) {
						break;
					} else {
						if (line.contains(name)) {
							int index = line.indexOf("   ");
							if (index > 0) {
								String n = line.substring(0, index);
								try {
									if (row_delect < 0) {
										// System.out.println("standaaabbb
										// "+line);
										row_delect = Integer.parseInt(n);
									}
								} catch (Exception e) {

								}
							}
						}
						;
					}
				} catch (IOException e) {
					e.printStackTrace();
					break;
				}
			}

			errorReadThread.join();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			// error();
		}
		return row_delect;
	}

	void runCommand(String command) {
		Thread standReadThread = null;
		Thread errorReadThread = null;
		try {
			final Process p = Runtime.getRuntime().exec(command, null);
			standReadThread = new Thread() {
				public void run() {
					InputStream is = p.getInputStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true) {
						String line;
						try {
							line = localBufferedReader.readLine();
							// System.out.println("stand "+line);
							if (line == null) {
								break;
							}
						} catch (IOException e) {
							e.printStackTrace();
							break;
						}
					}
				}
			};
			standReadThread.start();

			errorReadThread = new Thread() {
				public void run() {
					InputStream is = p.getErrorStream();
					BufferedReader localBufferedReader = new BufferedReader(new InputStreamReader(is));
					while (true) {
						String line;
						try {
							line = localBufferedReader.readLine();
							if (line == null) {
								break;
							} else {
								// System.out.println("error "+line);
							}
						} catch (IOException e) {
							e.printStackTrace();
							// error();
							break;
						}
					}
				}
			};
			errorReadThread.start();
			standReadThread.join();
			errorReadThread.join();
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
			// error();
		}
	}

	String readFileData(String path) {
		String content = null;
		FileInputStream fis = null;
		DataInputStream dis = null;
		try {
			File file = new File(path);
			fis = new FileInputStream(file);
			dis = new DataInputStream(fis);
			byte[] data = new byte[(int) file.length()];
			dis.readFully(data);
			content = new String(data, "utf-8");
		} catch (Exception e) {
			// e.printStackTrace();
		} finally {
			if (dis != null) {
				try {
					dis.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		return content;
	}

	public int getRoutePort() {
		return routePort;
	}

}
