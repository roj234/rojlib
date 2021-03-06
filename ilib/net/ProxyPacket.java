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
package ilib.net;

import ilib.ImpLib;
import org.apache.logging.log4j.Level;

import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;

/**
 * @author Roj234
 * @since  2020/10/1 17:53
 */
public class ProxyPacket implements Packet<INetHandler> {
    private PacketBuffer payload;
    private String channel;

    public ProxyPacket() {}

    public ProxyPacket(PacketBuffer payload, String channel) {
        this.payload = payload;
        this.channel = channel;
    }

    @Override
    public void readPacketData(PacketBuffer buffer) {
        channel = buffer.readString(127);
        this.payload = new PacketBuffer(buffer.readSlice(buffer.readableBytes()));
    }

    @Override
    public void writePacketData(PacketBuffer buffer) {
        buffer.writeString(channel)
              .writeBytes(payload);
    }

    @Override
    public void processPacket(@Nonnull final INetHandler handler) {
        MyChannel channel = MyChannel.CHANNELS.get(this.channel);
        if (channel != null) {
            try {
                if (handler instanceof INetHandlerPlayServer) {
                    channel.serverCodec.decode(this, handler);
                } else {
                    channel.clientCodec.decode(this, handler);
                }
            } catch (Throwable t) {
                ImpLib.logger().catching(Level.FATAL, new RuntimeException("There was a critical exception handling a packet on channel " + this.channel, t));
                MyChannel.kickWithMessage(handler, new TextComponentString("??????????????????????????????????????????, ??????, ????????????\n??????????????????BUG??????, ????????????????????????"));
            }
        }
    }

    public PacketBuffer payload() {
        return this.payload;
    }

    @Override
    public String toString() {
        return "ProxyPacket{'" + channel + "'}";
    }
}
