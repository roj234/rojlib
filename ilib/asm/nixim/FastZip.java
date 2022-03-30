package ilib.asm.nixim;

import ilib.Config;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import roj.util.EmptyArrays;

import net.minecraft.network.PacketBuffer;

import java.util.zip.Deflater;

/**
 * @author solo6975
 * @since 2022/4/6 22:41
 */
public class FastZip extends MessageToByteEncoder<ByteBuf> {
    private final byte[] out = new byte[8192];
    private byte[] tmp = EmptyArrays.BYTES;

    private final Deflater deflater;
    private int threshold;

    public FastZip(int thr) {
        this.threshold = thr;
        this.deflater = new Deflater(Config.compressionLevel);
    }

    protected void encode(ChannelHandlerContext ctx, ByteBuf in, ByteBuf out) throws Exception {
        PacketBuffer pb = new PacketBuffer(out);
        System.out.println("threshold=" + threshold + " count=" + in.readableBytes());
        if (in.readableBytes() < threshold) {
            pb.writeVarInt(0).writeBytes(in);
        } else {
            if (in.hasArray()) {
                deflater.setInput(in.array(), in.arrayOffset(), in.readableBytes());
                in.skipBytes(in.readableBytes());
            } else {
                byte[] tmp = this.tmp;
                if (tmp.length < in.readableBytes()) {
                    if (tmp.length < 8192) {
                        tmp = this.tmp = new byte[in.readableBytes()];
                    } else {
                        byte[] out1 = this.out;
                        while (in.isReadable()) {
                            int cnt = Math.min(in.readableBytes(), tmp.length);
                            in.readBytes(tmp, 0, cnt);
                            deflater.setInput(tmp, 0, cnt);
                            while (!deflater.needsInput()) {
                                pb.writeBytes(out1, 0, deflater.deflate(out1));
                            }
                        }

                        int cnt;
                        do {
                            cnt = deflater.deflate(out1, 0, out1.length, Deflater.SYNC_FLUSH);
                            pb.writeBytes(out1, 0, cnt);
                        } while (cnt > 0);

                        if (Config.resetCompressor) deflater.reset();
                        return;
                    }
                }

                int cnt = in.readableBytes();
                in.readBytes(tmp);
                deflater.setInput(tmp, 0, cnt);
            }

            byte[] out1 = this.out;
            while(!deflater.needsInput()) {
                pb.writeBytes(out1, 0, deflater.deflate(out1));
            }

            int cnt;
            do {
                cnt = deflater.deflate(out1, 0, out1.length, Deflater.SYNC_FLUSH);
                pb.writeBytes(out1, 0, cnt);
            } while (cnt > 0);
            if (Config.resetCompressor) deflater.reset();
        }

    }

    public void setCompressionThreshold(int thresholdIn) {
        this.threshold = thresholdIn;
    }
}
