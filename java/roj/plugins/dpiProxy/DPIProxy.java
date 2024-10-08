package roj.plugins.dpiProxy;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.LavaCompiler;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.net.*;
import roj.net.handler.Fail2Ban;
import roj.net.handler.Pipe2;
import roj.net.handler.Timeout;
import roj.plugin.Plugin;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/9/15 0015 16:15
 */
public class DPIProxy extends Plugin implements Consumer<MyChannel> {
	private ServerLaunch launch;
	private final Fail2Ban f2b = new Fail2Ban(5, 900000);

	private final List<DpiMatcher> patterns = new SimpleList<>();
	private final Map<String, Consumer<MyChannel>> handlers = new MyHashMap<>();
	private LavaCompiler compiler;

	@Override
	protected void onEnable() throws Exception {
		var cfg = getConfig();

		compiler = new LavaCompiler();
		for (var entry : cfg.getMap("match").entrySet()) {
			String key = entry.getKey();

			var matcher = compiler.linkLambda(DpiMatcher.class, entry.getValue().asString(), "data");
			if (matcher != null) patterns.add(matcher);
		}
		compiler.gctx.reset();
		getLogger().info("成功：生成了{}个Pattern", patterns.size());

		launch = ServerLaunch.tcp("DPIProxy").bind(NetUtil.parseListeningAddress(cfg.getString("port"))).initializator(this).launch();
	}

	@Override protected void onDisable() {IOUtil.closeSilently(launch);patterns.clear();}

	@Override public void accept(MyChannel ch) {ch.addLast("fail2ban", f2b).addLast("dpi_timer", new Timeout(1500)).addLast("dpi", new Matcher());}

	class Matcher implements ChannelHandler {
		final MyBitSet matchers = new MyBitSet(patterns.size());
		{matchers.fill(patterns.size());}

		@Override public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
			var data = (DynByteBuf) msg;
			var rIdx = data.rIndex;

			for (var itr = matchers.iterator(); itr.hasNext(); ) {
				var matcher = patterns.get(itr.nextInt());
				try {
					data.rIndex = rIdx;
					matcher.inspect(data);
				} catch (DpiException resp) {
					data.rIndex = rIdx;

					var ch = ctx.channel();
					ch.readInactive();
					ch.removeAll();

					if (resp.errno > 0) {
						var pipe = new Pipe2(ch, true);
						ch.addLast("pipe", pipe);

						ClientLaunch.tcp().initializator(channel -> channel.addLast("pipe", pipe)).connect(InetAddress.getByName(resp.getMessage()), resp.errno).launch();
						getLogger().info("Proxy {} => {}:{}", ch.remoteAddress(), resp.getMessage(), resp.errno);
					} else {
						switch (resp.errno) {
							case 0 -> {
								var handler = ServerLaunch.SHARED.get(resp.getMessage());
								if (handler == null) {
									getLogger().warn("找不到管道:"+resp.getMessage());
								} else {
									getLogger().info("Pipe {} => #{}", ch.remoteAddress(), resp.getMessage());
									handler.addTCPConnection(ch);
								}
							}
							case -1 -> {
								if (resp.getMessage() != null)
									ctx.channelWrite(IOUtil.getSharedByteBuf().putUTFData(resp.getMessage()));
								ctx.close();
							}
						}
					}

					return;
				} catch (IllegalArgumentException no_enough_data) {
					// thrown from DynByteBuf
					continue;
				} catch (/*OperationDone | */Throwable failed) {
					if (failed != OperationDone.INSTANCE) getLogger().warn("matcher {} 意外失败", failed, matcher.getClass().getSimpleName());
					itr.remove();

					if (matchers.size() == 0) {
						ctx.close();
					}
				}
			}
		}

		@Override public void onEvent(ChannelCtx ctx, Event event) throws IOException {
			if (event.id.equals("fail2ban:inspect")) {
				var copy = (DynByteBuf)event.getData();
				event.setResult(Event.RESULT_ACCEPT);
				event.stopPropagate();
			}
		}
	}
}
