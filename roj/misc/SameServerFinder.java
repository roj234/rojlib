package roj.misc;

import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.*;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.text.CharList;
import roj.ui.CLIUtil;
import roj.ui.ProgressBar;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

import static roj.ui.CLIUtil.MinecraftColor.minecraftJsonStyleToString;

/**
 * @author Roj234
 * @since 2023/1/26 0026 20:27
 */
public class SameServerFinder {
	static final Semaphore conn = new Semaphore(256);

	public static void main(String[] args) {
		SelectorLoop loop = new SelectorLoop(null, "ServerFinder", 4);
		ProgressBar bar = new ProgressBar("端口");
		bar.setUnit("");
		if (args.length == 0) {
			try {
				ClientLaunch.tcp().timeout(1000).connect(NetworkUtil.getConnectAddress(CLIUtil.userInput("ip"))).loop(loop).initializator(new PingTask()).launch();
			} catch (IOException e) {
				e.printStackTrace();
			}
			LockSupport.park();
			return;
		}
		for (String host : args) {
			InetAddress[] ip;
			try {
				ip = InetAddress.getAllByName(host);
			} catch (UnknownHostException e) {
				e.printStackTrace();
				continue;
			}
			System.out.println("当前IP:"+Arrays.toString(ip));
			for (int port = 1000; port < 65536; port++) {
				bar.setPrefix(Integer.toString(port));
				bar.update((port-1000)/64536f, 1);
				try {
					conn.acquire();
					ClientLaunch.tcp().timeout(1000).connect(ip[0], port).loop(loop).initializator(new PingTask()).launch();
				} catch (Exception e) {
					e.printStackTrace();
					break;
				}
			}
		}
	}

	public static class PingTask implements Consumer<MyChannel>, ChannelHandler {
		private int state;

		public PingTask() {}

		@Override
		public void accept(MyChannel ch) {
			try {
				ch.setOption(StandardSocketOptions.TCP_NODELAY, true);
			} catch (IOException e) {
				e.printStackTrace();
			}
			ch.addLast("timeout", new Timeout(10000,2000))
			  .addLast("splitter", new VarintSplitter(3))
			  .addLast("decoder", this);
		}

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			System.out.println("open " + ctx.remoteAddress());
			ByteList buf = IOUtil.getSharedByteBuf();
			buf.put(0) // packet id: handshake
			   .putVarInt(1343, false); // protocol
			InetSocketAddress addr = ((InetSocketAddress) ctx.remoteAddress());
			buf.putVarIntUTF(addr.getHostName()+"\u0000FML\u0000") // host
			   .putShort(addr.getPort()) // port
			   .putVarInt(1, false); // status query

			ctx.channelWrite(buf);

			buf.clear();
			ctx.channelWrite(buf.put(0));
		}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			try {
				DynByteBuf buf = ((DynByteBuf) msg);
				if (buf.readByte() != 0) {
					CLIUtil.error("Unexpected packet: " + buf.slice(0, Math.min(buf.readableBytes(), 16)));
					return;
				}

				CMapping json;
				try {
					json = new JSONParser().parse(buf.readVarIntUTF(32767)).asMap();
				} catch (ParseException e) {
					e.printStackTrace();
					return;
				}

				CharList ccc = IOUtil.getSharedCharBuf();
				minecraftJsonStyleToString(json.getOrCreateMap("description")).writeAnsi(ccc);
				System.out.println(ctx.remoteAddress()+"/"+json.getDot("players.online")+"/"+json.getDot("players.max"));
				System.out.println(ccc);

				System.err.println(json.toJSONb());
			} finally {
				ctx.close();
			}
		}

		@Override
		public void channelClosed(ChannelCtx ctx) throws IOException {
			conn.release();
		}

		@Override
		public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
			if (ex instanceof ConnectException) {
				ctx.close();
				return;
			}
			ctx.exceptionCaught(ex);
		}
	}
}
