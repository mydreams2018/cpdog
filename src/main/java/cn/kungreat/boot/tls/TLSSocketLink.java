package cn.kungreat.boot.tls;

import cn.kungreat.boot.em.ProtocolState;
import lombok.Getter;
import lombok.Setter;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

@Setter
@Getter
public class TLSSocketLink {
    private SSLEngine engine;
    private ByteBuffer inSrc;
    private ByteBuffer inSrcDecode;
    private ProtocolState protocolState = null;

    public TLSSocketLink(SSLEngine engine, ByteBuffer inSrc,ByteBuffer inSrcDecode) {
        this.engine = engine;
        this.inSrc = inSrc;
        this.inSrcDecode = inSrcDecode;
    }
}
