package cn.kungreat.test;

import cn.kungreat.boot.handler.WebSocketChannelInHandler;
import cn.kungreat.boot.utils.CutoverBytes;
import cn.kungreat.boot.utils.JdbcTemplate;
import cn.kungreat.boot.utils.JdbcUtils;

import java.security.MessageDigest;
import java.text.Collator;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.Locale;

public class Test2 {
    public static void main(String[] args) throws Exception {
//        String rt = "dGhlIHNhbXBsZSBub25jZQ==258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
//        MessageDigest instance = MessageDigest.getInstance("SHA");
//        instance.update(rt.getBytes("UTF-8"));
//        byte[] digest = instance.digest();
//        System.out.println(Base64.getEncoder().encodeToString(digest));
        //s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
        //s3pPLMBiTxaQ9kYGzzhZRbK+xOo=
//        byte bt = -12;
//        if(bt<0){
//            System.out.println("小0");
//        }
        byte[] bts = {0,-1,-1,-1};
        System.out.println(CutoverBytes.readInt(bts));
        //定义一个中文排序器
        Comparator c = Collator.getInstance(Locale.CHINA);

    }
}
