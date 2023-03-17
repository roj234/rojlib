package ilib.net.mock.adapter;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import roj.util.ByteList;

/**
 * @author solo6975
 * @since 2022/4/7 11:39
 */
public class T_Ignore implements Target {
	public T_Ignore() {}

	@Override
	public ByteBuf apply(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
		msg.release();
		return Unpooled.EMPTY_BUFFER;
	}

	@Override
	public void serialize(ByteList w) {
		w.put(ID_IGNORE);
	}

	@Override
	public String toString() {
		return "忽略该数据包";
	}
}
