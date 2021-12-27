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
package lac.server.nixim;

import lac.server.Config;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.crypt.MyCipher;
import roj.crypt.SM4;

import net.minecraft.network.PacketBuffer;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * S2C encrypt packet
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/10/15 20:31
 */
@Nixim("net.minecraft.network.login.server.SPacketEncryptionRequest")
class NxEncryption {
    @Shadow("field_149612_a")
    private String    hashedServerId;
    @Shadow("field_149610_b")
    private PublicKey publicKey;
    @Shadow("field_149611_c")
    private byte[]    verifyToken;

    @Inject("func_148840_b")
    public void write(PacketBuffer buf) {
        buf.writeByte(99);
        buf.writeString(this.hashedServerId);

        MyCipher sm4 = new MyCipher(new SM4(), MyCipher.MODE_PCBC);
        sm4.setKey(verifyToken, MyCipher.ENCRYPT | MyCipher.PKCS5_PADDING);
        sm4.setOption(MyCipher.IV, Config.ENCRYPT_IV);

        byte[] encoded = this.publicKey.getEncoded();
        try {
            sm4.crypt(ByteBuffer.wrap(encoded), ByteBuffer.wrap(encoded));
        } catch (GeneralSecurityException e) {
            e.printStackTrace();
        }
        buf.writeByteArray(encoded)
           .writeByteArray(this.verifyToken);
    }
}
