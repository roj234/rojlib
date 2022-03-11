package roj.net.misc;

import roj.net.WrappedSocket;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.SelectionKey;

/**
 * @author solo6975
 * @since 2022/1/24 23:35
 */
public abstract class FDChannel implements Selectable {
    public SelectionKey  key;
    public WrappedSocket ch;

    public FileDescriptor fd() throws IOException {
        return ch.fd();
    }

    protected FDChannel(WrappedSocket ch) {
        this.ch = ch;
    }

    protected FDChannel() {}
}
