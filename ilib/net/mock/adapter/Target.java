package ilib.net.mock.adapter;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import roj.util.ByteList;

/**
 * @author solo6975
 * @since 2022/4/7 11:29
 */
public interface Target {
	byte ID_REMAP = 0, ID_IGNORE = 1;

	static Target deserialize(ByteList r) {
		switch (r.readByte() & 0xFF) {
			case ID_REMAP:
				return new T_IdRemap(r.readVarInt(false), r.readVarInt(false));
			case ID_IGNORE:
				return new T_Ignore();
			default:
				return null;
		}
	}

	ByteBuf apply(ChannelHandlerContext ctx, ByteBuf message) throws Exception;

	void serialize(ByteList w);
}
