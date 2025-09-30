package roj.http.server;

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
		public void prepare(Response resp) {resp.setHeader("content-length", "0");}
		public boolean send(ContentWriter writer) {return false;}
	};

	static Content text(CharSequence msg) {return new TextContent(msg, "text/plain");}
	static Content json(CharSequence msg) {return new TextContent(msg, "application/json");}
	static Content html(CharSequence msg) {return new TextContent(msg, "text/html");}
	static Content file(Request req, FileInfo info) {return new FileContent().init(4, req, info);}
	/**
	 * 创建文件内容（尝试使用NIO的transferTo发送到网络以降低复制开销，若可行）。
	 * 注：此功能不支持HTTPS，无意义，所以实际上用处不大。
	 */
	static Content sendfile(Request req, DiskFileInfo info) {return new FileContent().init(0, req, info);}
	/**
	 * 创建字节缓冲内容的副本，源缓冲区将被复制一份。
	 */
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
	static Content redirect(Request req, String url) {return req.response().code(HttpUtil.FOUND).setHeader("location", url).noContent();}

	/**
	 * 准备响应内容，包括设置头部（如Content-Length）。
	 *
	 * @param resp 响应控制器
	 * @throws IOException 如果准备失败
	 */
    default void prepare(Response resp) throws IOException {}
	/**
	 * 发送响应内容。
	 *
	 * @param writer 内容写入器
	 * @return true 如果未全部写入数据（需要继续发送）
	 * @throws IOException 如果发送失败
	 */
	boolean send(ContentWriter writer) throws IOException;
	/**
	 * 释放资源（在通道关闭时调用）。
	 *
	 * @param ctx 通道上下文
	 * @throws IOException 如果释放失败
	 */
	default void release(ChannelCtx ctx) throws IOException {}
}