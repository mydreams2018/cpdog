package cn.kungreat.boot.utils;


public class WebSocketResponse {
    /*
        FINTXT 文本的首个返回标记字节  代表 完成.文本信息
    * */
    private static final byte FINTXT = -127;

    /*
       返回websocket 前几位字节标记位   每次的长度不超过 65535字节 一次返回完的情况
   * */
    public static byte[] getBytes(byte[] bytes) {
        if (bytes.length < 126) {
            byte[] bts = new byte[2];
            bts[0] = FINTXT;
            bts[1] = (byte) bytes.length;
            return bts;
        } else if (bytes.length <= 65535) {
            byte[] bts = CutoverBytes.intToByteArray(bytes.length);
            bts[0] = FINTXT;
            bts[1] = 126;
            return bts;
        }else {
            throw new RuntimeException("响应给客户端的数据长度超过65535字节...");
        }
    }
}
