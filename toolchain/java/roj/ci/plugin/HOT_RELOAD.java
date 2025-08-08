package roj.ci.plugin;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.AsmCache;
import roj.asm.ClassDefinition;
import roj.asm.ClassNode;
import roj.asmx.Context;
import roj.collect.ArrayList;
import roj.config.Tokenizer;
import roj.config.data.CEntry;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.net.MyChannel;
import roj.net.ServerLaunch;
import roj.ci.FMD;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/2/11 11:03
 */
public class HOT_RELOAD implements Processor {
	private static final Logger LOGGER = Logger.getLogger("热重载青春版");
	private final ArrayList<Client> clients = new ArrayList<>();
	private ServerLaunch channel4, channel6;

	@Override public String name() {return "热重载青春版";}
	@Override public boolean defaultEnabled() {return true;}

	@Override public void init(CEntry config) {
		try {
			stop();
			start(config.asInt());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		File agent = new File(FMD.BIN_PATH, "agent.jar");
		if (!agent.isFile()) {
			try (var zfw = new ZipFileWriter(agent)) {
				zfw.beginEntry(new ZEntry("META-INF/MANIFEST.MF"));
				zfw.write(("""
                        Manifest-Version: 1.0
                        Premain-Class: roj.ci.plugin.HRAgent
                        Agent-Class: roj.ci.plugin.HRAgent
                        Can-Redefine-Classes: true
                        """).getBytes(StandardCharsets.UTF_8));
				try (var za = new ZipFile(IOUtil.getJar(HOT_RELOAD.class))) {
					zfw.copy(za, za.getEntry("roj/ci/plugin/HRAgent.class"));
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		FMD.LOGGER.info("\"热重载青春版\"启动成功，在任意JVM中加入该参数即可使用");
		FMD.LOGGER.info("  -javaagent:\""+Tokenizer.escape(agent.getAbsolutePath())+"\"="+config.asInt());
	}

	public void start(int port) throws IOException {
		Consumer<MyChannel> handler = ctx -> {
			LOGGER.info("收到客户端的连接：{}", ctx.remoteAddress());
			Client t = new Client(ctx);
			ctx.addLast("handler", t);
			synchronized (this) {
				clients.add(t);
			}
		};

		channel4 = ServerLaunch.tcp("HotReload-V4")
				.bind2(InetAddress.getByName("127.0.0.1"), port)
				.option(StandardSocketOptions.SO_REUSEADDR, true)
				.initializator(handler).launch();
		try {
			channel6 = ServerLaunch.tcp("HotReload-V6")
					.bind2(InetAddress.getByName("::1"), port)
					.option(StandardSocketOptions.SO_REUSEADDR, true)
					.initializator(handler).launch();
		} catch (Exception e) {
			channel6 = null;
		}
	}
	public void stop() throws IOException {
		IOUtil.closeSilently(channel4);
		IOUtil.closeSilently(channel6);
	}

	@Override public synchronized void afterCompile(ProcessEnvironment ctx) {
		if (ctx.compiledClassIndex > 0) {
			List<ClassNode> classData = new ArrayList<>(ctx.compiledClassIndex);
			List<Context> classes = ctx.getClasses();
			for (int i = 0; i < ctx.compiledClassIndex; i++) {
				classData.add(classes.get(i).getData());
			}
			sendChanges(classData);
		}
	}
	public void sendChanges(List<? extends ClassDefinition> modified) {
		if (modified.isEmpty()) return;
		if (modified.size() > 9999) throw new IllegalArgumentException("Too many classes modified");

		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.put(1).putShort(modified.size());

		for (int i = 0; i < modified.size(); i++) {
			ClassDefinition clz = modified.get(i);
			ByteList data = AsmCache.toByteArrayShared(clz);

			try {
				send(tmp.putUTF(clz.name().replace('/', '.')).putInt(data.readableBytes()).put(data));
			} catch (Exception ignored) {
				// concurrent modification
			}
			tmp.clear();
		}
	}

	final class Client implements ChannelHandler {
		final MyChannel ctx;

		Client(MyChannel ctx) {this.ctx = ctx;}

		@Override
		public void channelRead(ChannelCtx ctx, Object msg) {
			DynByteBuf buf = (DynByteBuf) msg;
			if (buf.readableBytes() >= 2 && buf.readableBytes() >= buf.readShort(buf.rIndex)+2) {
				System.out.println(ctx.remoteAddress()+": "+buf.readUTF());
			}
		}

		@Override
		public void channelClosed(ChannelCtx ctx) {
			synchronized (HOT_RELOAD.this) {clients.remove(this);}
			LOGGER.info("与客户端的连接断开：{}", ctx.remoteAddress());
		}

		@Override
		public void exceptionCaught(ChannelCtx ctx, Throwable ex) throws Exception {
			if (ex instanceof IOException) {
				ctx.close();
			} else {
				ctx.exceptionCaught(ex);
			}
		}
	}

	private void send(DynByteBuf tmp) {
		for (Client client : clients) {
			try {
				tmp.rIndex = 0;
				client.ctx.fireChannelWrite(tmp);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
}