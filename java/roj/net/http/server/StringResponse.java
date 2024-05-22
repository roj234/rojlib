package roj.net.http.server;

import roj.io.buf.BufferPool;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.net.http.HttpUtil;
import roj.net.http.IllegalRequestException;
import roj.text.CharList;
import roj.text.UTF8MB4;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

public class StringResponse implements Response {
	final String mime;
	final CharSequence str;

	public StringResponse(CharSequence c, String mime) {
		str = Objects.requireNonNull(c, "str");
		this.mime = mime==null?null:mime+"; charset=UTF-8";
	}

	public CharSequence string() {return str;}
	public String mimetype() {return mime;}

	public static StringResponse detailedErrorPage(int code, Object e) {
		if (code == 0) {
			code = e instanceof IllegalRequestException ? ((IllegalRequestException) e).code : HttpUtil.INTERNAL_ERROR;
		}

		StringWriter sw = new StringWriter();
		sw.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>");
		sw.write(HttpUtil.getDescription(code));
		sw.write("</title></head><body><div><i><h2>编程错误:</h2></i><p><h3>");
		sw.write(HttpUtil.getDescription(code));
		sw.write("</h3><h3><font color='red'>");

		if (e != null) {
			if (e instanceof Throwable) {
				sw.write("以下为错误详细信息: <br/><pre>");
				((Throwable) e).printStackTrace(new PrintWriter(sw));
				sw.write("</pre>");
			} else {
				sw.write(e.toString());
			}
		}

		sw.write(
			"</font></h3></p><p>您可以点击<a href='javascript:location.reload();'>重试</a>.</p><br/><hr/>" +
				"<div>"+HttpServer11.SERVER_NAME +"</div></div><!-- padding for ie --><!-- padding for ie -->" +
				"<!-- padding for ie --><!-- padding for ie --></body></html>");

		return new StringResponse(sw.toString(), "text/html");
	}

	private DynByteBuf buf;
	private int off;

	public boolean send(ResponseWriter rh) throws IOException {
		if (buf == null) throw new IllegalStateException("Not prepared");

		if (buf.isReadable()) {
			rh.write(buf);
			if (buf.isReadable()) return true;
		}

		buf.clear();
		off = UTF8MB4.CODER.encodeFixedOut(str,off,str.length(),buf,buf.capacity());
		rh.write(buf);

		return buf.isReadable() || off < str.length();
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		if (buf != null) {
			BufferPool.reserve(buf);
			buf = null;
		}
		if (str instanceof CharList cl) cl._free();
	}

	@Override
	public void prepare(ResponseHeader rh, Headers h) {
		int len = str.length();
		if (len > 127) rh.enableCompression();

		buf = rh.ch().alloc().allocate(true, 4096);
		if (mime != null) h.putIfAbsent("content-type", mime);

		if (len < 10000) h.putIfAbsent("content-length", Integer.toString(DynByteBuf.byteCountUTF8(str)));
	}
}