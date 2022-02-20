package roj.net.mychat;

import roj.collect.IntMap;
import roj.collect.IntSet;
import roj.collect.RingBuffer;
import roj.io.BoxFile;
import roj.util.ByteList;

import java.util.PrimitiveIterator.OfInt;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class User extends AbstractUser {
    public String username;

    // 存储最近的一百条未接收消息
    public RingBuffer<Message> c2c = new RingBuffer<>(100);

    public IntSet friendsSet = new IntSet(), managedGroups, blocked;
    public IntMap<String> pendingAdd = new IntMap<>();
    public WSChat worker;
    public IntSet joinedGroups = new IntSet(), manageGroups;

    private BoxFile history;

    public User() {
        //try {
        //    this.history = new BoxFile(new File(user.name + ".db"));
        //} catch (IOException e) {
        //    e.printStackTrace();
        //}
    }

    public void onLogon(Context c, WSChat w) {
        if (worker != null) {
            worker.sendExternalLogout("您已在另一窗口登录");
        }
        worker = w;

        for (IntMap.Entry<String> friendNew : pendingAdd.entrySet()) {

        }
        pendingAdd.clear();

        flag |= User.F_ONLINE;
        for (OfInt itr = friendsSet.iterator(); itr.hasNext(); ) {
            int i = itr.nextInt();
            User u1 = (User) c.getUser(i);
            if (u1.worker != null) {
                u1.worker.sendOnlineState(id, flag);
            }
        }

        for (OfInt itr = joinedGroups.iterator(); itr.hasNext(); ) {
            int i = itr.nextInt();
            Group u1 = (Group) c.getUser(i);
            u1.onlineHook(this);
        }
    }

    public void onLogout(Context c, WSChat w) {
        worker = null;

        flag &= ~User.F_ONLINE;
        for (OfInt itr = friendsSet.iterator(); itr.hasNext(); ) {
            int i = itr.nextInt();
            User u1 = (User) c.getUser(i);
            if (u1.worker != null) {
                u1.worker.sendOnlineState(id, flag);
            }
        }

        for (OfInt itr = joinedGroups.iterator(); itr.hasNext(); ) {
            int i = itr.nextInt();
            Group u1 = (Group) c.getUser(i);
            u1.offlineHook(this);
        }
    }

    public void postMessage(Context c, Message m, boolean s) {
        if (m.uid == id) return;

        WSChat w = worker;
        if (w != null) {
            w.sendMessage(c.getUser(m.uid), m, s);
        } else {
            c2c.addLast(m);
        }
    }

    public static final byte F_ONLINE = 1, F_ALWAYS_OFFLINE = 2, F_NOT_SEE_ME = 4, F_NOT_SEE_HIM = 8, F_BLOCKED = 16;

    @Override
    public void put(ByteList b) {
        b.putInt(id).putJavaUTF(name);
        b.putJavaUTF("");
        b.putJavaUTF(face);
        b.putJavaUTF(desc);
        b.put(flag);
    }

    @Override
    public AbstractUser cStore() {
        put("id", id);
        put("name", name);
        put("username", "");
        put("face", face);
        put("desc", desc);
        put("online", flag);
        return this;
    }

    @Override
    public void addMoreInfo(IntSet known) {}
}
