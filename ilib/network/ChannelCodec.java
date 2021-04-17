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

import ilib.ImpLib;
import roj.collect.IntMap;
import roj.collect.ToIntMap;
import roj.util.ByteReader;
import roj.util.ByteWriter;
import roj.util.Helpers;

import net.minecraft.network.INetHandler;

import net.minecraftforge.fml.relauncher.Side;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * 消息编解码器
 */
public class ChannelCodec {
    private final IntMap<HandlerWrap<?>> indexes = new IntMap<>(4);
    private final ToIntMap<Class<? extends IMessage>> types = new ToIntMap<>(4);

    public final Side side;
    public final String channel;

    public ChannelCodec(Side side, String channel) {
        this.side = side;
        this.channel = channel;
    }

    public void addEnc(int id, Class<? extends IMessage> type) {
        this.types.putInt(type, id);
    }

    public <M extends IMessage> void addDec(int id, Supplier<M> type, IMessageHandler<M> handler) {
        this.indexes.put(id, new HandlerWrap<>(handler, type));
    }

    protected final ProxyPacket encode(@Nonnull IMessage msg) {
        return new ProxyPacket(encode0(msg).toByteArray(), channel);
    }

    @Nonnull
    ByteWriter encode0(@Nonnull IMessage msg) {
        int id = this.types.getOrDefault(msg.getClass(), -1);
        if (id <= 0) {
            throw new RuntimeException("Sending undefined packet " + msg.getClass().getName());
        }
        ByteWriter writer = new ByteWriter(64).writeVarInt(id, false);
        msg.toBytes(writer);
        return writer;
    }

    protected final void decode(ProxyPacket packet, INetHandler handler) {
        if (!validPacket(packet)) return;
        ByteReader payload = packet.payload();
        if (payload.isFinished()) {
            ImpLib.logger().error("Packet decoder {} received an empty packet!", channel);
            return;
        }
        int id = payload.readVarInt(false);
        if (id == 0) {
            for (ByteReader payload1 : decodeMergePackets(payload)) {
                handlePacket(handler, payload1, payload1.readVarInt(false));
            }
        } else {
            handlePacket(handler, payload, id);
        }
    }

    protected static ByteReader[] decodeMergePackets(ByteReader r) {
        int len = r.readVarInt(false);
        ByteReader[] brs = new ByteReader[len];
        for (int i = 0; i < len; i++) {
            brs[i] = new ByteReader(r.readBytesDelegated(r.readVarInt(false)));
        }
        return brs;
    }

    private void handlePacket(INetHandler handler, ByteReader payload, int id) {
        HandlerWrap<?> clazz = this.indexes.get(id);
        if (clazz == null) {
            ImpLib.logger().catching(new IllegalArgumentException("Receiving unknown " + id));
            return;
        }
        IMessage msg = clazz.supplier.get();
        msg.fromBytes(payload);
        clazz.handler.onMessage(msg, new MessageContext(handler, this.side));
    }

    protected boolean validPacket(ProxyPacket msg) {
        return true;
    }

    static class HandlerWrap<M extends IMessage> {
        final IMessageHandler<IMessage> handler;
        final Supplier<M> supplier;

        HandlerWrap(IMessageHandler<M> handler, Supplier<M> supplier) {
            this.handler = Helpers.cast(handler);
            this.supplier = supplier;
        }

        @Override
        public String toString() {
            return "HW{" + handler +
                    ", " + supplier +
                    '}';
        }
    }
}
