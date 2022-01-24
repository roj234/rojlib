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

import org.jetbrains.annotations.ApiStatus;
import roj.collect.MyHashSet;
import roj.crypt.Base64;
import roj.io.NIOUtil;
import roj.net.WrappedSocket;
import roj.net.http.serv.Reply;
import roj.net.http.serv.Request;
import roj.net.http.serv.RequestHandler;
import roj.net.http.serv.StringResponse;
import roj.net.misc.FDCLoop;
import roj.net.misc.FDChannel;
import roj.net.misc.Shutdownable;
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Websocket协议 <br>
 * @see https://datatracker.ietf.org/doc/html/rfc6455#section-7.4
 * @author Roj234
 * @since  2021/2/14 18:26
 */
public class WebSocketManager extends FDCLoop<WebSocketManager.Worker> {
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

    private final Set<String> validProtocol;
    private final MessageDigest SHA1;

    private Consumer<WrappedSocket> zipped, unzipped;

    public WebSocketManager(Shutdownable owner, int maxThreads) {
        super(owner, "WebSocket worker #", maxThreads, 30000, 100);
        this.validProtocol = new MyHashSet<>(4);
        this.validProtocol.add("");
        try {
            this.SHA1 = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException();
        }
        this.zipped = new ToWS(true);
        this.unzipped = new ToWS(false);
    }

    public void setZippedCsm(Consumer<WrappedSocket> zipped) {
        this.zipped = zipped;
    }

    public void setUnzippedCsm(Consumer<WrappedSocket> unzipped) {
        this.unzipped = unzipped;
    }

    public Set<String> getValidProtocol() {
        return validProtocol;
    }

    // B-F control frame

    private void calcKey(String key, ByteList out) {
        String sec = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

        ByteList bl = new ByteList(key.length() + sec.length());
        bl.putAscii(key).putAscii(sec);
        Base64.encode(bl.setValue(SHA1.digest(bl.list)), out);
    }

    public Reply switchToWebsocket(Request req, RequestHandler handle) {
        String ver = req.header("Sec-WebSocket-Version");
        String protocol = req.header("Sec-WebSocket-Protocol");
        if(!ver.equals("13") || !validProtocol.contains(protocol == null ? "" : protocol))
            return new Reply(Code.UNAVAILABLE, new StringResponse("Unsupported protocol \"" + protocol + "\""));

        String key = req.header("Sec-WebSocket-Key");
        Reply reply = new Reply(Code.SWITCHING_PROTOCOL);
        ByteList b = reply.getRawHeaders();
        b.clear();
        b.putAscii("HTTP/1.1 101 Switching Protocols\r\nServer: AsyncWS/1.0\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ");
        calcKey(key, b);
        if (protocol != null) {
            b.putAscii("\r\nSec-WebSocket-Protocol: ").putAscii(protocol);
        }

        boolean zip = false;
        String ext = req.header("Sec-WebSocket-Extensions");
        if (ext.indexOf("permessage-deflate") >= 0) {
            zip = true;
            b.putAscii("\r\nSec-WebSocket-Extensions: permessage-deflate");
            //The "Per-Message Compressed" bit, which indicates whether or not
            //the message is compressed.  RSV1 is set for compressed messages
            //and unset for uncompressed messages.
        }

        b.putAscii("\r\n");

        handle.waitAnd(zip ? zipped : unzipped);
        return reply;
    }

    public static class Worker extends FDChannel {
        private Deflater def;
        private Inflater inf;
        private ByteBuffer zipBuf;

        int maxData;
        int idle;

        public final UTFCoder uc;
        public int errCode;
        public String errMsg;

        public Worker(WrappedSocket ch, boolean zip) {
            super(ch);
            maxData = 1048576;
            uc = new UTFCoder();
            if (zip) {
                this.def = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
                this.inf = new Inflater(true);
                this.zipBuf = ByteBuffer.allocate(256);
            }
        }

        public void setMaxData(int maxData) {
            this.maxData = maxData;
        }

        public int getMaxData() {
            return maxData;
        }

