/*
 * This file is a part of MoreItems
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
package roj.net.mss;

import roj.crypt.CipheR;
import roj.net.cross.Util;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Random;
import java.util.zip.Adler32;

/**
 * MSS协议处理器：My Secure Socket
 * @author Roj233
 * @version 0.1
 * @since 2021/12/22 12:21
 */
public abstract class MSSEngine {
    //KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    //KeyPair kp = kpg.generateKeyPair();
    //server.init(new JPubKey(), kp);

    MSSEngine() { random = new SecureRandom(); }
    MSSEngine(Random rnd) { this.random = rnd; }

    final Random  random;
    final Adler32 hasher = new Adler32();

    boolean likeChunkedMode;

    static MSSCiphers[] defaultSupportedCiphers = {JCiphers.AES_CFB8 };
    MSSCiphers[] supportedCiphers = defaultSupportedCiphers;

    public static void setDefaultSupportedCiphers(MSSCiphers[] ciphers) {
        if (ciphers.length > 65535)
            throw new IllegalArgumentException("ciphers.length > 65535");
        MSSEngine.defaultSupportedCiphers = ciphers.clone();
    }

    public final void setSupportedCiphers(MSSCiphers[] ciphers) {
        if (ciphers.length > 65535)
            throw new IllegalArgumentException("ciphers.length > 65535");
        this.supportedCiphers = ciphers;
    }

    static final byte CLOSED = 1, DIFF_CIPHER = 2, VERIFY = 4;

    byte flag;
    int stage, bufferSize;
    char chunkSize, halfSharedKeySize;
    CipheR encoder, decoder;
    byte[] sharedKey, closePkt;

    /**
     * 客户端模式
     */
    public abstract boolean isClientMode();

    /**
     * 是否使用加密套件推荐的数据包分块大小
     */
    public final void setLikeChunkedMode(boolean b) {
        if (stage != 0) throw new IllegalStateException();
        this.likeChunkedMode = b;
    }

    /**
     * 数据包分块大小
     */
    public final int getChunkSize() {
        return chunkSize;
    }

    public final boolean isClosed() {
        return (flag & CLOSED) != 0;
    }

    /**
     * 关闭引擎
     * 若reason!=null会在下次调用wrap时向对等端发送关闭消息
     */
    public final void close(String reason) {
        if (reason == null || sharedKey == null || isClosed()) return;
        flag |= CLOSED;

        byte[] strs = reason.getBytes(StandardCharsets.UTF_8);
        closePkt = new byte[6 + strs.length];
        System.arraycopy(strs, 0, closePkt, 6, strs.length);
        for (int i = 0; i < strs.length; i++) {
            strs[i] ^= sharedKey[i % sharedKey.length];
        }
        hasher.reset();
        hasher.update(strs);
        int v = (int) hasher.getValue();
        closePkt[0] = PH_CLOSE;
        closePkt[1] = (byte) (v >> 24);
        closePkt[2] = (byte) (v >> 16);
        closePkt[3] = (byte) (v >> 8);
        closePkt[4] = (byte) v;
        closePkt[5] = (byte) strs.length;
    }

    public final void reset() {
        this.stage = 0;
        this.encoder = this.decoder = null;
        this.sharedKey = this.closePkt = null;
        this.flag = VERIFY;
    }

    public final boolean isHandshakeDone() {
        return stage >= HS_DONE;
    }

    public final byte[] getSharedKey() {
        if (stage != HS_DONE) throw new IllegalStateException();
        return sharedKey.clone();
    }

    public final Random getRandom() {
        return random;
    }

    public final CipheR getEncoder() {
        if (stage != HS_DONE) throw new IllegalStateException();
        return encoder;
    }

    public final CipheR getDecoder() {
        if (stage != HS_DONE) throw new IllegalStateException();
        return decoder;
    }

    public static final int HS_FINISHED = 0, HS_RCV = 1, HS_SND = 2;
    public static final int HS_OK = 0, BUFFER_OVERFLOW = 1, BUFFER_UNDERFLOW = 2;

