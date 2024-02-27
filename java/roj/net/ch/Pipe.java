package roj.net.ch;

import roj.io.NIOUtil;
import roj.io.buf.BufferPool;
import roj.util.DynByteBuf;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import static roj.io.NIOUtil.UNAVAILABLE;

/**
 * @author Roj233
 * @since 2021/12/24 23:27
 */
public final class Pipe implements Selectable {
	public Object att;
	private SelectionKey upKey, downKey;

	private SocketChannel up, down;
	private DynByteBuf pendUp, pendDown;
	public int idleTime;

	// state / flag
	public long uploaded, downloaded;
	public boolean closeDown = true;
	private int altWriteCheck;

	public Pipe() {}

	public final boolean isUpstreamEof() {return up == null || !up.isOpen();}
	public final boolean isDownstreamEof() {return down == null || !down.isOpen();}

	public final void setUp(MyChannel ctx) throws IOException {
		pendDown = ctx.i_getBuffer();
		if (!pendDown.isReadable()) pendDown = null;
		else {
			pendDown = BufferPool.buffer(true, pendDown.readableBytes()).put(pendDown);
			cipher(pendDown, true);
		}
		up = (SocketChannel) ctx.i_outOfControl();
	}
	public SocketChannel getUp() {return up;}

	public final void setDown(MyChannel ctx) throws IOException {
		pendUp = ctx.i_getBuffer();
		if (!pendUp.isReadable()) pendUp = null;
		else {
			pendUp = BufferPool.buffer(true, pendUp.readableBytes()).put(pendUp);
			cipher(pendUp, false);
		}
		down = (SocketChannel) ctx.i_outOfControl();
	}
	public SocketChannel getDown() {return down;}

	public void reset() {
		if (pendUp != null) BufferPool.reserve(pendUp);
		if (pendDown != null) BufferPool.reserve(pendDown);
		pendUp = pendDown = null;

		idleTime = 0;
	}

	@Override
	public void register(Selector sel, int ops, Object att) throws IOException {
		if (up == null || down == null) throw new IllegalStateException("U/D is empty");

		if (upKey != null && up.keyFor(sel) != upKey) upKey.cancel();
		if (downKey != null && down.keyFor(sel) != downKey) downKey.cancel();

		// remove old keys (in MyChannel)
		sel.selectNow();

		upKey = up.register(sel, pendUp == null ? SelectionKey.OP_READ : SelectionKey.OP_WRITE, att);
		downKey = down.register(sel, pendDown == null ? SelectionKey.OP_READ : SelectionKey.OP_WRITE, att);
	}

	public void selected(int readyOps) throws IOException {
		if (up == null || down == null) return;

		int c;
		if (pendUp != null) {
			try {
				c = write(up, pendUp);

				SelectionKey k = upKey;
				if (k == null) k = DummySelectionKey.INSTANCE;

				if (!pendUp.isReadable()) {
					BufferPool.reserve(pendUp);
					pendUp = null;

					k.interestOps(SelectionKey.OP_READ);
				}

				if (c > 0) {
					uploaded += c;
					altWriteCheck &= ~2;
				} else if (c < 0) {
					close();
					return;
				} else if (k.isWritable()) {
					k.interestOps(0);
					altWriteCheck |= 2;
				}
			} catch (IOException e) {
				close();
				return;
			}
		}

		if (pendDown != null) {
			try {
				c = write(down, pendDown);

				SelectionKey k = downKey;
				if (k == null) k = DummySelectionKey.INSTANCE;

				if (!pendDown.isReadable()) {
					BufferPool.reserve(pendDown);
					pendDown = null;

					k.interestOps(SelectionKey.OP_READ);
				}

				if (c > 0) {
					downloaded += c;
					altWriteCheck &= ~1;
				} else if (c < 0) {
					close();
					return;
				} else if (k.isWritable()) {
					k.interestOps(0);
					altWriteCheck |= 1;
				}
			} catch (IOException e) {
				close();
				return;
			}
		}

		BufferPool pool = BufferPool.localPool();
		DynByteBuf tmp = pool.allocate(true, 1536);

		// region up=>down
		if (pendDown == null) {
			try {
				c = read(up, tmp);
				cipher(tmp, true);
				if (c > 0) {
					idleTime = 0;
					write(down, tmp);
				} else if (c < 0) {
					close();
					return;
				}
			} catch (IOException e) {
				close();
				return;
			}

			if (tmp.isReadable()) {
				pendDown = tmp;
				tmp = pool.allocate(true, 1536);

				SelectionKey k = downKey;
				if (k != null) k.interestOps(SelectionKey.OP_WRITE);
			} else {tmp.clear();}
		}
		// endregion
		// region down=>up
		if (pendUp == null) {
			try {
				c = read(down, tmp);
				cipher(tmp, false);
				if (c > 0) {
					write(up, tmp);
				} else if (c < 0) {
					closeDown();
					return;
				}
			} catch (IOException e) {
				closeDown();
				return;
			}
		}

		if (tmp.isReadable()) {
			pendUp = tmp;

			SelectionKey k = upKey;
			if (k != null) k.interestOps(SelectionKey.OP_WRITE);
		} else {
			BufferPool.reserve(tmp);
		}
		// endregion
	}

	private void cipher(DynByteBuf tmp, boolean toDown) {}

	@Override
	public void tick(int elapsed) throws IOException {
		idleTime += elapsed;

		if (elapsed > 0 && altWriteCheck != 0) selected(0);
	}

	private void closeDown() throws IOException {
		if (closeDown) {
			try {
				down.close();
			} finally {
				down = null;
			}
		} else {
			close();
		}
	}

	public synchronized final void close() throws IOException {
		try {
			if (up != null) up.close();
			if (down != null) down.close();

			reset();
		} finally {
			pendUp = pendDown = null;
			up = down = null;
		}
	}

	@Override
	public String toString() {
		return "Pipe{" + "att=" + att + ", idle=" + idleTime + ", U=" + uploaded + ", D=" + downloaded + '}';
	}

	private static int read(SocketChannel ch, DynByteBuf dst) throws IOException {
		if (!ch.isOpen()) throw new ClosedChannelException();

		if (!dst.ensureWritable(1)) return 0;

		FileDescriptor fd = NIOUtil.tcpFD(ch);
		int r;
		NIOUtil.LLIO util = NIOUtil.tcpFdRW();
		try {
			do {
				r = util.read(fd, dst.address() + dst.wIndex(), dst.capacity() - dst.wIndex());
			} while (r == -3 && ch.isOpen());
		} catch (Throwable e) {
			ch.close();
			throw e;
		}

		if (r > 0) dst.wIndex(dst.wIndex() + r);
		else if (r == UNAVAILABLE) return 0;
		else if (r < 0) ch.shutdownInput();
		return r;
	}

	private static int write(SocketChannel ch, DynByteBuf src) throws IOException {
		if (!ch.isOpen()) throw new ClosedChannelException();

		FileDescriptor fd = NIOUtil.tcpFD(ch);
		int w;
		NIOUtil.LLIO util = NIOUtil.tcpFdRW();
		try {
			do {
				w = util.write(fd, src.address() + src.rIndex, src.readableBytes());
			} while (w == -3 && ch.isOpen() && fd.valid());
		} catch (Throwable e) {
			ch.close();
			throw e;
		}

		if (w > 0) src.rIndex += w;
		else if (w == UNAVAILABLE) return 0;
		else if (w < 0) ch.shutdownOutput();
		return w;
	}
}