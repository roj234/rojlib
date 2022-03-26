/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.net.http.serv;

import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.io.PooledBuf;
import roj.net.PlainSocket;
import roj.net.SocketSequence;
import roj.net.WrappedSocket;
import roj.net.http.*;
import roj.net.misc.FDChannel;
import roj.text.ACalendar;
import roj.util.ByteList;
import roj.util.FastThreadLocal;
import roj.util.Helpers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class RequestHandler extends FDChannel {
    public static final int KEEP_ALIVE_TIMEOUT = 300;
    private static final int SRCBUF_CAPACITY = 3072;

    public static final ACalendar RFC_DATE = new ACalendar(TimeZone.getTimeZone("GMT"));

    public static final class Local {
        public ACalendar date = RFC_DATE.copy();
        public MyHashMap<String, Object> ctx = new MyHashMap<>();
    }
    public static final FastThreadLocal<Local> LocalShared = FastThreadLocal.withInitial(Local::new);

    private final Router router;

    private static final byte INITIAL = 0, HANDSHAKE = 1, PARSING_REQUEST = 2,
            PROCESS_REQUEST = 3, PREPARE_REQUEST = 4, SENDING_HEAD = 5, SENDING_REPLY = 6,
            FLUSHING = 7, HANGING = 8, PRE_CLOSE = 9, CLOSING = 10, CLOSED = 11,
            EXCEPTION_CLOSED = 12, ASYNC_WAITING = 13;

    private static final int KEPT_ALIVE = 1, CLOSE_CONN = 2, HAS_DATE = 4, CHUNKED = 8,
            HAS_REPLY = 16, DEF_NOWARP = 32;

    private byte state, reqState, encoding, flag;

    private long time;

    private Request request;
    private Response reply;
    private RequestFinishHandler cb;

    private HttpLexer hl;
    private StreamPostHandler ph;

    private ByteList uc;
    private ByteBuffer hdr;

    public RequestHandler(WrappedSocket ch, Router router) {
        super(ch);
        this.router = router;
        this.uc = PooledBuf.alloc().retain();
        this.uc.ensureCapacity(128);
        this.tmp = PlainSocket.EMPTY;
    }

    @Override
    public void tick(int elapsed) {
        switch (state) {
            case PARSING_REQUEST:
                if (System.currentTimeMillis() > time) {
                    reply(Code.TIMEOUT).connClose();
                    reply = StringResponse.httpErr(Code.TIMEOUT);
                    state = PREPARE_REQUEST;
                    interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
                break;
            case HANDSHAKE:
            case HANGING:
                // exceeded time, no readable
            case SENDING_HEAD:
            case SENDING_REPLY:
            case FLUSHING:
            case CLOSING:
                // exceeded time, no writeable

                if (System.currentTimeMillis() > time) {
                    state = PRE_CLOSE;
                    interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                }
                break;
            case ASYNC_WAITING:
                if (System.currentTimeMillis() > time) {
                    time += router.writeTimeout(request);
                    state = PARSING_REQUEST;
                    reply(504);
                    state = SENDING_HEAD;
                    interestOps(SelectionKey.OP_WRITE);
                }
                break;
        }
    }

    public void setPreCloseCallback(RequestFinishHandler o) {
        cb = o;
    }

    public void setAsyncProcess(int timeout) {
        if (state != PROCESS_REQUEST) throw new IllegalStateException();
        state = ASYNC_WAITING;
        time = System.currentTimeMillis() + timeout;
        interestOps(0);
    }

    public void setAsyncProcessDone(Response resp) {
        if (state != ASYNC_WAITING) throw new IllegalStateException();
        state = SENDING_HEAD;
        reply = resp;
        time = System.currentTimeMillis() + router.writeTimeout(request);
        interestOps(SelectionKey.OP_WRITE);
    }

    public void setPostHandler(StreamPostHandler ph) {
        this.ph = ph;
    }

    public void setTimeout(int timeout) {
        time = System.currentTimeMillis() + timeout;
    }

    public void setReply(Response resp) {
        if (state != PARSING_REQUEST && state != PROCESS_REQUEST) {
            throw new IllegalStateException();
        }
        this.reply = resp;
    }

    @Override
    public void close() throws IOException {
        if (ch == null) return;

        try {
            clear();
        } catch (Throwable ignored) {}

        if (def != null) def.end();
        def = null;

        if (uc.list.length < 9999) {
            PooledBuf.alloc().release(uc);
        }
        uc = null;

        state = CLOSED;
        key.cancel();

        WrappedSocket ch = this.ch;
        this.ch = null;

        try {
            ch.shutdown();
        } catch (IOException ignored) {}

        ch.close();
    }

    @Override
    @SuppressWarnings("fallthrough")
    public void selected(int readyOps) throws Exception {
        try {
            switch (state) {
                case INITIAL:
                    time = System.currentTimeMillis() + router.readTimeout();
                    state = HANDSHAKE;
                case HANDSHAKE:
                    if (System.currentTimeMillis() > time) {
                        state = PRE_CLOSE;
                        selected(0);
                        return;
                    }

                    if (!ch.handShake()) {
                        interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        return;
                    }
                    interestOps(SelectionKey.OP_READ);

                    reqState = 0;
                    state = PARSING_REQUEST;
                case PARSING_REQUEST:
                    try {
                        if (!parse()) return;
                        if (ph != null) {
                            ph.onSuccess();
                            if (ph.visibleToRequest()) request.ctx().put(Request.CTX_POST_HANDLER, ph);
                        }
                    } catch (IllegalRequestException e) {
                        reply(e.code).connClose();
                        reply = StringResponse.forError(e.code, e.getMessage());
                        state = PREPARE_REQUEST;
                        selected(0);
                        return;
                    } catch (IOException e) {
                        throw e;
                    } catch (Throwable e) {
                        reply = router.errorCaught(e, this, "PARSING_REQUEST");
                        state = PREPARE_REQUEST;
                        selected(0);
                        return;
                    }
                    state = PROCESS_REQUEST;
                case PROCESS_REQUEST:
                    Response resp;
                    try {
                        resp = router.response(request, this);
                        // 默认200
                        if ((flag & HAS_REPLY) == 0 && resp != null) reply(200);
                        // 压缩
                        if (router.useCompress(request, this.reply = resp)) setCompress();
                    } catch (Throwable e) {
                        resp = router.errorCaught(e, this, "PROCESS_REQUEST");
                    }

                    this.reply = resp;

                    state = PREPARE_REQUEST;
                case PREPARE_REQUEST:
                    resp = this.reply;
                    try {
                        if (resp != null) resp.prepare();
                        prepare();
                    } catch (Throwable e) {
                        try {
                            if (resp != null) resp.release();
                        } catch (IOException ignored) {}

                        reply = resp = router.errorCaught(e, this, "PREPARE_REQUEST");
                        resp.prepare();
                        prepare();
                    }

                    // 没有调用reply并返回null且未发生异常
                    if ((flag & HAS_REPLY) == 0 && resp == null) {
                        state = PRE_CLOSE;
                        selected(0);
                        return;
                    }

                    state = SENDING_HEAD;
                    time = System.currentTimeMillis() + router.writeTimeout(request);
                case SENDING_HEAD:
                    ch.write(hdr);
                    if (hdr.hasRemaining()) {
                        forWrite();
                        return;
                    }
                    hdr = null;
                    if (chunked != null) chunked.enableOut();
                    state = SENDING_REPLY;
                case SENDING_REPLY:
                    resp = this.reply;
                    if (resp != null && resp.send(this)) {
                        forWrite();
                        return;
                    }
                    state = FLUSHING;
                case FLUSHING:
                    if (!finish() || !ch.dataFlush()) {
                        forWrite();
                        return;
                    } else {
                        if (cb != null && cb.onRequestFinish(this, true)) {
                            key.cancel();
                            ch = null;
                            cb = null;
                            clear();
                            state = CLOSED;
                            return;
                        }

                        if (request != null && !"close".equals(request.headers().get("Connection"))) {
                            state = HANGING;
                            clear();
                            interestOps(SelectionKey.OP_READ);
                            time = System.currentTimeMillis() + KEEP_ALIVE_TIMEOUT * 1000;
                        } else {
                            state = PRE_CLOSE;
                            selected(0);
                            return;
                        }
                    }
                case HANGING:
                    int r = ch.read();
                    if (r > 0 || (r == 0 && ch.buffer().position() > 0)) {
                        flag = KEPT_ALIVE;

                        reqState = 0;
                        time = System.currentTimeMillis() + router.readTimeout();
                        state = PARSING_REQUEST;
                        selected(0);
                        return;
                    } else if (r == 0) return;
                case PRE_CLOSE:
                    time = System.currentTimeMillis() + 100;
                    clear();
                    ch.buffer().clear();
                    state = CLOSING;
                case CLOSING:
                    if (!ch.shutdown() && System.currentTimeMillis() <= time) {
                        interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
                        return;
                    }

                    ch.close();
                    state = CLOSED;
                    key.cancel();
                    break;
                case CLOSED:
                case EXCEPTION_CLOSED:
                default:
                    throw new IllegalStateException();
            }
        } catch (Throwable e) {
            if (e.getClass() != IOException.class) e.printStackTrace();
            else System.out.println("异常 " + e.getMessage());

            try {
                close();
            } finally {
                state = EXCEPTION_CLOSED;
            }
        }
    }

    private void forWrite() throws Exception {
        interestOps(SelectionKey.OP_WRITE);
        if (System.currentTimeMillis() > time) {
            state = PRE_CLOSE;
            selected(0);
        }
    }

    private void interestOps(int ops) {
        if (key.interestOps() != ops)
            key.interestOps(ops);
    }

    private void clear() throws Exception {
        if (def != null) def.reset();
        request = null;
        hl = null;
        hdr = null;
        encoding = 0;
        reqState = 0;
        flag &= 1;

        setChunked0(false);

        Throwable e = null;
        if (reply != null) {
            try {
                reply.release();
            } catch (Throwable e1) {
                e = e1;
            }
            reply = null;
        }
        if (ph != null) {
            try {
                ph.onComplete();
            } catch (Throwable e1) {
                if (e == null) e = e1;
                else e.addSuppressed(e1);
            }
            ph = null;
        }
        if (cb != null) {
            try {
                cb.onRequestFinish(this, false);
            } catch (Throwable e1) {
                if (e == null) e = e1;
                else e.addSuppressed(e1);
            }
            cb = null;
        }
        if (e != null) Helpers.athrow(e);
    }

    private boolean parse() throws Exception {
        HttpLexer lexer = hl;

        if(lexer == null) {
            SocketSequence seq = new SocketSequence(true);
            lexer = new HttpLexer().init(seq.init(ch, 0, router.maxLength()));
            hl = lexer;
        }

        Request req = request;
        try {
            if (req == null) {
                String method = lexer.readHttpWord(),
                        path = lexer.readHttpWord(),
                        version = lexer.readHttpWord();

                if (version == null || !version.startsWith("HTTP/"))
                    throw new IllegalRequestException(Code.BAD_REQUEST, "无效请求头 " + version);

                // 浏览器限制2048
                if (path.length() > 2000) {
                    throw new IllegalRequestException(Code.URI_TOO_LONG);
                }

                int act = Action.valueOf(method);
                if (act <= 0)
                    throw new IllegalRequestException(Code.METHOD_NOT_ALLOWED, "无效请求类型 " + method);

                request = req = new Request(act, version.substring(version.indexOf('/') + 1), path);
                req.local = LocalShared.get();
                req.handler = this;

                compact(lexer, ch.buffer());
            }

            if (reqState < 1) {
                Headers headers = req.headers;
                headers.clear();
                try {
                    headers.readFromLexer(lexer);
                } catch (ParseException e) {
                    throw new IllegalRequestException(Code.BAD_REQUEST, e);
                }

                compact(lexer, ch.buffer());
                reqState = 1;

                validateHeader(headers);
                router.checkHeader(req);

                if ((flag & HAS_REPLY) != 0) {
                    state = PREPARE_REQUEST;
                    selected(0);
                    return false;
                }

                boolean c = req.header("Transfer-Encoding").equalsIgnoreCase("chunked");
                setChunked0(c);
            }
        } catch (OperationDone noDataAvailable) {
            lexer.index = 0;
            return false;
        }

        if (req.action() == Action.POST) {
            Headers headers = req.headers;
            ByteList uc = this.uc;

            if (reqState < 2) {
                uc.clear();

                String cl = headers.get("Content-Length");
                if (cl != null) {
                    long len = Long.parseLong(cl);
                    if (len > router.postMaxLength(req))
                        throw new IllegalRequestException(Code.ENTITY_TOO_LARGE);
                    req.postFields = new AtomicLong(len);

                    if (ph == null) {
                        if (len > 1024 * 1024 * 1024)
                            throw new IllegalRequestException(Code.INTERNAL_ERROR,
                            "对于大小超过8MB的post请求,必须使用StreamPostHandler");
                        uc.ensureCapacity((int) len);
                    }
                } else {
                    req.postFields = new AtomicLong(router.postMaxLength(req));
                }

                reqState = 2;
                time = System.currentTimeMillis() +
                        router.postTimeout(req, (int) (time - System.currentTimeMillis()));

                if ((flag & HAS_REPLY) != 0) {
                    state = PREPARE_REQUEST;
                    selected(0);
                    return false;
                }
            }

            AtomicLong len = (AtomicLong) req.postFields;
            StreamPostHandler ph = this.ph;
            int r = ch.buffer().position();
            if (!headers.containsKey("Content-Length")) {
                do {
                    ByteBuffer rb = ch.buffer();
                    int pos = rb.position();
                    if (pos == 0) continue;

                    rb.flip();
                    if (ph != null) ph.onData(rb);
                    else uc.put(rb);

                    if (rb.hasRemaining()) rb.compact();
                    else rb.clear();

                    if (len.addAndGet(r) < 0) throw new IllegalRequestException(Code.ENTITY_TOO_LARGE);
                } while ((r = ch.read()) > 0);
                if (r == 0) return false;
            } else {
                do {
                    ByteBuffer rb = ch.buffer();
                    int pos = rb.position();
                    if (pos == 0) continue;

                    rb.flip();
                    if (ph != null) ph.onData(rb);
                    else uc.put(rb);

                    if (rb.hasRemaining()) rb.compact();
                    else rb.clear();

                    if (len.addAndGet(-r) == 0) break;
                } while ((r = ch.read((int) Math.min(len.get(), 10000))) > 0);
                if (r < 0) throw new IOException("Unexpected EOF");
                if (len.get() > 0) return false;
            }
            req.postFields = uc;
        }

        hl = null;
        return true;
    }

    private static void validateHeader(Headers h) throws IllegalRequestException {
        int c = h.getCount("Content-Length");
        if (c > 1) {
            List<String> list = h.getAll("Content-Length");
            for (int i = 0; i < list.size(); i++) {
                if (!list.get(i).equals(list.get(0)))
                    throw new IllegalRequestException(400);
            }
        }
        if (c > 0 && h.containsKey("Transfer-Encoding"))
            throw new IllegalRequestException(400);
    }

    private static void compact(HttpLexer lexer, ByteBuffer buf) {
        if(buf != null)  {
            buf.flip().position(lexer.index);
            lexer.index = 0;
            buf.compact();
        }
    }

    // region Processing Request

    public void setChunked() {
        if ((flag & CHUNKED) == 0) {
            flag |= CHUNKED;
            setChunked0(true);
        }
    }

    private ChunkedSocket chunked;
    private void setChunked0(boolean on) {
        if (on) {
            if (!(ch instanceof ChunkedSocket)) {
                ch = chunked = chunked == null ? new ChunkedSocket(ch) : chunked;
                chunked.resetSelf();
                chunked.enableIn();
                if (tmp.capacity() == 0 || chunked.buffer().capacity() > tmp.capacity())
                    tmp = chunked.getBuffer(SRCBUF_CAPACITY);
            }
        } else if (ch instanceof ChunkedSocket) {
            chunked = (ChunkedSocket) ch;
            ch = ch.parent();
        }
    }

    public RequestHandler reply(int code) {
        if (state < PARSING_REQUEST || state > PREPARE_REQUEST) throw new IllegalStateException();
        flag |= HAS_REPLY;

        uc.clear();
        uc.putAscii("HTTP/1.1 ").putAscii(Integer.toString(code)).put((byte) ' ')
               .putAscii(Code.getDescription(code)).putAscii("\r\nServer: Async/2.0\r\n");

        return this;
    }

    public RequestHandler connClose() {
        if ((flag & HAS_REPLY) == 0) throw new IllegalStateException();

        if ((flag & CLOSE_CONN) != 0) return this;
        flag |= CLOSE_CONN;

        // mark to close
        if (request != null)
        request.headers.put("Connection", "close");
        uc.putAscii("Connection: close\r\n");
        return this;
    }

    public RequestHandler withDate() {
        if ((flag & HAS_REPLY) == 0) throw new IllegalStateException();

        if ((flag & HAS_DATE) != 0) return this;
        flag |= HAS_DATE;

        uc.putAscii("Date: ").putAscii(LocalShared.get().date.toRFCDate(System.currentTimeMillis())).putAscii("\r\n");
        return this;
    }

    public RequestHandler header(String k, String v) {
        if ((flag & HAS_REPLY) == 0) throw new IllegalStateException();
        uc.putAscii(k).putAscii(": ").putAscii(v).putAscii("\r\n");
        return this;
    }

    public RequestHandler header(String hdr) {
        if ((flag & HAS_REPLY) == 0) throw new IllegalStateException();
        uc.putAscii(hdr).putAscii("\r\n");
        return this;
    }

    public ByteList getRawHeaders() {
        if ((flag & HAS_REPLY) == 0) throw new IllegalStateException();
        return uc;
    }

    public <T> T returnNull() {
        return null;
    }

    public <T> T returns(T t) {
        return t;
    }

    private void prepare() {
        if (reply != null) reply.writeHeader(uc);
        else uc.putAscii("Content-Length: 0\r\n");
        if (cb == null && (flag & (KEPT_ALIVE|CLOSE_CONN)) == 0)
            uc.putAscii("Connection: keep-alive\r\n");

        boolean on = (flag & CHUNKED) != 0;
        setChunked0(on);
        if (on) {
            if (encoding != ENC_PLAIN) removeLength();
            uc.putAscii("Transfer-Encoding: chunked\r\n");
        }

        uc.putAscii("\r\n");

        hdr = ByteBuffer.wrap(uc.list, 0, uc.wIndex());
    }

    private void removeLength() {
        ByteList h = uc;
        byte[] b = h.list;
        byte[] b1 = "Content-Length: ".getBytes();
        find:
        for (int i = 0; i < h.wIndex(); i++) {
            if (b[i] == 'C' && h.wIndex() - i > 15) {
                int i1 = i;
                for (int j = 0; j < b1.length; j++) {
                    if (b1[j] != b[i+j]) {
                        continue find;
                    }
                }
                while (b[i++] != '\n');
                System.arraycopy(b, i, b, i1, h.wIndex() - i);
                h.wIndex(h.wIndex() - (i - i1));
                break;
            }
        }
    }

    // endregion
    // region Send Reply

    public int write(ByteBuffer buf) throws IOException {
        if (state != SENDING_REPLY) throw new IllegalStateException();
        if (!writePend()) return 0;

        switch (encoding) {
            default:
            case ENC_PLAIN:
                if (!buf.hasRemaining()) return 0;
                return ch.write(buf);
            case ENC_DEFLATE:
                int r1 = 0;
                byte[] b = uc.list;
                while (buf.hasRemaining()) {
                    int r;
                    buf.get(b, 0, r = Math.min(buf.remaining(), b.length));
                    r1 += r;
                    def.setInput(b, 0, r);

                    if (!deflate()) return r1;
                }
                return r1;
            case ENC_GZIP:
                r1 = 0;
                b = uc.list;
                while (buf.hasRemaining()) {
                    int r;
                    buf.get(b, 0, r = Math.min(buf.remaining(), b.length));
                    r1 += r;
                    def.setInput(b, 0, r);
                    crc.update(b, 0, r);

                    if (!deflate()) return r1;
                }
                return r1;
        }
    }

    public int write(InputStream in) throws IOException {
        if (state != SENDING_REPLY) throw new IllegalStateException();
        if (!writePend()) return 0;

        switch (encoding) {
            default:
            case ENC_PLAIN:
                ByteBuffer tmp = this.tmp;
                if (tmp.capacity() < SRCBUF_CAPACITY)
                    tmp = this.tmp = ByteBuffer.allocate(SRCBUF_CAPACITY);
                else tmp.clear();

                int r = in.read(tmp.array());
                if (r <= 0) return r;

                tmp.limit(r);
                flush();

                return r;
            case ENC_DEFLATE:
                byte[] b = uc.list;
                r = in.read(b);
                if (r > 0) {
                    def.setInput(b, 0, r);
                    deflate();
                }
                return r;
            case ENC_GZIP:
                b = uc.list;
                r = in.read(b);
                if (r > 0) {
                    def.setInput(b, 0, r);
                    crc.update(b, 0, r);
                    deflate();
                }
                return r;
        }
    }

    private boolean writePend() throws IOException {
        return flush() && (def == null || def.finished() || deflate());
    }

    private boolean deflate() throws IOException {
        ByteBuffer t = this.tmp;
        byte[] b = t.array();

        int r;
        while ((r = def.deflate(b)) > 0) {
            t.limit(r).position(0);
            if (!flush()) return false;
            if (r < b.length) break;
        }
        return true;
    }

    private boolean flush() throws IOException {
        while (tmp.hasRemaining()) {
            if (0 == ch.write(tmp)) return false;
        }
        return true;
    }

    @SuppressWarnings("fallthrough")
    private boolean finish() throws IOException {
        switch (encoding) {
            default:
            case ENC_PLAIN:
                if (!writePend()) return false;
                return (flag & CHUNKED) == 0 || ch.write(null) < 0;
            case ENC_GZIP:
                def.finish();
                if (!writePend()) return false;

                tmp.clear();
                tmp.putInt(Integer.reverseBytes((int) crc.getValue()))
                   .putInt(Integer.reverseBytes(def.getTotalIn())).flip();

                encoding = ENC_PLAIN;
                return finish();
            case ENC_DEFLATE:
                def.finish();

                encoding = ENC_PLAIN;
                return finish();
        }
    }

    // endregion

    private static final int ENC_PLAIN = 0, ENC_DEFLATE = 1, ENC_GZIP = 2;
    private static int isTypeSupported(String type) {
        switch (type) {
            case "*":
            case "deflate":
                return ENC_DEFLATE;
            case "gzip":
                return ENC_GZIP;
            //case "br":
            //    break;
            default:
                return -1;
        }
    }

    private CRC32 crc;
    private Deflater def;
    private ByteBuffer tmp;
    public void setCompress() {
        int enc = ENC_PLAIN;
        float maxQ = 0;

        String header = request.header("Accept-Encoding");
        for (Map.Entry<String, String> type : Headers.decodeValue(header, true).entrySet()) {
            float Q = 1;
            if (type.getValue() != null) {
                try {
                    Q = Float.parseFloat(type.getValue());
                } catch (NumberFormatException e) {
                    Q = 0;
                }
            }

            if (Q > maxQ) {
                int sup = isTypeSupported(type.getKey());
                if (sup >= 0) {
                    enc = sup;
                    maxQ = Q;
                }
            }
        }

        switch (enc) {
            case ENC_PLAIN:
                break;
            case ENC_GZIP:
                if ((flag & DEF_NOWARP) == 0) {
                    if (def != null) def.end();
                    def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                } else {
                    def.reset();
                }
                flag |= DEF_NOWARP;
                if (crc == null) crc = new CRC32();
                else crc.reset();
                uc.ensureCapacity(SRCBUF_CAPACITY);

                uc.putAscii("Content-Encoding: gzip\r\n");
                setChunked();

                tmp.clear();
                tmp.putShort((short) 0x1f8b).putLong((long) Deflater.DEFLATED << 56)
                   .flip();
                break;
            case ENC_DEFLATE:
                if ((flag & DEF_NOWARP) != 0 || def == null) {
                    if (def != null) def.end();
                    def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
                } else {
                    def.reset();
                }
                flag &= ~DEF_NOWARP;
                uc.ensureCapacity(SRCBUF_CAPACITY);

                uc.putAscii("Content-Encoding: deflate\r\n");
                setChunked();
                break;
        }
        encoding = (byte) enc;
    }
}
