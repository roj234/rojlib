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

import roj.collect.IntMap;
import roj.crypt.CipheR;
import roj.crypt.OAEP;
import roj.util.ComboRandom;
import roj.util.EmptyArrays;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Random;

/**
 * MSS引擎服务端模式
 * @author Roj233
 * @since 2021/12/22 12:21
 */
public final class MSSEngineServer extends MSSEngine {
    public MSSEngineServer() {}
    public MSSEngineServer(Random rnd) { super(rnd); }

    private MSSKeyPair key;
    private MSSKeyPair currKey;
    private MSSSessionManager sessionManager;

    /**
     * 以给定的密钥重置
     */
    public MSSEngineServer init(MSSKeyPair pair) {
        reset();
        this.key = pair;
        this.tmp = pair.encodedKey();
        return this;
    }

    public void setSessionManager(MSSSessionManager sm) {
        this.sessionManager = sm;
    }

    private IntMap<MSSKeyPair> preSharedKeys;

    public void setPSK(MSSPubKey[] keys) {
        if (keys == null || keys.length == 0) {
            preSharedKeys = null;
            return;
        }
        if (preSharedKeys == null)
            preSharedKeys = new IntMap<>(keys.length);
        else preSharedKeys.clear();
        for (MSSPubKey key : keys) {
            preSharedKeys.put(key.pskIdentity(), (MSSKeyPair) key);
        }
    }

    @Override
    public boolean isClientMode() {
        return false;
    }

    @Override
    public int getHandShakeStatus() {
        switch (stage) {
            case TMP_1:
            case TMP_2:
            case REUSE_SESSION_2:
                return HS_SND;
            case S_HS_WAIT:
            case S_RNDA_WAIT:
            case DONE_WAIT:
            case REUSE_SESSION:
                return HS_RCV;
            default:
                return HS_FINISHED;
        }
    }

    private IntMap<CipherSuite> getMap() {
        IntMap<CipherSuite> map = new IntMap<>(cipherSuites.length);
        for (CipherSuite c : cipherSuites) {
            map.put(c.specificationId, c);
        }
        return map;
    }

    @Override
    @SuppressWarnings("fallthrough")
    public int handshake(ByteBuffer snd, ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() > 0 && rcv.get(0) == PH_ERROR) {
            return packetError(snd);
        }

        switch (stage) {
            case S_HS_WAIT:
                // fast-fail preventing useless waiting
                if (rcv.remaining() < 1) return uf(1);
                if (rcv.get(0) != (byte) (PH_CLIENT_HALLO >>> 24))
                    return error(ILLEGAL_PACKET, "无效的协议头, 这也许不是一个MSS客户端", snd);

                if (rcv.remaining() < 18) return uf(18);

                int L = 18 + ((rcv.getChar(6) + rcv.getChar(8)) << 2);
                if (rcv.remaining() < L) return uf(L);
                if (rcv.getInt() != PH_CLIENT_HALLO)
                    return error(ILLEGAL_PACKET, "无效的协议头, 这也许不是一个MSS客户端", snd);

                if (rcv.getChar() != PROTOCOL_VERSION) return error(VERSION_MISMATCH, "协议版本不符", snd);

                int self = rcv.getInt(L - 4);
                if (self != 0 && sessionManager != null) {
                    MSSSession session = sessionManager.getSession(self);
                    if (session != null) {
                        stage = REUSE_SESSION;
                        rcv.position(L);
                        setSession0(this.session = session);
                        return handshake(snd, rcv);
                    }
                }

                int len = rcv.getChar(); rcv.getChar();

                IntMap<CipherSuite> map = getMap();
                CipherSuite select = null;
                int selectId = -1;
                for (int i = 0; i < len; i++) {
                    CipherSuite mySuite = map.get(rcv.getInt());
                    if (select == null || mySuite.preference > select.preference) {
                        select = mySuite;
                        selectId = i;
                    }
                }
                if (select == null) return error(ILLEGAL_STATE, "没有合适的加密套件", snd);
                suite = select;
                stage = TMP_1;
                sharedKey = new byte[] { 0, 0, (byte) (selectId >> 8), (byte) selectId, (byte) key.formatId() };
                currKey = key;

                if (preSharedKeys != null) {
                    int pskCount = rcv.getChar(8);
                    for (int i = 0; i < pskCount; i++) {
                        MSSKeyPair kp = preSharedKeys.get(rcv.getInt());
                        if (kp != null) {
                            i++;
                            sharedKey[0] = (byte) (i >> 8);
                            sharedKey[1] = (byte) i;
                            currKey = kp;
                            rcv.position(10 + ((len + pskCount) << 2));
                            break;
                        }
                    }
                }

                signer = suite.sign.get();
                dh = select.subKey.get();
                dh.initA(random, rcv.getInt());
                rcv.getInt();
            case TMP_1:
                L = 9 + tmp.length + dh.length() + signer.length();
                if (snd.remaining() < L) return of(L);

                byte[] pub = this.tmp;
                if (sharedKey[0] != 0 || sharedKey[1] != 0) {
                    // psk
                    pub = EmptyArrays.BYTES;
                }
                snd.put(PH_SERVER_HALLO).putChar((char) pub.length)
                   .putChar((char) dh.length()).put(sharedKey).put(pub);

                signer.setSignKey(currKey);

                int pos = snd.position(), lim = snd.limit();
                dh.writeA(snd);

                snd.limit(snd.position()).position(pos);
                signer.updateSign(snd);
                snd.limit(lim);

                if (keyMan != null) {
                    int type = keyMan.getRequiredClientKeyDesc();
                    if (type == 0) return error(INTERNAL_ERROR, "客户端验证选项有误", snd);
                    snd.putInt(type);
                } else {
                    snd.putInt(0);
                }

                stage = S_RNDA_WAIT;
                return HS_OK;
            case S_RNDA_WAIT:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.get(0) == PH_PREFLIGHT) {
                    if (rcv.remaining() < 3 + rcv.getChar(1)) return uf(3 + rcv.getChar(1));
                    rcv.position(3 + rcv.getChar(1));
                }

