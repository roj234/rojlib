package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.net.http.Code;
import roj.net.http.Headers;
import roj.net.http.IllegalRequestException;
import roj.text.UTF8MB4;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class StringResponse implements Response {
	final String mime;
	final CharSequence str;

	public StringResponse(CharSequence c, String mime) {
		if (c == null) throw new NullPointerException("str");
		str = c;
		this.mime = mime==null?null:mime + "; charset=UTF-8";
	}

	public StringResponse(CharSequence c) {
		this(c, "text/plain");
	}

	public static StringResponse of(String msg) {
		return new StringResponse(msg, "text/html");
	}

	public static StringResponse httpErr(int code) {
		String desc = code + " " + Code.getDescription(code);
		return new StringResponse("<title>" + desc + "</title><center><h1>" + desc + "</h1><hr/><div>"+HttpServer11.SERVER_NAME +"</div></center>", "text/html");
	}

	public static StringResponse forError(int code, Object e) {
		if (code == 0) {
			code = e instanceof IllegalRequestException ? ((IllegalRequestException) e).code : Code.INTERNAL_ERROR;
		}

		StringWriter sw = new StringWriter();
		sw.write("<html><head><meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"><title>");
		sw.write(Code.getDescription(code));
		sw.write("</title></head><body><div><i><h2>出现了错误:</h2></i><p><h3>");
		sw.write(Code.getDescription(code));
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

		rh.write(buf);
		if (buf.isReadable()) return true;

		buf.clear();
		off = UTF8MB4.CODER.encodeFixedOut(str,off, str.length(),buf,buf.capacity());
		rh.write(buf);

		return buf.isReadable() || off < str.length();
	}

	@Override
	public void release(ChannelCtx ctx) throws IOException {
		if (buf != null) {
			ctx.reserve(buf);
			buf = null;
		}
	}

	@Override
	public void prepare(ResponseHeader srv, Headers h) {
		int len = str.length();
		if (len > 127) srv.compressed();

		buf = srv.ch().allocate(true, 4096);
		if (mime != null) h.putIfAbsent("content-type", mime);

		if (len < 10000) h.putIfAbsent("content-length", Integer.toString(DynByteBuf.byteCountUTF8(str)));
		else srv.chunked();
	}
}
