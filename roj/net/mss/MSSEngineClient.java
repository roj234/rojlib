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
import roj.util.ComboRandom;
import roj.util.EmptyArrays;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

/**
 * MSS引擎客户端模式
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public final class MSSEngineClient extends MSSEngine {
    public MSSEngineClient() {}
    public MSSEngineClient(Random rnd) { super(rnd); }

    private static final MSSPubKey[] EMPTY = {};
    private MSSPubKey[] preSharedKeys = EMPTY;

    public void setPSK(MSSPubKey[] keys) {
        this.preSharedKeys = keys;
    }

    public void setSession(MSSSession session) {
        if (stage != C_HS_INIT) throw new IllegalStateException();
        this.session = session;
    }

    public void setPSKOnly(boolean b) {
        if (stage > 0) throw new IllegalStateException();
        if (b) flag |= PSK_ONLY;
        else flag &= ~PSK_ONLY;
    }
    public boolean isPSKOnly() {
        return (flag & PSK_ONLY) != 0;
    }

    private byte[] clientCert;
    private int tmp2;

    @Override
    public boolean isClientMode() {
        return true;
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
            case REUSE_SESSION:
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
                int L = 18 + ((cipherSuites.length + preSharedKeys.length) << 2) + 3 +
                    (preflight == null ? 0 : preflight.length);
                if (snd.remaining() < L) return of(L);

                snd.putInt(PH_CLIENT_HALLO).putChar(PROTOCOL_VERSION).putChar((char) cipherSuites.length)
                   .putChar((char) preSharedKeys.length);
                for (CipherSuite suite : cipherSuites) {
                    snd.putInt(suite.specificationId);
                }
                for (MSSPubKey key : preSharedKeys) {
                    snd.putInt(key.pskIdentity());
                }
                snd.putInt(tmp2 = random.nextInt());
                if (session == null) {
                    snd.putInt(0);
                } else {
                    snd.putInt(session.id);
                    setSession0(session);
                    if (preflight != null) {
                        snd.put(PH_PREFLIGHT).putChar((char) encoder.getCryptSize(preflight.length));
                        try {
                            encoder.crypt(ByteBuffer.wrap(preflight), snd);
                        } catch (GeneralSecurityException e) {
                            return error(e, "PREFLIGHT加密失败", snd);
                        }
                    } else {
                        snd.put(PH_PREFLIGHT).putChar((char) 0);
                    }
                }
                stage = TMP_1;
                return HS_OK;
            case TMP_1:
                if (rcv.remaining() < 1) return uf(1); // fast-fail preventing useless waiting
                if (rcv.get(0) != PH_SERVER_HALLO) {
                    if (rcv.get(0) == PH_REUSE_SESSION) {
                        stage = REUSE_SESSION;
                        return handshake(snd, rcv);
                    }
                    return error(ILLEGAL_PACKET, "无效的协议头, 这也许不是一个MSS服务器", snd);
                }
                if (rcv.remaining() < 14) return uf(14);
                L = rcv.getChar(1) + rcv.getChar(3) + 14;
                if (rcv.remaining() < L) return uf(L);
                if (snd.remaining() < 3) return of(3);

                CipherSuite s;
                try {
                    s = this.suite = cipherSuites[rcv.getChar(7)];
                } catch (ArrayIndexOutOfBoundsException e) {
                    return error(ILLEGAL_PACKET, "CS无效", snd);
                }

                rcv.position(10);
                MSSPubKey pub;
                if (rcv.getChar(1) > 0) {
                    if (isPSKOnly()) return error(ILLEGAL_STATE, "客户端只允许预共享密钥", snd);
                    byte[] pubKey = new byte[rcv.getChar(1)];
                    rcv.get(pubKey);

                    try {
                        pub = CipherSuite.getKey(rcv.get(9) & 0xFF).decode(pubKey);
                        if (keyMan != null) keyMan.verify(pub);
                    } catch (Throwable e) {
                        return error(e, "公钥有误", snd);
                    }
                } else {
                    // PSK模式
                    try {
                        pub = preSharedKeys[rcv.getChar(5) - 1];
                    } catch (ArrayIndexOutOfBoundsException e) {
                        return error(ILLEGAL_PACKET, "PSK无效", snd);
                    }
                }

                dh = s.subKey.get();
                dh.initB(random, tmp2);

                L = rcv.position();
                int lim = rcv.limit();
                rcv.limit(L + rcv.getChar(3));
                byte[] SecKey2 = dh.readB(rcv);

                MSSSign sign = signer = suite.sign.get();
                sign.setSignKey(pub);

                rcv.position(L);
                sign.updateSign(rcv);
                rcv.limit(lim);

                MSSCiphers cip = s.ciphers;
                int hsk = cip.getKeySize() >> 1;

                int crtId = rcv.getInt();
                if (crtId != 0) {
                    if (keyMan == null) return error(ILLEGAL_STATE, "没有证书", snd);
                    clientCert = keyMan.getClientKey(crtId);
                } else clientCert = EmptyArrays.BYTES;

                byte[] rndA = new byte[hsk + 2];
                random.nextBytes(rndA);
                rndA[0] = PH_CLIENT_RNDA;
                rndA[hsk + 1] = keyMan == null ? 0 : keyMan.getClientKeyType(crtId);
                try {
                    Cipher cipher = pub.encoder();

                    OAEP oaep = pub.createOAEP();
                    oaep.setRnd(random);
                    byte[] dst = new byte[oaep.length()];
                    oaep.pad(rndA, rndA.length, dst);

                    tmp = cipher.doFinal(dst);
                    if (tmp.length > 65535) return error(ILLEGAL_STATE, "公钥加密后数据过长", snd);

                    this.encoder = cip.createEncoder();
                    this.decoder = cip.createDecoder();

                    this.sharedKey = new byte[hsk << 1];

                    System.arraycopy(rndA, 1, sharedKey, 0, hsk);
                    this.decoder.setKey(sharedKey, CipheR.DECRYPT);

                    // 注意，这里和服务端的配置是反过来的
                    encoder.setOption("PRNG", ComboRandom.from(SecKey2,SecKey2.length>>1,SecKey2.length>>1));
                    decoder.setOption("PRNG", ComboRandom.from(SecKey2, 0, SecKey2.length>>1));

                    int max = Math.max(hsk, SecKey2.length);
                    for (int i = 0; i < max; i++) {
                        sharedKey[i % hsk] ^= SecKey2[i % SecKey2.length];
                    }

                    Arrays.fill(SecKey2, (byte) 0);

                    stage = TMP_2;
                } catch (GeneralSecurityException e) {
                    return error(e, "公钥加密失败", snd);
                }
            case TMP_2:
                L = 9 + tmp.length + clientCert.length + dh.length() +
                    (preflight == null ? 0 : encoder.getCryptSize(preflight.length));
                if (snd.remaining() < L) return of(L);
                snd.put(PH_CLIENT_RNDA).putChar((char) tmp.length).putChar((char) dh.length())
                   .putChar((char) clientCert.length)
                   .putChar((char) (preflight == null ? 0 : encoder.getCryptSize(preflight.length))).put(tmp);
                dh.writeB(snd);
                dh.clear();
                dh = null;
                snd.put(clientCert);
                if (preflight != null) {
                    try {
                        encoder.crypt(ByteBuffer.wrap(preflight), snd);
                    } catch (GeneralSecurityException e) {
                        return error(e, "PREFLIGHT加密失败", snd);
                    }
                    preflight = null;
                }

                tmp = null;
                stage = C_RNDB_WAIT;
                return HS_OK;
            case C_RNDB_WAIT:
                if (rcv.remaining() < 7) return uf(7);
                if (rcv.get(0) != PH_SERVER_RNDB) {
                    return error(ILLEGAL_PACKET, "无效的数据包头", snd);
                }
                if (rcv.remaining() < rcv.getChar(5) + 7) return uf(rcv.getChar(5) + 7);
                if (snd.remaining() < 3) return of(3);
                rcv.position(1);

                int sid = rcv.getInt();
                if (sid != 0) session = new MSSSession(sid, suite, sharedKey.clone());

                byte[] encoded = new byte[rcv.getChar() - signer.length()];
                rcv.get(encoded);

                hsk = sharedKey.length >> 1;
                ByteBuffer dst = ByteBuffer.wrap(sharedKey, hsk, hsk);

                try {
                    decoder.crypt(ByteBuffer.wrap(encoded), dst);
                } catch (GeneralSecurityException e) {
                    return error(e, "RNDB解密失败", snd);
                }

                if (dst.hasRemaining()) return error(ILLEGAL_PACKET, "RNDB解密失败", snd);

                encoder.setKey(sharedKey, CipheR.ENCRYPT);

                byte[] sk = sharedKey;
                byte[] dbs = tmp = new byte[sharedKey.length >> 1];
                for (int i = 0; i < dbs.length; i++) {
                    dbs[i] = (byte) (0x7E & (sk[i<<1] ^ sk[1+(i<<1)]));
                }
                int cs = encoder.getCryptSize(dbs.length);
                if (cs > 255)
                    return error(INTERNAL_ERROR, "DONE加密后数据过长", snd);
                snd.put(PH_DONE).put((byte) cs);

                signer.updateSign(sharedKey);
                for (byte b : signer.sign()) {
                    if (rcv.get() != b) return error(ILLEGAL_PACKET, "签名错误", snd);
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
                snd.put((byte) (flag & STREAMING));

                decoder.setKey(sharedKey, CipheR.DECRYPT);

                stage = HS_DONE;
                return HS_OK;
            case REUSE_SESSION:
                L = 1 + signer.length() + encoder.getCryptSize(sharedKey.length);
                if (rcv.remaining() < L) return uf(L);
                lim = rcv.limit();
                rcv.position(1).limit(L - signer.length());

                byte[] newKey = new byte[sharedKey.length];
                try {
                    decoder.crypt(rcv, ByteBuffer.wrap(newKey));
                } catch (GeneralSecurityException e) {
                    return error(e, "NEWKEY解密失败", snd);
                }

                rcv.limit(lim);

                for (int i = 0; i < sharedKey.length; i++) {
                    sharedKey[i] ^= newKey[i];
                }
                signer.updateSign(newKey);

                byte[] a = signer.sign();
                byte[] b = new byte[a.length];
                rcv.get(b);
                for (int j = 0; j < a.length; j++) {
                    if (a[j] != b[j])
                        return error(ILLEGAL_PACKET, "无效的会话签名", snd);
                }

                encoder.setKey(sharedKey, CipheR.ENCRYPT);
                decoder.setKey(sharedKey, CipheR.DECRYPT);

                preflight = null;
                stage = HS_DONE;
            case HS_DONE:
            case HS_FAIL:
                return HS_OK;
        }

        return error(INTERNAL_ERROR, "无效的引擎状态", snd);
    }
}
