// Copyright (c) 2015 D1SM.net

package net.fs.rudp;

/**
 * Reliable UDP 配置信息
 * @author hackpascal
 *
 */
public class RUDPConfig {

	/**
	 * 协议版本，至少目前而言都是 0
	 */
	public static short protocal_ver=0;

	/**
	 * 每个 UDP 的负载大小，字节
	 */
	public static int packageSize=1000;
	
	public static boolean twice_udp=false;
	
	public static boolean twice_tcp=false;
	
	/**
	 * 最大窗口大小？
	 */
	public static int maxWin = 5*1024;
	
	public static int ackListDelay = 5;
	public static int ackListSum = 300;
	
	/**
	 * 起始数据发送两次？
	 */
	public static boolean double_send_start = true;
	
	public static int reSendDelay_min = 100;
	public static float reSendDelay = 0.6f;
	
	/**
	 * 最大重发包次数
	 */
	public static int reSendTryTimes = 10;

}
