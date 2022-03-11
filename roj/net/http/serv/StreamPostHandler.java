package roj.net.http.serv;

import roj.net.http.IllegalRequestException;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/3/13 14:52
 */
public interface StreamPostHandler {
    void onData(ByteBuffer buf) throws IllegalRequestException;
    /**
     * 请求读取完毕
     */
    default void onSuccess() {}
    /**
     * 请求处理完毕
     * 若之前未调用onEnd则中途出现了错误
     */
    default void onComplete() throws IOException {

    }
    /**
     * request对象是否可以在context中保存此PostHandler
     */
    default boolean visibleToRequest() {
        return true;
    }
}
