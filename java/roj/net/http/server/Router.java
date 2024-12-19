package roj.net.http.server;

import org.jetbrains.annotations.Nullable;
import roj.net.http.IllegalRequestException;

/**
 * @author Roj234
 * @since 2020/11/28 20:54
 */
@FunctionalInterface
public interface Router {
	int DEFAULT_POST_SIZE = 4096;

	default int writeTimeout(@Nullable Request req, @Nullable Response resp) {return resp == null ? 60000 : 3600_000;}
	default int readTimeout(Request req) {return req != null ? 30000 : 3000;}

	Response response(Request req, ResponseHeader rh) throws Exception;

	default void checkHeader(Request req, @Nullable PostSetting cfg) throws IllegalRequestException {
		if (cfg != null) cfg.postAccept(DEFAULT_POST_SIZE, 0);
	}

	default int keepaliveTimeout() {return 300_000;}
	// 这个默认值是4KB的cookie+1KB的其它部分
	default int maxHeaderSize() {return 4096 + 1024;}
}