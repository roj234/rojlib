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
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Random;

import static roj.net.mss.CipherSuite.AsymmetricKeyType_OFFSET;

/**
 * MSS引擎服务端模式
 * @author Roj233
 * @version 1.0
 * @since 2021/12/22 12:21
 */
public final class MSSEngineServer extends MSSEngine {
    public MSSEngineServer() {}
    public MSSEngineServer(Random rnd) { super(rnd); }

    private byte[] pub;
    private MSSKeyPair key;
    private MSSKeyPair currKey;

    MSSEngineServer _init(int id, byte[] key, PrivateKey pri, MSSKeyPair[] psk) {
        reset();
        this.key = new SimplePSK(id << AsymmetricKeyType_OFFSET, null, pri);
        this.pub = key;
        this.setPreSharedKeys(psk);
        return this;
    }

    /**
     * 以给定的密钥格式重置MSSEngine
     * @param fmt 密钥传输格式
     * @param pub 公钥
     * @param pri 私钥
     * @param <T> 公钥格式
     */
    public <T> MSSEngineServer init(MSSKeyFormat<T> fmt, T pub, PrivateKey pri) throws GeneralSecurityException {
        reset();
        this.key = new SimplePSK(fmt.formatId() << AsymmetricKeyType_OFFSET, null, pri);
        this.pub = fmt.encode(pub);
        fmt.checkPrivateKey(pri);
        return this;
    }

    private IntMap<MSSKeyPair> preSharedKeys;

