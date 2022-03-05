import cn.kungreat.boot.NioBossServerSocket;
import cn.kungreat.boot.impl.NioBossServerSocketImpl;

public class test {
    public static void main(String[] args) {
        NioBossServerSocket nioBossServerSocket = NioBossServerSocketImpl.create();
        nioBossServerSocket.buildThread();
        nioBossServerSocket.start(9999);
        System.out.println();
    }
}
