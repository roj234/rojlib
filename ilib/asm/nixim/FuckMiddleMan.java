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
package ilib.asm.nixim;

import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;
import roj.text.crypt.SM4;
import roj.util.ByteList;

import net.minecraft.network.PacketBuffer;
import net.minecraft.network.login.server.SPacketEncryptionRequest;
import net.minecraft.util.CryptManager;

import java.io.IOException;
import java.security.DigestException;
import java.security.PublicKey;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/10/15 20:31
 */
@Nixim("net.minecraft.network.login.server.SPacketEncryptionRequest")
public class FuckMiddleMan extends SPacketEncryptionRequest {
    @Shadow("field_149612_a")
    private String    hashedServerId;
    @Shadow("field_149610_b")
    private PublicKey publicKey;
    @Shadow("field_149611_c")
    private byte[]    verifyToken;

    @Inject("func_148837_a")
    public void readPacketData(PacketBuffer _lvt_1_) throws IOException {
        this.hashedServerId = _lvt_1_.readString(20);
        byte[] key = _lvt_1_.readByteArray();
        this.verifyToken = _lvt_1_.readByteArray();
        SM4 sm4 = new SM4();
        sm4.reset(SM4.SM4_DECRYPT | SM4.SM4_AUTO_PADDING | SM4.SM4_ECB);
        sm4.setPassword(verifyToken);
        ByteList out = new ByteList();
        try {
            sm4.crypt(new ByteList(key), out);
            key = out.toByteArray();
        } catch (DigestException ignored) {}
        sm4.reset(0);
        this.publicKey = CryptManager.decodePublicKey(key);
    }

    @Inject("func_148840_b")
    public void writePacketData(PacketBuffer buf) throws IOException {
        buf.writeString(this.hashedServerId);

        byte[] encoded = this.publicKey.getEncoded();
        SM4 sm4 = new SM4();
        sm4.reset(SM4.SM4_ENCRYPT | SM4.SM4_AUTO_PADDING | SM4.SM4_ECB);
        byte[] token = this.verifyToken;
        sm4.setPassword(token);
        ByteList out = new ByteList();
        try {
            sm4.crypt(new ByteList(encoded), out);
            buf.writeBytes(out.list, 0, out.pos());
        } catch (DigestException e) {
            buf.writeBytes(encoded);
        }
        sm4.reset(0);
        buf.writeByteArray(token);
    }
}
