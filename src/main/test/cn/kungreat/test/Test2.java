package cn.kungreat.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
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
//        byte[] bts = {0,-1,-1,-1};
//        System.out.println(CutoverBytes.readInt(bts));
//        //定义一个中文排序器
//        Comparator c = Collator.getInstance(Locale.CHINA);

        FileReader fileReader = new FileReader(new File("D:\\kungreat\\IdeaProjects\\log1\\sys-info.log"), StandardCharsets.UTF_8);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        bufferedReader.lines().forEach(e ->{
            if(e.contains("WebSocketChannelOutHandler")){
                System.out.println(e.substring(e.length() - 3).trim());
            }
        });
//        System.out.println(bufferedReader.lines().filter(e->e.contains("WebSocketChannelOutHandler")).count());

    }
}
