package roj.net.http.serv;

import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2022/3/14 1:20
 */
public abstract class AsyncResponse implements Response {
    public AsyncResponse() {}

    @Override
    public void prepare() {}

    @Override
    public boolean wantCompress() {
        return true;
    }

    @Override
    public void writeHeader(ByteList list) {}
}
