package roj.http.server;

import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/3/13 14:52
 */
public interface BodyParser extends ChannelHandler {
	@Override
	void channelRead(ChannelCtx ctx, Object msg) throws IOException;

	/**
	 * 请求读取完毕
	 */
	default void onSuccess(DynByteBuf rest) throws IOException {}

	/**
	 * 请求处理完毕
	 * 若之前未调用onSuccess则中途出现了错误
	 */
	default void onComplete() throws IOException {}

	default Map<String, ?> getMapLikeResult() {return null;}
}