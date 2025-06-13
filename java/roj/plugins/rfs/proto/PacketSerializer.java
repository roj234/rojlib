package roj.plugins.rfs.proto;

import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/3/4 12:08
 */
public final class PacketSerializer implements ChannelHandler {
    private final ToIntMap<Class<? extends Packet>> clientEncoder = new ToIntMap<>(), serverEncoder = new ToIntMap<>();
    private final List<Function<DynByteBuf, Packet>> clientDecoder = new ArrayList<>(), serverDecoder = new ArrayList<>();

    public <T extends Packet> PacketSerializer register(Class<T> packet, Function<DynByteBuf, T> newPacket) {
        if (ClientPacket.class.isAssignableFrom(packet)) {
            serverEncoder.putInt(packet, serverEncoder.size());
            clientDecoder.add(Helpers.cast(newPacket));
        }
        if (ServerPacket.class.isAssignableFrom(packet)) {
            clientEncoder.putInt(packet, clientEncoder.size());
            serverDecoder.add(Helpers.cast(newPacket));
        }
        return this;
    }

    public ChannelHandler client() {return new Instance(clientEncoder, clientDecoder);}
    public ChannelHandler server() {return new Instance(serverEncoder, serverDecoder);}

    private static final class Instance implements ChannelHandler {
        private final ToIntMap<Class<? extends Packet>> encoder;
        private final List<Function<DynByteBuf, Packet>> decoder;
        private ByteList encodeBuffer = new ByteList();

        Instance(ToIntMap<Class<? extends Packet>> encoder, List<Function<DynByteBuf, Packet>> decoder) {
            this.encoder = encoder;
            this.decoder = decoder;
        }

        @Override
        public void channelRead(ChannelCtx ctx, Object msg) throws IOException {
            DynByteBuf ib = (DynByteBuf) msg;
            int pid = ib.readVUInt();
            var packetCreator = decoder.get(pid);
            if (packetCreator == null) throw new IllegalArgumentException("unknown packet #"+pid);
            var packet = packetCreator.apply(ib);
            if (ib.isReadable()) throw new IllegalArgumentException("trailing bytes in packet "+packet);

            ctx.channelRead(packet);
        }

        @Override
        public void channelWrite(ChannelCtx ctx, Object msg) throws IOException {
            int pid = encoder.getOrDefault(msg.getClass(), -1);
            if (pid < 0) throw new IllegalArgumentException("unknown packet "+msg);

            var out = IOUtil.getSharedByteBuf();
            ((Packet) msg).encode(out.putVUInt(pid));
            ctx.channelWrite(out);
        }
    }
}
