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
package roj.net.http;

import roj.collect.MyHashSet;
import roj.crypt.Base64;
import roj.io.IOUtil;
import roj.io.NIOUtil;
import roj.net.WrappedSocket;
import roj.net.http.serv.Reply;
import roj.net.http.serv.Request;
import roj.net.http.serv.RequestHandler;
import roj.net.http.serv.StringResponse;
import roj.net.misc.FDCLoop;
import roj.net.misc.FDChannel;
import roj.text.CharList;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Websocket协议 <br>
 * <a href="https://datatracker.ietf.org/doc/html/rfc6455">RFC6455</a>
 * @author Roj234
 * @since  2021/2/14 18:26
 */
public class WebSockets {
    public static final byte
            /**
             * 延续帧。本次数据传输采用了数据分片，当前收到的数据帧为其中一个数据分片。
             */
            FRAME_CONTINUE = 0x0,
            FRAME_TEXT     = 0x1,
            FRAME_BINARY   = 0x2,
            FRAME_CLOSE    = 0x8,
            FRAME_PING     = 0x9,
            FRAME_PONG     = 0xA;

    static final ThreadLocalRandom RNG = ThreadLocalRandom.current();

    private final Set<String> validProtocol;
    private final MessageDigest SHA1;

    public Supplier<? extends Worker> newWorker;
    public Consumer<? extends Worker> closeCallback;
    public FDCLoop<?> loop;

    public WebSockets() {
        this.validProtocol = new MyHashSet<>(4);
        this.validProtocol.add("");
        try {
            this.SHA1 = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException();
        }
    }

    public final Set<String> getValidProtocol() {
        return validProtocol;
    }

    // B-F control frame

    private void calcKey(String key, ByteList out) {
        String sec = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        ByteList bl = new ByteList(key.length() + sec.length());
        bl.putAscii(key).putAscii(sec);
        Base64.encode(bl.setArray(SHA1.digest(bl.list)), out);
    }

    public final Reply switchToWebsocket(Request req, RequestHandler handle) {
        String ver = req.header("Sec-WebSocket-Version");
        String protocol = req.header("Sec-WebSocket-Protocol");
        if(!ver.equals("13") || !validProtocol.contains(protocol == null ? "" : protocol))
            return new Reply(Code.UNAVAILABLE, new StringResponse("Unsupported protocol \"" + protocol + "\""));

        String key = req.header("Sec-WebSocket-Key");
        Reply reply = new Reply(Code.SWITCHING_PROTOCOL);
        ByteList b = reply.getRawHeaders();
        b.clear();
        b.putAscii("HTTP/1.1 101 Switching Protocols\r\n" +
                           "Server: Async/1.2\r\n" +
                           "Upgrade: websocket\r\n" +
                           "Connection: Upgrade\r\n" +
                           "Sec-WebSocket-Version: 13\r\n" +
                           "Sec-WebSocket-Accept: ");
        calcKey(key, b);
        if (protocol != null) {
            b.putAscii("\r\nSec-WebSocket-Protocol: ").putAscii(protocol);
        }

        boolean zip = false;
        String ext = req.header("Sec-WebSocket-Extensions");
        if (ext.contains("permessage-deflate")) {
            zip = true;
            b.putAscii("\r\nSec-WebSocket-Extensions: permessage-deflate");
            //The "Per-Message Compressed" bit, which indicates whether or not
            //the message is compressed.  RSV1 is set for compressed messages
            //and unset for uncompressed messages.
        }

        b.putAscii("\r\n");

        registerNewWorker(handle, zip);
        return reply;
    }

    protected void registerNewWorker(RequestHandler handle, boolean zip) {
        Worker w = newWorker.get();
        w.ch = handle.ch;
        if (zip) w.enableZip();

        handle.waitAnd(s -> {
            try {
                loop.register(Helpers.cast(w), Helpers.cast(closeCallback));
            } catch (Exception e) {
                Helpers.athrow(e);
            }
        });
    }

//    public final void setWorkerClass(Class<?> cls) {
//        if (!Worker.class.isAssignableFrom(cls))
//            throw new ClassCastException("Not inherit Worker");
//        ClassPoet poet = new ClassPoet(new Clazz(52 << 16, AccessFlag.PUBLIC, "", ""));
//    }

    public static UTFCoder getUTFCoder() {
        return IOUtil.SharedUTFCoder.get();
    }

