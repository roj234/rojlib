package roj.plugins.frp;

import roj.net.SelectorLoop;
import roj.net.http.Headers;
import roj.net.http.HttpHead;
import roj.net.http.h2.H2Connection;
import roj.net.http.h2.H2Exception;
import roj.net.http.h2.H2FlowControlSimple;
import roj.net.http.h2.H2Stream;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/9/15 0015 0:23
 */
public abstract class FrpCommon extends H2Connection {
	static final SelectorLoop loop = new SelectorLoop("AE 网络IO", 2, 1000000, 1);
	static final Headers OK = new Headers();
	static {OK.put(":status", "200");}

	public FrpCommon(boolean isServer) {super(null, isServer, new H2FlowControlSimple());}

	public byte[] hash = ArrayCache.BYTES;

	public static abstract class Control extends H2Stream {
		protected Control() {super(1);}
		protected Control(int id) {super(id);}

		@Override protected abstract String headerEnd(H2Connection man) throws IOException;
		@Override protected final void onHeaderDone(H2Connection man, HttpHead head, boolean hasData) {}
		protected abstract void onDataPacket(DynByteBuf buf);

		private final ByteList buffer = new ByteList();
		@Override protected final String onData(H2Connection man, DynByteBuf buf) throws IOException {
			if (buffer.wIndex() > 0) {
				buffer.put(buf);
				buf = buffer;
			}
			while (true) {
				int rb = buf.readableBytes() - 2;
				if (rb < 0 || rb < buf.readUnsignedShort(buf.rIndex)) {
					if (buf != buffer) buffer.put(buf);
					break;
				} else {
					int count = buf.readUnsignedShort();
					int wp = buf.wIndex();
					buf.wIndex(buf.rIndex+count);

					onDataPacket(buf);
					buf.wIndex(wp);

					buffer.clear();
				}
			}

			return null;
		}

		@Override protected final void onDone(H2Connection man) {onFinish(man);}
		@Override protected void onFinish(H2Connection man) {buffer._free();man.goaway(H2Exception.ERROR_OK, "关键连接终止");}
	}
}
