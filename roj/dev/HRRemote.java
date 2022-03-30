package roj.dev;

import roj.asm.Parser;
import roj.asm.tree.IClass;
import roj.io.NIOUtil;
import roj.net.PlainSocket;
import roj.net.misc.FDCLoop;
import roj.net.misc.FDChannel;
import roj.net.misc.Listener;
import roj.util.ByteList;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 热重载,支持一对多
 *     限制:
 *     1. 正在执行的函数, 下次调用才能起效
 *     2. 不能增加/修改/删除 方法/字段以及它们的类型/参数/访问级
 * @author Roj233
 * @since 2022/2/21 15:09
 */
public class HRRemote extends FDCLoop<HRRemote.Client> {
    public static final byte F_SILENT = 1, F_RESTART = 2;
    public static final byte R_OK = 0, R_ERR = 1, R_CRITICAL = 2;
    public static final int DEFAULT_PORT = 4485;

    final class Client extends FDChannel {
        private final ArrayList<ByteBuffer> packets = new ArrayList<>(2);

        @Override
        public void selected(int readyOps) throws Exception {
            if ((readyOps & SelectionKey.OP_WRITE) != 0) {
                if (!packets.isEmpty()) {
                    ByteBuffer b = packets.get(0);
                    try { ch.write(b); } catch (IOException e) {
                        close();
                        return;
                    }

                    if (!b.hasRemaining()) {
                        synchronized (packets) {
                            packets.remove(0);
                        }
                    }
                } else {
                    if (key.interestOps() == (SelectionKey.OP_WRITE | SelectionKey.OP_READ))
                        key.interestOps(SelectionKey.OP_READ);
                }
            }

            if ((readyOps & SelectionKey.OP_READ) != 0) {
                ByteBuffer buf = ch.buffer();

                try {
                    ch.read(buf.position() - 3);
                } catch (IOException e) {
                    close();
                    return;
                }

                if (buf.position() < 3) return;

                int data = buf.get(0) & 0xFF;
                int count = buf.getShort(1) & 0xFFFF;
                buf.clear();
                //System.out.println("重载结果: " + data + " 重载数量 " + count);
                if (data == R_CRITICAL) close();
            }
        }

        @Override
        public void close() throws IOException {
            key.cancel();
            ch.close();
            clients.remove(this);
        }

        public void accept(byte[] array) {
            if (!key.isValid()) return;
            key.interestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ);
            ByteBuffer data = ByteBuffer.wrap(array);
            synchronized (packets) {
                packets.add(data);
            }
        }
    }

    public void sendChanges(List<? extends IClass> modified) {
        if (modified.isEmpty()) return;
        if (modified.size() > 1000) throw new IllegalArgumentException("Too many classes modified");
        tmp.put((byte) 0x66).putShort(modified.size());
        for (int i = 0; i < modified.size(); i++) {
            IClass clz = modified.get(i);
            tmp.putJavaUTF(clz.name().replace('/', '.'));
            ByteList shared = Parser.toByteArrayShared(clz);
            tmp.putInt(shared.wIndex()).put(shared);
        }
        byte[] array = tmp.toByteArray();
        tmp.clear();

        for (Client client : clients) {
            client.accept(array);
        }
    }

    private final ByteList tmp;
    private final ConcurrentLinkedQueue<Client> clients;

    private final ServerSocket socket;
    public HRRemote(int port) throws IOException {
        super(null, "热重载服务器", 1, 1000, 0);
        this.clients = new ConcurrentLinkedQueue<>();
        this.tmp = new ByteList(2048);

        this.socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 100);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    public void start() throws IOException {
        registerListener(new Listener(socket) {
            @Override
            public void selected(int readyOps) throws Exception {
                Socket soc = socket.accept();
                Client t = new Client();
                t.ch = new PlainSocket(soc, NIOUtil.fd(soc));
                try {
                    register(t, null);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                clients.add(t);
            }
        });
    }
}
