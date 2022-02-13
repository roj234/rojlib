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
import roj.net.Pipeline;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * MSS协议处理器：My Secure Socket <br>
 *     现在支持了一半的1-RTT: 在CLIENT_RNDB发送之后可以用一半的密钥先加密
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public abstract class MSSEngine implements Pipeline {
    MSSEngine() { random = new SecureRandom(); }
    MSSEngine(Random rnd) { this.random = rnd; }

    final Random random;
    MSSKeyVerifier verifier;

    static CipherSuite[] defaultCipherSuites = { CipherSuites.X509_DHE_XCHACHA20_SHA256_XHASH,
                                                 CipherSuites.X509_DHE_AESGCM_SHA256_XHASH,
                                                 CipherSuites.X509_ECDHE_AESGCM_SHA384_XHASH };
    CipherSuite[] cipherSuites = defaultCipherSuites;

    public static void setDefaultCipherSuites(CipherSuite[] ciphers) {
        if (ciphers.length > 65535)
            throw new IllegalArgumentException("ciphers.length > 65535");
        MSSEngine.defaultCipherSuites = ciphers.clone();
    }

    public final void setCipherSuites(CipherSuite[] ciphers) {
        if (ciphers.length > 65535)
            throw new IllegalArgumentException("ciphers.length > 65535");
        this.cipherSuites = ciphers;
    }

    static final byte CLOSED = 1, STREAMING = 2, RANDOM_PADDING = 4, PSK_ONLY = 8;

    byte flag, stage;
    CipherSuite suite; MSSSubKey dh; MSSHash hash; MSSSign signer;
    int bufferSize;
    CipheR encoder, decoder;
    byte[] sharedKey, closeReason;
    int sessionId;
    MSSException error;

    /**
     * 客户端模式
     */
    public abstract boolean isClientMode();

    /**
     * 是否使用流模式
     */
    public final void setStreamMode(boolean b) {
        if (b) flag |= STREAMING;
        else flag &= ~STREAMING;
    }
    public final boolean isStreamMode() {
        return (flag & STREAMING) != 0;
    }
    public final boolean isClosed() {
        return (flag & CLOSED) != 0;
    }

    public abstract void setPreSharedKeys(MSSPubKey[] keys);

    public final CipherSuite getCipherSuite() {
        return suite;
    }

    public void setCertificateVerifier(MSSKeyVerifier verifier) {
        this.verifier = verifier;
    }
    public MSSKeyVerifier getCertificateVerifier() {
        return verifier;
    }

    public final int getSessionId() {
        if (stage != HS_DONE) throw new IllegalStateException();
        return sessionId;
    }

    /**
     * 关闭引擎
     * 若reason!=null会在下次调用wrap时向对等端发送关闭消息
     */
    public final void close(String reason) {
        if (reason == null || stage != HS_DONE || (flag & (STREAMING | CLOSED)) != 0) {
            endCipher();
            flag |= CLOSED;
            return;
        }

        byte[] r = closeReason = reason.getBytes(StandardCharsets.UTF_8);
        if (r.length > 255)
            r = closeReason = Arrays.copyOf(r, 255);
        for (int i = 0; i < r.length; i++) {
            r[i] ^= sharedKey[i % sharedKey.length];
        }
    }

    private void endCipher() {
        if (sharedKey != null) {
            Arrays.fill(sharedKey, (byte) 0);
            try {
                encoder.setKey(sharedKey, CipheR.ENCRYPT);
                decoder.setKey(sharedKey, CipheR.ENCRYPT);
            } catch (Throwable ignored) {}
            encoder = decoder = null;
            sharedKey = null;
        }
    }

    public final void reset() {
        endCipher();
        this.stage = 0;
        this.encoder = this.decoder = null;
        this.sharedKey = this.closeReason = null;
        this.flag = 0;
        this.dh = null;
        this.hash = null;
        this.suite = null;
        this.signer = null;
        this.error = null;
    }

    public final boolean isHandshakeDone() {
        return stage >= HS_DONE;
    }

    public final byte[] _getSharedKey() {
        if (stage != HS_DONE) throw new IllegalStateException();
        return sharedKey;
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

    // 公开状态
    public static final int HS_FINISHED = 0, HS_RCV = 1, HS_SND = 2;
    public static final int HS_OK = 0, BUFFER_OVERFLOW = 1, BUFFER_UNDERFLOW = 2;

    // 内部状态
    static final int C_HS_INIT = 0, C_RNDB_WAIT = 1, DONE_WAIT = 4;
    static final int S_HS_WAIT = 0, S_RNDA_WAIT = 1;
    static final int TMP_1 = 2, TMP_2 = 3, HS_DONE = 5, HS_FAIL = 6;

    // 数据包header
    static final char PROTOCOL_VERSION = 11;

    // 2-RTT:
    // CLIENT_HALLO =>
    // <= SERVER_HALLO
    //
    // CLIENT_RNDA =>
    // <= SERVER_RNDB
    //
    // PH_DONE =>
    // Payload =>

    // {
    //   CLIENT_HALLO hdr; // ASCII 'SSNC'
    //   u2 PROTOCOL_VERSION;
    //   u2 cipher_suite_count;
    //   u2 psk_count;
    //   int[cipher_suite_count] cipher_suites;
    //   int[psk_count] psk_ids; (negotiated)
    //   u4 random;
    // }
    static final int  PH_CLIENT_HALLO = 0x53534E43;
    // {
    //   SERVER_HALLO hdr;
    //   u2 pubkey_len; (0 means psk available)
    //   u2 dh_key_len+signature_len;
    //   u2 selected_psk_id;
    //   u2 selected_cipher_suite_id;
    //   byte[pubkey_len] pubkey;
    //   byte[dh_key_len] dh_key;
    //   byte[signature_len] dh_signature;
    // }
    static final byte PH_SERVER_HALLO = 0x40;
    // {
    //   CLIENT_RNDA hdr;
    //   u2 ciphertext_len;
    //   u2 dh_key_len;
    //   u2 certificate_len; (0 means not use client-verify)
    //   byte[ciphertext_len] ciphertext;
    //   byte[dh_key_len] dh_key;
    //   byte[certificate_len] certificate;
    // }
    static final byte PH_CLIENT_RNDA  = 0x41;
    // {
    //   SERVER_RNDB hdr;
    //   u4 key_hash;
    //   u2 keyB_len+sign_len;
    //   byte[keyB_len] keyB; (ciphered by rndA+dh_sec)
    //   byte[sign_len] dh_sign; (signed by signer via pubKey rndA,rndB,dh_common_key)
    // }
    static final byte PH_SERVER_RNDB  = 0x42;
    // { // 服务端验证密钥正确
    //   DONE hdr;
    //   u2 key_len;
    //   byte[key_len] key; (ciphered by rndA+rndB+dh_sec)
    // }
    static final byte PH_DONE         = 0x43;
    // {
    //   ERROR hdr;
    //   u1 code;
    //   u1 msg_len;
    //   byte[msg_len] msg;
    // }
    static final byte PH_ERROR        = 0x01;

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
        if (closeReason != null) throw new MSSException("输入已关闭");
        if ((flag & STREAMING) != 0) {
            if (dst.remaining() < rcv.remaining()) return dst.remaining() - rcv.remaining();
            try {
                decoder.crypt(rcv, dst);
            } catch (GeneralSecurityException e) {
                _close("解密失败");
            }
            return 0;
        }

        if (!rcv.hasRemaining()) return 1;
        switch (rcv.get(0)) {
            case PH_CHUNK:
                // u1 hdr, u2 len, opt[? hash], u1[len]
                int ph = 3 + hash.length();
                if (rcv.remaining() < ph) return ph - rcv.remaining();

                int len = rcv.getChar(1);
                if (rcv.remaining() < len + ph)
                    return len + ph - rcv.remaining();
                if (dst.remaining() < len)
                    return dst.remaining() - len;

                int lim = rcv.limit();
                rcv.position(3).limit(ph);
                Object _hash = null;
                try {
                    _hash = hash.preRead(rcv, decoder);
                } catch (GeneralSecurityException e) {
                    _close("Hash计算失败");
                }
                rcv.position(ph).limit(ph + len);
                int pos = dst.position();
                dst.mark();
                try {
                    decoder.crypt(rcv, dst);
                } catch (GeneralSecurityException e) {
                    _close("解密失败");
                }
                rcv.limit(lim);

                if (dst.position() - pos != len) {
                    _close("消息长度有误 exc " + len + ", got " + (dst.position() - pos));
                }

                if (_hash != null) {
                    lim = dst.limit();
                    dst.limit(dst.position()).reset();
                    pos = rcv.position();
                    rcv.position(3);
                    if (!hash.check(_hash, dst))
                        _close("Hash验证失败");
                    rcv.position(pos);
                    dst.position(dst.limit()).limit(lim);
                }

                return 0;
            case PH_CLOSE:
                // u1 hdr, u1 len, u4 hash, utf[len] msg
                if (rcv.remaining() < 6) return BUFFER_UNDERFLOW;
                if (rcv.remaining() < (rcv.get(1) & 0xFF) + 6) return BUFFER_UNDERFLOW;
                endCipher();
                flag |= CLOSED;

                rcv.position(6);
                byte[] msgs = new byte[rcv.get(1) & 0xFF];
                rcv.get(msgs);
                for (int i = 0; i < msgs.length; i++) {
                    msgs[i] ^= sharedKey[i % sharedKey.length];
                }
                String v = new String(msgs, StandardCharsets.UTF_8);

                throw new MSSException(hash.computeHandshakeHash(sharedKey) != rcv.getInt(2) ?
                                "不可信的关闭数据包: " + v :
                                "对等端关闭连接: " + v);
            default:
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
        if ((flag & STREAMING) != 0) {
            if (snd.remaining() < src.remaining()) return snd.remaining() - src.remaining();
            try {
                encoder.crypt(src, snd);
            } catch (GeneralSecurityException e) {
                _close("加密失败");
            }
            return 0;
        }

        if (closeReason != null) {
            int l = 6 + closeReason.length;
            if (snd.remaining() < l) return snd.remaining() - l;
            snd.put(PH_CLOSE).put((byte) closeReason.length)
               .putInt(hash.computeHandshakeHash(sharedKey)).put(closeReason);
            closeReason = null;
            endCipher();
            flag |= CLOSED;
            return l;
        }

        int lim = src.limit();
        try {
            while (src.position() < lim) {
                int w = Math.min(lim - src.position(), 65535);

                int t = 3 + hash.length() + w;
                if (snd.remaining() < t) {
                    return snd.remaining() - t;
                }

                src.limit(src.position() + w);

                // u1 hdr, u2 len, opt[? hash], u1[len]
                snd.put(PH_CHUNK).putChar((char) w);

                try {
                    hash.write(src, snd, encoder);
                    encoder.crypt(src, snd);
                } catch (GeneralSecurityException e) {
                    _close("加密失败");
                }
            }
        } finally {
            src.limit(lim);
        }
        return 0;
    }

    private void _close(String reason) throws MSSException {
        close(reason);
        throw new MSSException(reason);
    }

    /**
     * 是的, by design ,你要主动告诉对面错在哪里
     */
    public final void checkError() throws MSSException {
        if (error != null) throw error;
    }

    final int error(String reason, ByteBuffer snd) throws MSSException {
        stage = HS_FAIL;
        byte[] data = reason.getBytes(StandardCharsets.UTF_8);
        MSSException e = error = new MSSException(reason);
        if (snd.capacity() < 2 + data.length) throw e;
        snd.clear();
        snd.put(PH_ERROR).put((byte) data.length).put(data);
        return HS_OK;
    }

    final int error(Throwable ex, String reason, ByteBuffer snd) throws MSSException {
        stage = HS_FAIL;
        byte[] data = reason.getBytes(StandardCharsets.UTF_8);
        MSSException e = error = new MSSException(reason, ex);
        if (snd.capacity() < 2 + data.length) throw e;
        snd.clear();
        snd.put(PH_ERROR).put((byte) data.length).put(data);
        return HS_OK;
    }

    final int packetError(ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() < 2) return uf(2);
        if (rcv.remaining() < (rcv.get(1) & 0xFF) + 2) return uf((rcv.get(1) & 0xFF) + 2);
        byte[] utf = new byte[rcv.get(1) & 0xFF];
        for (int i = 0; i < utf.length; i++) {
            // 保不齐又来个log4j2或类似的0day...
            byte b = utf[i];
            if (b < 32) {
                utf[i] = ' ';
            } else {
                switch (b) {
                    case '{':
                    case '[':
                    case '<':
                    case '$':
                        utf[i] = ' ';
                }
            }
        }
        rcv.position(2);
        rcv.get(utf);
        stage = HS_FAIL;
        error = new MSSException("对面告诉你 '" + new String(utf, StandardCharsets.UTF_8) + "'");
        throw error;
    }
}
