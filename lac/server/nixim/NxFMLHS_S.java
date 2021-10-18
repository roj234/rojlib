package lac.server.nixim;

import ilib.util.PlayerUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import lac.server.*;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import net.minecraft.network.play.server.SPacketEntityEffect;
import net.minecraft.network.play.server.SPacketJoinGame;
import net.minecraft.network.play.server.SPacketPlayerAbilities;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldType;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;
import net.minecraftforge.fml.common.network.handshake.IHandshakeState;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.internal.FMLMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.ForgeRegistry;

import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 握手消息处理注入 服务端
 *
 * @author Roj233
 * @since 2021/7/8 19:00
 */
public enum NxFMLHS_S implements IHandshakeState<NxFMLHS_S> {
    START {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_S> c) {}
    },
    HELLO {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_S> cons) {
            if (msg instanceof FMLHandshakeMessage.ClientHello) {
                FMLLog.log.info("客户端: 协议{}", Integer.toHexString(((FMLHandshakeMessage.ClientHello)msg).protocolVersion()));
            } else {
                FMLHandshakeMessage.ModList client = (FMLHandshakeMessage.ModList)msg;
                NetworkDispatcher dsp = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                Map<String, String> list = client.modList();
                if (!list.isEmpty())
                    list.clear();
                else
                    list = ((CacheBridge) ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get().manager).getModList();
                dsp.setModList(list);
                boolean pass = ModInfo.check(list);
                FMLLog.log.info("客户端含有{}个mod, 通过反作弊验证第一阶段{}", client.modListSize(), pass ? "是" : "否");
                if(!pass) {
                    switch (Config.noPass1Type) {
                        case 0: // hangup
                            cons.accept(DONE);
                            // todo 测试
                            break;
                        case 1: // send rejection
                            cons.accept(ERROR);
                            dsp.rejectHandshake(ModInfo.makeFakeRejections(ctx.channel().remoteAddress(), list));
                            break;
                        case 2: // close connection
                            cons.accept(ERROR);
                            dsp.manager.closeChannel(new TextComponentString("未通过反作弊第一阶段验证"));
                            break;
                    }
                    return;
                }

                String rejection = ModInfo.checkModList(list, Side.CLIENT);
                if (rejection != null) {
                    cons.accept(ERROR);
                    dsp.rejectHandshake(rejection);
                } else {
                    cons.accept(WAITINGCACK);
                    ctx.writeAndFlush(new FMLHandshakeMessage.ModList(Loader.instance().getActiveModList()));
                }
            }
        }
    },
    WAITINGCACK {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_S> cons) {
            cons.accept(COMPLETE);
            if (!ctx.channel().attr(NetworkDispatcher.IS_LOCAL).get()) {
                Map<ResourceLocation, ForgeRegistry.Snapshot> snapshot = SharedSnapshot.getSnapshot();
                Iterator<Map.Entry<ResourceLocation, ForgeRegistry.Snapshot>> itr = snapshot.entrySet().iterator();

                while(itr.hasNext()) {
                    Map.Entry<ResourceLocation, ForgeRegistry.Snapshot> e = itr.next();
                    ctx.writeAndFlush(new FMLHandshakeMessage.RegistryData(itr.hasNext(), e.getKey(), e.getValue())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
            }

            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(this.ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            NetworkRegistry.INSTANCE.fireNetworkHandshake(ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get(), Side.SERVER);
        }
    },
    COMPLETE {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_S> cons) {
            cons.accept(Config.enableLoginModule ? LOGIN_INIT : DONE);
            ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(this.ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            if(!Config.enableLoginModule)
                ctx.fireChannelRead(new FMLMessage.CompleteHandshake(Side.SERVER));
        }
    },
    DONE {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_S> c) {}
    },
    ERROR {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_S> c) {}
    },
    LOGIN_INIT {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_S> cons) {
            cons.accept(DONE);
            NetworkDispatcher dsp = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
            NetworkManager conn = dsp.manager;

            // 阻止更新
            // dsp.player.connection = null;
            // 不处理移动数据包
            dsp.player.queuedEndExit = true;

            MinecraftServer server = PlayerUtil.getMinecraftServer();

            conn.sendPacket(new SPacketJoinGame(0, GameType.SURVIVAL, false, 0, EnumDifficulty.PEACEFUL, 1, WorldType.FLAT, true));

            conn.sendPacket(new SPacketCustomPayload("MC|Brand", (new PacketBuffer(Unpooled.buffer(32))).writeString("LAC-Login")));

            SPacketPlayerAbilities caps = new SPacketPlayerAbilities();
            caps.setAllowFlying(true);
            caps.setFlying(true);
            caps.setFlySpeed(0);
            caps.setWalkSpeed(0);
            conn.sendPacket(caps);

            conn.sendPacket(new SPacketEntityEffect(0, new PotionEffect(Potion.getPotionById(1), 999999, 128)));
            conn.sendPacket(new SPacketEntityEffect(0, new PotionEffect(Potion.getPotionById(2), 999999, 128)));
            conn.sendPacket(new SPacketEntityEffect(0, new PotionEffect(Potion.getPotionById(8), 999999, 128)));

            LoginMgr.handleConnect(dsp.player.getName(), conn, () -> {
                ctx.fireChannelRead(new FMLMessage.CompleteHandshake(Side.SERVER));
            });
        }
    }
}
