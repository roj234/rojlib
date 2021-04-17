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
package lac.client.packets;

import io.netty.buffer.ByteBuf;
import lac.client.AccessHelper;

import net.minecraft.client.Minecraft;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

import java.util.List;

/**
 * @author Roj233
 * @version 0.1
 * @since 2021/10/15 19:18
 */
public class PacketClassCheck implements Packet<INetHandler> {
    private String[] stringId;

    @Override
    public void readPacketData(PacketBuffer buffer) {
        int[] intId = buffer.readVarIntArray(100);
        AccessHelper x = (AccessHelper) accessHelper();
        List<Class<?>> sorted = x.getSorted(ByteBuf.class.getClassLoader());
        String[] sid = stringId = new String[intId.length];
        for (int i = 0; i < intId.length; i++) {
            sid[i] = sorted.get(i).getName();
        }
    }

    static Object accessHelper() {
        return PacketClassCheck.class.getClassLoader();
    }

    @Override
    public void writePacketData(PacketBuffer buffer) {
        buffer.writeByte(stringId.length);
        for (String id : stringId) {
            buffer.writeString(id);
        }
    }

    @Override
    public void processPacket(INetHandler handler) {
        Minecraft.getMinecraft().player.connection.sendPacket(this);
    }
}