    public void setPreSharedKeys(MSSPubKey[] keys) {
        if (keys == null || keys.length == 0) {
            preSharedKeys = null;
            return;
        }
        if (preSharedKeys == null)
            preSharedKeys = new IntMap<>(keys.length);
        else preSharedKeys.clear();
        for (MSSPubKey key : keys) {
            preSharedKeys.put(key.keyId(), (MSSKeyPair) key);
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
                return HS_SND;
            case S_HS_WAIT:
            case S_RNDA_WAIT:
            case DONE_WAIT:
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
                if (rcv.get(0) != (byte) (PH_CLIENT_HALLO >>> 24)) return error("无效的协议头, 这也许不是一个MSS客户端", snd);

                if (rcv.remaining() < 14) return uf(10);

                int L = 14 + ((rcv.getChar(6) + rcv.getChar(8)) << 2);
                if (rcv.remaining() < L) return uf(L);
                if (rcv.getInt() != PH_CLIENT_HALLO)
                    return error("无效的协议头, 这也许不是一个MSS客户端", snd);

                if (rcv.getChar() != PROTOCOL_VERSION) return error("协议版本不符", snd);
                int self = key.keyId() & 0xFF000000;
                int len = rcv.getChar(); rcv.getChar();

                IntMap<CipherSuite> map = getMap();
                CipherSuite select = null;
                int selectId = -1;
                for (int i = 0; i < len; i++) {
                    int cid = rcv.getInt();
                    if (self == (cid & 0xFF000000)) {
                        CipherSuite mySuite = map.get(cid);
                        if (select == null || mySuite.preference > select.preference) {
                            select = mySuite;
                            selectId = i;
                        }
                    }
                }
                if (select == null) return error("没有合适的加密套件", snd);
                suite = select;
                stage = TMP_1;
                sharedKey = new byte[] { 0, 0, (byte) (selectId >> 8), (byte) selectId };
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

                dh = select.createDHE(rcv.getInt() ^ random.nextInt());
                dh.init(random, true);
            case TMP_1:
                L = 9 + pub.length + dh.length();
                if (snd.remaining() < L) return of(L);
                snd.put(PH_SERVER_HALLO);

                byte[] pub = this.pub;
                if (sharedKey[0] != 0 || sharedKey[1] != 0) {
                    // psk
                    pub = EmptyArrays.BYTES;
                }
                snd.putChar((char) pub.length).putChar((char) dh.length()).put(sharedKey).put(pub);

                MSSSign sig = signer = suite.sign.get();
                sig.setSignKey(currKey);

                int pos = snd.position(), lim = snd.limit();
                dh.write1(snd);

                snd.limit(snd.position()).position(pos);
                sig.updateSign(snd);
                snd.limit(lim);

                stage = S_RNDA_WAIT;
                return HS_OK;
            case S_RNDA_WAIT:
                if (rcv.remaining() < 5) return uf(5);
                if (rcv.get(0) != PH_CLIENT_RNDA) return error("无效的数据包", snd);
                L = rcv.getChar(1) + rcv.getChar(3) + 5;
                if (rcv.remaining() < L) return uf(L);
                if (snd.remaining() < 3) return of(3);

                rcv.position(7);
                byte[] encoded = new byte[rcv.getChar(1)];
                rcv.get(encoded);
                try {
                    byte[] src = currKey.priDecoder().doFinal(encoded);
                    byte[] dst = new byte[currKey.maxEncodeBytes()];
                    OAEP oaep = new OAEP(dst.length);
                    int hsk = oaep.decode(src, 0, dst);
                    ByteBuffer data = ByteBuffer.wrap(dst);
                    if (data.get() != PH_CLIENT_RNDA)
                        return error("无效的数据包", snd);

                    MSSCiphers cip = suite.ciphers;
                    if (hsk > 7 + (cip.getKeySize() >> 1))
                        return error("无效的数据包", snd);
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

                    sessionId = data.getInt();

                    byte[] DHE_KEY = dh.read2(rcv).toByteArray();
                    dh.reset();
                    dh = null;

                    int max = Math.max(hsk, DHE_KEY.length);
                    for (int i = 0; i < max; i++) {
                        sharedKey[i % hsk] ^= DHE_KEY[i % DHE_KEY.length];
                    }

                    encoder.setOption("PRNG", ComboRandom.from(DHE_KEY,0,DHE_KEY.length>>1));
                    decoder.setOption("PRNG", ComboRandom.from(DHE_KEY,DHE_KEY.length>>1,DHE_KEY.length>>1));

                    Arrays.fill(DHE_KEY, (byte) 0);

                    if (rcv.getChar(5) > 0) {
                        int clientCertType = data.get() & 0xFF;
                        byte[] cert = new byte[rcv.getChar(5)];
                        rcv.get(cert);
                        verifier.verify(suite.keyFormat.decode(cert));
                    }

                    this.hash = suite.hash.get();
                    snd.put(PH_SERVER_RNDB).putChar((char) 0);
                    stage = TMP_2;
                } catch (GeneralSecurityException | ArrayIndexOutOfBoundsException e) {
                    return error(e, "无效的数据包", snd);
                }
            case TMP_2:
                // assume hash will <= 64 bytes
                L = snd.position() + sharedKey.length + 64;
                if (snd.capacity() < L) return of(L);

                int sndPos = snd.position();

                int hsk = sharedKey.length >> 1;
                ByteBuffer tmp = ByteBuffer.wrap(sharedKey, hsk, hsk);
                try {
                    if (encoder.crypt(tmp, snd) == CipheR.BUFFER_OVERFLOW)
                        return of(tmp.remaining() << 1);
                } catch (GeneralSecurityException e) {
                    return error(e, "RNDB加密失败", snd);
                }

                signer.updateSign(sharedKey, 0, sharedKey.length);
                snd.put(signer.sign());

                int i = snd.position() - sndPos;
                if (i > 65535)
                    return error("RNDB加密后数据过长", snd);
                snd.putChar(sndPos - 2, (char) i);

                decoder.setKey(sharedKey, CipheR.DECRYPT);

                stage = DONE_WAIT;
                return HS_OK;
            case DONE_WAIT:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.get(0) != PH_DONE) {
                    return error("无效的数据包", snd);
                }
                if (rcv.remaining() < rcv.getChar(1) + 3) return uf(rcv.getChar(1) + 3);

                rcv.position(1);
                // Change note: may have received more!
                encoded = new byte[rcv.getChar()];
                rcv.get(encoded);

                ByteBuffer copySec = ByteBuffer.wrap(new byte[sharedKey.length >> 1]);
                try {
                    int r = decoder.crypt(ByteBuffer.wrap(encoded), copySec);
                    if (r == CipheR.BUFFER_OVERFLOW)
                        return error("共享密钥有误", snd);
                } catch (GeneralSecurityException e) {
                    return error(e, "共享密钥有误", snd);
                }
                if (copySec.hasRemaining())
                    return error("共享密钥有误", snd);
                byte[] a = sharedKey;
                byte[] b = copySec.array();
                for (int j = 0; j < b.length; j++) {
                    if ((a[j<<1] ^ a[1+(j<<1)]) != b[j])
                        return error("共享密钥有误", snd);
                }

                encoder.setKey(sharedKey, CipheR.ENCRYPT);
                stage = HS_DONE;
                return HS_OK;
            case HS_DONE:
            case HS_FAIL:
                return HS_OK;
        }

        return error("无效的引擎状态", snd);
    }
}
