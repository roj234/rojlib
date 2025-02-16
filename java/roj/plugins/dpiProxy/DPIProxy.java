package roj.plugins.dpiProxy;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.LavaCompiler;
import roj.compiler.ast.expr.ExprParser;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.net.*;
import roj.net.handler.Fail2Ban;
import roj.net.handler.Pipe2;
import roj.net.handler.Timeout;
import roj.plugin.Plugin;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/9/15 0015 16:15
 */
public class DPIProxy extends Plugin {
	private static Logger LOGGER;

	private final Fail2Ban f2b = new Fail2Ban(5, 60000);
	private final List<ServerLaunch> servers = new SimpleList<>();

	@Override
	protected void onEnable() throws Exception {
		LOGGER = getLogger();
		LOGGER.setLevel(Level.valueOf(getConfig().getString("logLevel", "INFO")));

		var compiler = new LavaCompiler();
		var matchers = new MyHashMap<String, DpiMatcher>();

		for (var item : getConfig().getMap("inject").entrySet()) {
			var ctx = compiler.lctx;

			ctx.lexer.init(item.getValue().asString()+";");
			var node = ctx.ep.parse(ExprParser.STOP_SEMICOLON|ExprParser.SKIP_SEMICOLON);
			compiler.injector.put(item.getKey(), node.resolve(ctx));
		}

		Function<String, DpiMatcher> _createMatcher = file -> {
			var realFile = new File(getDataFolder(), file+".lava");
			if (realFile.isFile()) {
				try {
					String text = IOUtil.readString(realFile);
					compiler.fileName = realFile.getName();
					return compiler.linkLambda("roj/plugins/dpiProxy/impl/"+file, DpiMatcher.class, text, "data");
				} catch (Exception e) {
					Helpers.athrow(e);
				}
			}
			return null;
		};

		var proxies = getConfig().getList("proxies").raw();
		for (int i = 0; i < proxies.size(); i++) {
			var item = proxies.get(i).asMap();
			var patterns = new SimpleList<DpiMatcher>();
			var timeout = item.getInt("timeout", 15000);

			var port = item.getString("port");
			var launcher = ServerLaunch.tcp("DPIProxy-"+port).bind(NetUtil.parseListeningAddress(port)).initializator(ch ->
				ch.addLast("fail2ban", f2b)
				  .addLast("dpi_timer", new Timeout(timeout))
				  .addLast("dpi", new Matcher(patterns))
			);
			servers.add(launcher);

			var matcher = item.getList("matcher").raw();
			for (int j = 0; j < matcher.size(); j++) {
				var name = matcher.get(j).asString();
				patterns.add(Objects.requireNonNull(matchers.computeIfAbsent(name, _createMatcher), "Matcher compile failed"));
			}
		}

		compiler.api.reset();
		LOGGER.info("插件启用成功：编译了{}个Matcher", matchers.size());
		for (var server : servers) server.launch();
	}

	@Override protected void onDisable() {
		for (ServerLaunch server : servers) {
			IOUtil.closeSilently(server);
		}
		servers.clear();
	}

	static class Matcher implements ChannelHandler {
		private final MyBitSet matchers;
		private final List<DpiMatcher> patterns;

		public Matcher(List<DpiMatcher> patterns) {
			this.patterns = patterns;
			this.matchers = new MyBitSet(patterns.size());
			this.matchers.fill(patterns.size());
		}

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
						LOGGER.debug("{}: Proxy {}:{}", ch.remoteAddress(), resp.getMessage(), resp.errno);
					} else {
						switch (resp.errno) {
							case 0 -> {
								var handler = ServerLaunch.SHARED.get(resp.getMessage());
								if (handler == null) {
									LOGGER.warn("{}: 找不到管道 {}", ch.remoteAddress(), resp.getMessage());
								} else {
									LOGGER.debug("{}: Pipe #{}", ch.remoteAddress(), resp.getMessage());
									handler.addTCPConnection(ch);
								}
							}
							case -1 -> {
								if (resp.byteMessage != null)
									ctx.channelWrite(resp.byteMessage);
								else if (resp.getMessage() != null)
									ctx.channelWrite(IOUtil.getSharedByteBuf().putUTFData(resp.getMessage()));
								LOGGER.debug("{}: 关闭", ctx.remoteAddress());
								ctx.close();
							}
						}
					}

					return;
				} catch (IllegalArgumentException no_enough_data) {
					// thrown from DynByteBuf
					continue;
				} catch (/*OperationDone | */Throwable failed) {
					if (failed != OperationDone.INSTANCE) LOGGER.warn("{}匹配器意外失败", failed, matcher.getClass().getSimpleName());
					itr.remove();

					if (matchers.size() == 0) {
						if (LOGGER.canLog(Level.INFO)) {
							data.rIndex = rIdx;
							LOGGER.info("{}: [FAIL] {}", ctx.remoteAddress(), data.slice(Math.min(data.readableBytes(), 256)).dump());
						}
						ctx.close();
					}
				}
			}
			data.rIndex = rIdx;
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
