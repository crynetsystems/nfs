// Copyright (c) 2015 D1SM.net

package net.fs.utils;

import java.net.DatagramPacket;


/**
 * fs 报文检查类
 * 
 * @author hackpascal
 *
 */
public class MessageCheck{
	/**
	 * 获取 fs 报文的版本
	 * 
	 * @param dp	fs 报文 (偏移 0h)
	 * @return		fs 报文版本
	 */
	public static int checkVer(DatagramPacket dp){
		int ver=ByteShortConvert.toShort(dp.getData(), 0);
		return ver;
	}
	
	/**
	 * 获取 fs 报文类型
	 * 
	 * @param dp	fs 报文 (偏移 2h)
	 * @return		fs 报文类型
	 */
	public static int checkSType(DatagramPacket dp){
		int sType=ByteShortConvert.toShort(dp.getData(), 2);
		return sType;
	}
}
