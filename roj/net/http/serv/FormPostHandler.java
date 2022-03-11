package roj.net.http.serv;

import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.net.http.Headers;
import roj.net.http.HttpLexer;
import roj.net.http.IllegalRequestException;
import roj.util.ByteList;

import java.nio.ByteBuffer;

/**
 * @author Roj233
 * @since 2022/3/13 15:15
 */
public abstract class FormPostHandler implements StreamPostHandler {
    private byte[] boundary;
    private int state;

    private final ByteList tmp = new ByteList(32);
    private final HttpLexer hl = new HttpLexer();
    private final Headers hdr = new Headers();

    protected boolean hasArray;

    public FormPostHandler(Request req) {
        init(req);
    }

    public void init(Request req) {
        String ct = req.header("Content-Type");
        if (ct.startsWith("multipart")) {
            // multipart
            MyHashMap<String, String> hdr = Headers.decodeValue(ct, false);
            String boundary = hdr.get("boundary");
            if (boundary == null)
                throw new IllegalArgumentException("Not found boundary in Content-Type header: " + ct);
            this.boundary = IOUtil.SharedCoder.get().encode(boundary);
            this.state = 2;
        }
        tmp.clear();
    }

    @Override
    public final void onData(ByteBuffer buf) throws IllegalRequestException {
        hasArray = buf.hasArray();
        loop:
        while (true) {
            switch (state) {
                // begin: find name
                case 0:
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        if (b == '=') {
                            onKey(tmp.readAscii(tmp.wIndex()));
                            tmp.clear();
                            state = 1;
                            continue loop;
                        }
                        tmp.put(b);
                    }
                    return;
                // put data until find & [delim]
                case 1:
                    int i = buf.position();
                    while (buf.hasRemaining()) {
                        byte b = buf.get();
                        if (b == '&') {
                            int p = buf.position() - 1;
                            int l = buf.limit();

                            buf.position(i).limit(p);
                            onValue(buf);

                            buf.limit(l);

                            state = 0;
                            continue loop;
                        }
                    }
                    buf.position(i);
                    onValue(buf);
                    break;

                // first: find multipart begin
                case 2:
                    int L = 4 + boundary.length;
                    int prev = buf.position();
                    while (buf.remaining() > L) {
                        if (buf.get() == 0x2D) {
                            buf.mark();
                            check:
                            // --
                            if (buf.get() == 0x2D) {
                                byte[] boundary = this.boundary;
                                for (byte b : boundary) {
                                    if (b != buf.get()) {
                                        buf.reset();
                                        break check;
                                    }
                                }

                                int p = buf.position() - boundary.length - 2;
                                if (p > prev) {
                                    int lim = buf.limit();
                                    buf.limit(p).position(prev);
                                    onValue(buf);
                                    buf.limit(lim).position(p + boundary.length + 2);
                                }

                                // \r\n
                                char v = buf.getChar();
                                if (v != 0x0D0A) {
                                    state = -1;
                                    // EOF
                                    if (v == 0x2D2D && buf.getChar() == 0x0D0A) {
                                        return;
                                    }
                                    throw new IllegalArgumentException("Invalid multipart format");
                                }

                                state = 3;
                                continue loop;
                            }
                        }
                    }
                    buf.position(prev);
                    if (buf.remaining() < L) return;
                    onValue(buf);
                    buf.position(buf.limit());
                    break;
                // then, read header
                case 3:
                    hl.index = 0;
                    hdr.clear();
                    try {
                        hdr.readFromLexer(hl.init(new InfAsciiString(buf)));
                    } catch (OperationDone noDataAvailable) {
                        return;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        return;
                    }
                    onMPKey(hdr);
                    buf.position(buf.position() + hl.index);
                    state = 2;
                    break;
                case -1:
                    return;
            }
        }
    }

    protected void onMPKey(Headers hdr) throws IllegalRequestException {
        String cd = hdr.get("Content-Disposition");
        if (cd == null) throw new NullPointerException("No Content-Disposition header");
        MyHashMap<String, String> map = Headers.decodeValue(cd, false);
        String nm = map.get("name");
        if (nm == null) throw new NullPointerException("No name in Content-Disposition header");
        onKey(nm);
    }
    protected abstract void onKey(CharSequence key) throws IllegalRequestException;
    protected abstract void onValue(ByteBuffer buf) throws IllegalRequestException;
}
