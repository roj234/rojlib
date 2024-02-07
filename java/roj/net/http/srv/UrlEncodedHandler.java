package roj.net.http.srv;

import roj.net.URIUtil;
import roj.net.ch.ChannelCtx;
import roj.net.http.IllegalRequestException;
import roj.util.DynByteBuf;

import java.io.EOFException;
import java.io.IOException;
import java.net.MalformedURLException;

/**
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public class UrlEncodedHandler extends HPostHandler {
	private String key;
	private byte state;

	public UrlEncodedHandler() {}

	@Override
	public final void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		DynByteBuf buf = (DynByteBuf) msg;

		if (state == 0 && buf.isReadable()) state = 1;
		else if (state == 3) throw new EOFException();

		int pos = buf.rIndex;
		int lim = buf.wIndex();

		char c = key != null ? '&' : '=';
		while (pos < lim) {
			if (buf.get(pos) == c) {
				buf.wIndex(pos);

				try {
					String o = URIUtil.decodeURI(buf);
					if (key != null) {
						onValue(o);
						key = null;
					} else {
						onKey(o);
						key = o;
					}
				} catch (MalformedURLException e) {
					throw new IllegalRequestException(400, "invalid form key");
				} finally {
					buf.wIndex(lim);
					buf.rIndex = pos+1;
				}

				c = key != null ? '&' : '=';
			}
			pos++;
		}

		if (state == 2) {
			onValue(URIUtil.decodeURI(buf));
			state = 3;
		}
	}

	@Override
	public void onSuccess() {
		if (key == null) {
			if (state == 1) throw new IllegalArgumentException("Invalid format");
		}
		state = 2;
	}

	protected void onKey(String key) throws IllegalRequestException {}
	protected void onValue(String value) throws IllegalRequestException { map.put(key, value); }
}