                if (rcv.remaining() < 9) return uf(9);
                if (rcv.get(0) != PH_CLIENT_RNDA) return error(ILLEGAL_PACKET, "无效的数据包", snd);
                L = rcv.getChar(1) + rcv.getChar(3) + rcv.getChar(5) + rcv.getChar(7) + 9;
                if (rcv.remaining() < L) return uf(L);
                if (snd.remaining() < 3) return of(3);

                rcv.position(9);
                byte[] encoded = new byte[rcv.getChar(1)];
                rcv.get(encoded);
                try {
                    byte[] src = currKey.decoder().doFinal(encoded);
                    OAEP oaep = currKey.createOAEP();
                    int hsk = oaep.unpad(src, 0, src);
                    ByteBuffer data = ByteBuffer.wrap(src);
                    if (data.get() != PH_CLIENT_RNDA)
                        return error(ILLEGAL_PACKET, "无效的数据包", snd);

                    MSSCiphers cip = suite.ciphers;
                    if (hsk > 2 + (cip.getKeySize() >> 1)) {
                        return error(ILLEGAL_PACKET, "无效的数据包", snd);
                    }
                    hsk = cip.getKeySize() >> 1;

                    encoder = cip.createEncoder();
                    decoder = cip.createDecoder();

                    sharedKey = new byte[hsk << 1];
                    data.get(sharedKey, 0, hsk);
                    // 先设置下
                    encoder.setKey(sharedKey, CipheR.ENCRYPT);

                    byte[] rndB = new byte[hsk];
                    random.nextBytes(rndB);
                    System.arraycopy(rndB, 0, sharedKey, hsk, hsk);

                    lim = rcv.limit();
                    rcv.limit(rcv.position() + rcv.getChar(3));
                    byte[] SecKey2 = dh.readA(rcv);
                    dh.clear();
                    dh = null;
                    rcv.limit(lim);

                    int max = Math.max(hsk, SecKey2.length);
                    for (int i = 0; i < max; i++) {
                        sharedKey[i % hsk] ^= SecKey2[i % SecKey2.length];
                    }

                    if (sessionManager != null) {
                        session = sessionManager.newSession(suite, sharedKey);
                    }

                    encoder.setOption("PRNG", ComboRandom.from(SecKey2,0,SecKey2.length>>1));
                    decoder.setOption("PRNG", ComboRandom.from(SecKey2,SecKey2.length>>1,SecKey2.length>>1));

                    Arrays.fill(SecKey2, (byte) 0);

                    if (rcv.getChar(5) > 0) {
                        byte[] cert = new byte[rcv.getChar(5)];
                        rcv.get(cert);
                        if (keyMan == null)
                            return error(ILLEGAL_PACKET, "多余的证书", snd);
                        keyMan.verify(CipherSuite.getKey(data.get() & 0xFF).decode(cert));
                    } else if (keyMan != null && keyMan.getRequiredClientKeyDesc() != 0) {
                        return error(ILLEGAL_PACKET, "缺失证书", snd);
                    }

                    snd.put(PH_SERVER_RNDB).putInt(session == null ? 0 : session.id).putChar((char) 0);
                    stage = TMP_2;
                } catch (GeneralSecurityException | ArrayIndexOutOfBoundsException e) {
                    return error(e, "无效的数据包", snd);
                }

