package cn.kungreat.boot.tls;

import lombok.Getter;
import lombok.Setter;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

@Setter
@Getter
public class TLSSocketLink {
    private SSLEngine engine;
    private ByteBuffer inSrc;
    private ByteBuffer outEnc;

    public TLSSocketLink(SSLEngine engine, ByteBuffer inSrc, ByteBuffer out) {
        this.engine = engine;
        this.inSrc = inSrc;
        this.outEnc = out;
    }
}
