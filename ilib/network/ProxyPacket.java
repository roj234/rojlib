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
import ilib.ImpLib;
import org.apache.logging.log4j.Level;
import roj.util.ByteReader;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.INetHandler;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/10/1 17:53
 */
public class ProxyPacket implements Packet<INetHandler> {
    public static void register() {
        ATHandler.registerNetworkPacket(EnumConnectionState.PLAY, ProxyPacket.class);
    }

    private byte[] payload;
    private String channel;

    public ProxyPacket() {
    }

    public ProxyPacket(final byte[] payload, String channel) {
        this.payload = payload;
        this.channel = channel;
    }

    @Override
    public void readPacketData(final PacketBuffer buffer) {
        char[] arr = new char[buffer.readByte()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = (char) buffer.readByte();
        }
        channel = new String(arr);

        byte[] arr2 = new byte[buffer.readVarInt()];
        buffer.readBytes(arr2);
        this.payload = arr2;
    }

    @Override
    public void writePacketData(final PacketBuffer buffer) {
        buffer.writeByte(channel.length());
        buffer.ensureWritable(channel.length() + 1);
        for (int i = 0; i < channel.length(); i++) {
            buffer.writeByte(channel.charAt(i));
        }
        buffer.writeVarInt(this.payload.length);
        buffer.writeBytes(this.payload);
    }

    @Override
    public void processPacket(@Nonnull final INetHandler handler) {
        ILChannel channel = ILChannel.CHANNELS.get(this.channel);
        if (channel != null) {
            try {
                if (handler instanceof INetHandlerPlayServer) {
                    channel.serverCodec.decode(this, handler);
                } else {
                    channel.clientCodec.decode(this, handler);
                }
            } catch (Throwable t) {
                ImpLib.logger().catching(Level.FATAL, new RuntimeException("There was a critical exception handling a packet on channel " + this.channel, t));
                ILChannel.kickWithMessage(handler, new TextComponentString("在数据包处理过程中发生了异常, 是的, ①个异常\n为了防止各种BUG发生, 您的连接已经中断"));
            }
        }
    }

    public ByteReader payload() {
        return new ByteReader(this.payload);
    }

    @Override
    public String toString() {
        return "ProxyPacket{" +
                "payload=" + Arrays.toString(payload) +
                ", channel='" + channel + '\'' +
                '}';
    }
}
