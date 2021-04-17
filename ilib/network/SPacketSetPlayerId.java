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
package ilib.network;

import ilib.ATHandler;
import ilib.ClientProxy;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/1 17:53
 */
public class SPacketSetPlayerId implements Packet<INetHandler> {
    public static void register() {
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, SPacketSetPlayerId.class);
    }

    private int id;

    public SPacketSetPlayerId() {
    }

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
    public void processPacket(@Nonnull final INetHandler handler) {
        if(handler instanceof INetHandlerPlayClient) {
            clientSet();
        }
    }

    @SideOnly(Side.CLIENT)
    private void clientSet() {
        ClientProxy.mc.player.setEntityId(id);
    }
}
