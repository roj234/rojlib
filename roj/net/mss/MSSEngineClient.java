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
import roj.crypt.OAEP;
import roj.util.ByteList;
import roj.util.ComboRandom;
import roj.util.EmptyArrays;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MSS引擎客户端模式
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public final class MSSEngineClient extends MSSEngine {
    public static final AtomicInteger SESSION = new AtomicInteger();

    public MSSEngineClient() { sessionId = SESSION.incrementAndGet(); }
    public MSSEngineClient(Random rnd) { super(rnd); sessionId = SESSION.incrementAndGet();  }

    private static MSSPubKey[] defaultPreSharedKeys = {};
    private MSSPubKey[] preSharedKeys = defaultPreSharedKeys;

    @Deprecated
    public static void _setDefaultPSK(MSSPubKey[] keys) {
        defaultPreSharedKeys = keys;
    }
    public void setPreSharedKeys(MSSPubKey[] keys) {
        this.preSharedKeys = keys;
    }

    public void setSessionId(int sessionId) {
        this.sessionId = sessionId;
    }

    public void setOnlyUsePSK(boolean b) {
        if (stage > 0) throw new IllegalStateException();
        if (b) flag |= PSK_ONLY;
        else flag &= ~PSK_ONLY;
    }
    public boolean isOnlyUsePSK() {
        return defaultPreSharedKeys.length > 0 || (flag & PSK_ONLY) != 0;
    }

    private byte[] tmp;

    @Override
    public boolean isClientMode() {
        return true;
    }

    private byte clientCertType;
    private byte[] clientCert = EmptyArrays.BYTES;

    public <T> void setClientCert(MSSKeyFormat<T> fmt, T pubKey) throws GeneralSecurityException {
        clientCertType = (byte) fmt.formatId();
        clientCert = fmt.encode(pubKey);
    }

    @Override
    public int getHandShakeStatus() {
        switch (stage) {
            case C_HS_INIT:
            case TMP_2:
            case DONE_WAIT:
                return HS_SND;
            case C_RNDB_WAIT:
            case TMP_1:
                return HS_RCV;
            default:
                return HS_FINISHED;
        }
    }

    @Override
    @SuppressWarnings("fallthrough")
    public int handshake(ByteBuffer snd, ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() > 0 && rcv.get(0) == PH_ERROR) {
            return packetError(rcv);
        }

        switch (stage) {
            case C_HS_INIT:
                int L = 14 + ((cipherSuites.length + preSharedKeys.length) << 2);
                if (snd.remaining() < L) return of(L);

                snd.putInt(PH_CLIENT_HALLO).putChar(PROTOCOL_VERSION).putChar((char) cipherSuites.length)
                   .putChar((char) preSharedKeys.length);
                for (CipherSuite suite : cipherSuites) {
                    snd.putInt(suite.specificationId);
                }
                for (MSSPubKey key : preSharedKeys) {
                    snd.putInt(key.keyId());
                }
                snd.putInt(random.nextInt());
                stage = TMP_1;
                return HS_OK;
            case TMP_1:
                if (rcv.remaining() < 1) return uf(1); // fast-fail preventing useless waiting
                if (rcv.get(0) != PH_SERVER_HALLO) {
                    return error("无效的协议头, 这也许不是一个MSS服务器", snd);
                }
                if (rcv.remaining() < 9) return uf(9);
                L = rcv.getChar(1) + rcv.getChar(3) + 9;
                if (rcv.remaining() < L) return uf(L);
                if (snd.remaining() < 3) return of(3);

                CipherSuite s = this.suite = cipherSuites[rcv.getChar(7)];

                rcv.position(9);
                MSSPubKey pub;
                if (rcv.getChar(1) > 0) {
                    if (isOnlyUsePSK()) return error("客户端只允许预共享密钥", snd);
                    byte[] pubKey = new byte[rcv.getChar(1)];
                    rcv.get(pubKey);

                    try {
                        // 可以验证服务端 see X509KeyFormat
                        pub = s.keyFormat.decode(pubKey);
                        if (verifier != null) verifier.verify(pub);
                    } catch (Throwable e) {
                        return error(e, "公钥有误", snd);
                    }
                } else {
                    // PSK模式
                    pub = preSharedKeys[rcv.getChar(5) - 1];
                }

                dh = s.createDHE(0);
                dh.init(random);

                byte[] data = new byte[rcv.getChar(3)];
                rcv.get(data);

                MSSSign sign = signer = suite.sign.get();
                sign.setSignKey(pub);
                sign.updateSign(data, 0, data.length);

                byte[] DHE_KEY = dh.read1(new ByteList(data)).toByteArray();

                MSSCiphers cip = s.ciphers;
                int hsk = cip.getKeySize() >> 1;

                ByteBuffer plainBuf = ByteBuffer.allocate(7 + hsk);
                byte[] rndA = new byte[hsk];
                random.nextBytes(rndA);
                plainBuf.put(PH_CLIENT_RNDA).put(rndA)
                        .putInt(sessionId).put(clientCertType);
                try {
                    Cipher cipher = pub.encoder();

                    OAEP oaep = pub.createOAEP();
                    oaep.setRnd(random);
                    byte[] dst = new byte[oaep.length()];
                    oaep.encode(plainBuf.array(), plainBuf.capacity(), dst);

                    tmp = cipher.doFinal(dst);
                    if (tmp.length > 65535) return error("公钥加密后数据过长", snd);

                    this.encoder = cip.createEncoder();
                    this.decoder = cip.createDecoder();

                    this.sharedKey = new byte[hsk << 1];

                    System.arraycopy(rndA, 0, sharedKey, 0, hsk);
                    this.decoder.setKey(sharedKey, CipheR.DECRYPT);

                    // 注意，这里和服务端的配置是反过来的
                    encoder.setOption("PRNG", ComboRandom.from(DHE_KEY,DHE_KEY.length>>1,DHE_KEY.length>>1));
                    decoder.setOption("PRNG", ComboRandom.from(DHE_KEY, 0, DHE_KEY.length>>1));

                    int max = Math.max(hsk, DHE_KEY.length);
                    for (int i = 0; i < max; i++) {
                        sharedKey[i % hsk] ^= DHE_KEY[i % DHE_KEY.length];
                    }

                    Arrays.fill(DHE_KEY, (byte) 0);

                    stage = TMP_2;
                } catch (GeneralSecurityException e) {
                    return error(e, "公钥加密失败", snd);
                }
            case TMP_2:
                L = 5 + tmp.length + clientCert.length + dh.length();
                if (snd.remaining() < L) return of(L);
                snd.put(PH_CLIENT_RNDA).putChar((char) tmp.length).putChar((char) dh.length())
                   .putChar((char) clientCert.length).put(tmp);
                dh.write2(snd);
                dh.reset();
                dh = null;
                snd.put(clientCert);

                tmp = null;
                stage = C_RNDB_WAIT;
                return HS_OK;
            case C_RNDB_WAIT:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.get(0) != PH_SERVER_RNDB) {
                    return error("无效的数据包头", snd);
                }
                if (rcv.remaining() < rcv.getChar(1) + 3) return uf(rcv.getChar(1) + 3);
                if (snd.remaining() < 3) return of(3);
                rcv.position(1);

                byte[] encoded = new byte[rcv.getChar() - signer.length()];
                rcv.get(encoded);

                hsk = this.sharedKey.length >> 1;
                ByteBuffer dst = ByteBuffer.wrap(this.sharedKey, hsk, hsk);

                try {
                    int state = decoder.crypt(ByteBuffer.wrap(encoded), dst);
                } catch (GeneralSecurityException e) {
                    return error(e, "RNDB解密失败", snd);
                }

                if (dst.hasRemaining()) return error("RNDB解密失败", snd);

                this.hash = this.suite.hash.get();

                encoder.setKey(sharedKey, CipheR.ENCRYPT);

                snd.put(PH_DONE).putChar((char) 0);
                byte[] sk = sharedKey;
                byte[] dbs = tmp = new byte[sharedKey.length >> 1];
                for (int i = 0; i < dbs.length; i++) {
                    dbs[i] = (byte) (sk[i<<1] ^ sk[1+(i<<1)]);
                }

                signer.updateSign(sharedKey, 0, sharedKey.length);
                for (byte b : signer.sign()) {
                    if (rcv.get() != b) return error("签名错误", snd);
                }

                stage = DONE_WAIT;
            case DONE_WAIT:
                int sndPos = snd.position();
                dst = ByteBuffer.wrap(tmp);

                try {
                    if (CipheR.BUFFER_OVERFLOW == encoder.crypt(dst, snd))
                        return of(dst.remaining());
                } catch (GeneralSecurityException e) {
                    return error(e, "DONE加密失败", snd);
                }

                tmp = null;
                int i = snd.position() - sndPos;
                if (i > 65535)
                    return error("DONE加密后数据过长", snd);
                snd.putChar(sndPos - 2, (char) i);

                decoder.setKey(sharedKey, CipheR.DECRYPT);

                stage = HS_DONE;
                return HS_OK;
            case HS_DONE:
            case HS_FAIL:
                return HS_OK;
        }

        return error("无效的引擎状态", snd);
    }
}
