package roj.http.server;

import roj.http.Multimap;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.text.Escape;
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
			if (buf.get(i) != c) {i++;continue;}

			buf.wIndex(i);
			try {
				if (c == '&') {
					val.put(buf); // terminate
					submit();
					key = null;
					val.clear();
					c = '=';
				} else {
					if (buf.readableBytes() > 384) throw invalidKey();
					key = Escape.decodeURI(IOUtil.getSharedCharBuf(), IOUtil.getSharedByteBuf(), buf, true).toString();
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
			} else if (buf.readableBytes() > 384) {
				throw invalidKey();
			}
		}
	}
	@Override
	public void onSuccess(DynByteBuf buf) throws IOException {
		if (key == null) {
			if (buf.readableBytes() > 384) throw invalidKey();
			key = Escape.decodeURI(IOUtil.getSharedCharBuf(), IOUtil.getSharedByteBuf(), buf, true).toString();
		}
		submit();
		val._free();
	}

	private void submit() throws MalformedURLException {
		fields.add(key, Escape.decodeURI(IOUtil.getSharedCharBuf(), IOUtil.getSharedByteBuf(), val, true).toString());
	}

	private static IllegalRequestException invalidKey() {return IllegalRequestException.badRequest("invalid form key");}
}