package roj.net.handler;

import roj.collect.HashBiMap;
import roj.collect.ArrayList;
import roj.io.FastFailException;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.net.*;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NoConnectionPendingException;
import java.util.Iterator;

import static roj.util.ByteList.EMPTY;

public class uTP implements ChannelHandler {
	static final int REQ=1,ACK=2,RST=3,DTA=4,WIN=5,RET=6,SHUT=7;
	static final int SEND=0,WAIT=1,OPEN=2, LOCAL_CLOSE =3,CLOSE=4;
	static final int MTU=1540;

	// TODO this is not finished


	HashBiMap<DatagramPkt, Ctx> connections = new HashBiMap<>();

	@Override
	public void channelClosed(ChannelCtx ctx) throws IOException {
		HashBiMap<DatagramPkt, Ctx> c = connections;
		connections = null;

		Exception e = null;
		for (Ctx embed : c.values()) {
			try {
				embed.close();
			} catch (Exception e1) {
				if (e == null) e = e1;
				else e.addSuppressed(e1);
			}
		}
		if (e != null) Helpers.athrow(e);
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DatagramPkt pkt = (DatagramPkt) msg;

		Ctx embed = connections.get(pkt);
		if (embed == null) {
			pkt.data = IOUtil.getSharedByteBuf().putShort(0).put(RST);
			ctx.channelWrite(pkt);
			return;
		}

		try {
			embed.protocolRead(pkt.data);
		} catch (RuntimeException e) {
			throw new IOException("Protocol error: " + e.getMessage());
		}
	}

	@Override
	public void channelTick(ChannelCtx ctx) throws Exception {
		for (Iterator<Ctx> itr = connections.values().iterator(); itr.hasNext(); ) {
			Ctx c = itr.next();
			c.tick(1);
			if (!c.isOpen()) itr.remove();
		}
	}

	@Override
	public void onEvent(ChannelCtx ctx, Event event) throws IOException {
		if (event.id.equals(EmbeddedChannel.EMBEDDED_CLOSE)) {
			connections.removeByValue((Ctx) event.getData());
		}
	}

	protected void TOUInitSubChannel(Ctx embedded) throws IOException {

	}

	public MyChannel protocol_connect(InetSocketAddress address, int timeout) throws IOException {
		Ctx ctx = new Ctx(null);
		ctx.connect(address, timeout);

		connections.put(ctx.pkt, ctx);
		TOUInitSubChannel(ctx);

		return ctx;
	}

	public static final class Ctx extends MyChannel {
		static final int IN_TICK=1,SENDED=2,SEND_ACK=4,SEND_RET=8;

		final MyChannel owner;
		DatagramPkt pkt;
		int prot_state, prot_flag;
		long timeout;

		ArrayList<DynByteBuf> rcv_seqs = new ArrayList<>();
		int rcv_seq,rcv_win;

		DynByteBuf outBuf = EMPTY;
		int outBufMax;
		ArrayList<DynByteBuf> snd_seqs = new ArrayList<>();
		int snd_seq,snd_remote_seq,snd_win;

		int mtu, half_acceptable_latency;
		long timer;
		int limit;

		public Ctx(MyChannel owner) {
			this.owner = owner;
			rb = EMPTY;

			mtu = 1472;
			half_acceptable_latency = 10;

			outBufMax = mtu*12;
		}

		@Override
		public boolean isOpen() {
			return state < CLOSED && prot_state < CLOSE;
		}
		@Override
		public boolean isInputOpen() {
			return state < CLOSED && prot_state < LOCAL_CLOSE;
		}
		@Override
		public boolean isOutputOpen() {
			return state < CLOSED && prot_state < CLOSE;
		}

		@Override
		public SocketAddress remoteAddress() {
			return null;
		}

