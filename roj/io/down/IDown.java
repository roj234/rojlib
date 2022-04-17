package roj.io.down;

import roj.concurrent.task.ITask;
import roj.io.FileUtil;
import roj.io.NIOUtil;
import roj.net.WrappedSocket;
import roj.net.http.HttpClient;
import roj.net.misc.FDChannel;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import static roj.io.down.Downloader.*;

/**
 * @author Roj233
 * @since 2022/2/28 21:49
 */
public abstract class IDown extends FDChannel implements ITask {
    IDown() {}

    RandomAccessFile file;
    HttpClient client;
    URL url;

    byte state;
    byte[] buf;

    IProgress progress;

    long begin = System.currentTimeMillis();

    abstract long getDownloaded();
    abstract long getRemain();
    abstract long getTotal();
    abstract long getAverageSpeed();

    @Override
    public final void selected(int readyOps) throws Exception {
        if (state > 0) {
            if (key == null) return;
            key.interestOps(0);
            HELP.pushTask(this);
            return;
        }

        try {
            idle = 0;

            if(progress != null && progress.wasShutdown()) {
                close();
                return;
            }

            SelectionKey key = this.key;
            if (key.interestOps() == (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                key.interestOps(SelectionKey.OP_READ);
            }

            WrappedSocket ch = this.ch;
            if (ch == null || ch.read() < 0) {
                retry();
                return;
            }

            selected0();
        } catch (Exception e) {
            retry();
        }
    }

    abstract void selected0() throws Exception;

    int idle, retry;

    @Override
    public final void tick(int elapsed) throws IOException {
        if ((idle += elapsed) > FileUtil.timeout) {
            retry();
        }
    }

    final void retry() throws IOException {
        idle = 0;
        HttpClient client = this.client;
        if ((progress == null || !progress.wasShutdown()) && retry++ < maxRetryCount && client != null) {
            state = 0;

            if (ch != null) {
                try {
                    ch.close();
                } catch (IOException ignored) {}
                ch = null;
            }

            retry0();
            // reconnect
            client.disconnect();
            // 这个不会, 因为无效的SelectionKey会在下次循环被删除
            POOL.unregister(this);
            // 直接init会触发ConcurrentModificationException
            // 因为新的SelectionKey会被立即加入
            // 上锁也没用, 这就是在iterator里添加的故事
            HELP.pushTask(this);
        } else {
            if (progress != null) progress.shutdown();
            close();
        }
    }

    void retry0() throws IOException {}

    @Override
    public final boolean isDone() {
        return false;
    }

    public final void waitFor() throws InterruptedException {
        HttpClient client = this.client;
        if (client == null) return;
        synchronized (client) {
            if (idle >= 0) client.wait();
        }
    }

    final void reg() throws Exception {
        try {
            POOL.register(this, null);
        } catch (Exception e) {
            if (progress != null) progress.shutdown();
            throw e;
        }
    }

    @Override
    public final void calculate() throws Exception {
        try {
            if (progress != null && progress.wasShutdown()) {
                state = -1;
                return;
            }

            switch (state) {
                case 0:
                    int port = url.getPort();
                    client.createSocket(url.getHost(), port < 0 ? url.getDefaultPort() : port, false);
                    ch = client.getAsyncChannel();

                    InetSocketAddress ep = client.getEndpoint();
                    InetAddress addr = ep.getAddress();
                    if (addr.isAnyLocalAddress()) {
                        addr = InetAddress.getLocalHost();
                    }

                    NIOUtil.connect(ch.fd(), addr, ep.getPort());
                    state = 1;
                    reg();
                    key.interestOps(SelectionKey.OP_CONNECT);
                    return;
                case 1:
                    client.setConnected();
                    setClientInfo(client);
                    state = 2;
                    key.interestOps(SelectionKey.OP_READ);
                    return;
                case 2:
                    setClientInfo(client);
                    ByteBuffer buf = ch.buffer();

                    // 随着http头接收到的还可能有一些数据,设置为'可写'selected能够被调用
                    // 因为这里不是selector线程所以不能直接调用
                    key.interestOps(buf.position() > 0 ?
                                        SelectionKey.OP_READ | SelectionKey.OP_WRITE :
                                        SelectionKey.OP_READ);
                    state = -1;
            }
        } catch (Throwable e) {
            retry();
        }
    }

    protected abstract void setClientInfo(HttpClient client) throws Exception;
}
