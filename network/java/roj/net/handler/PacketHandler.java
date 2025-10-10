package roj.net.handler;

import roj.collect.ArrayList;
import roj.collect.ToIntMap;
import roj.net.ChannelCtx;
import roj.net.ChannelHandler;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2025/3/4 12:08
 */
public final class PacketHandler implements ChannelHandler {
    private final ToIntMap<Class<? extends Packet>> clientEncoder = new ToIntMap<>(), serverEncoder = new ToIntMap<>();
    private final List<Function<DynByteBuf, ? extends Packet>> clientDecoder = new ArrayList<>(), serverDecoder = new ArrayList<>();

    private final Class<?> clientPacketClass, serverPacketClass;

    public PacketHandler(Class<? extends Packet> clientPacketClass, Class<? extends Packet> serverPacketClass) {
        this.clientPacketClass = clientPacketClass;
        this.serverPacketClass = serverPacketClass;
    }

    public <T extends Packet> PacketHandler register(Class<T> packet, Function<DynByteBuf, T> newPacket) {
        if (clientPacketClass.isAssignableFrom(packet)) {
            serverEncoder.putInt(packet, serverEncoder.size());
            clientDecoder.add(newPacket);
        }
        if (serverPacketClass.isAssignableFrom(packet)) {
            clientEncoder.putInt(packet, clientEncoder.size());
            serverDecoder.add(newPacket);
        }
        return this;
    }

    public ChannelHandler client() {return new Instance(clientEncoder, clientDecoder);}
    public ChannelHandler server() {return new Instance(serverEncoder, serverDecoder);}

    private static final class Instance implements ChannelHandler {
        private final ToIntMap<Class<? extends Packet>> encoder;
        private final List<Function<DynByteBuf, ? extends Packet>> decoder;
        private final ByteList encodeBuffer = new ByteList();

        Instance(ToIntMap<Class<? extends Packet>> encoder, List<Function<DynByteBuf, ? extends Packet>> decoder) {
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

            var tmp = encodeBuffer;
            ((Packet) msg).encode(tmp.putVUInt(pid));
            var buf = ctx.alloc().allocate(true, tmp.readableBytes()).put(tmp);
            tmp.clear();
            try {
                ctx.channelWrite(buf);
            } finally {
                buf.release();
            }
        }
    }
}
