package roj.net.http.server;

import roj.collect.MyHashMap;
import roj.net.ch.ChannelCtx;

/**
 * @author Roj233
 * @since 2024/7/3 19:38
 */
public abstract class LocalPostHandler implements HPostHandler {
	public MyHashMap<String, Object> data;
	@Override
	public void handlerAdded(ChannelCtx ctx) {data = new MyHashMap<>();}
}