    public static class Worker extends FDChannel {
        public static final ByteBuffer EMPTY = ByteBuffer.allocate(0);

        /**
         关闭握手异常代号
         代号	描述	使用场景
         1000	正常关闭	会话正常完成时
         1001	离开	应用离开且不期望后续连接的尝试而关闭连接时
         1002	协议错误	因协议错误而关闭连接时
         1003	不可接受的数据类型	非二进制或文本类型时
         1007	无效数据	文本格式错误，如编码错误
         1008	消息违反政策	当应用程序由于其他代号不包含的原因时
         1009	消息过大	当接收的消息太大，应用程序无法处理时（帧的载荷最大为64字节）
         1010	需要拓展
         1011	意外情况
         2 其他代号
         代号	描述	使用情况
         0~999	禁止
         1000~2999	保留
         3000~3999	需要注册	用于程序库、框架和应用程序
         4000~4999	私有	应用程序自由使用
         */
        public static final int ERR_OK = 1000,
                ERR_CLOSED = 1001,
                ERR_PROTOCOL = 1002,
                ERR_INVALID_FORMAT = 1003,
                ERR_INVALID_DATA = 1007,
                ERR_POLICY = 1008,
                ERR_TOO_LARGE = 1009,
                ERR_EXTENSION_REQUIRED = 1010,
                ERR_UNEXPECTED = 1011;

        public static final int RSV_COMPRESS = 0x40;

        // flag的可选位
        public static final int
                REMOTE_NO_CTX = 1,
                LOCAL_NO_CTX = 2,
                LOCAL_SIMPLE_MASK = 4,
                ACCEPT_PARTIAL_MSG = 8,
                I_SEND_COMPRESS = 16,
                REMOTE_MASK = 0x80; // 标志为服务端

        private Deflater def;
        private Inflater inf;
        private byte[] zipIn;
        private ByteBuffer zipOut;

        private final byte[] mask = new byte[4];
        private int except = 2;

        private Fragments cf;
        private ByteBuffer sending;

        // 最大数据长度
        protected int maxData;
        // 空置时间 ms
        protected int idle;
        protected byte flag;
        // 发送分片大小
        protected int fragmentSize;

        public int errCode;
        public String errMsg;

        public Worker() {
            maxData = Integer.MAX_VALUE;
            flag = (byte) REMOTE_MASK;
            fragmentSize = 4096;
        }

        public final void enableZip() {
            if (def != null) return;
            this.def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
            this.inf = new Inflater(true);
            this.zipIn = new byte[256];
            this.zipOut = ByteBuffer.allocate(256);
        }

        public void setMaxData(int maxData) {
            this.maxData = maxData;
        }

        public int getMaxData() {
            return maxData;
        }

        protected static CharList decodeToUTF(ByteBuffer in) {
            return getUTFCoder().decodeR(in, true);
        }

        protected static ByteBuffer encodeUTF(CharSequence seq) {
            ByteList b = getUTFCoder().encodeR(seq);
            return ByteBuffer.wrap(b.list, 0, b.wIndex());
        }

        @Override
        public void tick() throws IOException {
            ch.dataFlush();
            if (idle++ == 30000) {
                send(FRAME_PING, null);
            } else if (idle == 35000) {
                error(ERR_CLOSED, null);
            }
        }

        @Override
        public void close() throws IOException {
            key.cancel();
            try {
                ch.shutdown();
            } catch (IOException ignored) {}
            ch.close();
            if (inf != null){
                inf.end();
                def.end();
            }
        }

