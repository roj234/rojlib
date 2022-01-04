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

import roj.collect.Int2IntMap;
import roj.crypt.CipheR;
import roj.util.Helpers;

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Random;

/**
 * MSS引擎客户端模式
 * @author Roj233
 * @version 0.1
 * @since 2021/12/22 12:21
 */
public final class MSSEngineClient extends MSSEngine {
    public MSSEngineClient() {}
    public MSSEngineClient(Random rnd) { super(rnd); }

    static MSSPubKey<?>[]  defaultKeyFormats;
    private MSSPubKey<?>[] keyFormats = defaultKeyFormats;

    static {
        try {
            defaultKeyFormats = new MSSPubKey<?>[] {
                    JPubKey.JAVARSA, new X509PubKey()
            };
        } catch (GeneralSecurityException e) {
            Helpers.athrow(e);
        }
    }

    public static MSSPubKey<?>[] getDefaultKeyFormats() {
        return defaultKeyFormats;
    }

    public static void setDefaultKeyFormats(MSSPubKey<?>... formats) {
        if (formats.length > 65535)
            throw new IllegalArgumentException("formats.length > 65535");
        MSSEngineClient.defaultKeyFormats = formats.clone();
    }

    public void setKeyFormats(MSSPubKey<?>... formats) {
        if (formats.length > 65535)
            throw new IllegalArgumentException("formats.length > 65535");
        this.keyFormats = formats;
    }

    public MSSPubKey<?>[] getKeyFormats() {
        return keyFormats;
    }

    private byte[] tmp;

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
                return HS_RCV;
            default:
                return HS_FINISHED;
        }
    }

    static final int C_HS_INIT = 0, C_RNDB_WAIT = 1, DONE_WAIT = 4;

    @Override
    @SuppressWarnings("fallthrough")
    public int handshake(ByteBuffer snd, ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() > 0 && rcv.get(0) == PH_ERROR) {
            return packetError(rcv);
        }

        switch (stage) {
            case C_HS_INIT:
                if (snd.remaining() < 6 + keyFormats.length << 2) return of(6 + keyFormats.length << 2);
                snd.putInt(PH_CLIENT_HALLO).putChar((char) keyFormats.length);
                for (MSSPubKey<?> format : keyFormats) {
                    snd.putInt(format.specificationId());
                }
                stage = TMP_1;
                return HS_OK;
            case TMP_1:
                // u1 hdr, u4 len, u4 pkLen, byte[pkLen] data, u2 keyFmt, u2 cipherLen, u4[cipherLen] cpSpec
                if (rcv.remaining() < 1) return uf(1); // fast-fail preventing useless waiting
                if (rcv.get(0) != PH_SERVER_HALLO) {
                    return error("无效的协议头, 这也许不是一个MSS服务器");
                }
                if (rcv.remaining() < 5) return uf(5);
                if (rcv.remaining() < rcv.getInt(1) + 5) return uf(rcv.getInt(1) + 5);
                if (snd.remaining() < 3) return of(3);
                rcv.position(5);
                byte[] pubKey = new byte[rcv.getInt()];
                rcv.get(pubKey);

                PublicKey pub;
                try {
                    pub = keyFormats[rcv.getChar()].decode(pubKey);
                } catch (GeneralSecurityException | ArrayIndexOutOfBoundsException e) {
                    return error("公钥有误", e);
                }

                Int2IntMap map = new Int2IntMap(supportedCiphers.length);
                for (int i = 0; i < supportedCiphers.length; i++) {
                    int j = map.putInt(supportedCiphers[i].specificationId(), i);
                    if (0 <= j)
                        return error("重复的specificationId " + supportedCiphers[i].getClass().getName() + " - " + supportedCiphers[j].getClass().getName());
                }

                int len = rcv.getChar();
                for (int i = 0; i < len; i++) {
                    int j = map.get(rcv.getInt());
                    if (j >= 0) {
                        MSSCiphers selected = supportedCiphers[j];

                        int hsk = selected.getSharedKeySize() >> 1;
                        ByteBuffer tmp = ByteBuffer.allocate(3 + hsk);
                        byte[] rndA = new byte[hsk];
                        random.nextBytes(rndA);
                        tmp.put(PH_CLIENT_RNDA).putChar((char) i).put(rndA);
                        try {
                            Cipher cipher = Cipher.getInstance(pub.getAlgorithm());
                            cipher.init(Cipher.ENCRYPT_MODE, pub);
                            this.tmp = cipher.doFinal(tmp.array());
                            if (this.tmp.length > 65535) return error("公钥加密后数据过长");

                            this.encoder = selected.createEncoder();
                            this.decoder = selected.createDecoder();
                            this.decoder.setKey(rndA, CipheR.DECRYPT);

                            this.sharedKey = new byte[hsk << 1];
                            System.arraycopy(rndA, 0, sharedKey, 0, hsk);
                            stage = TMP_2;
                        } catch (GeneralSecurityException e) {
                            return error("公钥加密失败", e);
                        }
                        len -= i + 1;
                        pubKey = null;
                        break;
                    }
                }
                if (pubKey != null)
                    return error("没有共同的对称加密方式");
                rcv.position(rcv.position() + (len << 2));
            case TMP_2:
                if (snd.remaining() < 3 + tmp.length) return of(3 + tmp.length);
                snd.put(PH_CLIENT_RNDA).putChar((char) tmp.length).put(tmp);
                tmp = null;
                stage = C_RNDB_WAIT;
                return HS_OK;
            case C_RNDB_WAIT:
                if (rcv.remaining() < 7) return uf(7);
                if (rcv.get(0) != PH_SERVER_RNDB) {
                    return error("无效的数据包头");
                }
                if (rcv.remaining() < rcv.getChar(5) + 7) return uf(rcv.getChar(5) + 7);
                if (snd.remaining() < 3) return of(3);
                rcv.position(1);
                int hash = rcv.getInt();
                byte[] encoded = new byte[rcv.getChar()];
                rcv.get(encoded);

                int hsk = this.sharedKey.length >> 1;
                ByteBuffer dst = ByteBuffer.wrap(this.sharedKey, hsk, hsk);

                try {
                    decoder.crypt(ByteBuffer.wrap(encoded), dst);
                } catch (GeneralSecurityException e) {
                    return error("RNDB解密失败", e);
                }

                if (dst.hasRemaining())
                    return error("RNDB解密失败");

                if (this.hash.computeHandshakeHash(sharedKey) != hash)
                    return error("RNDB哈希有误");

                encoder.setKey(sharedKey, CipheR.ENCRYPT);

                snd.put(PH_DONE).putChar((char) 0);

                stage = DONE_WAIT;
            case DONE_WAIT:
                int sndPos = snd.position();
                if (sndPos == 0) {
                    stage = HS_DONE;
                    encoder.setKey(sharedKey, CipheR.ENCRYPT);
                    decoder.setKey(sharedKey, CipheR.DECRYPT);
                    return HS_OK;
                }

                dst = ByteBuffer.wrap(this.sharedKey);
                try {
                    if (CipheR.BUFFER_OVERFLOW == encoder.crypt(dst, snd))
                        return of(dst.remaining());
                } catch (GeneralSecurityException e) {
                    return error("DONE加密失败", e);
                }

                int i = snd.position() - sndPos;
                if (i > 65535)
                    return error("DONE加密后数据过长");
                snd.putChar(sndPos - 2, (char) i);

                return HS_OK;
            case HS_DONE:
            case HS_FAIL:
                return HS_OK;
        }

        return error("无效的引擎状态");
    }
}
