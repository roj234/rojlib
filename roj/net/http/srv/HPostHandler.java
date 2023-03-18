package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.net.ch.ChannelHandler;
import roj.security.SipHashMap;

import java.io.IOException;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/3/13 14:52
 */
public abstract class HPostHandler implements ChannelHandler {
	public Map<String, Object> map;

	@Override
	public void handlerAdded(ChannelCtx ctx) {
		map = new SipHashMap<>();
	}

	@Override
	public abstract void channelRead(ChannelCtx ctx, Object msg) throws IOException;

	/**
	 * 请求读取完毕
	 */
	public void onSuccess() throws IOException {}

	/**
	 * 请求处理完毕
	 * 若之前未调用onSuccess则中途出现了错误
	 */
	public void onComplete() throws IOException {}
}
