package roj.net.tcp.serv;

import roj.net.tcp.util.WrappedSocket;

import java.io.IOException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/28 20:49
 */
public interface Response {
    String CRLF = "\r\n";

    void prepare() throws IOException;

    /**
     * Send some content to
     *
     * @return true if not all data were written.
     * @throws IllegalStateException if not prepared.
     */
    boolean send(WrappedSocket channel) throws IOException;

    void release() throws IOException;
}
