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
package lac.injector.patch;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.crypt.MyCipher;
import roj.crypt.SM4;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.CryptManager;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * S2C, decrypt packet
 *
 * @author Roj233
 * @since 2021/10/15 20:31
 */
@Nixim(/*"net.minecraft.network.login.server.SPacketEncryptionRequest"*/"mi")
class NxEncryption {
    @Shadow("a")
    private String    hashedServerId;
    @Shadow("b")
    private PublicKey publicKey;
    @Shadow("c")
    private byte[]    verifyToken;

    @Inject("a")
    public void readJianrong(PacketBuffer buf) {
        boolean isMyCryptPacket = buf.readByte() == 99;
        if (!isMyCryptPacket)
            buf.readerIndex(buf.readerIndex() - 1);
        this.hashedServerId = buf.readString(20);
        byte[] key = buf.readByteArray();
        this.verifyToken = buf.readByteArray();
        if (isMyCryptPacket) {
            MyCipher sm4 = new MyCipher(new SM4(), MyCipher.MODE_CFB);
            sm4.setKey(verifyToken, MyCipher.DECRYPT);
            try {
                sm4.crypt(ByteBuffer.wrap(key), ByteBuffer.wrap(key));
            } catch (GeneralSecurityException e) {
                e.printStackTrace();
            }
        }
        this.publicKey = CryptManager.decodePublicKey(key);
    }
}
