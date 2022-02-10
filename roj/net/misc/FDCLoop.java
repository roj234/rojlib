package roj.net.misc;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

/**
 * @author solo6975
 * @since 2022/1/24 23:27
 */
public class FDCLoop<T extends FDChannel> extends NIOSelectLoop<T> {
    public FDCLoop(Shutdownable owner, String prefix, int maxThreads, int idleKill, int threshold) {
        super(owner, prefix, 1, maxThreads, idleKill, threshold);
    }
    public FDCLoop(Shutdownable owner, String prefix, int minThreads, int maxThreads, int idleKill, int threshold) {
        super(owner, prefix, minThreads, maxThreads, idleKill, threshold);
    }

    @Override
    public void unregister(T h) throws IOException {
        if (h.key != null) {
            h.key.cancel();
            h.key = null;
        }
    }

    @Override
    protected void register1(Selector sel, T h, Object att) throws IOException {
        FileDescriptorChannel fdc = new FileDescriptorChannel(h.ch.fd());
        h.key = fdc.register(sel, SelectionKey.OP_READ, att);
    }
}
