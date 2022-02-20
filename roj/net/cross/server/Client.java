package roj.net.cross.server;

import roj.collect.IntMap;
import roj.concurrent.PacketBuffer;
import roj.config.data.CMapping;
import roj.io.NIOUtil;
import roj.net.WrappedSocket;
import roj.net.cross.AEClient;
import roj.net.misc.FDChannel;
import roj.net.misc.Pipe;
import roj.net.misc.Selectable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.function.Consumer;

import static roj.net.cross.Util.*;
import static roj.net.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
public final class Client extends FDChannel implements Selectable, Consumer<Client> {
    Room room;
    int  clientId;

    // region 统计数据 (可以删除)
    public final long creation;
    public long       lastPacket;

    public int getClientId() {
        return clientId;
    }

    public Room getRoom() {
        return room;
    }
    // endregion

    // 状态量
    private Stated state;
    long timer, pingTimer;
    int st1, waiting;
    UPnPPingTask task;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String s = ch.socket().getRemoteSocketAddress().toString();
        sb.append("[").append(s.startsWith("/") ? s.substring(1) : s).append("]");
        if (room != null) {
            sb.append(" \"").append(room.id).append("\"#").append(clientId);
        }
        return sb.toString();
    }

    Client(WrappedSocket ch) {
        super(ch);
        this.creation = System.currentTimeMillis() / 1000;
        this.state = Handshake.HANDSHAKE;
        this.state.enter(this);
        this.wBuf = ByteBuffer.allocateDirect(2000);
        wBuf.limit(0);
    }

    @Override
    public void selected(int readyOps) {
        if (waiting > 0) return;
        try {
            if (state == null) {
                try {
                    if (!ch.shutdown() && timer-- > 0) {
                        return;
                    }
                } catch (IOException ignored) {}
                close();
            } else {
                Stated newState = state.next(this);
                if (newState == PipeLogin.PIPE_OK) {
                    key.cancel();
                } else if (newState != state) {
                    state = newState;
                    if (newState == null) {
                        timer = 10;
                    } else {
                        newState.enter(this);
                    }
                }
            }
        } catch (Throwable e) {
            if (e.getClass() != IOException.class) e.printStackTrace();
            else syncPrint(this + ": 异常 " + e.getMessage());

            try {
                write1(ch, (byte) PS_ERROR_IO);
                ch.shutdown();
            } catch (IOException ignored) {}
            close();
        }
    }

    public CMapping serialize() {
        CMapping json = new CMapping();
        json.put("id", clientId);
        json.put("ip", ch.socket().getRemoteSocketAddress().toString());
        Stated state = this.state;
        json.put("state", state == null ? "CLOSED" : state.getClass().getSimpleName());
        json.put("time", creation);
        long up = 0, down = 0;
        if (!pipes.isEmpty()) {
            synchronized (pipes) {
                for (PipeGroup pg : pipes.values()) {
                    Pipe pr = pg.pairRef;
                    if (pr == null) continue;
                    up += pr.uploaded;
                    down += pr.downloaded;
                }
            }
        }
        json.put("up", clientId == 0 ? down : up);
        json.put("down", clientId == 0 ? up : down);
        json.put("heart", lastPacket / 1000);
        json.put("pipe", pipes.size());
        return json;
    }

    static final int MAX_PACKET = 10020;
    int        wTimer;
    ByteBuffer wBuf;
    public void tick(int elapsed) throws IOException {
        if (ch == null) return;
        if (state != null) {
            state.tick(this);
            if (state instanceof Logout) return;
        } else return;

        ch.dataFlush();

        if (clientId == 0 && !pipes.isEmpty()) {
            long time = System.currentTimeMillis();
            // 嗯...差不多可以，只要脸不太黑...
            if ((time & 127) == 0) {
                synchronized (pipes) {
                    for (Iterator<PipeGroup> itr = pipes.values().iterator(); itr.hasNext(); ) {
                        PipeGroup pair = itr.next();
                        if (pair.pairRef.idleTime > AEServer.PIPE_TIMEOUT) {
                            itr.remove();
                            pair.close(-2);
                        }
                    }
                }
            }
        }

        ByteBuffer b = this.wBuf;
        if (b.hasRemaining()) {
            ch.write(b);
            if (wTimer > 1000) {
                syncPrint(this + " 写出超时!");
                toState(Logout.LOGOUT);
            }
            wTimer += elapsed;
            if (b.hasRemaining()) return;
            if (!packets.hasMore())
                key.interestOps(SelectionKey.OP_READ);
        }

        if (packets.hasMore()) {
            b.clear();
            while (!packets.take(b)) {
                if (b.capacity() >= MAX_PACKET) {
                    packets.poll();
                    throw new IllegalStateException("Packet too big");
                }
                NIOUtil.clean(b);
                b = this.wBuf = ByteBuffer.allocateDirect(Math.min(b.capacity() << 1, MAX_PACKET));
            }
            b.flip();
            ch.write(b);
            if (b.hasRemaining()) {
                wTimer = 0;
                key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            } else if (!packets.hasMore())
                key.interestOps(SelectionKey.OP_READ);
        }
    }

    @Override
    public void close() {
        try {
            ch.close();
        } catch (IOException ignored) {}

        if (room != null) {
            if (room.master == this) {
                room.master = null;
                server.rooms.remove(room.id);
                room.close();
            } else {
                synchronized (room.clients) {
                    room.clients.remove(clientId);
                }
                Client mw = room.master;
                if (mw != null) {
                    ByteBuffer x = ByteBuffer.allocate(5).put((byte) PH_CLIENT_LOGOUT).putInt(clientId);
                    x.flip();
                    mw.sync(x);
                }
            }
        }

        if (key != null) key.cancel();
        ch = null;
        server.remain.getAndIncrement();
    }

    @Override
    public void accept(Client client) {
        assert client == this;
        close();
    }

    private final PacketBuffer packets = new PacketBuffer();
    public void sync(ByteBuffer rb) {
        if (rb.remaining() > MAX_PACKET) throw new IllegalArgumentException("Packet too big");
        packets.offer(rb);
        key.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    void toState(Stated state) {
        this.state = state;
        this.state.enter(this);
    }

    final IntMap<PipeGroup> pipes = new IntMap<>();
    PipeGroup pending;
    String generatePipe(int[] tmp) {
        if (pending != null) return "管道 #" + pending.id + " 处于等待状态";
        if (clientId == 0) return "只有客户端才能请求管道";
        if (server.pipes.size() > 100) return "服务器等待打开的管道过多";
        if (pipes.size() > AEClient.MAX_CHANNEL_COUNT) return "你打开的管道过多";
        server.pipeTimeoutHandler();

        PipeGroup group = pending = new PipeGroup();
        group.downOwner = this;
        group.id = server.pipeId++;
        do {
            group.upPass = server.rnd.nextInt();
        } while (group.upPass == 0);
        do {
            group.downPass = server.rnd.nextInt();
        } while (group.downPass == 0 || group.downPass == group.upPass);
        group.pairRef = new Pipe(null, null);
        group.pairRef.att = group;
        group.timeout = System.currentTimeMillis() + 5000;

        tmp[0] = group.id;
        tmp[1] = group.upPass;

        synchronized (pipes) {
            pipes.put(group.id, group);
        }
        synchronized (room.master.pipes) {
            room.master.pipes.put(group.id, group);
        }
        server.pipes.put(group.id, group);

        return null;
    }

    public void closePipe(int pipeId) {
        PipeGroup group;
        synchronized (pipes) {
            group = pipes.remove(pipeId);
        }
        if (group != null) {
            try {
                group.close(clientId == 0 ? 0 : 1);
            } catch (IOException ignored) {}
        }
    }

    public PipeGroup getPipe(int pipeId) {
        return pipes.get(pipeId);
    }

    UPnPPingTask ping(byte[] ip, char port, long sec) {
        if (System.currentTimeMillis() - pingTimer < 10000) {
            return null;
        }
        pingTimer = System.currentTimeMillis();

        UPnPPingTask task = new UPnPPingTask(ip, port, sec);
        server.asyncExecute(task);
        return task;
    }
}
