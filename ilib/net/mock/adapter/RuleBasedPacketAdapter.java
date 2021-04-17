package ilib.net.mock.adapter;

import ilib.net.mock.MockingUtil;
import ilib.net.mock.PacketAdapter;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import roj.collect.IntMap;
import roj.util.ByteList;

import net.minecraft.network.EnumConnectionState;

/**
 * @author solo6975
 * @since 2022/4/7 0:09
 */
public class RuleBasedPacketAdapter implements PacketAdapter {
	private EnumConnectionState state = EnumConnectionState.HANDSHAKING;
	public IntMap<Target> stateLoginIn, statePlayIn, stateLoginOut, statePlayOut;

	public RuleBasedPacketAdapter() {
		stateLoginIn = new IntMap<>();
		statePlayIn = new IntMap<>();
		stateLoginOut = new IntMap<>();
		statePlayOut = new IntMap<>();
	}

	public void serialize(ByteList w) {
		write(w, stateLoginIn);
		write(w, statePlayIn);
		write(w, stateLoginOut);
		write(w, statePlayOut);
	}

	public RuleBasedPacketAdapter deserialize(ByteList r) {
		state = EnumConnectionState.HANDSHAKING;
		read(r, stateLoginIn);
		read(r, statePlayIn);
		read(r, stateLoginOut);
		read(r, statePlayOut);
		return this;
	}

	private static void write(ByteList w, IntMap<Target> map) {
		w.putVarInt(map.size(), false);
		for (IntMap.Entry<Target> entry : map.selfEntrySet()) {
			entry.getValue().serialize(w.putVarInt(entry.getIntKey(), false));
		}
	}

	private static IntMap<Target> read(ByteList r, IntMap<Target> map) {
		int i = r.readVarInt(false);
		map.clear();
		map.ensureCapacity(i);
		while (i-- > 0) {
			map.putInt(r.readVarInt(false), Target.deserialize(r));
		}
		return map;
	}

	@Override
	public ByteBuf processInboundPacket(ChannelHandlerContext ctx, ByteBuf message) throws Exception {
		switch (state) {
			case LOGIN:
				Target t = stateLoginIn.get(MockingUtil.readVarInt(message));
				if (t == null) return null;
				return t.apply(ctx, message);
			case PLAY:
				t = statePlayIn.get(MockingUtil.readVarInt(message));
				if (t == null) return null;
				return t.apply(ctx, message);
			default:
				return null;
		}
	}

	@Override
	public ByteBuf processOutboundPacket(ChannelHandlerContext ctx, ByteBuf message) throws Exception {
		switch (state) {
			case LOGIN:
				Target t = stateLoginOut.get(MockingUtil.readVarInt(message));
				if (t == null) return null;
				return t.apply(ctx, message);
			case PLAY:
				t = statePlayOut.get(MockingUtil.readVarInt(message));
				if (t == null) return null;
				return t.apply(ctx, message);
			default:
				return null;
		}
	}

	@Override
	public void setConnectionState(EnumConnectionState state) {
		this.state = state;
	}
}
