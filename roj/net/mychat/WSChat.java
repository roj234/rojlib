package roj.net.mychat;

import roj.collect.IntSet;
import roj.concurrent.PacketBuffer;
import roj.config.JSONParser;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.net.http.WebSockets;
import roj.text.CharList;
import roj.text.UTFCoder;
import roj.util.ByteList;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import static roj.net.http.WebSockets.FRAME_BINARY;
import static roj.net.http.WebSockets.FRAME_TEXT;

/**
 * @author Roj234
 * @since 2022/2/7 18:48
 */
public abstract class WSChat extends WebSockets.Worker {
    public static final int P_USER_STATE = 1, P_USER_INFO = 2, P_MESSAGE = 3,
                            P_SYS_MESSAGE = 4, P_DATA_CHANGED = 5, P_ROOM_STATE = 6,
                            P_SPACE_NEW = 7, P_GET_HISTORY = 8, P_REMOVE_HISTORY = 9,
                            P_COLD_HISTORY = 14, P_NEW_FRIEND = 15;
    public static final int P_HEARTBEAT = 10, P_LOGOUT = 11, P_LOGIN = 12, P_ALERT = 13,
                            P_JSON_PAYLOAD = 255;

    static final int USE_BINARY = 32, FIRST_HEART = 64;

    private JSONParser.JSONLexer jl;

    protected final IntSet known = new IntSet();
    protected final PacketBuffer pb = new PacketBuffer(true, 0);
    protected boolean shutdownInProgress;

    @Override
    public final void tick() throws IOException {
        super.tick();
        if (!hasDataPending() && pb.hasMore()) {
            ByteList b = WebSockets.getUTFCoder().byteBuf;
            b.ensureCapacity(256);

            ByteBuffer suchLarge = ByteBuffer.wrap(b.list);
            while (!hasDataPending() && pb.hasMore()) {
                suchLarge.clear();
                if (!pb.take(suchLarge)) {
                    b.setArray(pb.poll());
                    suchLarge = ByteBuffer.wrap(b.list);
                } else {
                    suchLarge.flip();
                }

                send((flag & USE_BINARY) != 0 ? FRAME_BINARY : FRAME_TEXT, suchLarge);
            }
        } else if (shutdownInProgress) error(ERR_OK, null);
    }

    public WSChat() {
        maxData = 262144;
    }

    @Override
    protected final void onData(int ph, ByteBuffer in) throws IOException {
        if (ph == FRAME_TEXT) {
            jsonPacket(in);
        } else {
            flag |= USE_BINARY;
            binaryPacket(in);
        }
    }

    private void binaryPacket(ByteBuffer in) throws IOException {
        switch (in.get() & 0xFF) {
            case P_LOGOUT:
                error(ERR_OK, null);
                break;
            case P_HEARTBEAT:
                in.rewind();
                send(FRAME_BINARY, in);
                if ((flag & FIRST_HEART) == 0) {
                    init();
                    flag |= FIRST_HEART;
                }
                break;
            case P_USER_INFO:
                while (in.hasRemaining()) requestUserInfo(in.getInt());
                break;
            case P_MESSAGE:
                message(in.getInt(), decodeToUTF(in));
                break;
            case P_REMOVE_HISTORY:
                requestClearHistory(in.getInt(), in.getInt());
                break;
            case P_GET_HISTORY:
                int id = in.getInt();
                int off = in.getInt();
                int len = in.getInt();
                requestHistory(id, decodeToUTF(in), off, len);
                break;
            case P_COLD_HISTORY:
                requestColdHistory(in.getInt());
                break;
            case P_NEW_FRIEND:
                addFriendResult(in.getInt(), in.get());
                break;
            case P_JSON_PAYLOAD:
                jsonPacket(in);
                break;
            default:
                error(ERR_INVALID_DATA, "无效包类别 " + in.get(0));
                break;
        }
    }

    private void jsonPacket(ByteBuffer in) throws IOException {
        try {
            CharList s = decodeToUTF(in);
            if (jl == null) jl = new JSONParser.JSONLexer();
            CMapping map = JSONParser.parse(jl.init(s), JSONParser.LITERAL_KEY | JSONParser.NO_DUPLICATE_KEY).asMap();
            switch (map.getInteger("act")) {
                case P_LOGOUT:
                    error(ERR_OK, null);
                    break;
                case P_HEARTBEAT:
                    in.rewind();
                    send(FRAME_TEXT, in);
                    if ((flag & FIRST_HEART) == 0) {
                        init();
                        flag |= FIRST_HEART;
                    }
                    break;
                case P_USER_INFO:
                    CList id = map.getOrCreateList("id");
                    for (int i = 0; i < id.size(); i++) {
                        requestUserInfo(id.get(i).asInteger());
                    }
                    break;
                case P_MESSAGE:
                    message(map.getInteger("to"), map.getString("msg"));
                    break;
                case P_REMOVE_HISTORY:
                    requestClearHistory(map.getInteger("id"), map.getInteger("time"));
                    break;
                case P_GET_HISTORY:
                    requestHistory(map.getInteger("id"), map.getString("filter"),
                                   map.getInteger("off"), map.getInteger("len"));
                    break;
                case P_COLD_HISTORY:
                    requestColdHistory(map.getInteger("id"));
                    break;
                case P_NEW_FRIEND:
                    addFriendResult(map.getInteger("id"), map.getInteger("result"));
                    break;
                default:
                    error(ERR_INVALID_DATA, "无效包类别 " + map.getInteger("act"));
            }
        } catch (Throwable e) {
            e.printStackTrace();
            error(ERR_INVALID_DATA, "JSON解析失败");
        }
    }