    // 状态
    static final int C_HS_INIT = 0, C_RNDB_WAIT = 1, DONE_WAIT = 4;
    static final int S_HS_WAIT = 0, S_RNDA_WAIT = 1;
    static final int TMP_1 = 2, TMP_2 = 3, HS_DONE = 5, HS_FAIL = 6;

    // 数据包header
    static final int  PH_CLIENT_HALLO = 0x53534E43; // ASCII 'SSNC'
    static final byte PH_ERROR        = 0x01;
    static final byte PH_SERVER_HALLO = 0x40;
    static final byte PH_CLIENT_RNDA  = 0x41;
    static final byte PH_SERVER_RNDB  = 0x42;
    static final byte PH_DONE         = 0x43;

    // u1 PH_CHUNK, u2 length, u1[length] data
    static final byte PH_CHUNK        = 0x30;
    // u1 PH_CLOSE, u1 flags
    static final byte PH_CLOSE        = 0x31;

    /**
     * 握手状态
     * @return HS_OK HS_WAITING HS_SEND
     */
    public abstract int getHandShakeStatus();

    public int getBufferSize() {
        if (bufferSize == 0)
            throw new IllegalStateException();
        int cap = bufferSize;
        bufferSize = 0;
        return cap;
    }

    final int of(int rem) {
        bufferSize = rem;
        return BUFFER_OVERFLOW;
    }

    final int uf(int rem) {
        bufferSize = rem;
        return BUFFER_UNDERFLOW;
    }

    /**
     * 进行握手操作
     * NOTICE: 当且仅当返回OK时才能发送sndbuf中数据！
     * @param snd 预备发送的数据缓冲区
     * @param rcv 接收到的数据
     * @return 状态 HS_OK BUFFER_OVERFLOW BUFFER_UNDERFLOW
     */
    public abstract int handshake(ByteBuffer snd, ByteBuffer rcv) throws MSSException;

    /**
     * 解码收到的数据
     * @param rcv 接收缓冲
     * @param dst 目的缓冲
     * @return 0ok，正数代表rcv还需要至少n个字节，负数代表dst还需要n个字节
     */
    public final int unwrap(ByteBuffer rcv, ByteBuffer dst) throws MSSException {
        if (stage != HS_DONE) throw new MSSException("请先握手");
        if ((flag & CLOSED) != 0) throw new MSSException("引擎已关闭");
        if (closePkt != null) throw new MSSException("输入已关闭");

        if (!rcv.hasRemaining()) return 1;
        switch (rcv.get(0)) {
            case PH_CHUNK:
                // u1 hdr, u2 encoded_len, opt[u2 decoded_len], opt[u4 hash], u1[encoded_len]
                int j = 3 + (flag & (DIFF_CIPHER | VERIFY)), k;
                if (rcv.remaining() < j) return j - rcv.remaining();
                if (rcv.remaining() < rcv.getChar(1) + j)
                    return rcv.getChar(1) + j - rcv.remaining();
                // 3,7 12,124; 5,9 14,18
                if (dst.remaining() < rcv.getChar(k = (j & 2) == 0 ? 3 : 1))
                    return dst.remaining() - rcv.getChar(k);

                rcv.position(j);
                int pos = dst.position();
                try {
                    decoder.crypt(rcv, dst);
                } catch (GeneralSecurityException e) {
                    _close("解密失败");
                }
                int len = dst.position() - pos;
                if (len != rcv.getChar(k)) {
                    System.out.println("RLen " + (int)rcv.getChar(k));
                    System.out.println("MLen " + len);
                    _close("消息长度有误");
                }
                int lim = dst.limit();
                dst.position(pos).limit(pos + len);

                if (j > 5) {
                    hasher.reset();
                    hasher.update(dst);
                    dst.limit(lim).position(pos + len);
                    if (hasher.getValue() != rcv.getInt(3 + (flag & DIFF_CIPHER))) {
                        _close("Adler32验证失败");
                    }
                }

                if (rcv.hasRemaining()) rcv.compact();
                else rcv.clear();
                return 0;
            case PH_CLOSE:
                // u1 hdr, u4 hash, u1 len, utf[len] msg
                if (rcv.remaining() < 6) return BUFFER_UNDERFLOW;
                if (rcv.remaining() < (rcv.get(5) & 0xFF) + 6) return BUFFER_UNDERFLOW;
                flag |= CLOSED;

                rcv.position(6);
                byte[] msgs = new byte[rcv.remaining()];
                rcv.get(msgs);
                String v = new String(msgs, StandardCharsets.UTF_8);

                for (int i = 0; i < msgs.length; i++) {
                    msgs[i] ^= sharedKey[i % sharedKey.length];
                }
                hasher.reset();
                hasher.update(msgs);

                throw new MSSException(hasher.getValue() != rcv.getInt(1) ?
                                "不可信的关闭数据包: " + v :
                                "对等端关闭连接: " + v);
            default:
                Util.dumpBuffer(rcv);
                _close("无效的数据包");
                return 0;
        }
    }

