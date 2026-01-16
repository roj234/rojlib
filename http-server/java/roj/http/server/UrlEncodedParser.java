package roj.http.server;

import roj.collect.Multimap;
import roj.net.ChannelCtx;
import roj.text.URICoder;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

/**
 * application/x-www-form-urlencoded
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public class UrlEncodedParser implements BodyParser {
	private static final int MAX_KEY_LENGTH = 1024;

	public Multimap<String, String> fields;
	@Override public void handlerAdded(ChannelCtx ctx) {fields = new Multimap<>();key = null;val.clear();}
	@Override public Map<String, ?> getMapLikeResult() {return fields;}

	private String key;
	private final ByteList val = new ByteList();

	@Override
	public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
		var buf = (DynByteBuf) msg;
		int i = buf.rIndex;
		int len = buf.wIndex();

		// a=b&c=d
		char c = key != null ? '&' : '=';
		while (i < len) {
			if (buf.getByte(i) != c) {i++;continue;}

			buf.wIndex(i);
			try {
				if (c == '&') {
					// terminate previous
					fields.add(key, URICoder.decodeURI(val.put(buf), true));
					key = null;
					val.clear();
					c = '=';
				} else {
					makeKey(buf);
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
				val.put(buf);
				buf.rIndex = buf.wIndex();
			} else if (buf.readableBytes() > MAX_KEY_LENGTH) {
				throw invalidKey();
			}
		}
	}
	@Override
	public void onSuccess(DynByteBuf buf) throws IOException {
		if (key == null) makeKey(buf);
		fields.add(key, URICoder.decodeURI(val, true));
		val.release();
	}

	private void makeKey(DynByteBuf buf) throws IllegalRequestException, MalformedURLException {
		if (buf.readableBytes() > MAX_KEY_LENGTH) throw invalidKey();
		while (buf.isReadable() && buf.getByte(buf.rIndex) == '&') buf.rIndex++;
		if (!buf.isReadable()) throw invalidKey();
		key = URICoder.decodeURI(buf, true);
	}

	private static IllegalRequestException invalidKey() {return IllegalRequestException.badRequest("invalid form key");}
}