    protected void init() {}
    @Override
    protected void onClosed() {}

    protected abstract void message(int to, CharSequence msg);
    protected abstract void requestUserInfo(int id);
    protected void addFriendResult(int id, int result) {}

    protected void requestHistory(int id, CharSequence filter, int off, int len) {}
    protected void requestClearHistory(int id, int timeout) {}
    protected void requestColdHistory(int id) {}

    public final void sendUserInfo(User user) {
        known.add(user.id);
        user.addMoreInfo(known);

        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            user.put(b.put((byte) P_USER_INFO));
        } else {
            CMapping map = new CMapping();
            map.put("act", P_USER_INFO);
            user.cStore();
            map.merge(user, true, false);
            b = asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendGroupChange(int groupId, User user, boolean add) {
        if (add) known.add(user.id);

        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            b.put((byte) P_ROOM_STATE).putInt(groupId);
            if (add) {
                user.put(b);
            } else {
                b.putInt(user.getInteger("id"));
            }
        } else {
            CMapping map = new CMapping();
            map.put("act", P_ROOM_STATE);
            map.put("id", groupId);
            if (add) {
                user.cStore();
                map.put("user", user);
            } else {
                map.put("uid", user.getInteger("id"));
            }
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendDataChanged(int userId) {
        if (!known.remove(userId)) return;

        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            b.put((byte) P_DATA_CHANGED).putInt(userId);
        } else {
            CMapping map = new CMapping();
            map.put("act", P_DATA_CHANGED);
            map.put("id", userId);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendOnlineState(int userId, boolean online) {
        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            b.put((byte) P_USER_STATE).putInt(userId).putBool(online);
        } else {
            CMapping map = new CMapping();
            map.put("act", P_USER_STATE);
            map.put("id", userId);
            map.put("on", online);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendExternalLogout(String reason) {
        sendUTF(reason, P_LOGOUT);
        shutdownInProgress = true;
    }
    public final void sendAlert(String reason) {
        sendUTF(reason, P_ALERT);
    }
    private void sendUTF(String utf, int id) {
        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            ByteList.writeUTF(b.put((byte) id), utf, -1);
        } else {
            CMapping map = new CMapping();
            map.put("act", id);
            map.put("desc", utf);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendMessage(User user, Message message, boolean sys) {
        if (message.text.length() > maxData - 10) throw new IllegalArgumentException("Message too long");
        int userId = user.id;

        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            ByteList.writeUTF(b.put((byte) (sys ? P_SYS_MESSAGE : P_MESSAGE))
                               .putInt(userId).putInt(message.uid).putLong(message.time), message.text, -1);
        } else {
            CMapping map = new CMapping();
            map.put("act", sys ? P_SYS_MESSAGE : P_MESSAGE);
            map.put("id", userId);
            if (userId != message.uid && !sys) {
                map.put("uid", message.uid);
            }
            CMapping subMap = map.getOrCreateMap("msg");
            subMap.put("time", message.time);
            subMap.put("text", message.text);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));

        if (known.add(userId)) sendUserInfo(user);
    }
    public final void sendNotLogin() {
        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            b.put((byte) P_LOGIN);
        } else {
            CMapping map = new CMapping();
            map.put("act", P_LOGIN);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendSpaceChanged() {
        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            b.put((byte) P_SPACE_NEW);
        } else {
            CMapping map = new CMapping();
            map.put("act", P_SPACE_NEW);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendHistory(int userId, int total, List<Message> msgs) {
        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            b.put((byte) P_GET_HISTORY).putInt(userId).putInt(total).putInt(msgs.size());
            for (int i = 0; i < msgs.size(); i++) {
                Message msg = msgs.get(i);
                b.putLong(msg.time).putInt(msg.uid).putJavaUTF(msg.text);
            }
        } else {
            CMapping map = new CMapping();
            map.put("act", P_GET_HISTORY);
            map.put("id", userId);
            map.put("total", total);
            CList data = new CList(msgs.size());
            for (int i = 0; i < msgs.size(); i++) {
                Message msg = msgs.get(i);
                CMapping subMap = new CMapping(2);
                data.add(subMap);
                subMap.put("time", msg.time);
                subMap.put("text", msg.text);
                subMap.put("uid", msg.uid);
            }
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
    public final void sendNewFriend(int userId, String text) {
        UTFCoder asyncUC = WebSockets.getUTFCoder();
        ByteList b = asyncUC.byteBuf;
        if ((flag & USE_BINARY) != 0) {
            b.clear();
            ByteList.writeUTF(b.put((byte) P_NEW_FRIEND), text, -1);
            b.putInt(userId);
        } else {
            CMapping map = new CMapping();
            map.put("act", P_NEW_FRIEND);
            map.put("id", userId);
            map.put("desc", text);
            asyncUC.encodeR(map.toShortJSONb());
        }
        pb.offer(ByteBuffer.wrap(b.list, 0, b.wIndex()));
    }
}