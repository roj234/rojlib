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

import lac.server.CacheBridge;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.collect.MyHashMap;

import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.handshake.INetHandlerHandshakeServer;
import net.minecraft.network.handshake.client.C00Handshake;

import java.util.Map;

/**
 * Your description here
 *
 * @author Roj233
 * @version 0.1
 * @since 2021/10/15 20:21
 */
@Nixim(value = "net.minecraft.network.handshake.client.C00Handshake")
class NxC00Handshake extends C00Handshake {
    @Copy
    private Map<String, String> modList;

    @Inject(value = "func_148837_a", at = At.TAIL)
    public void read(PacketBuffer buf) {
        if (buf.readableBytes() > 0) {
            modList = new MyHashMap<>();
            while (buf.readableBytes() > 0) {
                modList.put(buf.readString(222), buf.readString(222));
            }
        }
    }

    @Inject(value = "func_148833_a", at = At.REPLACE)
    public void process(INetHandlerHandshakeServer handler) {
        ((CacheBridge) ((NetHandlerPlayServer)handler).netManager).setModList(modList);
        handler.processHandshake(this);
    }
}
