package roj.net.misc;

import roj.net.WrappedSocket;

import java.nio.channels.SelectionKey;

/**
 * @author solo6975
 * @since 2022/1/24 23:35
 */
public abstract class FDChannel implements Selectable {
    public SelectionKey  key;
    public WrappedSocket ch;

    @Override
    public boolean isClosedOn(SelectionKey key) {
        return ch == null;
    }

    protected FDChannel(WrappedSocket ch) {
        this.ch = ch;
    }

    protected FDChannel() {}
}
