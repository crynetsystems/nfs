// Copyright (c) 2015 D1SM.net

package net.fs.utils;

/**
 * int 类型转换库
 * 
 * @author hackpascal
 *
 */
public class ByteIntConvert {
    
	/**
	 * 将字节数组转换为 int 类型，大端序
	 * 
	 * @param b			要转换的字节数组
	 * @param offset	开始转换的偏移
	 * @return			返回转换出的 int 数据
	 */
    public static int toInt(byte[] b,int offset) { 
    	return b[offset + 3] & 0xff | (b[offset + 2] & 0xff) << 8
        | (b[offset + 1] & 0xff) << 16 | (b[offset] & 0xff) << 24;
    }
    
    /**
	 * 将 int 数据转换为字节数组，大端序
	 * 
	 * @param n			要转换的数据
	 * @param b			输出结果的字节数组
	 * @param offset	输出到数组的下标
	 */
    public static void toByteArray(int n,byte[] buf,int offset) {
    	buf[offset] = (byte) (n >> 24);
    	   buf[offset + 1] = (byte) (n >> 16);
    	   buf[offset + 2] = (byte) (n >> 8);
    	   buf[offset + 3] = (byte) n;
    }

}







