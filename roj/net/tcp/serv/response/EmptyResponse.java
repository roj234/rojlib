package roj.net.tcp.serv.response;

import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/11/29 0:22
 */
public final class EmptyResponse implements HTTPResponse {
    public static final HTTPResponse INSTANCE = new EmptyResponse();

    private EmptyResponse() {
    }

    @Override
    public void prepare() {
    }

    @Override
    public boolean send(WrappedSocket channel) {
        return false;
    }

    @Override
    public void release() {
    }

    @Override
    public void writeHeader(CharList list) {
    }
}