    /**
     * 编码数据用于发送
     * @param src 源缓冲
     * @param snd 发送缓冲
     * @return 非负数ok(写出)，负数代表snd还需要至少n个字节
     */
    public final int wrap(ByteBuffer src, ByteBuffer snd) throws MSSException {
        if (stage != HS_DONE) throw new MSSException("请先握手");
        if ((flag & CLOSED) != 0) throw new MSSException("引擎已关闭");
        if (closePkt != null) {
            if (snd.remaining() < closePkt.length) return snd.remaining() - closePkt.length;
            snd.put(closePkt);
            int l = closePkt.length;
            closePkt = null;
            flag |= CLOSED;
            return l;
        }

        int lim = src.limit();
        int tw = 0;
        try {
            while (src.remaining() > 0) {
                int w = Math.min(src.remaining(), chunkSize);

                if (snd.remaining() < 9 + w) {
                    return snd.remaining() - 9 - w;
                }

                src.limit(src.position() + w);

                snd.put(PH_CHUNK);
                if ((flag & DIFF_CIPHER) != 0) snd.putChar((char) 0);
                // u1 hdr, u2 encoded_len, u2 decoded_len, u4 hash, u1[encoded_len]
                snd.putChar((char) w);
                if ((flag & VERIFY) != 0) {
                    hasher.reset();
                    hasher.update(src);
                    snd.putInt((int) hasher.getValue());
                }

                int sp = snd.position();
                try {
                    if (CipheR.BUFFER_OVERFLOW == encoder.crypt(src, snd)) {
                        snd.position(snd.position() - 9);
                        return -1024;
                    }
                } catch (GeneralSecurityException e) {
                    _close("加密失败");
                }

                if ((flag & DIFF_CIPHER) != 0) {
                    int el = snd.position() - sp;
                    if (el > 65535) _close("密文长度无效");
                    snd.putChar(sp - flag & (DIFF_CIPHER | VERIFY), (char) el);
                }
            }
        } finally {
            src.limit(lim);
        }
        return tw;
    }

    private void _close(String reason) throws MSSException {
        close(reason);
        throw new MSSException(reason);
    }

    public final void getHandshakeErrorPacket(String reason, ByteBuffer buf) {
        if (stage != HS_FAIL) throw new IllegalStateException();
        byte[] utf = reason.getBytes(StandardCharsets.UTF_8);
        buf.put(PH_ERROR).put((byte) utf.length).put(utf);
    }

    final int error(String reason) throws MSSException {
        stage = HS_FAIL;
        throw new MSSException(reason);
    }

    final int error(String reason, Throwable ex) throws MSSException {
        stage = HS_FAIL;
        throw new MSSException(reason, ex);
    }

    final int packetError(ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() < 2) return BUFFER_UNDERFLOW;
        if (rcv.remaining() < (rcv.get(1) & 0xFF) + 2) return BUFFER_UNDERFLOW;
        byte[] utf = new byte[rcv.remaining() - 2];
        rcv.get(utf);
        return error("对等端: " + new String(utf, StandardCharsets.UTF_8));
    }
}
