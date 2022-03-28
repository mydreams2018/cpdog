package cn.kungreat.boot.utils;


public class CutoverBytes {

    /***
     * @param a 数值
     * @return 把数值转换成byte数组
     */
    public static byte[] intToByteArray(int a) {
        return new byte[] {
                (byte) ((a >> 24)),
                (byte) ((a >> 16) & 255),
                (byte) ((a >> 8)  & 255),
                (byte) ((a >> 0)  & 255)
        };
    }
    /***
     * @param a 数值
     * @return 把数值转换成byte数组
     */
    public static byte[] longToByteArray(long a) {
        return new byte[] {
                (byte) ((a >> 56)),
                (byte) ((a >> 48)  & 255),
                (byte) ((a >> 40)  & 255),
                (byte) ((a >> 32)  & 255),
                (byte) ((a >> 24)  & 255),
                (byte) ((a >> 16)  & 255),
                (byte) ((a >> 8)   & 255),
                (byte) ((a >> 0)   & 255)
        };
    }
    /***
     * @param bytes 数组
     * @return 把数组转换成long数值
     */
    public static long readLong(byte[] bytes) {
        return (((long)bytes[0] << 56) +
                ((long)(bytes[1] & 255) << 48) +
                ((long)(bytes[2] & 255) << 40) +
                ((long)(bytes[3] & 255) << 32) +
                ((long)(bytes[4] & 255) << 24) +
                ((bytes[5] & 255) << 16) +
                ((bytes[6] & 255) <<  8) +
                ((bytes[7] & 255) <<  0));
    }
    /***
     * @param bytes 数组
     * @return 把数组转换成int数值 必须4个byte
     */
    public static int readInt(byte... bytes) {
        return ((bytes[0] << 24) +
                ((bytes[1] & 255) << 16) +
                ((bytes[2] & 255) <<  8) +
                ((bytes[3] & 255) <<  0));
    }
}
