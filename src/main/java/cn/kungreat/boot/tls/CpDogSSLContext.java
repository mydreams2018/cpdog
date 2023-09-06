package cn.kungreat.boot.tls;

import cn.kungreat.boot.CpdogMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class CpDogSSLContext {
    private static final Logger LOGGER = LoggerFactory.getLogger(CpDogSSLContext.class);
    public static final ConcurrentHashMap<Integer, TLSSocketLink> TLS_SOCKET_LINK = new ConcurrentHashMap<>(1024);
    public static SSLContext context = null;
    //用来存放复用的 TLSSocketLink
    public static final LinkedBlockingQueue<TLSSocketLink> REUSE_TLS_SOCKET_LINK = new LinkedBlockingQueue<>(1024);
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

    public static void reuseTLSSocketLink(int channelHash){
        TLSSocketLink remove = CpDogSSLContext.TLS_SOCKET_LINK.remove(channelHash);
        if(remove != null){
            remove.clear();
            REUSE_TLS_SOCKET_LINK.offer(remove);
        }
    }

    public static TLSSocketLink getSSLEngine(SocketChannel socketChannel) throws Exception {
        SSLEngine engine = context.createSSLEngine();
        engine.setUseClientMode(false);
        engine.setNeedClientAuth(false);
        engine.beginHandshake();
        if (doHandshake(socketChannel, engine)) {
            LOGGER.info("tls握手完成:");
            ShakeHands.CpdogThread currentThread = (ShakeHands.CpdogThread) Thread.currentThread();
            ByteBuffer changeInSrc = currentThread.getInSrc();
            changeInSrc.flip();
            // 可能有读取多的没有用完的数据、需要转换到channel所绑定的TLSSocketLink中去
            // [很少发生. 在极端的情况下数据读完了、后续不会触发work对象注册的-read事件、会造成websocket握手没有触发.数据是在的]
            TLSSocketLink poll = REUSE_TLS_SOCKET_LINK.poll();
            if(poll != null){
                //复用 TLSSocketLink  减少 创建/GC
                poll.getInSrc().put(changeInSrc);
                poll.setEngine(engine);
                TLS_SOCKET_LINK.put(socketChannel.hashCode(),poll);
                return poll;
            }
            ByteBuffer inSrc = ByteBuffer.allocate(32768).put(changeInSrc);
            TLSSocketLink tlsSocketLink = new TLSSocketLink(engine,inSrc,ByteBuffer.allocate(32768));
            TLS_SOCKET_LINK.put(socketChannel.hashCode(),tlsSocketLink);
            return tlsSocketLink;
        } else{
            LOGGER.error("tls握手失败:");
            engine.closeOutbound();
            socketChannel.close();
        }
        return null;
    }

    private static boolean doHandshake(SocketChannel socketChannel, SSLEngine engine) throws Exception {
        LOGGER.info("TLS开始握手...");
        ShakeHands.CpdogThread currentThread = (ShakeHands.CpdogThread) Thread.currentThread();
        ByteBuffer inSrc = currentThread.getInSrc();
        ByteBuffer inSrcDecode = currentThread.getInSrcDecode();
        ByteBuffer outSrcEncode = currentThread.getOutSrcEncode();
        inSrc.clear();
        inSrcDecode.clear();
        outSrcEncode.clear();

        SSLEngineResult.HandshakeStatus handshakeStatus = engine.getHandshakeStatus();
        while (handshakeStatus != SSLEngineResult.HandshakeStatus.FINISHED) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    int read = socketChannel.read(inSrc);
                    if(read == 0){
                        /*  结构可以优化... */
                        if(!inSrc.hasRemaining()){
                            LOGGER.info("原始数据扩容:{}",inSrc.capacity()*2);
                            ByteBuffer temsrc = ByteBuffer.allocate(inSrc.capacity()*2);
                            inSrc.flip();
                            temsrc.put(inSrc);
                            currentThread.setInSrc(temsrc);
                            inSrc = temsrc;
                        }
                    }
                    inSrc.flip();
                    SSLEngineResult unwrap = engine.unwrap(inSrc,inSrcDecode);
                    inSrc.compact();
                    changeInStates(engine,unwrap,inSrc,inSrcDecode);
                    handshakeStatus = unwrap.getHandshakeStatus();
                    if(read == -1){
                        LOGGER.info("握手关闭了、流==-1 调用closeInbound");
                        engine.closeInbound();
                        handshakeStatus = engine.getHandshakeStatus();
                    }
                    break;
                case NEED_WRAP:
                    inSrcDecode.flip();
                    SSLEngineResult wrap = engine.wrap(inSrcDecode, outSrcEncode);
                    inSrcDecode.compact();
                    changeOutStates(engine,wrap,outSrcEncode,socketChannel);
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
                    inSrc.flip();
                    SSLEngineResult unwrapAgain = engine.unwrap(inSrc,inSrcDecode);
                    inSrc.compact();
                    changeInStates(engine,unwrapAgain,inSrc,inSrcDecode);
                    handshakeStatus = unwrapAgain.getHandshakeStatus();
                    break;
                case NOT_HANDSHAKING:
                    return false;
            }
            if(engine.isOutboundDone()){
                return false;
            }
            inSrcDecode = currentThread.getInSrcDecode();
            inSrc = currentThread.getInSrc();
            outSrcEncode = currentThread.getOutSrcEncode();
        }
        return true;
    }

    private static void changeInStates(SSLEngine engine, SSLEngineResult sslEngineResult,ByteBuffer inSrc,ByteBuffer inSrcDecode) throws SSLException {
        switch (sslEngineResult.getStatus()) {
            case BUFFER_OVERFLOW:
                LOGGER.info("扩容入站解密数据:{}",inSrcDecode.capacity());
                int applicationBufferSize = engine.getSession().getApplicationBufferSize();
                ByteBuffer b = ByteBuffer.allocate(applicationBufferSize + inSrcDecode.position());
                inSrcDecode.flip();
                b.put(inSrcDecode);
                ShakeHands.CpdogThread currentThreadIn = (ShakeHands.CpdogThread) Thread.currentThread();
                currentThreadIn.setInSrcDecode(b);
                break;
            case BUFFER_UNDERFLOW:
                int netSize = engine.getSession().getPacketBufferSize();
                if (netSize > inSrc.capacity()) {
                    LOGGER.info("扩容入站src数据:{}",inSrc.capacity());
                    ByteBuffer srcGrow = ByteBuffer.allocate(netSize);
                    inSrc.flip();
                    srcGrow.put(inSrc);
                    ShakeHands.CpdogThread currentThreadInSrc = (ShakeHands.CpdogThread) Thread.currentThread();
                    currentThreadInSrc.setInSrc(srcGrow);
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

    private static void changeOutStates(SSLEngine engine,SSLEngineResult sslEngineResult, ByteBuffer outSrcDecode
                                         ,SocketChannel socketChannel) throws Exception {
        switch (sslEngineResult.getStatus()) {
            case BUFFER_OVERFLOW:
                LOGGER.info("扩容出站加密数据:{}",outSrcDecode.capacity());
                ByteBuffer buf = ByteBuffer.allocate(outSrcDecode.capacity() * 2);
                outSrcDecode.flip();
                buf.put(outSrcDecode);
                ShakeHands.CpdogThread currentThreadOut = (ShakeHands.CpdogThread) Thread.currentThread();
                currentThreadOut.setOutSrcEncode(buf);
                break;
            case BUFFER_UNDERFLOW:
                LOGGER.error("outTLS-我不认为我们应该到这里");
                break;
            case OK:
                outSrcDecode.flip();
                if(outSrcDecode.hasRemaining()){
                    socketChannel.write(outSrcDecode);
                }
                outSrcDecode.clear();
                break;
            case CLOSED:
                if(!engine.isOutboundDone()){
                    outSrcDecode.flip();
                    if(outSrcDecode.hasRemaining()){
                        socketChannel.write(outSrcDecode);
                    }
                    outSrcDecode.clear();
                    engine.closeOutbound();
                }
        }
    }

    public static ByteBuffer inDecode(TLSSocketLink socketLink, ByteBuffer decode, int spin) throws SSLException {
        spin--;
        ByteBuffer inSrc = socketLink.getInSrc();
        SSLEngine engine = socketLink.getEngine();
        SSLEngineResult unwrap = engine.unwrap(inSrc, decode);
        switch (unwrap.getStatus()) {
            case BUFFER_OVERFLOW:
                LOGGER.info("read-扩容入站解密数据:{}",decode.capacity());
                int applicationBufferSize = engine.getSession().getApplicationBufferSize();
                ByteBuffer b = ByteBuffer.allocate(applicationBufferSize + decode.position());
                decode.flip();
                b.put(decode);
                return inDecode(socketLink,b,spin);//扩容后尝试再次转换
            case BUFFER_UNDERFLOW:
                int netSize = engine.getSession().getPacketBufferSize();
                if (netSize > inSrc.capacity()) {
                    LOGGER.info("read-扩容入站src数据:{}",inSrc.capacity());
                    ByteBuffer srcGrow = ByteBuffer.allocate(netSize);
                    srcGrow.put(inSrc);
                    srcGrow.flip();
                    socketLink.setInSrc(srcGrow);
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
            /* 需要优化...自旋  */
            return inDecode(socketLink,decode,spin);
        }
        return decode;
    }

    public static void outEncode(SocketChannel socketChannel,ByteBuffer outSrc) throws Exception {
        TLSSocketLink tlsSocketLink = TLS_SOCKET_LINK.get(socketChannel.hashCode());
        SSLEngine engine = tlsSocketLink.getEngine();
        ByteBuffer outEnc = CpdogMain.THREAD_LOCAL.get();
        SSLEngineResult wrap = engine.wrap(outSrc,outEnc);
        switch (wrap.getStatus()){
            case BUFFER_OVERFLOW:
                LOGGER.info("out-扩容出站加密数据:{}",outEnc.capacity());
                ByteBuffer buf = ByteBuffer.allocate(outEnc.capacity() * 2);
                outEnc.flip();
                buf.put(outEnc);
                CpdogMain.THREAD_LOCAL.set(buf);
                outEncode(socketChannel,outSrc);
                return;
            case BUFFER_UNDERFLOW:
                LOGGER.error("out-我不认为我们应该到这里");
                break;
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
            LOGGER.error("多次调用了outEncode--");
            outEncode(socketChannel,outSrc);
        }
    }
}
