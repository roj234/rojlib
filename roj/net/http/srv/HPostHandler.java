package roj.net.http.srv;

import roj.net.http.IllegalRequestException;
import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2022/3/13 14:52
 */
public interface HPostHandler {
	void onData(DynByteBuf buf) throws IllegalRequestException;

	/**
	 * 请求读取完毕
	 */
	default void onSuccess() {}

	/**
	 * 请求处理完毕
	 * 若之前未调用onSuccess则中途出现了错误
	 */
	default void onComplete() throws IOException {}
}