        @Override
        public final void selected(int readyOps) throws Exception {
            idle = 0;
            if (flush != null) {
                if (ch.write(flush) < 0) {
                    close();
                    return;
                }
                if (!flush.hasRemaining()) {
                    if (flush != ch.buffer()) {
                        NIOUtil.clean(flush);
                    } else {
                        flush.clear();
                    }
                    flush = null;
                    if (sending != null) {
                        sendFragments();
                        if (sending != null || flush != null) return;
                    }
                    key.interestOps(SelectionKey.OP_READ);
                }
                return;
            }

            WrappedSocket ch = this.ch;
            ByteBuffer rb = ch.buffer();
            int exc = this.except;
            do {
                if (rb.position() < exc) {
                    int read = ch.read(exc - rb.position());
                    if (read < 0) {
                        close();
                    }
                }
                if (rb.position() < exc) {
                    this.except = exc;
                    return;
                }

                int len = rb.get(1) & 0xFF;
                if ((len & 0x80) != (flag & REMOTE_MASK)) {
                    // not masked properly
                    error(ERR_PROTOCOL, null);
                    return;
                }
                switch (len & 0x7F) {
                    case 126:
                        // extended 16 bits (2 bytes) of len
                        len = 4;
                        break;
                    case 127:
                        // extended 64 bits (8 bytes) of len
                        len = 10;
                        break;
                    default:
                        // no extended
                        len = 2;
                }
                if ((flag & REMOTE_MASK) != 0) len += 4;
                exc = len;
                if ((rb.get(0) & 15) >= 0x8) {
                    if (exc > 6) {
                        error(ERR_TOO_LARGE, "control frame size");
                        return;
                    }
                    if ((rb.get(0) & 0x80) == 0) {
                        error(ERR_PROTOCOL, "control frame fragmented");
                        return;
                    }
                }
                if (rb.position() < exc) continue;

                if ((flag & REMOTE_MASK) != 0) {
                    int pos = rb.position();
                    rb.position(exc - 4);
                    rb.get(mask).position(pos);
                }

                switch (rb.get(1) & 0x7F) {
                    case 126:
                        exc += rb.getChar(2);
                        break;
                    case 127:
                        long l = rb.getLong(2);
                        if (l > Integer.MAX_VALUE - exc) {
                            error(ERR_TOO_LARGE, ">2G");
                            return;
                        }
                        exc += l;
                        break;
                    default:
                        exc += rb.get(1) & 127;
                }
                if (exc > maxData) {
                    error(ERR_TOO_LARGE, null);
                    return;
                }
                if (rb.position() < exc) continue;

                rb.flip().position(len);
                except = 2;

                if ((flag & REMOTE_MASK) != 0) {
                    mask(rb);
                }

                int head = rb.get(0) & 0xFF;

                if (cf == null) {
                    if ((head & 0xF) == 0) {
                        error(ERR_PROTOCOL, "Unexpected continuous frame");
                        return;
                    } else if ((head & 0x80) == 0) {
                        cf = new Fragments(head);
                    }
                } else if ((head & 0xF) != 0 && (head & 0xF) < 8) {
                    error(ERR_PROTOCOL, "Receive new message in continuous frame");
                    return;
                } else {
                    head = (head & 0x80) | (cf.data & 0x7F);
                }

                if ((head & RSV_COMPRESS) != 0) {
                    byte[] zi = this.zipIn;
                    if (zi == null) {
                        error(ERR_PROTOCOL, "Illegal rsv bits");
                        return;
                    }
                    int rem = rb.remaining();
                    if (zi.length < rem + 4) {
                        zipIn = zi = new byte[zi.length + rem + 4];
                    }
                    rb.get(zi, 0, rem).clear();
                    if ((head & 0x80) != 0) {
                        zi[rem++] = 0; zi[rem++] = 0;
                        zi[rem++] = -1; zi[rem++] = -1;
                    }
                    inf.setInput(zi, 0, rem);

                    ByteBuffer zo = this.zipOut;
                    zo.clear();

                    do {
                        int cnt = inf.inflate(zo.array(), zo.position(), zo.remaining());
                        zo.position(zo.position() + cnt);
                        if (!zo.hasRemaining()) {
                            ByteBuffer zb1 = this.zipOut = ByteBuffer.allocate(zo.capacity() << 1);
                            zo.position(0);
                            zb1.put(zo);
                            zo = zb1;
                        }
                    } while (!inf.needsInput());

                    // not continuous
                    if ((head & 0x80) != 0 && (flag & REMOTE_NO_CTX) != 0) {
                        inf.reset();
                    }

                    (rb = zo).flip();
                }

                if (cf != null) {
                    if ((flag & ACCEPT_PARTIAL_MSG) != 0) {
                        onPacket(0x80 | (head & 15), rb);
                        cf.fragments++;
                    } else {
                        cf.append(rb);
                        if (cf.length > maxData) {
                            error(ERR_TOO_LARGE, null);
                        }

                        if ((head & 0x80) != 0) {
                            onPacket(cf.data & 0xF, cf.payload());
                            cf = null;
                        }
                    }
                } else {
                    onPacket(head & 15, rb);
                }

                rb.clear();
                return;
            } while (true);
        }

