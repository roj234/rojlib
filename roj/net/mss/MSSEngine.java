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
import roj.crypt.PKCS5;
import roj.crypt.Padding;
import roj.net.Pipeline;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;

/**
 * MSS协议处理器：My Secure Socket <br>
 * <a href="https://www.rfc-editor.org/info/rfc8446">RFC8446 - TLS 1.3</a> <br>
 *     现在支持了一半的1-RTT: 在CLIENT_RNDB发送之后可以用一半的密钥先加密
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public abstract class MSSEngine implements Pipeline {
    // region 我不适合搞这种东西
    //   1. 通信过程
    //
    //     {xxx} => 由一级公钥保护的数据
    //     [xxx] => 由二级公钥保护的数据
    //     <xxx> => 由共享密钥保护的数据
    //     *     => 可选项
    //
    //           Client                                               Server
    //
    //   第一次访问:
    //          ClientHallo
    //          协议头
    //          支持的加密套件
    //          随机32位整数
    //          * 保存的公钥                -------->
    //                                                          ServerHallo
    //                                                           一级公钥类型
    //                                                       证书(一级公钥) *
    //                                                          动态二级公钥A
    //                                                          选择的加密套件
    //                                    <--------          请求客户端证书 *
    //          ClientRandomA
    //          {共享密钥A}
    //          {* 客户端证书类型}
    //          动态二级公钥B
    //          * 客户端证书
    //          [* preflight数据]          -------->
    //                                                         ServerRandomB
    //                                                             [共享密钥B]
    //                                    <--------               二级公钥签名
    //          HandShakeDone
    //          共享密钥的摘要               -------->
    //          <应用数据>                  <------->                <应用数据>
    //
    //
    //   往后(如果返回的是ServerUseSession的话):
    //          ClientHello
    //          协议头
    //          支持的加密套件
    //          随机32位整数
    //          * 保存的公钥
    //          会话ID
    //          <* preflight数据>          -------->
    //                                                     ServerUseSession
    //                                                              <新密钥>
    //                                                              密钥签名
    //          <应用数据>                  <------->               <应用数据>
    //
    //   2. 共识
    //     ux => x字节的无符号整数
    //     sx => x字节的有符号整数
    //     a[b] => 长度为b的a数组
    //     a{b} => 长度为b的a数据
    //
    //     预约定 => 由应用层指定值的具体意义
    //
    //     Big-Endian
    //
    //   3. 数据包
    //      数据包统一使用头部第一字节表示 (ClientHallo除外)
    //      enum {
    //          握手:
    //          HandshakeError   (0x01) // '警告'
    //          ClientHallo      (0x53)
    //          ServerHallo      (0x40)
    //          ClientRandomA    (0x41)
    //          ServerRandomB    (0x42)
    //          HandshakeDone    (0x43)
    //          ServerUseSession (0x44)
    //          非流式传输:
    //          ApplicationData  (0x30)
    //          Close            (0x31)
    //
    //          (255)
    //      } Header
    //
    //   3.1 ClientHallo
    //     客户端发送此数据包
    //     服务端收到后:
    //       若session_id不为0, 且同一复用session: 发送ServerUseSession
    //       否则, 选择一个加密套件, 若无法选择, 则必须发送'illegal_state'警告(并终止连接,下同)
    //       若提供了预共享密钥(psk) 则[可以]选择一个psk 并发送ServerHallo
    //
    //     struct {
    //       Header 0x53534E43;
    //       u2 protocol_version;
    //       u2 cipher_suite_count;
    //       u2 psk_count;
    //       u4[cipher_suite_count] cipher_suites;
    //       u4[psk_count] psk_ids;
    //       u4 random_32;
    //       u4 session_id;
    //     } ClientHallo;
    //
    //     protocol_version: 目前固定为 11, 应当在不为11时发送'version_mismatch'警告
    //     cipher_suites: 按照[加密套件整数格式]定义的32位整数数组
    //     psk_ids: 具体的值无实际意义, [预约定]
    //     session_id: 为0时表示创建新的session, 否则代表复用此session的[尝试]
    //     random_32: 服务器[可以]用它作为二级公钥生成参数的[一部分]
    //
    //   3.2 ServerHallo
    //     服务端发送此数据包作为新session开始的标志
    //     客户端收到后:
    //       若必须选择psk而没有选择: 发送'illegal_state'警告
    //       若select_psk_id或select_suite_id超出范围: 发送'illegal_packet'警告
    //       若要求证书但没有: 发送'illegal_state'警告
    //       处理
    //       发送ClientRandomA
    //
    //     struct {
    //       Header ServerHallo;
    //       u2 public_key_len;
    //       u2 secondary_key_len;
    //       u2 select_psk_id;
    //       u2 select_suite_id;
    //       u1 key_format;
    //       PublicKey{public_key_len} public_key;
    //       SecondaryKey[secondary_key_len] secondary_key;
    //       u4 client_certificate_desc;
    //     } ServerHallo;
    //
    //     select_suite_id: 选择的加密套件是ClientHallo中表示的第i个
    //     select_psk_id: 为0时表示未选择预共享密钥, 否则代表选择CH表示的第i-1个
    //     public_key/secondary_key: 本质为字节数组, 按照CipherSuite的定义解析
    //     client_certificate_desc: 非零时要求客户端提供一个证书
    //        具体的值无实际意义, [预约定]
    //
    //   3.3 ClientRandomA
    //     客户端发送此数据包
    //     服务端收到后:
    //       若要求证书但没有: 发送'illegal_packet'警告
    //       解密RandomA
    //       处理
    //       发送ServerRandomB
    //
    //     struct {
    //       Header ClientRandomA;
    //       u2 ciphertext_len;
    //       u2 secondary_key_len;
    //       u2 certificate_len;
    //       u2 preflight_len;
    //       RandomA{ciphertext_len} data;
    //       SecondaryKey[secondary_key_len] secondary_key;
    //       PublicKey[certificate_len] certificate;
    //       byte[preflight_len] application_data;
    //     } ClientRandomA;
    //
    //     struct {
    //       byte[hsk] random_a;
    //       u1 certificate_format_id;
    //     } RandomA;
    //
    //     data: 用一级公钥加密的 RandomA 结构
    //
    //     certificate_format_id: certificate_len非零时表示证书的类型
    //     session_id: 客户端[希望]获得的session_id
    //     hsk: 加密套件约定的密钥大小的一半
    //
    //   3.4 ServerRandomB
    //     服务端发送此数据包
    //     客户端收到后:
    //       验证二级公钥的签名, 不通过发送'illegal_packet'警告
    //       解密random_b
    //       计算共享密钥
    //       发送HandshakeDone
    //       客户端握手完毕,开始发送应用数据
    //
    //     struct {
    //       Header ServerRandomB;
    //       u4 session_id;
    //       u2 length;
    //       byte[hsk] random_b;
    //       byte[N] signature;
    //     } ServerRandomB;
    //
    //     length: hsk+N
    //     hsk同上
    //     N: 加密套件约定的签名长度
    //     session_id: 会话id, 为0表示不保存
    //
    //   3.5 HandshakeDone
    //     客户端发送此数据包
    //     服务端收到后:
    //       验证共享密钥的哈希, 不通过发送'illegal_packet'警告
    //       服务端握手完毕,开始发送应用数据
    //
    //     struct {
    //       Header HandshakeDone;
    //       u1 length;
    //       u1 flag;
    //       byte[length] hash;
    //     } HandshakeDone;
    //
    //     flag: 第二位(2)若存在,则进入流式传输
    //           其余位保留
    //
    //   3.6 Error
    //     任意方向
    //     收到后:
    //       显示错误消息
    //       断开连接
    //
    //     struct {
    //       Header HandshakeError;
    //       ErrorType code;
    //       u1 length;
    //       utf[length] message;
    //     } HandshakeError;
    //
    //     enum {
    //       illegal_packet     (0x00) // 正常网络环境不应出现的错误
    //       illegal_state      (0x01) // 正常网络环境可能出现的错误
    //       version_mismatch   (0x02)
    //       internal_error     (0x03)
    //       custom             (0xff)
    //       (255)
    //     } ErrorType;
    //
    //     message: 以UTF8格式储存的不超过255长度的错误详细信息
    //
    //   3.7 ServerUseSession
    //     服务端发送此数据包
    //     客户端收到后:
    //       验证签名, 不通过发送'illegal_packet'警告
    //       原密钥与解密后的newKey异或
    //       客户端握手完毕,开始发送应用数据
    //
    //     struct {
    //       Header ServerUseSession;
    //       byte[hsk] newKey;
    //       byte[N] signature;
    //     } ServerUseSession;
    //
    //   非流式传输阶段
    //
    //   3.8 ApplicationData
    //     任意方向
    //     收到后:
    //       解密,验证,并转交给应用
    //
    //     struct {
    //       Header ApplicationData;
    //       u2 length;
    //       byte[length] data;
    //     } ApplicationData;
    //
    //   3.9 Close
    //     任意方向
    //     收到后:
    //       验证签名, 不通过... 也没办法
    //       关闭连接
    //
    //     struct {
    //       Header Close;
    //       ErrorType code;
    //       u1 length;
    //       utf[length] reason;
    //       byte[N] signature;
    //       u1 flag;
    //     } Close;
    //
    //     reason: 关闭原因
    //     flag: 第一位(1)若存在,关闭此session
    //           其余位保留
    //
    //   4. 加密套件
    //   4.1 数字id
    //   高位                                                            低位
    //   +-----------------------------------------------------------------+
    //   |                   1 1 1 1 1 1 1 1 1 1 2 2 2 2 2 2 2 2 2 2 3 3 3 |
    //   | 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 |
    //   +----------------+---------------+---------------+----------------+
    //   |    一级公钥类型  |   对称加密类型   |    签名类型    |   二级公钥类型   |
    //   +----------------+---------------+---------------+----------------+
    //
    //   4.1.1 id定义
    //      看CipherSuite.java得了
    //       <128 保留
    //      >=128 [预约定]
    //
    //   4.2 算法
    //
    //   5. 具体的处理过程
    //   5.1
    //   6. 安全性


    // endregion

    MSSEngine() { random = new SecureRandom(); }
    MSSEngine(Random rnd) { this.random = rnd; }

    final Random random;

    static CipherSuite[] defaultCipherSuites = { CipherSuites.DHE_XCHACHA20_SHA256,
                                                 CipherSuites.DHE_AESCFB_SHA256 };
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

    MSSKeyManager keyMan;
    byte[] preflight;

    public byte[] getPreflightData() {
        return preflight;
    }

    public void setPreflightData(byte[] data) {
        if (stage > 0) throw new IllegalStateException();
        this.preflight = data;
    }

    static final byte CLOSED = 1, STREAMING = 2, PSK_ONLY = 4;

    byte flag, stage;
    CipherSuite suite; MSSSubKey dh; MSSSign signer;
    int bufferSize;
    CipheR encoder, decoder;
    byte[] sharedKey, closeReason, tmp;
    MSSSession session;
    Padding padding;
    MSSException error;

    /**
     * 客户端模式
     */
    public abstract boolean isClientMode();

    /**
     * 是否使用流模式
     */
    public final void setStreamMode() {
        if (stage > 0) throw new IllegalStateException();
        flag |= STREAMING;
    }
    public final boolean isStreamMode() {
        return (flag & STREAMING) != 0;
    }
    public final boolean isClosed() {
        return (flag & CLOSED) != 0;
    }

    public abstract void setPSK(MSSPubKey[] keys);

    public final CipherSuite getCipherSuite() {
        return suite;
    }

    public void setKeyManager(MSSKeyManager verifier) {
        this.keyMan = verifier;
    }
    public MSSKeyManager getKeyManager() {
        return keyMan;
    }

    public final MSSSession getSession() {
        if (stage != HS_DONE) throw new IllegalStateException();
        return session;
    }

    /**
     * 将数据包对齐到这个大小
     */
    public void setPadding(int size) {
        if (size <= 16 || size > 65535) throw new IllegalArgumentException();
        this.padding = new PKCS5(size);
    }
    public void setPadding(Padding padding) {
        this.padding = padding;
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
        if (r.length > 255) closeReason = Arrays.copyOf(r, 255);
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
        this.closeReason = null;
        this.flag = 0;
        this.dh = null;
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
    static final int TMP_1 = 2, TMP_2 = 3, HS_DONE = 5, HS_FAIL = 6, REUSE_SESSION = 7, REUSE_SESSION_2 = 8;

    // 数据包header
    static final char PROTOCOL_VERSION = 11;

    static final byte ILLEGAL_PACKET   = 0;
    static final byte ILLEGAL_STATE    = 1;
    static final byte VERSION_MISMATCH = 2;
    static final byte INTERNAL_ERROR   = 3;

    static final int  PH_CLIENT_HALLO = 0x53534E43;
    static final byte PH_SERVER_HALLO = 0x40;
    static final byte PH_CLIENT_RNDA  = 0x41;
    static final byte PH_SERVER_RNDB  = 0x42;
    static final byte PH_DONE         = 0x43;
    static final byte PH_REUSE_SESSION= 0x44;
    static final byte PH_PREFLIGHT    = 0x45;
    static final byte PH_ERROR        = 0x01;
    static final byte PH_CHUNK        = 0x30;
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
                // u1 hdr, u2 len
                if (rcv.remaining() < 3) return 3 - rcv.remaining();

                int L = rcv.getChar(1);
                if (rcv.remaining() < L + 3) return L + 3 - rcv.remaining();
                // 这可不负责压缩, 所以没问题
                if (dst.remaining() < L) return dst.remaining() - L;

                int lim = rcv.limit();
                rcv.position(3).limit(3 + L);
                try {
                    int dstLim = dst.limit();
                    dst.mark();
                    decoder.crypt(rcv, dst);
                    if (padding != null) {
                        dst.limit(dst.position()).reset();
                        padding.unpad(dst);
                        dst.position(dst.limit()).limit(dstLim);
                    }
                } catch (GeneralSecurityException e) {
                    _close("解密失败");
                }
                rcv.limit(lim);
                return 0;
            case PH_CLOSE:
                // u1 hdr, u1 len, ux hash, utf[len] msg
                if (rcv.remaining() < 2 + signer.length()) return BUFFER_UNDERFLOW;
                if (rcv.remaining() < (rcv.get(1) & 0xFF) + 2 + signer.length()) return BUFFER_UNDERFLOW;
                endCipher();
                flag |= CLOSED;

                rcv.position(2 + signer.length());

                byte[] msgs = new byte[rcv.get(1) & 0xFF];
                rcv.get(msgs);
                String v = new String(msgs, StandardCharsets.UTF_8);

                signer.updateSign(sharedKey);
                rcv.position(2);
                for (byte b : signer.sign()) {
                    if (rcv.get() != b) {
                        throw new MSSException("不可信的关闭原因: " + v);
                    }
                }

                throw new MSSException("对等端关闭连接: " + v);
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
            int l = 2 + signer.length() + closeReason.length;
            if (snd.remaining() < l) return snd.remaining() - l;

            snd.put(PH_CLOSE).put((byte) closeReason.length);
            signer.updateSign(sharedKey);
            snd.put(signer.sign()).put(closeReason);

            closeReason = null;
            endCipher();
            flag |= CLOSED;
            return l;
        }

        if (padding != null) {
            int len = Math.min(src.remaining(), 16384);
            if (tmp == null || tmp.length < len) tmp = new byte[len];
        }
        ByteBuffer src1 = padding == null ? src : ByteBuffer.wrap(tmp);
        int lim = src.limit();
        try {
            while (src.position() < lim) {
                // 嗯，就是人工限制, 2^14
                int w = Math.min(lim - src.position(), 16384);

                int t = 3 + w;
                if (snd.remaining() < t) return snd.remaining() - t;

                if (padding != null) {
                    src.get(tmp, 0, w);
                    try {
                        padding.pad(tmp, w, tmp);
                    } catch (GeneralSecurityException e) {
                        _close("加密失败");
                    }
                    src1.position(0).limit(padding.getPaddedLength(w));
                } else {
                    src.limit(src.position() + w);
                }

                // u1 hdr, u2 len, u1[len]
                snd.put(PH_CHUNK).putChar((char) w);

                try {
                    encoder.crypt(src1, snd);
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

    // region HandShakeUtil

    final void setSession0(MSSSession session) {
        byte[] SK = sharedKey = session.sharedKey.clone();
        CipherSuite S = suite = session.suite;
        signer = S.sign.get();
        signer.setSignKey(sharedKey);
        signer.updateSign(sharedKey);

        encoder = S.ciphers.createEncoder();
        encoder.setKey(SK, CipheR.ENCRYPT);

        decoder = S.ciphers.createDecoder();
        decoder.setKey(SK, CipheR.DECRYPT);
    }

    public final void checkError() throws MSSException {
        if (error != null) throw error;
    }

    final int error(int code, String reason, ByteBuffer snd) throws MSSException {
        stage = HS_FAIL;
        byte[] data = reason.getBytes(StandardCharsets.UTF_8);
        MSSException e = error = new MSSException(reason);
        if (snd.capacity() < 3 + data.length) throw e;
        snd.clear();
        snd.put(PH_ERROR).put((byte) code).put((byte) data.length).put(data);
        return HS_OK;
    }

    final int error(Throwable ex, String reason, ByteBuffer snd) throws MSSException {
        stage = HS_FAIL;
        byte[] data = reason.getBytes(StandardCharsets.UTF_8);
        MSSException e = error = new MSSException(reason, ex);
        if (snd.capacity() < 3 + data.length) throw e;
        snd.clear();
        snd.put(PH_ERROR).put(INTERNAL_ERROR).put((byte) data.length).put(data);
        return HS_OK;
    }

    final int packetError(ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() < 3) return uf(3);
        if (rcv.remaining() < (rcv.get(2) & 0xFF) + 3) return uf((rcv.get(2) & 0xFF) + 3);
        byte[] utf = new byte[rcv.get(2) & 0xFF];
        rcv.position(3);
        rcv.get(utf);
        for (int i = 0; i < utf.length; i++) {
            // 保不齐又来个log4j2或类似的0day...
            int b = utf[i] & 0xFF;
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
        stage = HS_FAIL;
        error = new MSSException("对面告诉你(" + (rcv.get(1) & 0xFF) + ") '" + new String(utf, StandardCharsets.UTF_8) + "'");
        throw error;
    }

    // endregion
}
