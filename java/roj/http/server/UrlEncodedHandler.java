package roj.http.server;

import roj.collect.MyHashMap;
import roj.http.IllegalRequestException;
import roj.net.ChannelCtx;
import roj.text.Escape;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.MalformedURLException;

/**
 * application/x-www-form-urlencoded
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public class UrlEncodedHandler implements HPostHandler {
	public MyHashMap<String, Object> data;
	@Override
	public void handlerAdded(ChannelCtx ctx) {data = new MyHashMap<>();}

	protected String name;

	public void init(String contentType) {
		if (!contentType.startsWith("application/x-www-form-urlencoded"))
			throw new IllegalArgumentException("不支持的content-type: '"+contentType+"'");
		name = null;
	}

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;
		int i = buf.rIndex;
		int len = buf.wIndex();

		// a=b&c=d
		char c = name != null ? '&' : '=';
		while (i < len) {
			if (buf.get(i) != c) {i++;continue;}

			buf.wIndex(i);
			try {
				if (c == '&') {
					onValue(ctx, buf);
					name = null;
					c = '=';
				} else {
					if (buf.readableBytes() > 384) throw invalidKey();
					name = Escape.decodeURI(buf);
					if (name.indexOf('&') >= 0) throw invalidKey();
					onKey(ctx, name);
					c = '&';
				}
			} catch (MalformedURLException e) {
				throw invalidKey();
			} finally {
				buf.wIndex(len);
				buf.rIndex = i+1;
			}
		}

		if (buf.isReadable()) {
			if (c == '&') {
				onValue(ctx, buf);
				buf.rIndex = buf.wIndex();
			} else if (buf.readableBytes() > 384) {
				throw invalidKey();
			}
		}
	}
	@Override
	public void onSuccess() throws IOException {
		if (name == null && !data.isEmpty()) throw invalidKey();
	}

	private static IllegalRequestException invalidKey() {return IllegalRequestException.badRequest("invalid form key");}

	protected void onKey(ChannelCtx ctx, String key) throws IOException {data.put(name, new ByteList());}
	protected void onValue(ChannelCtx ctx, DynByteBuf value) throws IOException {((ByteList)data.get(name)).put(value);}
}