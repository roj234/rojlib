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

import javax.crypto.Cipher;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.util.Random;

/**
 * MSS引擎服务端模式
 * @author Roj233
 * @version 1.0
 * @since 2021/12/22 12:21
 */
public final class MSSEngineServer extends MSSEngine {
    public MSSEngineServer() {}
    public MSSEngineServer(Random rnd) { super(rnd); }

    private int keyFormatId;
    private byte[] pub;
    private Cipher priDecoder;

    MSSEngineServer __init(int keyFormatId, byte[] publicKey, PrivateKey privateKey) throws GeneralSecurityException {
        this.keyFormatId = keyFormatId;
        this.pub = publicKey;
        this.priDecoder = Cipher.getInstance(privateKey.getAlgorithm());
        this.priDecoder.init(Cipher.DECRYPT_MODE, privateKey);
        return this;
    }

    /**
     * 以给定的密钥格式重置MSSEngine
     * @param format 密钥传输格式
     * @param publicKey 公钥
     * @param privateKey 私钥
     * @param <T> 公钥格式
     */
    public <T> MSSEngineServer init(MSSPubKey<T> format, T publicKey, PrivateKey privateKey) throws GeneralSecurityException {
        reset();
        this.keyFormatId = format.specificationId();
        this.pub = format.encode(publicKey);
        format.checkPrivateKey(privateKey);
        this.priDecoder = Cipher.getInstance(privateKey.getAlgorithm());
        this.priDecoder.init(Cipher.DECRYPT_MODE, privateKey);
        return this;
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

    static final int S_HS_WAIT = 0, S_RNDA_WAIT = 1;

    @Override
    public int handshake(ByteBuffer snd, ByteBuffer rcv) throws MSSException {
        if (rcv.remaining() > 0 && rcv.get(0) == PH_ERROR) {
            return packetError(snd);
        }

        switch (stage) {
            case S_HS_WAIT:
                if (rcv.remaining() < 6) return uf(6);
                if (rcv.remaining() < (rcv.getChar(4) << 2) + 6)
                    return uf((rcv.getChar(4) << 2) + 6);
                if (rcv.getInt() != PH_CLIENT_HALLO)
                    return error("无效的协议头, 这也许不是一个MSS客户端");
                int self = keyFormatId;
                int len = rcv.getChar();
                for (int i = 0; i < len; i++) {
                    if (self == rcv.getInt()) {
                        stage = TMP_1;
                        sharedKey = new byte[] { (byte) (i >> 8), (byte) i };
                        break;
                    }
                }
                rcv.position(6 + (len << 2));
                if (stage != TMP_1)
                    return error("客户端不支持服务端使用的公钥格式");
            case TMP_1:
                // u1 hdr, u4 len, u4 pkLen, byte[pkLen] data, u2 keyFmt, u2 cipherLen, u4[cipherLen] cpSpec
                if (snd.remaining() < 13 + pub.length + supportedCiphers.length << 2)
                    return of(13 + pub.length + supportedCiphers.length << 2);
                snd.put(PH_SERVER_HALLO)
                   .putInt(pub.length + (supportedCiphers.length << 2) + 4)
                   .putInt(pub.length)
                   .put(pub).put(sharedKey).putChar((char) supportedCiphers.length);
                for (MSSCiphers cipherFactory : supportedCiphers) {
                    snd.putInt(cipherFactory.specificationId());
                }
                stage = S_RNDA_WAIT;
                return HS_OK;
            case S_RNDA_WAIT:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.get(0) != PH_CLIENT_RNDA) {
                    return error("无效的数据包头");
                }
                if (rcv.remaining() < rcv.getChar(1) + 3) return uf(rcv.getChar(1) + 3);
                if (snd.remaining() < 3) return of(3);
                rcv.position(3);
                byte[] encoded = new byte[rcv.remaining()];
                rcv.get(encoded);
                try {
                    ByteBuffer data = ByteBuffer.wrap(priDecoder.doFinal(encoded));
                    halfSharedKeySize = (char) (data.remaining() - 3);
                    if (data.get() != PH_CLIENT_RNDA)
                        return error("无效的RNDA哈希");

                    MSSCiphers selected = supportedCiphers[data.getChar()];
                    encoder = selected.createEncoder();
                    decoder = selected.createDecoder();
                    chunkSize = (char) (likeChunkedMode ? selected.preferChunkSize() : 65535);

                    sharedKey = new byte[halfSharedKeySize << 1];
                    data.get(sharedKey, 0, halfSharedKeySize);
                    byte[] rndB = new byte[halfSharedKeySize];
                    random.nextBytes(rndB);
                    System.arraycopy(rndB, 0, sharedKey, halfSharedKeySize, halfSharedKeySize);

                    System.arraycopy(sharedKey, 0, rndB, 0, halfSharedKeySize);
                    encoder.setKey(rndB);

                    if (!selected.isCipherTextSame()) flag |= DIFF_CIPHER;

                    hasher.reset();
                    hasher.update(sharedKey);
                    snd.put(PH_SERVER_RNDB).putInt((int) hasher.getValue()).putChar((char) 0);
                    stage = TMP_2;
                } catch (GeneralSecurityException | ArrayIndexOutOfBoundsException e) {
                    return error("无效的RNDA数据包", e);
                }
            case TMP_2:
                int sndPos = snd.position();

                ByteBuffer tmp = ByteBuffer.wrap(sharedKey, halfSharedKeySize, halfSharedKeySize);
                try {
                    if (encoder.crypt(tmp, snd) == CipheR.BUFFER_OVERFLOW)
                        return of(tmp.remaining() << 1);
                } catch (GeneralSecurityException e) {
                    return error("RNDB加密失败", e);
                }

                int i = snd.position() - sndPos;
                if (i > 65535)
                    return error("RNDB加密后数据过长");
                snd.putChar(sndPos - 2, (char) i);

                encoder.setKey(sharedKey);
                decoder.setKey(sharedKey);

                stage = DONE_WAIT;
                return HS_OK;
            case DONE_WAIT:
                if (rcv.remaining() < 3) return uf(3);
                if (rcv.get(0) != PH_DONE) {
                    return error("无效的数据包头");
                }
                if (rcv.remaining() < rcv.getChar(1) + 3) return uf(rcv.getChar(1) + 3);

                rcv.position(3);
                encoded = new byte[rcv.remaining()];
                rcv.get(encoded);

                ByteBuffer copySec = ByteBuffer.wrap(new byte[halfSharedKeySize << 1]);
                try {
                    int r = decoder.crypt(ByteBuffer.wrap(encoded), copySec);
                    if (r == CipheR.BUFFER_OVERFLOW)
                        return error("共享密钥有误");
                } catch (GeneralSecurityException e) {
                    return error("共享密钥有误", e);
                }
                if (copySec.hasRemaining())
                    return error("共享密钥有误");
                byte[] a = sharedKey;
                byte[] b = copySec.array();
                for (int j = 0; j < a.length; j++) {
                    if (a[j] != b[j])
                        return error("共享密钥有误");
                }

                stage = HS_DONE;
                return HS_OK;
            case HS_DONE:
            case HS_FAIL:
                return HS_OK;
        }

        return error("无效的引擎状态");
    }
}
