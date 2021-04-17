package roj.net.tcp.serv.response;

import roj.net.tcp.serv.Response;
import roj.text.CharList;

public interface HTTPResponse extends Response {
    /**
     * Write HTTP request header
     */
    void writeHeader(CharList list);
}
