package roj.http.server;

import roj.http.Headers;
import roj.http.HttpUtil;
import roj.http.WebSocket;
import roj.net.ChannelCtx;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

@FunctionalInterface
public interface Content {
	Content EMPTY = new Content() {
		public void prepare(ResponseHeader rh, Headers h) {h.put("content-length", "0");}
		public boolean send(ContentWriter rh) {return false;}
	};

	static Content text(CharSequence msg) {return new TextContent(msg, "text/plain");}
	static Content json(CharSequence msg) {return new TextContent(msg, "application/json");}
	static Content html(CharSequence msg) {return new TextContent(msg, "text/html");}
	static Content file(Request req, FileInfo info) {return new FileContent().init(4, req, info);}
	static Content sendfile(Request req, DiskFileInfo info) {return new FileContent().init(0, req, info);}
	static Content bytes(DynByteBuf buffer) {
		var resp = new AsyncContent();
		resp.offerAndRelease(buffer);
		resp.setEof();
		return resp;
	}
	/**
	 * 显示一个用户友好的错误界面
	 */
	static Content internalError(String message) {return TextContent.errorPage(message);}
	/**
	 * 显示一个用户友好的错误界面
	 */
	static Content internalError(String message, Throwable exception) {return TextContent.errorPage(message, exception);}
	static Content httpError(int code) {return HttpServer.getInstance().createHttpError(code);}
	static Content websocket(Request req, Function<Request, WebSocket> newHandler) {return websocket(req, newHandler, WebSocketResponse.EMPTY_PROTOCOL);}
	static Content websocket(Request req, Function<Request, WebSocket> newHandler, Set<String> protocols) {return WebSocketResponse.websocket(req, newHandler, protocols);}
	static Content redirect(Request req, String url) {return req.server().code(HttpUtil.FOUND).header("location", url).noContent();}

    default void prepare(ResponseHeader rh, Headers h) throws IOException {}
	/**
	 * @return true if not all data were written.
	 */
	boolean send(ContentWriter rh) throws IOException;
	default void release(ChannelCtx ctx) throws IOException {}
}