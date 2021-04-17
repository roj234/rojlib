package roj.net.tcp.serv;

import roj.net.tcp.serv.util.Request;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.Socket;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/28 20:54
 */
@FunctionalInterface
public interface Router {
    default int writeTimeout(@Nullable Request request) {
        return 2000;
    }

    default int readTimeout() {
        return 1500;
    }

    Response response(Socket socket, Request request) throws IOException;

    default int maxLength() {
        return 1048576; // 1MB
    }

    default boolean checkAction(int action) {
        return action != -1;
    }

    default int postMaxLength() {
        return maxLength();
    }
}