        private void mask(ByteBuffer b) {
            byte[] mask = this.mask;
            int maskI = (mask[0] & 0xFF) << 24 | (mask[1] & 0xFF) << 16 |
                        (mask[2] & 0xFF) << 8 | mask[3] & 0xFF;

            int pos = b.position();
            while (b.remaining() > 4) {
                b.putInt(b.getInt(b.position()) ^ maskI);
            }
            int i = 0;
            while (b.hasRemaining()) {
                b.put((byte) (b.get(b.position()) ^ mask[i++ & 3]));
            }
            b.position(pos);
        }

        protected void onPacket(int ph, ByteBuffer in) throws IOException {
            switch (ph & 0xF) {
                case FRAME_CLOSE:
                    if (in.remaining() < 2) {
                        error(ERR_PROTOCOL, "reason missing");
                        return;
                    }
                    if (errCode == 0) {
                        in.mark();
                        errCode = in.getChar();
                        errMsg = in.hasRemaining() ? getUTFCoder().decode(in, false) : "";
                    }
                    onClosed();
                    send(FRAME_CLOSE, in);
                    assert flush == null;
                    close();
                    break;
                case FRAME_PONG:
                    break;
                case FRAME_PING:
                    send(FRAME_PONG, null);
                    break;
                case FRAME_TEXT:
                case FRAME_BINARY:
                    onData(ph, in);
                    break;
            }
        }

        protected void onData(int ph, ByteBuffer in) throws IOException {
            System.out.println("Data " + NIOUtil.dumpClean(in));
            send(ph & 15, in);
        }

        protected void onClosed() {
            System.out.println("Closed via " + errCode + "@" + errMsg);
        }

        public final boolean hasDataPending() {
            return flush != null || sending != null;
        }

        public final void error(int code, String msg) throws IOException {
            if (errCode != 0) {
                close();
                return;
            }

            if (msg == null) msg = "";
            else if (msg.length() > 255)
                msg = msg.substring(0, 255);
            errCode = code;
            errMsg = msg;

            UTFCoder uc = getUTFCoder();
            ByteList bb = uc.byteBuf;
            bb.clear();
            bb.putShort(code);
            bb.ensureCapacity(bb.wIndex() + 2);
            ByteList.writeUTF(bb, msg, -1);

            onClosed();
            ByteBuffer buf = ByteBuffer.wrap(bb.list, 0, bb.wIndex());
            send(FRAME_CLOSE, buf);
            assert flush == null;
            close();
        }

        private void sendFragments() throws IOException {
            ByteBuffer data = sending;

            int rem = data.remaining();
            // frame[1] ... frame[count - 1]: opcode=0
            while (rem > fragmentSize) {
                data.limit(data.position() + fragmentSize);
                send0(0, data, (flag & I_SEND_COMPRESS) != 0);
                rem -= fragmentSize;
                if (flush != null) {
                    enqueue(data, rem);
                    return;
                }
            }

            // frame[count]: not continuous
            data.limit(data.position() + rem);
            send0(0x80, data, (flag & I_SEND_COMPRESS) != 0);

            sending = null;
            flag &= ~I_SEND_COMPRESS;
        }
        private void enqueue(ByteBuffer data, int rem) {
            data.limit(data.position() + rem);
            sending = data;
        }

        private ByteBuffer flush;
        public void send(int opcode, ByteBuffer data) throws IOException {
            if (sending != null) throw new IllegalStateException("Continuous frame is sending");
            opcode |= 0x80;

            if (data == null) data = EMPTY;
            int rem = data.remaining();
            boolean comp = rem > 0 && (opcode & RSV_COMPRESS) != 0;
            if (rem > fragmentSize) {
                // frame[0]: continuous flag + original opcode
                data.limit(data.position() + fragmentSize);
                send0(opcode ^ 0x80, data, comp);

                if (comp) flag |= I_SEND_COMPRESS;

                enqueue(data, rem - fragmentSize);
                if (flush == null) sendFragments();
            } else {
                send0(opcode, data, comp);
            }
        }

