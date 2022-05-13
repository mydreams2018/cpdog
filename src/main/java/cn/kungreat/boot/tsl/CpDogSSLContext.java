package cn.kungreat.boot.tsl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

public class CpDogSSLContext {
    private static final Logger logger = LoggerFactory.getLogger(CpDogSSLContext.class);
    public static final ConcurrentHashMap<Integer,TSLSocketLink> TSL_SOCKET_LINK = new ConcurrentHashMap<>(1024);
    public static SSLContext context = null;

    static {
        try {
            // System.setProperty("javax.net.debug", "all"); 显示网络通信的详情信息
            context = SSLContext.getInstance("TLSv1.2");
            context.init(keyManagerFactory("7740639_www.kungreat.cn.jks", "2Hxvob35", "2Hxvob35"),
                    trustManagerFactory("7740639_www.kungreat.cn.jks", "2Hxvob35"),
                    SecureRandom.getInstance("SHA1PRNG"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /*    此类充当基于密钥材料源的关键经理的工厂。每个密钥管理器管理特定类型的密钥材料，
    以供安全套接字使用。密钥材料基于密钥库和 或 提供程序特定的源。
        SunX509  KeyManagerFactory.getDefaultAlgorithm();
    */
    public static KeyManager[] keyManagerFactory(String fileName, String keystorePassword, String keyPassword) throws Exception {
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(ClassLoader.getSystemResourceAsStream(fileName),keystorePassword.toCharArray());
        keyManagerFactory.init(keyStore, keyPassword.toCharArray());
        return keyManagerFactory.getKeyManagers();
    }

    /*    此类充当基于信任材料源的信任管理器的工厂。每个信任管理器管理特定类型的信任材料，
 以供安全套接字使用。信任材料基于密钥库和/或特定于提供程序的源。
        TrustManagerFactory.getDefaultAlgorithm()  PKIX
 */
    public static TrustManager[] trustManagerFactory(String fileName, String keystorePassword) throws Exception {
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(ClassLoader.getSystemResourceAsStream(fileName), keystorePassword.toCharArray());
        trustManagerFactory.init(keyStore);
        return trustManagerFactory.getTrustManagers();
    }

    public static TSLSocketLink getSSLEngine(SocketChannel socketChannel) throws Exception {
        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(false);
        engine.beginHandshake();
        if (doHandshake(socketChannel, engine)) {
            logger.info("tsl握手完成:");
            TSLSocketLink tslSocketLink = new TSLSocketLink(engine,ByteBuffer.allocate(32768),ByteBuffer.allocate(32768));
            TSL_SOCKET_LINK.put(socketChannel.hashCode(),tslSocketLink);
            return tslSocketLink;
        } else{
            logger.info("tsl握手失败:");
            engine.closeOutbound();
            socketChannel.close();
        }
        return null;
    }

    private static boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        logger.info("TSL开始握手...");
        ShakeHands.CpdogThread currentThread = (ShakeHands.CpdogThread) Thread.currentThread();
        ByteBuffer insrc = currentThread.getInsrc();
        ByteBuffer insrcDecode = currentThread.getInsrcDecode();
        ByteBuffer outsrc = currentThread.getOutsrc();
        ByteBuffer outsrcEncode = currentThread.getOutsrcEncode();
        insrc.clear();
        insrcDecode.clear();
        outsrc.clear();
        outsrcEncode.clear();

        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    int read = socketChannel.read(insrc);
                    if(read == 0){
                        if(!insrc.hasRemaining()){
                            logger.info("原始数据扩容:{}",insrc.capacity()*2);
                            ByteBuffer temsrc = ByteBuffer.allocate(insrc.capacity()*2);
                            insrc.flip();
                            temsrc.put(insrc);
                            currentThread.setInsrc(temsrc);
                        }
                    }
                    insrc.flip();
                    SSLEngineResult unwrap = engine.unwrap(insrc,insrcDecode);
                    insrc.compact();
                    changeInStates(engine,unwrap,insrc,insrcDecode);
                    handshakeStatus = unwrap.getHandshakeStatus();
                    if(read == -1){
                        logger.info("握手关闭了、流==-1 调用closeInbound");
                        engine.closeInbound();
                        handshakeStatus = engine.getHandshakeStatus();
                    }
                    break;
                case NEED_WRAP:
                    insrcDecode.flip();
                    SSLEngineResult wrap = engine.wrap(insrcDecode, outsrcEncode);
                    insrcDecode.compact();
                    changeOutStates(engine,wrap,outsrcEncode,socketChannel);
                    handshakeStatus = wrap.getHandshakeStatus();
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        task.run();
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                case NEED_UNWRAP_AGAIN:
                    insrc.flip();
                    SSLEngineResult unwrapAgaing = engine.unwrap(insrc,insrcDecode);
                    insrc.compact();
                    changeInStates(engine,unwrapAgaing,insrc,insrcDecode);
                    handshakeStatus = unwrapAgaing.getHandshakeStatus();
                    break;
                case FINISHED:
                    return true;
                case NOT_HANDSHAKING:
                    return false;
            }
            if(engine.isOutboundDone()){
                return false;
            }
            insrcDecode = currentThread.getInsrcDecode();
            insrc = currentThread.getInsrc();
            outsrcEncode = currentThread.getOutsrcEncode();
        }
        return true;
    }

    private static void changeInStates(SSLEngine engine, SSLEngineResult sslEngineResult,ByteBuffer insrc,ByteBuffer insrcDecode) throws SSLException {
        switch (sslEngineResult.getStatus()) {
            case BUFFER_OVERFLOW:
                logger.info("扩容入站解密数据:{}",insrcDecode.capacity());
                int applicationBufferSize = engine.getSession().getApplicationBufferSize();
                ByteBuffer b = ByteBuffer.allocate(applicationBufferSize + insrcDecode.position());
                insrcDecode.flip();
                b.put(insrcDecode);
                ShakeHands.CpdogThread currentThreadIn = (ShakeHands.CpdogThread) Thread.currentThread();
                currentThreadIn.setInsrcDecode(b);
                break;
            case BUFFER_UNDERFLOW:
                int netSize = engine.getSession().getPacketBufferSize();
                if (netSize > insrcDecode.capacity() && netSize > insrc.capacity()) {
                    logger.info("扩容入站src数据:{}",insrc.capacity());
                    ByteBuffer srcg = ByteBuffer.allocate(netSize);
                    insrc.flip();
                    srcg.put(insrc);
                    ShakeHands.CpdogThread currentThreadInSrc = (ShakeHands.CpdogThread) Thread.currentThread();
                    currentThreadInSrc.setInsrc(srcg);
                }
                break;
            case OK:
                break;
            case CLOSED:
                if (!engine.isInboundDone()) {
                    engine.closeInbound();
                }
        }

    }

    private static void changeOutStates(SSLEngine engine,SSLEngineResult sslEngineResult, ByteBuffer outsrcDecode
                                         ,SocketChannel socketChannel) throws Exception {
        switch (sslEngineResult.getStatus()) {
            case BUFFER_OVERFLOW:
                logger.info("扩容出站加密数据:{}",outsrcDecode.capacity());
                ByteBuffer buf = ByteBuffer.allocate(outsrcDecode.capacity() * 2);
                outsrcDecode.flip();
                buf.put(outsrcDecode);
                ShakeHands.CpdogThread currentThreadOut = (ShakeHands.CpdogThread) Thread.currentThread();
                currentThreadOut.setOutsrcEncode(buf);
                break;
            case BUFFER_UNDERFLOW:
                throw new RuntimeException("outssl-我不认为我们应该到这里");
            case OK:
                outsrcDecode.flip();
                if(outsrcDecode.hasRemaining()){
                    socketChannel.write(outsrcDecode);
                }
                outsrcDecode.clear();
                break;
            case CLOSED:
                if(!engine.isOutboundDone()){
                    outsrcDecode.flip();
                    if(outsrcDecode.hasRemaining()){
                        socketChannel.write(outsrcDecode);
                    }
                    outsrcDecode.clear();
                    engine.closeOutbound();
                }
        }
    }

    public static ByteBuffer inDecode(TSLSocketLink socketLink,ByteBuffer decode,int spin) throws SSLException {
        spin--;
        ByteBuffer inSrc = socketLink.getInSrc();
        SSLEngine engine = socketLink.getEngine();
        SSLEngineResult unwrap = engine.unwrap(inSrc, decode);
        switch (unwrap.getStatus()) {
            case BUFFER_OVERFLOW:
                logger.info("read-扩容入站解密数据:{}",decode.capacity());
                int applicationBufferSize = engine.getSession().getApplicationBufferSize();
                ByteBuffer b = ByteBuffer.allocate(applicationBufferSize + decode.position());
                decode.flip();
                b.put(decode);
                return inDecode(socketLink,b,spin);//扩容后尝试再次转换
            case BUFFER_UNDERFLOW:
                int netSize = engine.getSession().getPacketBufferSize();
                if (netSize > decode.capacity() && netSize > inSrc.capacity()) {
                    logger.info("read-扩容入站src数据:{}",inSrc.capacity());
                    ByteBuffer srcg = ByteBuffer.allocate(netSize);
                    srcg.put(inSrc);
                    socketLink.setInSrc(srcg);
                }
                break;
            case OK:
                break;
            case CLOSED:
                if (!engine.isInboundDone()) {
                    engine.closeInbound();
                }
        }
        if(inSrc.hasRemaining() && spin > 0){
            /* 由于解密的特殊性、必需自旋一定的数量.因为可能收到数据全、但是它解密认为数据不全.也有可能真的不全..  */
            return inDecode(socketLink,decode,spin);
        }
        return decode;
    }

    public static void outEncode(SocketChannel socketChannel,ByteBuffer outSrc) throws Exception {
        TSLSocketLink tslSocketLink = TSL_SOCKET_LINK.get(socketChannel.hashCode());
        SSLEngine engine = tslSocketLink.getEngine();
        ByteBuffer outEnc = tslSocketLink.getOutEnc();
        SSLEngineResult wrap = engine.wrap(outSrc,outEnc);
        switch (wrap.getStatus()){
            case BUFFER_OVERFLOW:
                logger.info("out-扩容出站加密数据:{}",outEnc.capacity());
                ByteBuffer buf = ByteBuffer.allocate(outEnc.capacity() * 2);
                outEnc.flip();
                buf.put(outEnc);
                tslSocketLink.setOutEnc(buf);
                outEncode(socketChannel,outSrc);
                return;
            case BUFFER_UNDERFLOW:
                throw new RuntimeException("out-我不认为我们应该到这里");
            case OK:
                outEnc.flip();
                if(outEnc.hasRemaining()){
                    socketChannel.write(outEnc);
                }
                outEnc.clear();
                break;
            case CLOSED:
                if(!engine.isOutboundDone()){
                    outEnc.flip();
                    if(outEnc.hasRemaining()){
                        socketChannel.write(outEnc);
                    }
                    outEnc.clear();
                    engine.closeOutbound();
                }
        }
        if(outSrc.hasRemaining() && !engine.isOutboundDone()){
            logger.info("多次调用了outEncode--");
            outEncode(socketChannel,outSrc);
        }
    }
}
