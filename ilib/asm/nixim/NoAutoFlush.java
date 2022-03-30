package ilib.asm.nixim;

import ilib.asm.util.IFlushable;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import roj.asm.nixim.Copy;
import roj.asm.nixim.Inject;
import roj.asm.nixim.Inject.At;
import roj.asm.nixim.Nixim;
import roj.asm.nixim.Shadow;

import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.EnumPacketDirection;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;

import net.minecraftforge.fml.common.network.internal.FMLProxyPacket;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author solo6975
 * @since 2022/4/6 21:13
 */
@Nixim(value = "net.minecraft.network.NetworkManager", copyItf = true)
class NoAutoFlush extends NetworkManager implements IFlushable {
    @Copy
    private AtomicBoolean autoFlush;

    @Shadow("field_150746_k")
    private Channel channel;

    @Inject(value = "<init>", at = At.TAIL)
    public NoAutoFlush(EnumPacketDirection packetDirection) {
        super(packetDirection);
        autoFlush = new AtomicBoolean(true);
    }

    @Inject("func_150732_b")
    private void dispatchPacket(Packet<?> p, GenericFutureListener<? extends Future<? super Void>>[] callback) {
        EnumConnectionState packet = EnumConnectionState.getFromPacket(p);
        EnumConnectionState protocol = this.channel.attr(PROTOCOL_ATTRIBUTE_KEY).get();
        boolean changed = packet != protocol && !(p instanceof FMLProxyPacket);

        if (channel.eventLoop().inEventLoop()) {
            if (changed) {
                setConnectionState(packet);
            }

            send(p, callback);
        } else {
            if (!changed && callback == null) {
                ChannelPromise _null = channel.voidPromise();
                if (this.autoFlush.get()) {
                    channel.writeAndFlush(packet, _null);
                } else {
                    channel.write(packet, _null);
                }
            } else {
                if (changed) {
                    this.channel.config().setAutoRead(false);
                }

                this.channel.eventLoop().execute(() -> {
                    if (changed) {
                        setConnectionState(packet);
                    }

                    send(p, callback);
                });
            }
        }

    }

    @Copy
    private void send(Packet<?> p, GenericFutureListener<? extends Future<? super Void>>[] callback) {
        if (callback == null) {
            channel.write(p, channel.voidPromise());
        } else {
            ChannelFuture future = channel.write(p);
            future.addListeners(callback);
            future.addListener(ChannelFutureListener.FIRE_EXCEPTION_ON_FAILURE);
        }

        if (this.autoFlush.get()) {
            this.channel.flush();
        }
    }

    @Copy
    public void setAutoFlush(boolean flush) {
        boolean prev = this.autoFlush.getAndSet(flush);
        if (!prev && flush) {
            this.channel.flush();
        }
    }
}
