package cn.kungreat.test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;


public class test {
    public static void main(String[] args) throws IOException {
        //        ByteBuffer byteBuffer = ByteBuffer.allocate(12);
//        byteBuffer.put("dfsdfsdfsfsf".getBytes());
//        System.out.println(byteBuffer.capacity());
//        System.out.println(byteBuffer.remaining());
        byte[] bytes = "刘大胖".getBytes(StandardCharsets.UTF_8);
        ByteBuffer wrap = ByteBuffer.wrap(bytes, 0, bytes.length - 1);
        CharBuffer charBuffer = CharBuffer.allocate(256);
        Charset charset = Charset.forName("UTF-8");
        CoderResult decode = charset.newDecoder().decode(wrap, charBuffer, false);
        System.out.println(charBuffer.flip());
        if(decode.isError()){
            throw new RuntimeException("编解码错误");
        }
        System.out.println(decode.isMalformed());
        System.out.println(decode.isOverflow());
        System.out.println(decode.isUnderflow());
    }
}
