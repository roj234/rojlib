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
package lac.server.packets;

import lac.server.CacheBridge;
import lac.server.ModInfo;
import lac.server.TimeoutHandler;

import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;

/**
 * @author Roj233
 * @since 2021/10/15 19:18
 */
public class PacketClassCheck implements Packet<INetHandler> {
    private int[]    idInInt;
    private String[] idInString;

    public PacketClassCheck() {}

    public PacketClassCheck(int[] ids) {
        this.idInInt = ids;
    }

    @Override
    public void readPacketData(PacketBuffer buffer) {
        int i = buffer.readByte();
        idInString = new String[i];
        for (int j = 0; j < i; j++) {
            idInString[j] = buffer.readString(200);
        }
    }

    @Override
    public void writePacketData(PacketBuffer buffer) {
        buffer.writeVarIntArray(idInInt);
    }

    @Override
    public void processPacket(INetHandler handler) {
        int state = 0;
        int[] data = ((CacheBridge) ((NetHandlerPlayServer) handler).netManager).getClassArray(false);
        if (data == null) {
            state = 1;
            System.out.println("????????????class?????????: ?????? " + ((NetHandlerPlayServer) handler).player.getName());
        } else {
            for (int i = 0; i < idInString.length; i++) {
                if (!idInString[i].equals(ModInfo.classListOrdered[data[i]])) {
                    state = 2;
                    System.out.println("???????????????: ?????? " + ((NetHandlerPlayServer) handler).player.getName());
                    System.out.println("?????????[" + data[i] + "]: " + idInString[i] + " <=> " + ModInfo.classListOrdered[i]);
                    break;
                }
            }
        }
        TimeoutHandler.onPacket("ClassCheck", ((NetHandlerPlayServer) handler).player.getName(), state);
    }
}
