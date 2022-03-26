package roj.io.down;

import roj.concurrent.task.ITask;
import roj.io.FileUtil;
import roj.net.http.HttpClient;
import roj.net.misc.FDChannel;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URL;
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

    boolean init;
    byte[] buf;

    IProgress progress;

    final long begin = System.currentTimeMillis();

    abstract long getDownloaded();
    abstract long getRemain();
    abstract long getTotal();
    abstract long getAverageSpeed();

    @Override
    public final void selected(int readyOps) throws Exception {
        if (init) {
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

            if (key.interestOps() == (SelectionKey.OP_READ | SelectionKey.OP_WRITE)) {
                key.interestOps(SelectionKey.OP_READ);
            }

            if (ch.read() < 0) {
                retry();
                return;
            }

            selected0();
        } catch (Exception e) {
            e.printStackTrace();
            if (progress != null) progress.shutdown();
            throw e;
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
        if (retry++ < maxRetryCount) {
            retry0();
            // reconnect
            HttpClient client = this.client;
            if (client == null) return;
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
}
