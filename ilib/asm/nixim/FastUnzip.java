package ilib.asm.nixim;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.PacketBuffer;
import roj.util.EmptyArrays;

import java.util.zip.Inflater;

import static ilib.Config.maxCompressionPacketSize;

/**
 * @author solo6975
 * @since 2022/4/6 22:41
 */
public class FastUnzip extends ChannelInboundHandlerAdapter {
    private final Inflater inflater;
    private int threshold;
    private byte[] tmp = EmptyArrays.BYTES;

    public FastUnzip(int threshold) {
        this.threshold = threshold;
        this.inflater = new Inflater();
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf) msg;
        if (in.readableBytes() != 0) {
            PacketBuffer pb = new PacketBuffer(in);
            int len = pb.readVarInt();
            if (len == 0) {
                ctx.fireChannelRead(pb.readBytes(pb.readableBytes()));
            } else {
                if (len < threshold) {
                    throw new DecoderException("Badly compressed packet - size of " + len + " is below server threshold of " + threshold);
                }

                if (len > maxCompressionPacketSize) {
                    throw new DecoderException("Badly compressed packet - size of " + len + " is larger than protocol maximum of " + maxCompressionPacketSize);
                }

                byte[] out = new byte[len];
                if (pb.hasArray()) {
                    inflater.setInput(pb.array(), pb.arrayOffset(), pb.readableBytes());
                    pb.skipBytes(pb.readableBytes());
                } else {
                    byte[] tmp = this.tmp;
                    if (tmp.length < pb.readableBytes()) {
                        if (tmp.length < 8192) {
                            tmp = this.tmp = new byte[pb.readableBytes()];
                        } else {
                            int outOff = 0;

                            while (pb.isReadable()) {
                                int cnt = Math.min(pb.readableBytes(), tmp.length);
                                pb.readBytes(tmp, 0, cnt);
                                inflater.setInput(tmp, 0, cnt);
                                while (!inflater.needsInput()) {
                                    inflater.inflate(out, outOff, out.length - outOff);
                                }
                            }

                            if (outOff < out.length) {
                                throw new DecoderException("??????????????????");
                            }
                            ctx.fireChannelRead(Unpooled.wrappedBuffer(out));
                            return;
                        }
                    }

                    int cnt = pb.readableBytes();
                    pb.readBytes(tmp);
                    inflater.setInput(tmp, 0, cnt);
                }

                if (inflater.inflate(out) < out.length) {
                    throw new DecoderException("??????????????????");
                }
                inflater.reset();
                ctx.fireChannelRead(Unpooled.wrappedBuffer(out));
            }
        }
    }

    public void setCompressionThreshold(int thresholdIn) {
        this.threshold = thresholdIn;
    }
}
