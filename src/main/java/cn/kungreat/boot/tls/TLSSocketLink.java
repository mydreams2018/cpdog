package cn.kungreat.boot.tls;

import cn.kungreat.boot.em.ProtocolState;
import lombok.Getter;
import lombok.Setter;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

/*
 * 一个套接字连接 关联一个此对象
 * */
@Setter
@Getter
public class TLSSocketLink {
    /*
     * 加密 解密 的引擎
     * */
    private SSLEngine engine;
    /*
     * 从套接字读取的源数据
     * */
    private ByteBuffer inSrc;
    /*
     * 从源数据解密后的数据
     * */
    private ByteBuffer inSrcDecode;
    /*
     * websocket 协议完成标识
     * */
    private ProtocolState protocolState = null;

    public TLSSocketLink(SSLEngine engine, ByteBuffer inSrc, ByteBuffer inSrcDecode) {
        this.engine = engine;
        this.inSrc = inSrc;
        this.inSrcDecode = inSrcDecode;
    }

    public void clear() {
        this.engine = null;
        this.protocolState = null;
        this.inSrc.clear();
        this.inSrcDecode.clear();
    }
}
