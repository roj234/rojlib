package roj.plugins.minecraft.server.network;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/3/19 0019 15:52
 */
public class ConstantPacket extends Packet implements ChannelHandler {
	byte[] constantData;
	Consumer<DynByteBuf> call;

	public ConstantPacket(String name, Consumer<DynByteBuf> buf) {
		super(name, new ByteList());
		this.call = buf;
	}
	public ConstantPacket(String name, String buf) { super(name, TextUtil.hex2bytes(buf, new ByteList())); }

	@Override
	public DynByteBuf getData() {
		Consumer<DynByteBuf> c = call;
		if (c != null) {
			synchronized (this) {
				if (call == c) {
					call = null;
					data.clear();
					c.accept(data);
				}
			}
		}
		return data.slice();
	}

	public DynByteBuf getConstantData() { return constantData == null ? null : new ByteList(constantData); }
	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;
		constantData = buf.toByteArray();
		ctx.removeSelf();
		ctx.channelWrite(buf);
	}
}