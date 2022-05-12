package cn.kungreat.boot.tsl;

import lombok.Getter;
import lombok.Setter;

import javax.net.ssl.SSLEngine;
import java.nio.ByteBuffer;

@Setter
@Getter
public class TSLSocketLink {
    private SSLEngine engine;
    private ByteBuffer inSrc;
    private ByteBuffer outEnc;

    public TSLSocketLink(SSLEngine engine, ByteBuffer inSrc, ByteBuffer out) {
        this.engine = engine;
        this.inSrc = inSrc;
        this.outEnc = out;
    }
}
