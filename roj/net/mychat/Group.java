package roj.net.mychat;

import roj.collect.*;
import roj.concurrent.SimpleSpinLock;
import roj.config.data.CList;
import roj.util.ByteList;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class Group extends AbstractUser {
    static final boolean initialSendUserInfo = false;

    public List<User> users = new SimpleList<>();

    public final RingBuffer<Message> history = new RingBuffer<>(100);
    public final MyHashSet<User> onlineUsers = new MyHashSet<>();
    public final Int2IntMap userFlags = new Int2IntMap();
    final SimpleSpinLock lock = new SimpleSpinLock();

    public int owner;
    public final IntSet operators = new IntSet();

    public void addUser(User b) {
        users.add(b);
    }

    public void postMessage(Context c, Message m, boolean sys) {
        history.addLast(m);
        lock.enqueueReadLock();
        try {
            for (User entry : onlineUsers) {
                WSChat w = entry.worker;
                if (w == null || entry.id == m.uid) continue;
                w.sendMessage(this, m, sys);
            }
        } finally {
            lock.releaseReadLock();
        }
    }

    public void onlineHook(User entry) {
        lock.enqueueWriteLock();
        try {
            onlineUsers.add(entry);
        } finally {
            lock.releaseWriteLock();
        }
    }

    public void offlineHook(User entry) {
        lock.enqueueWriteLock();
        try {
            onlineUsers.remove(entry);
        } finally {
            lock.releaseWriteLock();
        }
    }

    @Override
    public void put(ByteList b) {
        b.putInt(id).putJavaUTF(name);
        b.putJavaUTF("");
        b.putJavaUTF(face);
        b.putJavaUTF(desc);

        if (users.isEmpty()) {
            b.put((byte) 255);
            return;
        }

        if (initialSendUserInfo) {
            b.put((byte) 1);
            for (int i = 0; i < users.size(); i++) {
                users.get(i).put(b);
            }
        } else {
            b.put((byte) 0);
            for (int i = 0; i < users.size(); i++) {
                b.putInt(users.get(i).id);
            }
        }
    }

    @Override
    public AbstractUser cStore() {
        CList list = getOrCreateList("users");
        list.clear();
        if (initialSendUserInfo) {
            remove("raw");
            for (int i = 0; i < users.size(); i++) {
                AbstractUser user = users.get(i);
                user.cStore();
                list.add(user);
            }
        } else {
            put("raw", 1);
            for (int i = 0; i < users.size(); i++) {
                list.add(users.get(i).id);
            }
        }
        put("id", id);
        put("name", name);
        // todo
        put("username", "");
        put("face", face);
        put("desc", desc);
        return this;
    }

    @Override
    public void addMoreInfo(IntSet known) {
        if (users != null) {
            for (int i = 0; i < users.size(); i++) {
                known.add(users.get(i).id);
            }
        }
    }
}
