package lac.injector.patch;

import com.google.common.collect.Multimap;
import com.google.common.util.concurrent.Futures;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import roj.collect.MyHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;

import net.minecraftforge.fml.common.FMLLog;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.handshake.FMLHandshakeMessage;
import net.minecraftforge.fml.common.network.handshake.IHandshakeState;
import net.minecraftforge.fml.common.network.handshake.NetworkDispatcher;
import net.minecraftforge.fml.common.network.internal.FMLNetworkHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.registries.ForgeRegistry;
import net.minecraftforge.registries.GameData;

import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2021/7/8 19:00
 */
public enum NxFMLHS_C implements IHandshakeState<NxFMLHS_C> {
    START {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_C> c) {}
    },
    HELLO {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_C> cons) {
            boolean isVanilla = msg == null;
            if (isVanilla) {
                cons.accept(DONE);
            } else {
                cons.accept(WAITINGSERVERDATA);
            }

            ctx.writeAndFlush(FMLHandshakeMessage.makeCustomChannelRegistration(NetworkRegistry.INSTANCE.channelNamesFor(Side.CLIENT)));
            if (isVanilla) {
                ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get().abortClientHandshake("VANILLA");
            } else {
                FMLHandshakeMessage.ServerHello sh = (FMLHandshakeMessage.ServerHello)msg;
                FMLLog.log.info("服务端: 协议 {}", Integer.toHexString(sh.protocolVersion()));
                if (sh.protocolVersion() > 1) {
                    ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get().setOverrideDimension(sh.overrideDim());
                }

                ctx.writeAndFlush(new FMLHandshakeMessage.ClientHello()).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                ctx.writeAndFlush(new FMLHandshakeMessage.ModList(Loader.instance().getActiveModList()) {}).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
        }
    },
    WAITINGSERVERDATA {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_C> cons) {
            String modRejections = FMLNetworkHandler.checkModList((FMLHandshakeMessage.ModList)msg, Side.SERVER);
            if (modRejections != null) {
                cons.accept(ERROR);
                NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                dispatcher.rejectHandshake(modRejections);
            } else {
                if (!ctx.channel().attr(NetworkDispatcher.IS_LOCAL).get()) {
                    cons.accept(WAITINGSERVERCOMPLETE);
                } else {
                    cons.accept(PENDINGCOMPLETE);
                }

                ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(this.ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
            }
        }
    },
    WAITINGSERVERCOMPLETE {
        public void accept(ChannelHandlerContext ctx, FMLHandshakeMessage msg, Consumer<? super NxFMLHS_C> cons) {
            FMLHandshakeMessage.RegistryData pkt = (FMLHandshakeMessage.RegistryData)msg;
            Map<ResourceLocation, ForgeRegistry.Snapshot> snap = ctx.channel().attr(NetworkDispatcher.FML_GAMEDATA_SNAPSHOT).setIfAbsent(new MyHashMap<>());

            ForgeRegistry.Snapshot entry = new ForgeRegistry.Snapshot();
            entry.ids.putAll(pkt.getIdMap());
            entry.dummied.addAll(pkt.getDummied());
            entry.overrides.putAll(pkt.getOverrides());
            snap.put(pkt.getName(), entry);
            if (pkt.hasMore()) {
                cons.accept(WAITINGSERVERCOMPLETE);
                FMLLog.log.debug("收到注册表 {}: {} IDs, {} overrides, {} dummied", pkt.getName(), entry.ids.size(), entry.overrides.size(), entry.dummied.size());
            } else {
                ctx.channel().attr(NetworkDispatcher.FML_GAMEDATA_SNAPSHOT).set(null);
                Multimap<ResourceLocation, ResourceLocation> locallyMissing = Futures.getUnchecked(Minecraft.getMinecraft().addScheduledTask(() -> {
                    return GameData.injectSnapshot(snap, false, false);
                }));
                if (!locallyMissing.isEmpty()) {
                    cons.accept(ERROR);
                    NetworkDispatcher dispatcher = ctx.channel().attr(NetworkDispatcher.FML_DISPATCHER).get();
                    dispatcher.rejectHandshake("方块或物品与服务端不匹配");
                    FMLLog.log.fatal("缺少了{}个注册表项", locallyMissing.size());
                    locallyMissing.asMap().forEach((key, value) -> {
                        FMLLog.log.debug("> {} => {}", key, value);
                    });
                } else {
                    cons.accept(PENDINGCOMPLETE);
                    ctx.writeAndFlush(new FMLHandshakeMessage.HandshakeAck(this.ordinal())).addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
                }
            }
        }
    },
    PENDINGCOMPLETE {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_C> c) {}
    },
    COMPLETE {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_C> c) {}
    },
    DONE {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_C> c) {}
    },
    ERROR {
        public void accept(ChannelHandlerContext a, FMLHandshakeMessage b, Consumer<? super NxFMLHS_C> c) {}
    }
}
