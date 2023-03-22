package cn.kungreat.test;

import java.nio.ByteBuffer;

public class Test3 {
    public static void main(String[] args) {
        ByteBuffer a = ByteBuffer.allocate(1024);
        ByteBuffer b = ByteBuffer.allocate(1024);
        b.put((byte) 55);
        b.put((byte) 56);
        b.flip();
        a.put(b);
        a.flip();//[pos=0 lim=2 cap=1024]
        a.compact();//[pos=2 lim=1024 cap=1024]
        System.out.println(a);
    }
}
