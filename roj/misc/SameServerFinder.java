package roj.misc;

import roj.collect.ToIntMap;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.net.NetworkUtil;
import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.SelectorLoop;
import roj.net.ch.handler.Timeout;
import roj.net.ch.handler.VarintSplitter;
import roj.net.ch.osi.ClientLaunch;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.ui.EasyProgressBar;
import roj.ui.UIUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2023/1/26 0026 20:27
 */
public class SameServerFinder {
	static final Semaphore conn = new Semaphore(256);

	public static void main(String[] args) {
		SelectorLoop loop = new SelectorLoop(null, "ServerFinder", 4);
		EasyProgressBar bar = new EasyProgressBar("端口");
		bar.setUnit("");
		if (args.length == 0) {
			try {
				ClientLaunch.tcp().timeout(1000).connect(NetworkUtil.getConnectAddress(UIUtil.userInput("ip"))).loop(loop).initializator(new PingTask()).launch();
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
				bar.setPercentStr(Integer.toString(port));
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

		public PingTask() {

		}

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
					CmdUtil.error("Unexpected packet: " + buf.slice(0, Math.min(buf.readableBytes(), 16)));
					return;
				}

				CMapping json;
				try {
					json = new JSONParser().parse(buf.readVarIntUTF(32767)).asMap();
				} catch (ParseException e) {
					e.printStackTrace();
					return;
				}
				String desc = minecraftStyle2String(json.getOrCreateMap("description")).toString();
				CharList ccc = IOUtil.getSharedCharBuf().append(desc);
				CmdUtil.Color.minecraftColor(ccc);
				System.out.println(ctx.remoteAddress()+"/"+json.getDot("players.online")+"/"+json.getDot("players.max"));
				System.out.println(ccc);

				System.err.println(json.toJSONb());
			} finally {
				ctx.close();
			}
		}

		static ToIntMap<String> mapa = new ToIntMap<>();
		static {
			makeMap("BLACK", '0',
				"DARK_BLUE", '1',
				"DARK_GREEN", '2',
				"DARK_AQUA", '3',
				"DARK_RED", '4',
				"DARK_PURPLE", '5',
				"GOLD", '6',
				"GRAY", '7',
				"DARK_GRAY", '8',
				"BLUE", '9',
				"GREEN", 'a',
				"AQUA", 'b',
				"RED", 'c',
				"LIGHT_PURPLE", 'd',
				"YELLOW", 'e',
				"WHITE", 'f');
		}
		private static void makeMap(Object... arr) {
			for (int i = 0; i < arr.length;) {
				String k = arr[i++].toString().toLowerCase();
				char v = (char) arr[i++];
				mapa.putInt(k, v);
			}
		}

		private static StringBuilder minecraftStyle2String(CMapping map) {
			StringBuilder sb = new StringBuilder();
			minecraftStyle2String(map, sb, 0);
			return sb;
		}
		private static int minecraftStyle2String(CMapping map, StringBuilder sb, int state) {
			int colorCode = mapa.getInt(map.getString("color"));
			if (colorCode != 0) {
				state = state & 0xFFFFFF | (colorCode << 24);
				applyStyle(state, sb);
			}
			if (map.containsKey("italic")) {
				if (map.getBool("italic")) {
					state |= 1;
					sb.append("\u00a7o");
				} else {
					state &= ~1;
					sb.append("\u00a7r");
					applyStyle(state, sb);
				}
			}
			if (map.containsKey("bold")) {
				if (map.getBool("bold")) {
					state |= 2;
					sb.append("\u00a7l");
				} else {
					state &= ~2;
					sb.append("\u00a7r");
					applyStyle(state, sb);
				}
			}
			if (map.containsKey("underlined")) {
				if (map.getBool("underlined")) {
					state |= 4;
					sb.append("\u00a7n");
				} else {
					state &= ~4;
					sb.append("\u00a7r");
					applyStyle(state, sb);
				}
			}
			if (map.containsKey("strikethrough")) {
				if (map.getBool("strikethrough")) {
					state |= 8;
					sb.append("\u00a7m");
				} else {
					state &= ~8;
					sb.append("\u00a7r");
					applyStyle(state, sb);
				}
			}
			if (map.containsKey("obfuscated")) {
				if (map.getBool("obfuscated")) {
					state |= 16;
					sb.append("\u00a7k");
				} else {
					state &= ~16;
					sb.append("\u00a7r");
					applyStyle(state, sb);
				}
			}
			sb.append(map.getString("text"));
			if (map.containsKey("extra")) {
				CList list = map.get("extra").asList();
				for (int i = 0; i < list.size(); i++) {
					state = minecraftStyle2String(list.get(i).asMap(), sb, state);
				}
			}
			return state;
		}

		private static void applyStyle(int state, StringBuilder sb) {
			int color = state >>> 24;
			if (color != 0) sb.append('\u00a7').append((char)color);
			if ((state & 2) != 0) sb.append("\u00a7l");
			if ((state & 8) != 0) sb.append("\u00a7m");
			if ((state & 4) != 0) sb.append("\u00a7n");
			if ((state & 1) != 0) sb.append("\u00a7o");
			if ((state & 16) != 0) sb.append("\u00a7k");
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
