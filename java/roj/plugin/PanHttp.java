package roj.plugin;

import roj.collect.SimpleList;
import roj.io.buf.BufferPool;
import roj.net.http.server.HttpCache;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.auto.GET;
import roj.text.ACalendar;
import roj.text.CharList;
import roj.util.ArrayCache;

import java.lang.ref.WeakReference;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/8/6 0006 16:18
 */
public final class PanHttp implements Function<Request, InetSocketAddress> {
	static long startTime = System.currentTimeMillis();
	static List<WeakReference<BiConsumer<CharList, Request>>> statuses = new SimpleList<>();

	PanHttp() {}

	public static synchronized void addStatusProvider(BiConsumer<CharList, Request> provider) {
		for (int i = statuses.size() - 1; i >= 0; i--) {
			var status = statuses.get(i);
			if (status.get() == provider) return;
			if (status.get() == null) statuses.remove(i);
		}
		statuses.add(new WeakReference<>(provider));
	}

	public InetSocketAddress apply(Request req) {
		if (req.getField("x-proxy-token").equals("asdf12138")) {
			var forwardedFor = req.getField("x-forwarded-for");
			int i = forwardedFor.indexOf(',');
			if (i < 0) i = forwardedFor.length();
			return new InetSocketAddress(forwardedFor.substring(0, i), Integer.parseInt(req.getField("x-proxy-port")));
		}
		return (InetSocketAddress) req.connection().remoteAddress();
	}

	@GET
	public Response status(Request req) {
		var sb = new CharList();
		sb.append("<title>服务器统计信息</title><p><h1>服务器统计</h1></p><pre>");
		sb.append("启动时间:").append(ACalendar.toLocalTimeString(startTime)).append('\n');
		sb.append("正常运行:").append((System.currentTimeMillis() - startTime) / 1000).append("秒\n");
		sb.append("反向代理:").append(HttpCache.proxyRequestRetainer != null).append('\n');
		sb.append("当前线程:").append(Thread.currentThread().getName()).append('\n');
		BufferPool.localPool().status(sb).append('\n');
		ArrayCache.status(sb).append('\n');

		for (int i = 0; i < statuses.size(); i++) {
			var status = statuses.get(i);
			var consumer = status.get();
			if (consumer == null) {
				statuses.remove(i--);
				continue;
			}

			try {
				consumer.accept(sb, req);
				sb.append('\n');
			} catch (Exception e) {
				Panger.LOGGER.warn("status error", e);
			}
		}

		req.responseHeader().put("refresh", "3");
		return Response.html(sb);
	}
}