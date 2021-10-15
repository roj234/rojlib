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
package ilib.asm.nixim;

import ilib.Config;
import ilib.ImpLib;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Nixim;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/1/16 15:06
 */
@Nixim("net.minecraft.network.PacketBuffer")
public class NiximPacketBuffer extends PacketBuffer {
    public NiximPacketBuffer(ByteBuf wrapped) {
        super(wrapped);
    }

    @Inject("func_189425_b")
    public byte[] readByteArray(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength) {
            final DecoderException o = new DecoderException("ByteArray with size " + i + " is bigger than allowed " + maxLength);
            if(!Config.packetBufferInfinity) {
                throw o;
            } else {
                ImpLib.logger().error(o);
            }
            return new byte[0];
        } else {
            byte[] abyte = new byte[i];
            this.readBytes(abyte);
            return abyte;
        }
    }

    @Inject("func_189424_c")
    public int[] readVarIntArray(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength) {
            final DecoderException o = new DecoderException("VarIntArray with size " + i + " is bigger than allowed " + maxLength);
            if(!Config.packetBufferInfinity) {
                throw o;
            } else {
                ImpLib.logger().error(o);
            }
            return new int[0];
        } else {
            int[] aint = new int[i];

            for(int j = 0; j < aint.length; ++j) {
                aint[j] = this.readVarInt();
            }

            return aint;
        }
    }

    @Inject("func_189423_a")
    public long[] readLongArray(@Nullable long[] array, int maxLength) {
        int i = this.readVarInt();
        if (array == null || array.length != i) {
            if (i > maxLength) {
                final DecoderException o = new DecoderException("LongArray with size " + i + " is bigger than allowed " + maxLength);
                if(!Config.packetBufferInfinity) {
                    throw o;
                } else {
                    ImpLib.logger().error(o);
                }
                return array == null ? new long[0] : array;
            }

            array = new long[i];
        }

        for(int j = 0; j < array.length; ++j) {
            array[j] = this.readLong();
        }

        return array;
    }

    @Inject("func_150789_c")
    public String readString(int maxLength) {
        int i = this.readVarInt();
        if (i > maxLength * 4) {
            final DecoderException o = new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + i + " > " + maxLength * 4 + ")");
            if(!Config.packetBufferInfinity) {
                throw o;
            } else {
                ImpLib.logger().error(o);
            }
            return "";
        } else if (i < 0) {
            final DecoderException o = new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
            if(!Config.packetBufferInfinity) {
                throw o;
            } else {
                ImpLib.logger().error(o);
            }
            return "";
        } else {
            String s = this.toString(this.readerIndex(), i, StandardCharsets.UTF_8);
            this.readerIndex(this.readerIndex() + i);
            if (s.length() > maxLength) {
                final DecoderException o = new DecoderException("The received string length is longer than maximum allowed (" + i + " > " + maxLength + ")");
                if(!Config.packetBufferInfinity) {
                    throw o;
                } else {
                    ImpLib.logger().error(o);
                }
            }
            return s;
        }
    }

    @Inject("func_150793_b")
    public NBTTagCompound readCompoundTag() {
        int i = this.readerIndex();
        byte b0 = this.readByte();
        if (b0 == 0) {
            return null;
        } else {
            this.readerIndex(i);

            try {
                return CompressedStreamTools.read(new ByteBufInputStream(this), new NBTSizeTracker(Config.nbtMaxLength));
            } catch (IOException var4) {
                throw new EncoderException(var4);
            }
        }
    }
}
