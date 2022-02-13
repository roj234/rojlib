package roj.misc;

import roj.crypt.CipheR;
import roj.io.NIOUtil;
import roj.net.MSSSocket;
import roj.net.NetworkUtil;
import roj.net.misc.Pipe;
import roj.net.misc.PipeIOThread;
import roj.net.mss.*;

import java.io.File;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;

/**
 * @author Roj234
 * @since 2022/2/7 17:35
 */
public class MSSProxier implements Runnable {
    public static void main(String[] args) throws IOException, GeneralSecurityException {
        if (args.length < 3) {
            System.out.println("客户端: MSSProxier <local_port> <remote_domain_or_ip> <remote_port>");
            System.out.println("服务端: MSSProxier <local_port> <remote_domain_or_ip> <remote_port> <key_password>");
            System.out.println(" proxy via MSS connection");
            return;
        }
        MSSProxier inst = new MSSProxier(new InetSocketAddress(Integer.parseInt(args[0])), 100);
        inst.remote = new InetSocketAddress(InetAddress.getByName(args[1]), Integer.parseInt(args[2]));
        if (args.length > 3) {
            KeyPair pair = NetworkUtil.genAndStoreRSAKey(new File("mssproxy.key"),
                                                         new File("mssproxy_client.key"),
                                                         args[3].getBytes(StandardCharsets.UTF_8));
            if (pair == null) {
                System.err.println("Failed to generate keypair, exiting");
                return;
            }

            SimpleEngineFactory f = new SimpleEngineFactory(JKeyFormat.JAVARSA, pair.getPublic(), pair.getPrivate());
            f.setPSK(new MSSKeyPair[] {
                new SimplePSK(233, pair.getPublic(), pair.getPrivate())
            });
            inst.alloc = f;
        } else {
            NetworkUtil.MSSLoadClientRSAKey(new File("mssproxy_client.key"));
        }
        inst.run();
    }

    final ServerSocket socket;
    public InetSocketAddress remote;
    MSSEngineFactory alloc;

    public MSSProxier(InetSocketAddress address, int conn) throws IOException {
        ServerSocket socket = this.socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(address, conn);
    }

    @Override
    public void run() {
        while (true) {
            Socket c;
            try {
                c = socket.accept();
            } catch (IOException e) {
                break;
            }

            try {
                c.setReuseAddress(true);
                PipeIOThread.syncRegister(null, new ProxyPipe(NIOUtil.fd(c), connect(), alloc != null), null);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private MSSSocket connect() throws IOException, GeneralSecurityException {
        Socket tar = new Socket();
        tar.connect(remote, 2400);
        return new MSSSocket(tar, NIOUtil.fd(tar), alloc == null ? new MSSEngineClient() : alloc.newEngine());
    }

    public void stop() throws IOException {
        socket.close();
    }

    static final class ProxyPipe extends Pipe {
        MSSSocket mss;
        CipheR enc, dec;
        final ByteBuffer tmp;
        final boolean s;

        public ProxyPipe(FileDescriptor u, MSSSocket d, boolean server) {
            super(u, d.fd());
            this.mss = d;
            tmp = ByteBuffer.allocate(BUFFER_CAPACITY);
            this.s = server;
        }

        @Override
        public void selected(int readyOps) throws IOException {
            if (upstream == null || downstream == null) return;
            if (mss != null) {
                downKey.interestOps(0);
                upKey.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                if (!mss.handShake()) return;
                downKey.interestOps(SelectionKey.OP_READ);
                upKey.interestOps(SelectionKey.OP_READ);
                enc = s ? mss.getDecrypter() : mss.getEncrypter();
                dec = s ? mss.getEncrypter() : mss.getDecrypter();
                mss = null;
            }
            transfer(false);
        }

        @Override
        protected final void doCipher() throws GeneralSecurityException {
            ByteBuffer target = toh;
            ByteBuffer tmp = this.tmp;
            if (target.hasRemaining()) {
                tmp.clear();
                enc.crypt(target, tmp);
                tmp.flip();
                target.clear();
                target.put(tmp).flip();
            }

            target = toc;
            if (target.hasRemaining()) {
                tmp.clear();
                dec.crypt(target, tmp);
                tmp.flip();
                target.clear();
                target.put(tmp).flip();
            }
        }
    }
}
