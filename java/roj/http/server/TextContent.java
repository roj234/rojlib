package roj.http.server;

import roj.http.Headers;
import roj.io.buf.BufferPool;
import roj.net.ChannelCtx;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.logging.LogHelper;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Objects;

public class TextContent implements Content {
	final CharSequence text;
	final FastCharset charset;
	final String mime;

	public TextContent(CharSequence text, String mime) {this(text, mime, FastCharset.UTF8());}
	public TextContent(CharSequence text, String mime, FastCharset charset) {
		this.text = Objects.requireNonNull(text, "str");
		this.mime = mime==null?null:mime+"; charset="+charset.name();
		this.charset = charset;
	}

	public CharSequence string() {return text;}
	public String mimetype() {return mime;}

	public static TextContent errorPage(CharSequence e) {return errorPage("内部错误", e);}
	public static TextContent errorPage(String title, Object o) {
		var sb = new CharList();
		sb.append("<html><head><title>").append(title)
		  .append(" - openresty</title><style>*{margin:0;padding:0}i{color:#888;font-size:13px;cursor:pointer}i:hover{color:#1890ff;text-decoration:underline}</style></head>"+
			  "<body style=\"background-color:#ebf0f6;font-family:'Microsoft Yahei';line-height:1.5;position:fixed;width:100%;height:100%;display:flex;justify-content:center;align-items:center\">" +
			  "<div style=\"background:#fff;border-radius:8px;padding-bottom:50px;box-shadow:0 5px 20px rgb(0,0,0,0.3)\"><div style=\"font-size:24px;margin:20px 0;text-align:center\">").append(title)
		  .append("</div><div style=\"color:#333;background:#f8f8f8;font-size:16px;margin:0 24px;border-radius:2px;padding:24px 40px\"><pre style=\"border-left:5px solid #1890ff;padding:5px 10px;color:#666\">");
		if (o instanceof Throwable ex) LogHelper.printError(ex, sb, "");
		else sb.append(o);
		sb.append("</pre><i onclick=\"location.reload()\">刷新一下？</i></div></div></body></html>");
		return new TextContent(sb, "text/html");
	}

	private DynByteBuf buf;
	private int off;

	public boolean send(ContentWriter rh) throws IOException {
		if (buf == null) throw new IllegalStateException("Not prepared");

		if (buf.isReadable()) {
			rh.write(buf);
			if (buf.isReadable()) return true;
		}

		buf.clear();
		off = charset.encodeFixedOut(text,off, text.length(),buf,buf.capacity());
		rh.write(buf);

		return buf.isReadable() || off < text.length();
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		if (buf != null) {
			BufferPool.reserve(buf);
			buf = null;
		}
		if (text instanceof CharList cl) cl._free();
	}

	@Override
	public void prepare(ResponseHeader rh, Headers h) {
		int len = text.length();
		if (len > 127) rh.enableCompression();

		int bufferCap = 4096;
		if (len < 10000) {
			int length = charset.byteCount(text);
			h.putIfAbsent("content-length", Integer.toString(length));
			if (bufferCap > length) bufferCap = length;
		}
		buf = rh.connection().alloc().allocate(true, bufferCap);
		if (mime != null) h.putIfAbsent("content-type", mime);
	}
}