package roj.net.handler;

import roj.net.ChannelCtx;
import roj.net.DatagramPkt;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2024/6/28 3:09
 */
public final class Socks5ClientU extends Socks5Client {
	public Socks5ClientU(InetSocketAddress remote) {super(remote);}
	public Socks5ClientU(InetSocketAddress remote, String username, String password) {super(remote, username, password);}

	@Override
	int requestType() {return 3;}
	@Override
	void writeUp(ChannelCtx ctx, DynByteBuf buf) throws IOException {
		ctx.channelWrite(new DatagramPkt((InetSocketAddress) ctx.channel().remoteAddress(), buf));
	}
	@Override
	boolean handlerBinderAddr(InetSocketAddress soca) {
		// 接下来客户端的所有UDP都需要往代理的此端口发送。
		return false;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var pkt = (DatagramPkt) msg;
		if (state == 3) {
			pkt.address = remote;
			ctx.channelRead(msg);
		} else {
			super.channelRead(ctx, pkt.data);
		}
	}

	@Override
	public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
		var pkt = (DatagramPkt) msg;

		// TODO 分片

		//2 RSV 占两个字节 即 0x0000
		//1 FRAG Current fragment number
		//1 ATYP 目的地址类型
		//* DST.ADDR 目的地址
		//2 DST.PORT 目的端口
		//* DATA 真正的数据
		super.channelWrite(ctx, msg);
	}
}