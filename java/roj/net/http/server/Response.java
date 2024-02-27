package roj.net.http.server;

import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;
import roj.net.http.HttpUtil;
import roj.net.http.ws.WebSocketHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Set;
import java.util.function.Function;

@FunctionalInterface
public interface Response {
	Response EMPTY = new Response() {
		public void prepare(ResponseHeader rh, Headers h) {h.put("content-length", "0");}
		public boolean send(ResponseWriter rh) {return false;}
	};

	static Response text(CharSequence msg) {return new StringResponse(msg, "text/plain");}
	static Response json(CharSequence msg) {return new StringResponse(msg, "application/json");}
	static Response html(CharSequence msg) {return new StringResponse(msg, "text/html");}
	static Response file(Request req, FileInfo info) {return new FileResponse().init(4, req, info);}
	static Response sendfile(Request req, DiskFileInfo info) {return new FileResponse().init(0, req, info);}
	static Response bytes(DynByteBuf buffer) {
		var resp = new AsyncResponse();
		resp.offerAndRelease(buffer);
		resp.setEof();
		return resp;
	}
	/**
	 * 显示一个用户友好的错误界面
	 */
	static Response internalError(String message) {return StringResponse.errorPage(message);}
	/**
	 * 显示一个用户友好的错误界面
	 */
	static Response internalError(String message, Throwable exception) {return StringResponse.errorPage(message, exception);}
	static Response httpError(int code) {
		String desc = code+" "+HttpUtil.getDescription(code);
		return new StringResponse("<title>"+desc+"</title><center><h1>"+desc+"</h1><hr/><div>"+HttpServer11.SERVER_NAME+"</div></center>", "text/html");
	}
	static Response websocket(Request req, Function<Request, WebSocketHandler> newHandler) {return websocket(req, newHandler, WebSocketResponse.EMPTY_PROTOCOL);}
	static Response websocket(Request req, Function<Request, WebSocketHandler> newHandler, Set<String> protocols) {return WebSocketResponse.websocket(req, newHandler, protocols);}

	default void prepare(ResponseHeader rh, Headers h) throws IOException {}
	/**
	 * @return true if not all data were written.
	 */
	boolean send(ResponseWriter rh) throws IOException;
	default void release(ChannelCtx ctx) throws IOException {}
}