        @Override
        public void tick() throws IOException {
            ch.dataFlush();
            if (idle++ > 30000) {
                send(FRAME_PING, null);
            } else if (idle > 40000) {
                // 10s not pong received
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
            inf.end();
            def.end();
        }

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

        public static final int FLAG_COMPRESSED = 0b0100_0000;

        private final byte[] mask = new byte[4];
        private int except = 2;
        @Override
        public void selected(int readyOps) throws Exception {
            idle = 0;
            if (flush != null) {
                ch.write(flush);
                if (!flush.hasRemaining()) {
                    if (flush != ch.buffer()) {
                        NIOUtil.clean(flush);
                    } else {
                        flush.clear();
                    }
                    flush = null;
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
                if ((len & 0x80) == 0) {
                    error(ERR_PROTOCOL, null);
                    return;
                }
                len &= 127;
                switch (len & 127) {
                    case 126:
                        // extended 16 bits (2 bytes) of len
                        len = 8;
                        break;
                    case 127:
                        // extended 64 bits (8 bytes) of len
                        len = 14;
                        break;
                    default:
                        // no extended
                        len = 6;
                }
                if ((exc = len) > 6) {
                    if ((rb.get(0) & 15) > 0x8) {
                        error(ERR_TOO_LARGE, null);
                        return;
                    }
                }
                if (rb.position() < exc) continue;

                int maskI = rb.getInt(exc - 4);
                byte[] mask = this.mask;
                int pos = rb.position();
                rb.position(exc - 4);
                rb.get(mask).position(pos);

                switch (len) {
                    case 8:
                        exc += rb.getChar(2);
                        break;
                    case 14:
                        long l = rb.getLong(2);
                        if (l > Integer.MAX_VALUE - exc) {
                            error(ERR_TOO_LARGE, null);
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

                while (rb.remaining() > 4) {
                    rb.putInt(rb.getInt(rb.position()) ^ maskI);
                }
                int i = 0;
                while (rb.hasRemaining()) {
                    rb.put((byte) (rb.get(rb.position()) ^ mask[i++ & 3]));
                }
                rb.position(len);

                int b0 = rb.get(0) & 0xFF;
                if ((b0 & FLAG_COMPRESSED) != 0) {
                    ByteBuffer zb = this.zipBuf;
                    if (zb.capacity() < rb.remaining() << 1) {
                        zb = this.zipBuf = ByteBuffer.allocate(rb.remaining() << 1);
                    } else {
                        zb.clear();
                    }
                    zb.put(rb);
                    int initPos = zb.position();
                    inf.reset();
                    inf.setInput(zb.array(), 0, initPos);
                    do {
                        int uncom = inf.inflate(zb.array(), zb.position(), zb.remaining());
                        zb.position(zb.position() + uncom);
                        if (!zb.hasRemaining()) {
                            ByteBuffer zb1 = this.zipBuf = ByteBuffer.allocate(zb.capacity() << 1);
                            zb.position(initPos).limit(zb.capacity());
                            zb1.put(zb);
                            zb = zb1;
                            initPos = 0;
                        } else {
                            break;
                        }
                    } while (true);
                    zb.flip().position(initPos);
                    handlePacket(b0, zb);
                } else {
                    handlePacket(b0, rb);
                }

                rb.clear();
                return;
            } while (true);
        }

        @ApiStatus.OverrideOnly
        protected void handlePacket(int b0, ByteBuffer in) throws IOException {
            switch (b0 & 15) {
                case FRAME_CLOSE:
                    if (errCode == 0) {
                        in.mark();
                        errCode = in.getChar();
                        if (in.hasRemaining()) {
                            byte[] data = new byte[in.remaining()];
                            in.get(data).reset();
                            errMsg = uc.decode(data);
                        } else {
                            errMsg = "";
                        }
                    }
                    System.out.println("Closed via " + errCode + "@" + errMsg);
                    send(FRAME_CLOSE, in);
                    assert flush == null;
                    close();
                    break;
                case FRAME_PONG:
                    //System.out.println("PONG");
                    break;
                case FRAME_PING:
                    send(FRAME_PONG, null);
                    break;
                case FRAME_TEXT:
                case FRAME_BINARY:
                    System.out.println("Data " + NIOUtil.dumpClean(in));
                    send(b0 & 15, in);
                    break;
            }
        }

        public void error(int code, String msg) throws IOException {
            if (errCode != 0) {
                close();
                return;
            }

            if (msg == null) msg = "";
            else if (msg.length() > 255)
                msg = msg.substring(0, 255);
            errCode = code;
            errMsg = msg;
            ByteList bl = uc.byteBuf.putShort(code);
            bl.ensureCapacity(bl.wIndex() + 2);
            ByteList.writeUTF(bl, msg, -1);

            ByteBuffer buf = ByteBuffer.wrap(bl.list, 0, bl.wIndex());
            send(FRAME_CLOSE, buf);
            assert flush == null;
            close();
        }

        private ByteBuffer flush;
        public void send(int opcode, ByteBuffer data) throws IOException {
            int $len = data == null ? 0 : data.remaining();
            ByteBuffer out;
            if (data == ch.buffer()) {
                out = ByteBuffer.allocateDirect($len + 10);
            } else {
                out = ch.buffer();
                out.clear();
            }
            out.put((byte) (0x80 | opcode));
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
            if (data != null) out.put(data);
            out.flip();
            ch.write(out);
            if (out.hasRemaining()) {
                key.interestOps(SelectionKey.OP_WRITE);
                flush = out;
            } else {
                out.clear();
            }
        }
    }

    public final class ToWS implements Consumer<WrappedSocket> {
        private final boolean zip;

        ToWS(boolean zip) {this.zip = zip;}

        @Override
        public void accept(WrappedSocket ch) {
            try {
                WebSocketManager.this.register(new Worker(ch, zip), null);
            } catch (Exception ignored) {}
        }
    }
}
