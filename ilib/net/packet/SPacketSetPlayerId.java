/*
 * This file is a part of MI
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
package ilib.net.packet;

import ilib.ATHandler;
import ilib.ClientProxy;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * @author Roj234
 * @since  2020/10/1 17:53
 */
public class SPacketSetPlayerId implements Packet<INetHandlerPlayClient> {
    public static void register() {
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, SPacketSetPlayerId.class);
    }

    private int id;

    public SPacketSetPlayerId() {}

    public SPacketSetPlayerId(int id) {
        this.id = id;
    }

    @Override
    public void readPacketData(final PacketBuffer buffer) {
        this.id = buffer.readVarInt();
    }

    @Override
    public void writePacketData(final PacketBuffer buffer) {
        buffer.writeVarInt(id);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void processPacket(INetHandlerPlayClient handler) {
        ClientProxy.mc.player.setEntityId(id);
    }
}
