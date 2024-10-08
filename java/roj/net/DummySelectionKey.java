package roj.net;

import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

final class DummySelectionKey extends SelectionKey {
	static final SelectionKey INSTANCE = new DummySelectionKey();

	@Override
	public SelectableChannel channel() {throw new UnsupportedOperationException();}

	@Override
	public Selector selector() {throw new UnsupportedOperationException();}

	@Override
	public boolean isValid() {return false;}

	@Override
	public void cancel() {}

	@Override
	public int interestOps() {return 0;}

	@Override
	public SelectionKey interestOps(int ops) {
		if ((ops & SelectionKey.OP_READ) == 0) throw new CancelledKeyException();
		return this;
	}

	@Override
	public int readyOps() {return 0;}
}