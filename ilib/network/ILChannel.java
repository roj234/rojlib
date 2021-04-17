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
// 天天造轮子系列
package ilib.network;

import ilib.ClientProxy;
import ilib.Config;
import ilib.ImpLib;
import ilib.util.DimensionHelper;
import ilib.util.PlayerUtil;
import ilib.util.TimeUtil;
import org.apache.logging.log4j.Level;
import roj.asm.tree.Clazz;
import roj.asm.tree.insn.ClassInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.InsnList;
import roj.collect.MyHashMap;
import roj.reflect.DirectAccessor;
import roj.util.ByteWriter;
import roj.util.Helpers;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.*;
import net.minecraft.network.play.server.SPacketDisconnect;
import net.minecraft.util.ITickable;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class ILChannel implements ITickable {
    public static final MyHashMap<String, ILChannel> CHANNELS = new MyHashMap<>(2, 0.9f);
    final ChannelCodec serverCodec, clientCodec;

    /**
     * 注册一个{@link ProxyPacket}数据包处理系统
     *
     * @param channel 频道名
     */
    public ILChannel(String channel) {
        serverCodec = new ChannelCodec(Side.SERVER, channel);
        clientCodec = new ChannelCodec(Side.CLIENT, channel);
        CHANNELS.put(channel, this);
        TimeUtil.registerTickHandler(this);
    }

    static Clazz oneKey;
    static AtomicInteger xx = new AtomicInteger();
    /**
     * 注册一个消息
     *
     * @param handler 消息处理程序
     * @param clazz   class
     * @param id      ID，不一定要连续
     * @param side    接收方
     * @param <M>     消息类型
     */
    @SuppressWarnings("unchecked")
    public <M extends IMessage> void registerMessage(@Nonnull IMessageHandler<M> handler, Class<M> clazz, int id, Side side) {
        if (oneKey == null) {
            oneKey = DirectAccessor.builder(Supplier.class).constructFuzzy(clazz, "get").getInternal();
        } else {
            oneKey.name = "ilib/network/GCA$" + xx.getAndIncrement();
            String name = clazz.getName().replace('.', '/');
            InsnList x = oneKey.methods.get(2).code.instructions;
            ClassInsnNode _new = (ClassInsnNode) x.get(0);
            _new.owner = name;
            InvokeInsnNode _init = (InvokeInsnNode) x.get(2);
            _init.owner = name;
        }
        Supplier<M> supplier = (Supplier<M>) DirectAccessor.i_build(oneKey);
        registerMessage(handler, clazz, supplier, id, side);
    }

    /**
     * 注册一个消息
     *
     * @param handler  消息处理程序
     * @param clazz    class
     * @param supplier 消息提供者
     * @param id       ID，不一定要连续
     * @param side     接收方
     * @param <M>      消息类型
     */
    public <M extends IMessage> void registerMessage(@Nonnull IMessageHandler<M> handler, @Nonnull Class<M> clazz, @Nonnull Supplier<M> supplier, int id, @Nullable Side side) {
        if (id <= 0)
            throw new IllegalArgumentException("Id must be positive");
        id += 1;
        if (side == Side.SERVER) {
            clientCodec.addEnc(id, clazz);
            serverCodec.addDec(id, supplier, handler);
        } else {
            serverCodec.addEnc(id, clazz);
            if (ImpLib.isClient)
                clientCodec.addDec(id, supplier, handler);
            if (side == null) {
                clientCodec.addEnc(id, clazz);
                serverCodec.addDec(id, supplier, handler);
            }
        }
    }

    /**
     * 发送到玩家
     */
    public void sendTo(@Nonnull IMessage message, @Nonnull EntityPlayerMP player) {
        List<IMessage> pkts = pending.computeIfAbsent(player, Helpers.fnArrayList());
        pkts.add(message);
        if (pkts.size() > 5 || pending.size() > 5) {
            timer = 0;
            sendAllPending();
        }
    }

    private int timer = 0;

    public void update() {
        if (++timer > Config.packetDelay) {
            sendAllPending();
            timer = 0;
        }
    }

    private final MyHashMap<EntityPlayerMP, List<IMessage>> pending = new MyHashMap<>();
    // todo 尽可能发送大块数据，避免网络中充斥着许多小数据块

    public void sendAllPending() {
        for (Map.Entry<EntityPlayerMP, List<IMessage>> entry : pending.entrySet()) {
            if (entry.getKey().connection == null) continue;
            List<IMessage> list = entry.getValue();
            if (list.size() == 1) {
                sendTo(serverCodec.encode(list.get(0)), entry.getKey());
            } else {
                ByteWriter[] messages = new ByteWriter[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    messages[i] = serverCodec.encode0(list.get(i));
                }
                sendTo(new ProxyPacket(encodePackets(messages), serverCodec.channel), entry.getKey());
            }
        }
        pending.clear();
    }

    private static byte[] encodePackets(ByteWriter[] messages) {
        ByteWriter writer = new ByteWriter(64)
                .writeVarInt(0, false) // internal packet id

                .writeVarInt(messages.length, false);
        for (ByteWriter w : messages) {
            writer.writeVarInt(w.list.pos(), false)
                    .writeBytes(w);
        }
        return writer.toByteArray();
    }

    /**
     * 发送到玩家
     */
    private void sendTo(@Nonnull Packet<?> message, @Nonnull EntityPlayerMP player) {
        if (player.connection == null) {
            ImpLib.logger().catching(Level.WARN, new IllegalStateException("Player is disconnected."));
            return;
        }
        if (player.connection.netManager.channel().attr(NetworkManager.PROTOCOL_ATTRIBUTE_KEY).get() != EnumConnectionState.PLAY) {
            ImpLib.logger().catching(Level.ERROR, new IllegalStateException("Too early to send packet"));
            return;
        }

        player.connection.netManager.sendPacket(message);
    }

    /**
     * 发送给所有
     */
    public void sendToAll(@Nonnull IMessage message) {
        PlayerUtil.getOnlinePlayers().forEach((player) -> sendTo(message, player));
    }

    /**
     * 发送给周围
     */
    public void sendToAllAround(@Nonnull IMessage message, @Nonnull World world, int x, int y, int z, double radius) {
        double dx = x + 0.5d, dy = y + 0.5d, dz = z + 0.5d;
        world.playerEntities.forEach((player) -> {
            if (player.getDistanceSq(dx, dy, dz) < radius) {
                sendTo(message, (EntityPlayerMP) player);
            }
        });
    }

    /**
     * 发送给周围
     */
    public void sendToAllAround(@Nonnull IMessage message, int dimension, int x, int y, int z, double radius) {
        World world = DimensionHelper.getWorldForDimension(null, dimension);
        if (world instanceof WorldServer) {
            sendToAllAround(message, world, x, y, z, radius);
            return;
        }
        double dx = x + 0.5d, dy = y + 0.5d, dz = z + 0.5d;
        PlayerUtil.getOnlinePlayers().forEach((player) -> {
            if (player.world.provider.getDimension() == dimension &&
                    player.getDistanceSq(dx, dy, dz) < radius) {
                sendTo(message, player);
            }
        });
    }

    /**
     * 发送到邻近的chunk, 一般来说，你可以使用{@link #sendToAllTrackingChunk(IMessage, int, int, int)}
     * C: TileEntity center chunk
     * N: Nearby chunk (Of course no one's hand can longer than a chunk)
     * =====-=====-=====
     * | N | | N | | N |
     * =====-=====-=====
     * | N | | C | | N |
     * =====-=====-=====
     * | N | | N | | N |
     * =====-=====-=====
     */
    public void sendToAllTrackingNearbyChunk(@Nonnull IMessage message, int dimension, int x, int z) {
        World world = DimensionHelper.getWorldForDimension(null, dimension);
        if (world != null) {
            sendToAllTrackingNearbyChunk(message, world, x, z);
        }
    }

    /**
     * 发送到邻近的chunk, 一般来说，你可以使用{@link #sendToAllTrackingChunk(IMessage, World, int, int)}
     * C: TileEntity center chunk
     * N: Nearby chunk (Of course no one's hand can longer than a chunk)
     * =====-=====-=====
     * | N | | N | | N |
     * =====-=====-=====
     * | N | | C | | N |
     * =====-=====-=====
     * | N | | N | | N |
     * =====-=====-=====
     */
    public void sendToAllTrackingNearbyChunk(@Nonnull IMessage message, @Nonnull World world, int x, int z) {
        for (int dx = -1; dx < 2; dx++) {
            for (int dz = -1; dz < 2; dz++) {
                sendToAllTrackingChunk(message, world, x + dx, z + dz);
            }
        }
    }

    public void sendToAllTrackingChunk(@Nonnull IMessage message, int dimension, int x, int z) {
        World world = DimensionHelper.getWorldForDimension(null, dimension);
        if (world != null) {
            sendToAllTrackingChunk(message, world, x, z);
        }
    }

    public void sendToAllTrackingChunk(@Nonnull IMessage message, @Nonnull World world, int x, int z) {
        List<EntityPlayerMP> chunk = PlayerUtil.getAllPlayersWatchingChunk(world, x, z);
        for (int i = 0; i < chunk.size(); i++) {
            EntityPlayerMP player = chunk.get(i);
            sendTo(message, player);
        }
    }

    public void sendToAllTracking(@Nonnull IMessage message, Entity entity) {
        if (!ImpLib.proxy.isMainThread(false)) {
            ImpLib.proxy.runAtMainThread(false, () -> sendToAllTracking(message, entity));
        } else {
            ((WorldServer) entity.getEntityWorld()).getEntityTracker().sendToTracking(entity, serverCodec.encode(message));
        }
    }

    public void sendToDimension(@Nonnull IMessage message, int dimensionId) {
        List<EntityPlayerMP> dimension = PlayerUtil.getAllPlayersInDimension(dimensionId);
        for (int i = 0; i < dimension.size(); i++) {
            EntityPlayerMP player = dimension.get(i);
            sendTo(message, player);
        }
    }

    @SideOnly(Side.CLIENT)
    public void sendToServer(@Nonnull IMessage message) {
        EntityPlayerSP player = ClientProxy.mc.player;
        if (player != null) {
            player.connection.getNetworkManager().sendPacket(clientCodec.encode(message));
        }
    }

    public static void kickWithMessage(EntityPlayerMP player, String text) {
        kickWithMessage(player.connection, new TextComponentString(text));
    }

    public static void kickWithMessage(EntityPlayerMP player, ITextComponent text) {
        kickWithMessage(player.connection, text);
    }

    public static void kickWithMessage(INetHandler handler, String text) {
        kickWithMessage(handler, new TextComponentString(text));
    }

    @SuppressWarnings("unchecked")
    public static void kickWithMessage(INetHandler handler, ITextComponent text) {
        NetworkManager man;
        if (handler instanceof NetHandlerPlayServer) {
            (man = ((NetHandlerPlayServer) handler).getNetworkManager()).sendPacket(new SPacketDisconnect(text), result -> man.closeChannel(text));
        } else {
            (man = ((NetHandlerPlayClient) handler).getNetworkManager()).closeChannel(text);
        }
        man.channel().config().setAutoRead(false);
    }

}