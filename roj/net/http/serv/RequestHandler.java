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

import roj.concurrent.OperationDone;
import roj.concurrent.Waitable;
import roj.config.ParseException;
import roj.math.MathUtils;
import roj.math.MutableInt;
import roj.net.NetworkUtil;
import roj.net.PlainSocket;
import roj.net.SocketSequence;
import roj.net.WrappedSocket;
import roj.net.http.*;
import roj.net.misc.FDChannel;
import roj.text.ACalendar;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

public final class RequestHandler extends FDChannel {
    public static final int KEEP_ALIVE_TIMEOUT = 300;
    private static final int SRCBUF_CAPACITY = 3072;

    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
    public static final ACalendar        RFC_DATE    = new ACalendar(TimeZone.getTimeZone("GMT"));

    public static final class Local {
        public ACalendar date = RFC_DATE.copy();
        public RequestHandler active;
        public Object[] misc;
    }
    public static final FastThreadLocal<Local> LocalShared = FastThreadLocal.withInitial(Local::new);

    private final Router router;

    private static final byte INITIAL = 0, HANDSHAKE = 1, PARSING_REPLY = 2,
            SENDING_HEAD = 3, SENDING_REPLY = 4, FLUSHING = 5, HANGING = 6, PRE_CLOSE = 7,
            CLOSING = 8, CLOSED = 9, EXCEPTION_CLOSED = 10, ASYNC_WAITING = 11;

    private static final int KEPT_ALIVE = 1, CLOSE_CONN = 2, HAS_DATE = 4, CHUNKED = 8,
            HAS_REPLY                   = 16;

    private byte state, reqState, encoding, flag;

    private long time;

    private Request request;
    private Response reply;
    private Consumer<WrappedSocket> cb;
    private Waitable w;

    private HttpLexer hl;

    private final ByteList uc;
    private ByteBuffer hdr;

    public RequestHandler(WrappedSocket ch, Router router) {
        super(ch);
        this.router = router;
        this.uc = new ByteList(128);
        this.tmp = PlainSocket.EMPTY;
    }

    @Override
    public void tick(int elapsed) {
        switch (state) {
            case HANDSHAKE:
            case PARSING_REPLY:
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
                if (w.isDone()) {
                    time += router.writeTimeout(request);
                    state = SENDING_REPLY;
                    w = null;
                    interestOps(SelectionKey.OP_WRITE);
                } else if (System.currentTimeMillis() > time) {
                    time += router.writeTimeout(request);
                    state = SENDING_REPLY;
                    w.cancel();
                    w = null;
                    interestOps(SelectionKey.OP_WRITE);
                }
        }
    }

    public void setPreCloseCallback(Consumer<WrappedSocket> o) {
        cb = o;
    }

    public void waitFor(Waitable w, int timeout) {
        if (state != SENDING_REPLY) throw new IllegalStateException();
        state = ASYNC_WAITING;
        interestOps(0);
        this.w = w;
        this.time = System.currentTimeMillis() + timeout;
    }

