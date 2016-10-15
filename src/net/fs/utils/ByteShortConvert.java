// Copyright (c) 2015 D1SM.net

package net.fs.utils;

/**
 * short 类型转换库
 * 
 * @author hackpascal
 *
 */
public final class ByteShortConvert {
   
	/**
	 * 将 short 数据转换为字节数组，大端序
	 * 
	 * @param i			要转换的数据
	 * @param b			输出结果的字节数组
	 * @param offset	输出到数组的下标
	 * @return			返回参数 b
	 */
    public static byte[] toByteArray(short i,byte[] b,int offset) {
    	 b[offset] = (byte) (i >> 8);
    	   b[offset + 1] = (byte) (i >> 0);
        return b; 
    }


    /**字节数组转换为 short 数据，大端序
     * 
     * @param b			要转换的字节数组
     * @param offset	开始转换的偏移
     * @return			返回转换出的 short 数据
     */
    public static short toShort(byte[] b,int offset) { 
        return  (short) (((b[offset] << 8) | b[offset + 1] & 0xff)); 
    }    
    

	/**
	 * 将 short 数据转换为字节数组，大端序
	 * 
	 * @param i			要转换的数据
	 * @param b			输出结果的字节数组
	 * @param offset	输出到数组的下标
	 * @return			返回参数 b
	 */
    public static byte[] toByteArrayUnsigned(int s,byte[] b,int offset) {
   	 b[offset] = (byte) (s >> 8);
   	   b[offset+1] = (byte) (s >> 0);
       return b; 
   }
    
    /**字节数组转换为 unsigned short 数据，大端序
     * 
     * @param b			要转换的字节数组
     * @param offset	开始转换的偏移
     * @return			返回转换出的 short 数据
     */
    public static int toShortUnsigned(byte[] b,int offset) {
    	int i = 0;
    	i |= b[offset+0] & 0xFF;
    	i <<= 8;
    	i |= b[offset+1] & 0xFF;
    	return i;
    }    
    
     
}
