package roj.plugins.minecraft;

import roj.config.JsonParser;
import roj.config.node.MapValue;
import roj.io.IOUtil;
import roj.net.*;
import roj.net.handler.Timeout;
import roj.net.handler.VarintSplitter;
import roj.plugin.Plugin;
import roj.plugin.SimplePlugin;
import roj.text.CharList;
import roj.text.logging.Logger;
import roj.ui.Argument;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

import static roj.ui.CommandNode.argument;
import static roj.ui.CommandNode.literal;
import static roj.ui.Tty.TextEffect.minecraftJsonStyleToString;

/**
 * @author Roj234
 * @since 2023/1/26 20:27
 */
@SimplePlugin(id = "mcPing", desc = """
	Minecraft协议端口扫描工具
	并发连接数=256
	获取服务器MOTD: mcping test <ip:port>
	扫描该地址的所有高位端口: mcping <ip/domain>
	""", version = "2.1")
public class MCPinger extends Plugin {

	static final Semaphore conn = new Semaphore(256);
	static Logger logger;

	private final EasyProgressBar bar = new EasyProgressBar("端口");
	private SelectorLoop loop;

	@Override
	protected void onEnable() throws Exception {
		loop = new SelectorLoop("mcPing", 4);
		logger = getLogger();

		registerCommand(literal("mcping").then(literal("test").then(argument("addr", Argument.string()).executes(ctx -> {
			String addr = ctx.argument("addr", String.class);

			conn.acquire();
			ClientLaunch.tcp().loop(loop).timeout(1000).connect(Net.parseAddress(addr, 25565)).initializator(new PingTask()).launch();
		}))).then(argument("addr", Argument.string()).executes(ctx -> {
			String addr = ctx.argument("addr", String.class);
			InetAddress[] ips;
			try {
				ips = InetAddress.getAllByName(addr);
			} catch (UnknownHostException e) {
				getLogger().warn("找不到主机", e);
				return;
			}

			try {
				for (var ip : ips) {
					System.out.println("正在扫描:"+ip);
					bar.setTotal(64536);
					for (int port = 1000; port < 65536; port++) {
						bar.setPrefix(Integer.toString(port));
						bar.increment(1);

						conn.acquire();
						ClientLaunch.tcp().timeout(1000).connect(ip, port).loop(loop).initializator(new PingTask()).launch();
					}
				}
			} finally {
				bar.close();
			}
		})));
	}

	@Override
	protected void onDisable() {loop.close();}

	static final class PingTask implements Consumer<MyChannel>, ChannelHandler {
		PingTask() {}

		@Override
		public void accept(MyChannel ch) {
			try {
				ch.setOption(StandardSocketOptions.TCP_NODELAY, true);
			} catch (IOException e) {
				e.printStackTrace();
			}
			ch.addLast("timeout", new Timeout(5000,500))
			  .addLast("splitter", new VarintSplitter(3))
			  .addLast("decoder", this);
		}

		@Override
		public void channelOpened(ChannelCtx ctx) throws IOException {
			logger.debug("端口开放 "+ctx.remoteAddress());
			ByteList buf = IOUtil.getSharedByteBuf();
			buf.put(0) // packet id: handshake
			   .putVarInt(760); // protocol
			InetSocketAddress addr = ((InetSocketAddress) ctx.remoteAddress());
			buf.putVarIntUTF(addr.getHostName()+"\u0000FML\u0000") // host
			   .putShort(addr.getPort()) // port
			   .putVarInt(1); // status query

			ctx.channelWrite(buf);

			buf.clear();
			ctx.channelWrite(buf.put(0));
		}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			var buf = (DynByteBuf) msg;

			checkMinecraftMotd:
			if (buf.isReadable() && buf.readByte() == 0) {
				MapValue json;
				try {
					json = new JsonParser().parse(buf.readVarIntUTF(32767)).asMap();
				} catch (Exception e) {
					break checkMinecraftMotd;
				}

				logger.warn("MC服务器 "+ctx.remoteAddress()+": "+json.query("players.online")+"/"+json.query("players.max")+
					"\n"+minecraftJsonStyleToString(json.getMap("description")).writeAnsi(new CharList()).toStringAndFree());
			} else {
				buf.rIndex = 0;
				logger.debug("其它协议 "+ctx.remoteAddress()+": "+buf.dump());
			}

			ctx.close();
		}

		@Override
		public void channelClosed(ChannelCtx ctx) {conn.release();}

		@Override
		public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
			ctx.close();

			if (ex instanceof ConnectException) return;
			if (ex instanceof IOException) logger.debug("连接关闭: "+ex.getMessage());
			else ex.printStackTrace();
		}
	}
}