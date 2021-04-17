package roj.net.tcp.serv.response;

import roj.collect.MyHashMap;
import roj.net.tcp.util.WrappedSocket;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.IOException;
import java.util.Map;

public final class HeadResponse implements HTTPResponse {
    private final Map<CharSequence, CharSequence> header = new MyHashMap<>();
    private final HTTPResponse delegation;

    public HeadResponse() {
        this.delegation = null;
    }

    public HeadResponse(HTTPResponse delegation) {
        if (delegation.getClass() == HeadResponse.class)
            throw new IllegalArgumentException();
        this.delegation = delegation;
    }

    public void prepare() throws IOException {
        if (delegation != null)
            delegation.prepare();
    }

    public boolean send(WrappedSocket channel) throws IOException {
        return delegation != null && delegation.send(channel);
    }

    public void release() throws IOException {
        if (delegation != null)
            delegation.release();
    }

    public Map<CharSequence, CharSequence> headers() {
        return header;
    }

    @Override
    public void writeHeader(CharList list) {
        for (Map.Entry<CharSequence, CharSequence> entry : header.entrySet()) {
            list.append(entry.getKey()).append(": ").append(TextUtil.escape(entry.getValue())).append(CRLF);
        }
        if (delegation != null)
            delegation.writeHeader(list);
    }
}