                if (rcv.getChar(7) > 0) {
                    getPreflight(snd, rcv, rcv.getChar(7));
                }
            case TMP_2:
                L = snd.position() + encoder.getCryptSize(sharedKey.length >> 1)
                        + signer.length();
                if (snd.capacity() < L) return of(L);

                int sndPos = snd.position();

                int hsk = sharedKey.length >> 1;
                ByteBuffer tmp = ByteBuffer.wrap(sharedKey, hsk, hsk);
                try {
                    encoder.crypt(tmp, snd);
                } catch (GeneralSecurityException e) {
                    return error(e, "RNDB加密失败", snd);
                }

                signer.updateSign(sharedKey);
                snd.put(signer.sign());

                int i = snd.position() - sndPos;
                if (i > 65535)
                    return error(INTERNAL_ERROR, "RNDB加密后数据过长", snd);
                snd.putChar(sndPos - 2, (char) i);

                decoder.setKey(sharedKey, CipheR.DECRYPT);

                stage = DONE_WAIT;
                return HS_OK;
            case DONE_WAIT:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.get(0) != PH_DONE) {
                    return error(0, "无效的数据包", snd);
                }
                if (rcv.remaining() < (rcv.get(1) & 0xFF) + 3) return uf((rcv.get(1) & 0xFF) + 3);

                rcv.position(1);
                // Change note: may have received more!
                encoded = new byte[rcv.get() & 0xFF];
                rcv.get(encoded);

                ByteBuffer copySec = ByteBuffer.wrap(new byte[sharedKey.length >> 1]);
                try {
                    decoder.crypt(ByteBuffer.wrap(encoded), copySec);
                } catch (GeneralSecurityException e) {
                    return error(e, "共享密钥有误", snd);
                }
                if (copySec.hasRemaining())
                    return error(ILLEGAL_PACKET, "共享密钥有误", snd);

                byte[] a = sharedKey;
                byte[] b = copySec.array();
                for (int j = 0; j < b.length; j++) {
                    if (((a[j<<1] ^ a[1+(j<<1)]) & 0x7E) != b[j])
                        return error(ILLEGAL_PACKET, "共享密钥有误", snd);
                }
                int f1 = rcv.get() & 0xFF;
                flag |= f1 & STREAMING;

                if ((flag & STREAMING) != 0 && !suite.ciphers.isStreamCipher())
                    return error(INTERNAL_ERROR, suite.ciphers + " is not a stream cipher", snd);

                encoder.setKey(sharedKey, CipheR.ENCRYPT);
                stage = HS_DONE;
                return HS_OK;
            case REUSE_SESSION:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.remaining() < 3 + rcv.getChar(1)) return uf(3 + rcv.getChar(1));
                if (rcv.get() != PH_PREFLIGHT) return error(ILLEGAL_PACKET, "无效的数据包", snd);

                char c = rcv.getChar();
                if (c > 0) getPreflight(snd, rcv, c);

                stage = REUSE_SESSION_2;
            case REUSE_SESSION_2:
                L = 1 + signer.length() + encoder.getCryptSize(sharedKey.length);
                if (snd.remaining() < L) return of(L);

                byte[] newKey = new byte[sharedKey.length];
                random.nextBytes(newKey);
                for (int j = 0; j < newKey.length; j++) {
                    sharedKey[j] ^= newKey[j];
                }

                signer.updateSign(newKey);
                snd.put(PH_REUSE_SESSION);
                try {
                    encoder.crypt(ByteBuffer.wrap(newKey), snd);
                } catch (GeneralSecurityException e) {
                    return error(e, "NEWKEY加密失败", snd);
                }
                snd.put(signer.sign());

                encoder.setKey(sharedKey, CipheR.ENCRYPT);
                decoder.setKey(sharedKey, CipheR.DECRYPT);

                stage = HS_DONE;
            case HS_DONE:
            case HS_FAIL:
                return HS_OK;
        }

        return error(INTERNAL_ERROR, "无效的引擎状态", snd);
    }

    private void getPreflight(ByteBuffer snd, ByteBuffer rcv, int len) throws MSSException {
        preflight = new byte[decoder.getCryptSize(len)];
        ByteBuffer tmp = ByteBuffer.wrap(preflight);
        int lim = rcv.limit();
        rcv.limit(rcv.position() + len);
        try {
            decoder.crypt(rcv, tmp);
        } catch (GeneralSecurityException e) {
            error(ILLEGAL_PACKET, "共享密钥有误", snd);
        } finally {
            rcv.limit(lim);
        }
        if (tmp.hasRemaining())
            error(ILLEGAL_PACKET, "共享密钥有误", snd);
    }
}