		@Override
		public <T> MyChannel setOption(SocketOption<T> k, T v) throws IOException {
			throw new UnsupportedOperationException("not implemented");
		}
		@Override
		public <T> T getOption(SocketOption<T> k) throws IOException {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		public void disconnect0() throws IOException {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		protected void bind0(InetSocketAddress na) throws IOException {
			throw new UnsupportedOperationException("not implemented");
		}

		@Override
		public boolean connect0(InetSocketAddress address) throws IOException {
			if (state != INITIAL) throw new IOException("Must be INITIAL state");

			switch (prot_state) {
				case SEND:
					prot_state = WAIT;
					pkt = new DatagramPkt(address, null);

					protocolSend(IOUtil.getSharedByteBuf().put(REQ));

					if (timeout > 0) this.timeout = System.currentTimeMillis()+timeout;
					state = CONNECT_PENDING;
					return false;
				case WAIT: throw new ConnectionPendingException();
				case OPEN: throw new AlreadyConnectedException();
				default: throw new ClosedChannelException();
			}
		}
		@Override
		public SocketAddress finishConnect0() throws IOException {
			if (state != CONNECT_PENDING) throw new NoConnectionPendingException();
			switch (prot_state) {
				case SEND:
					throw new NoConnectionPendingException();
				case WAIT:
					if (timeout != 0 && System.currentTimeMillis() > timeout)
						throw new IOException("no further information");
					return null;
				case OPEN:
					return remoteAddress();
				default:
					throw new IOException("connection reset");
			}
		}

		@Override
		protected void write(Object o) throws IOException {
			BufferPool bp = alloc();
			DynByteBuf data = (DynByteBuf) o;
			if (!data.isDirect()) {
				data = bp.allocate(true, data.readableBytes()).put(data);
			}

			try {
				int w = writeData(data);
				if ((prot_flag&1) != 0) prot_flag |= 2;

				if (data.isReadable()) {
					pending.ringAddLast(bp.allocate(true, data.readableBytes()).put(data));
				} else {
					fireFlushed();
				}
			} finally {
				if (o != data) BufferPool.reserve(data);
			}
		}

		@Override
		public void tick(int elapsed) throws Exception {
			if (state >= CLOSED) return;
			if (state == CONNECT_PENDING && timeout != 0 && System.currentTimeMillis() > timeout) {
				close();
				throw new FastFailException(timeout);
			}

			if (prot_state < CLOSE && timer != 0 && System.currentTimeMillis() - timer >= half_acceptable_latency) {
				protocolSend(IOUtil.getSharedByteBuf().put(RET).putShort(rcv_seq));
				timer = 0;
			}

			prot_flag |= IN_TICK;
			try {
				super.tick(elapsed);
			} finally {
				if ((prot_flag&SENDED) == 0) sendData();
				prot_flag ^= IN_TICK;
			}
		}

		@Override
		public void flush() throws IOException {
			if (pending.isEmpty()||state>=CLOSED) return;

			BufferPool bp = alloc();
			lock.lock();
			try {
				do {
					DynByteBuf buf = (DynByteBuf) pending.peekFirst();
					if (buf == null) break;

					int w = writeData(buf);

					if (!buf.isReadable()) {
						pending.pollFirst();
						BufferPool.reserve(buf);
					} else {
						break;
					}
				} while (true);

				if (pending.isEmpty()) {
					flag &= ~(PAUSE_FOR_FLUSH|TIMED_FLUSH);
					fireFlushed();
				}
			} finally {
				lock.unlock();
			}
		}

		@Override
		protected void read() throws IOException {}

		@Override
		protected void closeHandler() throws IOException {
			if (prot_state == OPEN) {
				protocolSend(IOUtil.getSharedByteBuf().put(RST));
				state = CLOSED;
			}

			// cleanup
			BufferPool p = alloc();

			if (outBuf.capacity()>0){
				BufferPool.reserve(outBuf);
				outBuf = EMPTY;
			}
			for (int i = rcv_seqs.size() - 1; i >= 0; i--) {
				DynByteBuf b = rcv_seqs.get(i);
				if (b!=null) BufferPool.reserve(b);
			}
			rcv_seqs.clear();
			for (int i = snd_seqs.size() - 1; i >= 0; i--) {
				BufferPool.reserve(snd_seqs.get(i));
			}
			snd_seqs.clear();

			super.closeHandler();
		}
		@Override
		public void closeGracefully0() throws IOException {
			if (state < LOCAL_CLOSE) {
				if (state < OPEN) {
					close();
					return;
				}

				protocolSend(IOUtil.getSharedByteBuf().put(SHUT));
				state = LOCAL_CLOSE;
			}
		}

		void protocolRead(DynByteBuf buf) throws IOException {
			int seq = buf.readUnsignedShort();
			if (seq == rcv_seq || buf.getU(buf.rIndex) == RST) {
				flag |= SEND_ACK;

				int i = 0;
				if (!rcv_seqs.isEmpty()) {
					// count how many packets received
					while (i < rcv_seqs.size()) {
						if (rcv_seqs.get(i) == null) break;
						i++;
					}
					rcv_seq += i;

					protocolRead1(buf);

					BufferPool bp = alloc();
					i = 0;
					while (i < rcv_seqs.size()) {
						buf = rcv_seqs.get(i);
						if (buf == null) break;
						protocolRead1(buf);

						BufferPool.reserve(buf);
						i++;
					}

					rcv_seqs.removeRange(0, i);
				} else {
					protocolRead1(buf);
				}

				// send ack
				if ((flag & SEND_ACK) != 0) {
					protocolSend(IOUtil.getSharedByteBuf().put((ACK|(OPEN<<4))).putShort(rcv_seq));
					flag &= ~SEND_ACK;
				}

				// next
				rcv_seq++;
			} else {
				flag |= SEND_RET;
				timer = System.currentTimeMillis();

				seq -= rcv_seq;
				// previous packet (may send by RET)
				if (seq < 0) {
					limit++;
					return;
				}
				limit += 1 + seq / 8;

				rcv_seqs.ensureCapacity(seq);
				rcv_seqs._setSize(Math.max(seq,rcv_seqs.size()));

				rcv_seqs.set(seq-1, alloc().allocate(false, buf.readableBytes()).put(buf));
			}
		}

		private void protocolRead1(DynByteBuf buf) throws IOException {
			int k = buf.readUnsignedByte();
			switch (k) {
				case ACK|(WAIT<<4): // connection establish
					if (prot_state != WAIT) break;
					prot_state = OPEN;

					mtu = Math.min(mtu, buf.readUnsignedShort()+256);
					mtu -= 3;

					snd_win = buf.readInt();
					if (snd_win < mtu) break;
					if (rcv_win < mtu) throw new IOException("receive window size should not smaller than 1xMTU");

					protocolSend(IOUtil.getSharedByteBuf().put((ACK|(WAIT<<4))).putInt(rcv_win));

					//todo ?
					open();
					return;

				case ACK|(OPEN<<4): // ack
					if (prot_state != OPEN || !onAck(buf.readUnsignedShort())) break;
					return;
				case DTA|(RET<<4): // data with ack
					if (prot_state != OPEN || !onAck(buf.readUnsignedShort())) break;
				case DTA: // data
					if (prot_state != OPEN) break;

					if (rb.writableBytes() < buf.readableBytes()) {
						BufferPool p = alloc();

						DynByteBuf newBuf = p.allocate(false, rb.capacity()<<1);
						newBuf.put(rb);
						BufferPool.reserve(rb);
						rb = newBuf;
					}

					rcv_win -= buf.readableBytes();
					if (rcv_win < 0) throw new IOException("window size violation");

					try {
						fireChannelRead(rb);
					} finally {
						rb.compact();
					}
					return;

				case ACK|(WIN<<4):
					rcv_seq = 0;
					break;
				case RET: // ret
					if (prot_state != OPEN) break;
					int pid = buf.readUnsignedShort()-snd_remote_seq;
					if (pid<0||pid>=snd_seqs.size()) break;

					limit += 3;
					pkt.data = snd_seqs.get(pid);
					try {
						owner.fireChannelWrite(pkt);
					} finally {
						pkt.data = null;
					}
					return;
				case WIN: // window update
					if (prot_state != OPEN) break;
					snd_win += buf.readUnsignedShort();
					if (snd_win < mtu) limit += 3;
					else sendData();
					return;

				case RST: // connection reset
					close();
					throw new IOException("connection reset");
				case SHUT: // remote close
					if (prot_state == OPEN) {
						protocolSend(IOUtil.getSharedByteBuf().put((SHUT|(ACK<<4))));

						prot_state = CLOSE;
						close();
					} else if (prot_state < CLOSE) break;
					return;
				case SHUT|(ACK<<4): // close ack
					if (prot_state != LOCAL_CLOSE) break;

					prot_state = CLOSE;
					close();
					return;
			}

			throw new IOException("protocol error: " + Integer.toHexString(k));
		}

		private boolean onAck(int seq) throws IOException {
			if (snd_remote_seq >= seq) return false;

			BufferPool p = alloc();
			int cnt = seq-snd_remote_seq;
			for (int i = 0; i < cnt; i++) {
				BufferPool.reserve(snd_seqs.get(i));
			}
			snd_seqs.removeRange(0, cnt);

			snd_remote_seq = seq;

			if (seq >= 32767) { // reset seq num
				protocolSend(IOUtil.getSharedByteBuf().put((ACK|(WIN<<4))));
				snd_seq = 0;
			}

			return true;
		}

		// UDP包含校验
		private void protocolSend(DynByteBuf buf) throws IOException {
			sendData();

			DynByteBuf bb = alloc().allocate(true, buf.readableBytes()+2)
								   .put((byte) snd_seq++).put(buf);
			snd_seqs.add(bb);

			pkt.data = bb;
			try {
				owner.fireChannelWrite(pkt);
			} finally {
				pkt.data = null;
				bb.rIndex = 0;
			}
		}

		private int writeData(DynByteBuf buf) throws IOException {
			if (prot_state != OPEN) return -1;
			if (outBuf.writableBytes() < buf.readableBytes() && outBuf.capacity() < outBufMax) {
				BufferPool p = alloc();

				DynByteBuf newBuf = p.allocate(true, Math.min(MathUtils.getMin2PowerOf(outBuf.capacity()+buf.readableBytes()), outBufMax));
				newBuf.put(outBuf);
				BufferPool.reserve(outBuf);
				outBuf = newBuf;
			}

			int wrote = Math.min(buf.readableBytes(), outBuf.writableBytes());
			outBuf.put(buf, wrote);

			if (outBuf.readableBytes() >= mtu) sendData();

			return wrote;
		}

		private void sendData() throws IOException {
			if (snd_win < Math.min(outBuf.readableBytes(),mtu)) return;

			BufferPool p = alloc();
			DynByteBuf tmp = p.allocate(true, mtu+5);
			try {
				pkt.data = tmp;

				do {
					int cnt = Math.min(outBuf.readableBytes(),mtu);
					if (snd_win < cnt) break;
					snd_win -= cnt;

					tmp.clear();
					tmp.putShort(snd_seq++);
					if ((flag & SEND_ACK) != 0) {
						flag ^= SEND_ACK;
						tmp.put((DTA|(RET<<4))).putShort(rcv_seq);
					} else {
						tmp.put(DTA);
					}

					tmp.put(outBuf,cnt);

					// store to buffer
					int t = tmp.readableBytes();
					snd_seqs.add(p.allocate(true, t).put(tmp));
					tmp.rIndex -= t;

					owner.fireChannelWrite(pkt);

				} while (outBuf.readableBytes() >= mtu);
			} finally {
				BufferPool.reserve(tmp);
				pkt.data = null;
			}
			outBuf.compact();
		}
	}
}