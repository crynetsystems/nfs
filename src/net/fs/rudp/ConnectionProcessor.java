// Copyright (c) 2015 D1SM.net

package net.fs.rudp;


/**
 * ConnectionProcessor 接口
 *
 * @author hackpascal
 *
 */
public interface ConnectionProcessor {
	/**
	 * 用于处理 UDP 连接？
	 * 
	 * @param conn
	 */
	abstract void process(final ConnectionUDP conn);
}
