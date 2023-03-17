package roj.net.http;

import roj.net.JavaSslFactory;
import roj.net.ch.ChannelHandler;
import roj.net.ch.MyChannel;
import roj.net.ch.SelectorLoop;
import roj.net.ch.handler.SSLCipher;
import roj.net.ch.handler.Timeout;
import roj.net.http.h2.HttpClient20;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.NamespaceKey;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

public abstract class IHttpClient {
	public static final SelectorLoop POLLER = new SelectorLoop(null, "NIO请求池", 0, 4, 60000, 100);

	public static final NamespaceKey DOWNLOAD_EOF = NamespaceKey.of("hc","data_eof");
	public static final NamespaceKey UPLOAD_EOF = NamespaceKey.of("hc","body_eof");

	protected String action = "GET";
	protected Object body;
	protected final Headers header = new Headers();
	protected URL url;
	protected int state;
	public boolean urlPreEncoded;

	public static final int V1_1 = 1, V2_0 = 2;
	public static IHttpClient create(int version) {
		switch (version) {
			case V1_1: return new HttpClient11();
			case V2_0: return new HttpClient20();
			default: throw new UnsupportedOperationException();
		}
	}

	public static SyncHttpClient syncWait(IHttpClient client, int connTimeout, int readTimeout) throws IOException {
		MyChannel ctx = MyChannel.openTCP();
		SyncHttpClient sbr = new SyncHttpClient();
		ctx.addLast("h11@client", client.asChannelHandler())
		   .addLast("Timer", new Timeout(readTimeout, 1000))
		   .addLast("Merger", sbr);
		client.connect(ctx, connTimeout);
		POLLER.register(ctx, null);
		return sbr;
	}

	public final IHttpClient method(String type) {
		if (Action.valueOf(type) < 0) throw new IllegalArgumentException(type);
		action = type;
		return this;
	}

	public final String method() {
		return action;
	}

	public final Headers headers() {
		return header;
	}

	public final IHttpClient header(CharSequence k, String v) {
		this.header.put(k, v);
		return this;
	}

	public final void headers(Map<String, String> headers) {
		headers(headers, true);
	}

	public final void headers(Map<String, String> headers, boolean clear) {
		if (clear) header.clear();
		header.putAll(headers);
	}

	public IHttpClient body(DynByteBuf body) {
		this.body = body;
		return this;
	}

	public IHttpClient bodyFromDownstream() {
		this.body = "DOWNSTREAM";
		return this;
	}

	public Object body() {
		return body;
	}

	public IHttpClient url(URL url) {
		this.url = url;
		String host = url.getHost();
		if (url.getPort() >= 0) host += ":" + url.getPort();
		header.put("Host", host);
		return this;
	}

	public final URL url() {
		return url;
	}

	public IHttpClient copyFrom(IHttpClient c) {
		method(c.method()).url(c.url()).headers(Helpers.cast(c.headers()));
		body = c.body();
		return this;
	}

	public boolean connect(MyChannel ctx, int timeout) throws IOException {
		if (url.getProtocol().equalsIgnoreCase("https") && null == ctx.handler("SSL")) {
			try {
				ctx.addFirst("SSL", new SSLCipher(JavaSslFactory.getClientDefault().get()));
			} catch (NoSuchAlgorithmException e) {
				throw new RuntimeException(e);
			}
		}
		InetAddress host = InetAddress.getByName(url.getHost());
		return ctx.connect(new InetSocketAddress(host, url.getPort() < 0 ? url.getDefaultPort() : url.getPort()), timeout);
	}

	public abstract HttpHead response();

	public abstract ChannelHandler asChannelHandler();

	public abstract void waitFor() throws InterruptedException;
}
