package roj.net.cross.server;

import roj.collect.IntMap;
import roj.concurrent.task.ITaskNaCl;
import roj.config.data.CMapping;
import roj.net.WrappedSocket;
import roj.net.cross.AEClient;
import roj.net.misc.PacketBuffer;
import roj.net.misc.Pipe;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.concurrent.locks.LockSupport;

import static roj.net.cross.Util.*;
import static roj.net.cross.server.AEServer.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:21
 */
final class Client implements ITaskNaCl, Runnable {
    WrappedSocket ch;

    Room   room;
    int    clientId;
    Thread self;

    // region 统计数据 (可以删除)
    public final long creation;
    public long lastHeart;

    public int getClientId() {
        return clientId;
    }

    public Room getRoom() {
        return room;
    }
    // endregion

    private Stated state;

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
        this.ch = ch;
        this.creation = System.currentTimeMillis() / 1000;
        this.state = Handshake.HANDSHAKE;
    }

    @Override
    public void run() {
        self = Thread.currentThread();
        catcher:
        try {
            while (state != null && !server.shutdown) {
                state = state.next(this);
                if (state == PipeLogin.PIPE_OK) break catcher;
            }
            state = Closed.CLOSED;

            while (!ch.shutdown()) {
                LockSupport.parkNanos(100);
            }
            ch.close();
        } catch (Throwable e) {
            syncPrint(this + ": 断开 " + e.getMessage());
            if (e.getClass() != IOException.class) e.printStackTrace();

            try {
                write1(ch, (byte) PS_ERROR_IO);
                ch.shutdown();
            } catch (IOException ignored) {}

            try {
                ch.close();
            } catch (IOException ignored) {}
        }

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

        ch = null;
        server.remain.getAndIncrement();

        synchronized (this) {
            notify();
        }
    }

    public CMapping serialize() {
        CMapping json = new CMapping();
        json.put("id", clientId);
        json.put("ip", ch.socket().getRemoteSocketAddress().toString());
        json.put("state", state.getClass().getSimpleName());
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
        json.put("heart", lastHeart / 1000);
        json.put("pipe", pipes.size());
        return json;
    }

    @Override
    public void calculate(Thread thread) {
        run();
    }

    @Override
    public boolean isDone() {
        return ch == null;
    }

    void pollPackets() throws IOException {
        byte[] data = packets.poll();
        if (data != null) {
            ByteBuffer src = ByteBuffer.wrap(data);
            int time = 1000;
            while (time-- > 0 && !server.shutdown) {
                ch.write(src);
                if (src.hasRemaining()) { LockSupport.parkNanos(1000); } else break;
            }
            if (time <= 0) throw new IOException("超时");
        }

        if (!pipes.isEmpty()) {
            long time = System.currentTimeMillis();
            // 嗯...差不多可以，只要脸不太黑...
            if ((time & 127) != 0) return;
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

    private final PacketBuffer packets = new PacketBuffer();
    public void sync(ByteBuffer rb) {
        packets.offer(rb);
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
}