        private void send0(int opcode, ByteBuffer data, boolean compressed) throws IOException {
            int $len = data.remaining();

            if (compressed) {
                byte[] ucl = getUTFCoder().byteBuf.list;

                if (data.hasArray()) {
                    def.setInput(data.array(), data.position(), data.remaining());
                    data.position(data.limit());
                } else {
                    byte[] zb = zipIn.length >= ucl.length ? zipIn : ucl;
                    data.get(zb, 0, $len);
                    def.setInput(zb, 0, $len);
                }

                byte[] zb;
                if (data == zipOut) {
                    zb = zipIn.length >= ucl.length ? zipIn : ucl;
                } else {
                    zb = zipOut.array();
                }
                int zbo = 0;

                if ((flag & LOCAL_NO_CTX) != 0) {
                    def.finish();
                }

                int flush = Deflater.NO_FLUSH;
                while (true) {
                    int d = def.deflate(zb, zbo, zb.length - zbo, flush);
                    zbo += d;
                    if (zb.length == zbo) {
                        byte[] zb1 = new byte[zb.length << 1];
                        System.arraycopy(zb, 0, zb1, 0, zbo);
                        zb = zb1;
                    } else if (d == 0) {
                        if (!def.needsInput()) {
                            System.out.println("WS: def.needsInput() returns 0");
                        }

                        if (flush == Deflater.NO_FLUSH) {
                            flush = Deflater.SYNC_FLUSH;
                        } else {
                            break;
                        }
                    }
                }

                if ((opcode & 0x80) != 0) {
                    if ((flag & LOCAL_NO_CTX) != 0) {
                        def.reset();
                    }

                    if (zbo >= 4 &&
                            zb[zbo - 1] == -1 &&
                            zb[zbo - 2] == -1 &&
                            zb[zbo - 3] == 0 &&
                            zb[zbo - 4] == 0) {
                        zbo -= 4;
                    } else {
                        System.out.println("WS: Not meet deflater Trailer");
                        zb[zbo++] = 0;
                    }
                }

                data = ByteBuffer.wrap(zb, 0, $len = zbo);
            }

            if ((flag & REMOTE_MASK) == 0) {
                $len += 4;
            }

            ByteBuffer out;
            if (NIOUtil.directBufferEquals(data, ch.buffer()) || ch.buffer().capacity() < $len + 10) {
                out = ByteBuffer.allocateDirect($len + 10);
            } else {
                out = ch.buffer();
                out.clear();
            }
            out.put((byte) opcode);
            if ($len <= 125) {
                out.put((byte) $len);
            } else if ($len <= 65535) {
                out.put((byte) 126)
                   .put((byte) ($len >> 8))
                   .put((byte) $len);
            } else {
                out.put((byte) 127).putInt(0)
                   .put((byte) ($len >> 24))
                   .put((byte) ($len >> 16))
                   .put((byte) ($len >> 8))
                   .put((byte) $len);
            }
            if ((flag & REMOTE_MASK) == 0) {
                out.put(1, (byte) (out.get(1) | REMOTE_MASK));
                if ((flag & LOCAL_SIMPLE_MASK) != 0)
                    out.putInt(0);
                else {
                    int mask = RNG.nextInt();
                    out.putInt(mask);
                    byte[] bm = this.mask;
                    bm[0] = (byte) (mask >>> 24);
                    bm[1] = (byte) (mask >>> 16);
                    bm[2] = (byte) (mask >>> 8);
                    bm[3] = (byte) mask;
                    mask(data);
                }
            }
            out.put(data).flip();
            if (ch.write(out) < 0) {
                close();
                return;
            }
            if (out.hasRemaining()) {
                key.interestOps(SelectionKey.OP_WRITE);
                flush = out;
            } else {
                if (out == ch.buffer()) out.clear();
                else NIOUtil.clean(out);
            }
        }
    }

    public static final class Fragments {
        Fragments(int data) {
            this.data = (byte) data;
            this.payloads = new ArrayList<>();
        }

        public final byte data;
        public int fragments;
        private ArrayList<ByteBuffer> payloads;
        private ByteBuffer payload;
        long length;

        public void append(ByteBuffer b) {
            ByteBuffer one = ByteBuffer.allocate(b.remaining());
            length += b.remaining();
            one.put(b);
            payloads.add(one);
            fragments++;
        }

        public ByteBuffer payload() {
            if (payload == null) {
                int cap = 0;
                for (int i = 0; i < payloads.size(); i++) {
                    cap += payloads.get(i).capacity();
                }
                ByteBuffer p = payload = ByteBuffer.allocate(cap);
                for (int i = 0; i < payloads.size(); i++) {
                    ByteBuffer src = payloads.get(i);
                    src.position(0);
                    p.put(src);
                }
                p.position(0);
                payloads = null;
            }
            return payload;
        }

        @Override
        public String toString() {
            return "Fragments{" +
                    "data=" + data +
                    ", fragments=" + fragments +
                    ", length=" + length +
                    '}';
        }
    }
}