    @Override
    public void close() throws IOException {
        if (ch == null) return;

        try {
            clear();
        } catch (IOException ignored) {}

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
                    state = PARSING_REPLY;
                case PARSING_REPLY:
                    Response resp;
                    try {
                        if(!parse()) return;
                        LocalShared.get().active = this;
                        try {
                            resp = router.response(ch, request, this);
                            // 默认200
                            if ((flag & HAS_REPLY) == 0 && resp != null) reply(200);
                            // 压缩
                            if (router.useCompress(request, this.reply = resp)) setEncoder();
                        } catch (Throwable e) {
                            e.printStackTrace();
                            reply(500).connClose();
                            resp = StringResponse.forError(0, e);
                        }
                    } catch (IllegalRequestException e) {
                        reply(e.code).connClose();
                        resp = StringResponse.forError(0, e);
                    }

                    try {
                        if (resp != null) resp.prepare();
                        prepare();
                    } catch (Throwable e) {
                        e.printStackTrace();
                        reply(500).connClose();
                        resp = StringResponse.forError(0, e);
                        resp.prepare();
                    }

                    this.reply = resp;

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
                        if (cb != null) {
                            cb.accept(ch);
                            key.cancel();
                            ch = null;
                            state = CLOSED;
                            return;
                        }

                        if (!request.headers().get("Connection").equals("close")) {
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
                    if (r > 0 || ch.buffer().position() > 0) {
                        flag = KEPT_ALIVE;

                        reqState = 0;
                        state = PARSING_REPLY;
                        time = System.currentTimeMillis() + router.readTimeout();
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
             e.printStackTrace();

            try {
                close();
            } finally {
                state = EXCEPTION_CLOSED;
            }
        }
    }

    private void forWrite() throws Exception {
        if (w != null) return;
        interestOps(SelectionKey.OP_WRITE);
        if (System.currentTimeMillis() > time) {
            state = PRE_CLOSE;
            selected(0);
        }
    }

    private void prepare() {
        if (reply != null) reply.writeHeader(uc);
        else uc.putAscii("Content-Length: 0\r\n");
        if ((flag & (KEPT_ALIVE|CLOSE_CONN)) == 0)
            uc.putAscii("Connection: keep-alive\r\n");
        uc.putAscii("\r\n");

        if ((flag & CHUNKED) != 0) removeLength();
        hdr = ByteBuffer.wrap(uc.list, 0, uc.wIndex());
    }

    private void removeLength() {
        ByteList h = uc;
        byte[] b = h.list;
        byte[] b1 = "Content-Length:".getBytes();
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

    private void interestOps(int ops) {
        if (key.interestOps() != ops)
            key.interestOps(ops);
    }

    private void clear() throws IOException {
        hex = null;
        if (def != null) def.end();
        def = null;
        request = null;
        hl = null;
        hdr = null;
        encoding = 0;
        reqState = 0;
        flag &= 1;
        if (reply != null) {
            reply.release();
            reply = null;
        }
    }

    private boolean parse() throws Exception {
        HttpLexer lexer = hl;

        if(lexer == null) {
            SocketSequence seq = new SocketSequence(true);
            lexer = new HttpLexer().init(seq.init(ch, 0, router.maxLength()));
            hl = lexer;
        }

        try {
            Request req = request;
            if (req == null) {
                String method = lexer.readHttpWord(),
                        path = lexer.readHttpWord(),
                        version = lexer.readHttpWord();

                if (version == null || !version.startsWith("HTTP/"))
                    throw new IllegalRequestException(Code.BAD_REQUEST, "无效请求头 " + version);

                if (path.length() > 1024) {
                    throw new IllegalRequestException(Code.URI_TOO_LONG);
                }

                int act = Action.valueOf(method);
                if (act <= 0)
                    throw new IllegalRequestException(Code.METHOD_NOT_ALLOWED, "无效请求类型 " + method);

                request = req = new Request(act, version.substring(version.indexOf('/') + 1), path);

                compact(lexer, ch.buffer());
            }

            Headers headers = req.headers;
            if (reqState < 1) {
                headers.clear();
                try {
                    headers.readFromLexer(lexer);
                } catch (ParseException e) {
                    throw new IllegalRequestException(Code.BAD_REQUEST, e);
                }

                compact(lexer, ch.buffer());
                reqState = 1;

                router.checkHeader(req);
            }

            if (req.action() == Action.POST) {
                ByteList uc = this.uc;
                if (reqState < 2) {
                    uc.clear();

                    String cl = headers.get("Content-Length");
                    if (cl != null) {
                        int len = MathUtils.parseInt(cl);
                        if (len > router.postMaxLength(req))
                            throw new IllegalRequestException(Code.ENTITY_TOO_LARGE);
                        req.postFields = new MutableInt(len);
                        uc.ensureCapacity(len);
                    }
                    reqState = 2;
                    time = router.postReadTimeout(req, (int) (time - System.currentTimeMillis()));
                }

                if (req.postFields == null) {
                    // no content length
                    if (!"close".equalsIgnoreCase(headers.get("Connection")))
                        throw new IllegalRequestException(411);
                    int r;
                    while ((r = ch.read()) > 0) {
                        ByteBuffer rb = ch.buffer();
                        rb.flip();
                        uc.put(rb);
                        if (uc.wIndex() > router.postMaxLength(req))
                            throw new IllegalRequestException(Code.ENTITY_TOO_LARGE);
                        rb.clear();
                    }
                    if (r == 0) return false;
                } else {
                    MutableInt remain = (MutableInt) req.postFields;
                    if (ch.read(remain.getValue()) < 0) throw new IOException("Unexpected EOF");

                    ByteBuffer rb = ch.buffer();
                    int r = rb.position();
                    rb.flip();
                    uc.put(rb);
                    rb.clear();

                    if (remain.addAndGet(-r) > 0) return false;
                }
                req.postFields = uc;
            }

            compact(lexer, ch.buffer());
            hl = null;
            return true;
        } catch (OperationDone noDataAvailable) {
            lexer.index = 0;
            return false;
        }
    }

    private static void compact(HttpLexer lexer, ByteBuffer buf) {
        if(buf != null)  {
            buf.flip().position(lexer.index);
            lexer.index = 0;
            buf.compact();
        }
    }

    @SuppressWarnings("fallthrough")
    private boolean finish() throws IOException {
        switch (encoding) {
            default:
            case ENC_PLAIN:
                if (hex != null) {
                    tmp.clear();
                    tmp.putInt(0x300D0A0D).put((byte) 0x0A).flip();
                    ch.write(tmp);
                }
                return true;
            case ENC_GZIP:
                def.finish();
                if (!writePend()) return false;

                tmp.clear();
                tmp.put((byte) 0x38).putChar((char) 0x0D0A)
                   .putInt(Integer.reverseBytes((int) crc.getValue()))
                   .putInt(Integer.reverseBytes(def.getTotalIn()))
                   .putInt(0x0D0A300D).putChar((char) 0x0A0D).put((byte) 0x0A)
                   .flip();

                def.end();
                return flush();
            case ENC_DEFLATE:
                def.finish();
                if (!writePend()) return false;
                tmp.clear();
                tmp.put((byte) 0x30).putInt(0x0D0A0D0A).flip();

                def.end();
                return flush();
        }
    }

    public RequestHandler reply(int code) {
        if (state != PARSING_REPLY) throw new IllegalStateException();
        flag |= HAS_REPLY;

        uc.clear();
        uc.putAscii("HTTP/1.1 ").putAscii(Integer.toString(code)).put((byte) ' ')
               .putAscii(Code.getDescription(code)).putAscii("\r\nServer: Async/2.0\r\n");

        // if (code >= 400) connClose();
        return this;
    }

    public RequestHandler connClose() {
        if ((flag & HAS_REPLY) == 0) throw new IllegalStateException();

        if ((flag & CLOSE_CONN) != 0) return this;
        flag |= CLOSE_CONN;

        // mark to close
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

    public Response nullResponse() {
        return null;
    }

    public int write(ByteBuffer buf) throws IOException {
        if (!writePend()) return 0;

        switch (encoding) {
            default:
            case ENC_PLAIN:
                if (!buf.hasRemaining()) return 0;

                ByteBuffer tmp = this.tmp;
                if (hex != null) {
                    tmp.clear();
                    makeChunkHead(buf.remaining(), tmp);
                    tmp.flip();
                    ch.write(tmp);
                }

                int x = buf.remaining();
                ch.write(buf);
                if (buf.hasRemaining()) {
                    if (tmp.capacity() < buf.remaining())
                        tmp = this.tmp = ByteBuffer.allocate(buf.remaining());
                    else tmp.clear();
                    tmp.put(buf).flip();
                }
                return x;
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
        if (!writePend()) return 0;

        switch (encoding) {
            default:
            case ENC_PLAIN:
                ByteBuffer tmp = this.tmp;
                if (tmp.capacity() < SRCBUF_CAPACITY)
                    tmp = this.tmp = ByteBuffer.allocate(SRCBUF_CAPACITY);

                byte[] b = tmp.array();
                int r = in.read(b, 10, b.length - 12);
                if (r <= 0) return r;

                if (hex != null) {
                    tmp.clear();
                    makeChunkHead(r, tmp);
                    tmp.flip();
                    ch.write(tmp);
                }

                tmp.position(10).limit(b.length - 12);
                flush();

                return r;
            case ENC_DEFLATE:
                b = uc.list;
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
        while ((r = def.deflate(b, 10, b.length - 12)) > 0) {
            t.clear();
            makeChunkHead(r, t);
            t.flip();
            ch.write(t);
            if (t.hasRemaining()) throw new IOException("Should not happen");

            t.limit(12 + r).position(10);
            t.putChar(10 + r, (char) 0x0D0A);
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
    private void setEncoder() {
        int enc = ENC_PLAIN;
        float maxQ = 0;

        String header = request.header("Accept-Encoding");
        for (String type : header.split(",")) {
            float Q = 1;
            int i = (type = type.trim()).indexOf('=');
            if (i >= 0) {
                try {
                    Q = Float.parseFloat(type.substring(i + 1));
                } catch (NumberFormatException e) {
                    Q = 0;
                }
            }

            if (Q > maxQ) {
                int sup = isTypeSupported(i < 0 ? type : type.substring(0, i));
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
                def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                if (crc == null) crc = new CRC32();
                else crc.reset();
                uc.ensureCapacity(SRCBUF_CAPACITY);

                uc.putAscii("Content-Encoding: gzip\r\n");
                setChunked();

                tmp.clear();
                makeChunkHead(10, tmp);
                tmp.putShort((short) 0x1f8b).putLong((long) Deflater.DEFLATED << 56)
                   .putChar((char) 0x0D0A).flip();
                break;
            case ENC_DEFLATE:
                def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
                uc.ensureCapacity(SRCBUF_CAPACITY);

                uc.putAscii("Content-Encoding: deflate\r\n");
                setChunked();
                break;
        }
        encoding = (byte) enc;
    }

    private byte[] hex;
    public void setChunked() {
        if ((flag & CHUNKED) == 0) {
            flag |= CHUNKED;
            hex = new byte[8];
            if (tmp.capacity() == 0) {
                tmp = ByteBuffer.allocate(SRCBUF_CAPACITY);
                tmp.flip();
            }

            uc.putAscii("Transfer-Encoding: Chunked\r\n");
        }
    }
    private void makeChunkHead(int len, ByteBuffer tmp) {
        byte[] hex = this.hex;
        int off = NetworkUtil.number2hex(len, hex);
        tmp.put(hex, off, 8 - off).putChar((char) 0x0D0A);
    }
}
