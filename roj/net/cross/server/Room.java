package roj.net.cross.server;

import roj.collect.IntMap;
import roj.collect.LongBitSet;
import roj.collect.RingBuffer;
import roj.config.data.CMapping;
import roj.net.misc.Pipe;
import roj.net.misc.Waiters;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj233
 * @since 2022/1/24 3:18
 */
public final class Room {
    Client master;
    String id, token;

    String motdString;
    byte[] motd, portMap;

    // 房主的直连地址
    byte[] upnpAddress;

    final IntMap<Client> clients = new IntMap<>();
    int index;

    // 配置项
    public boolean locked;
    Waiters resetLock;

    // 统计数据
    public final long creation;

    // 聊天相关
    // 启用聊天功能的
    final LongBitSet          chatEnabled;
    // 禁言的
    final LongBitSet          chatDisabled;
    // 最近的消息
    final RingBuffer<Message> recentMessage;

    static final class Message {
        final Client from;
        final String msg;

        Message(Client from, String msg) {
            this.from = from;
            this.msg = msg;
        }
    }

    public Room(String id, Client owner, String token) {
        this.master = owner;
        this.id = id;
        this.token = token;
        this.clients.put(0, owner);
        this.index = 1;
        this.creation = System.currentTimeMillis() / 1000;
        this.resetLock = new Waiters();
        owner.room = this;

        this.chatEnabled = new LongBitSet();
        this.chatDisabled = new LongBitSet();
        this.recentMessage = new RingBuffer<>(ChatUtil.RECENT_MESSAGE_COUNT);
    }

    public void close() {
        token = null;
        synchronized (clients) {
            clients.clear();
        }
    }

    public void kick(int id) {
        synchronized (clients) {
            clients.remove(id);
        }
    }

    public CMapping serialize() {
        long up = 0, down = 0;
        if (!master.pipes.isEmpty()) {
            synchronized (master.pipes) {
                for (PipeGroup group : master.pipes.values()) {
                    Pipe ref = group.pairRef;
                    if (ref != null) {
                        up += ref.downloaded;
                        down += ref.uploaded;
                    }
                }
            }
        }

        CMapping json = new CMapping();
        json.put("id", id);
        json.put("pass", token);
        json.put("time", creation);
        json.put("up", up);
        json.put("down", down);
        json.put("users", clients.size());
        json.put("index", index);
        json.put("motd", motdString);
        json.put("master", master == null ? "" : master.ch.socket().getRemoteSocketAddress().toString());
        return json;
    }

    public void hostInit(Client w, byte[] motd, byte[] port) {
        this.motd = motd;
        motdString = new String(motd, StandardCharsets.UTF_8);
        this.portMap = port;
    }

    public void globalChat(int i, ByteBuffer rb) {

    }
}
