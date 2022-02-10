package roj.net.mychat;

import roj.collect.IntList;
import roj.collect.IntSet;
import roj.config.data.CList;
import roj.util.ByteList;

import java.util.List;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class Group extends User {
    public IntList userIds = new IntList();
    public List<User> users;

    @Override
    public void put(ByteList b) {
        super.put(b);
        if (userIds.isEmpty()) {
            b.put((byte) 255);
            return;
        }

        if (users != null) {
            b.put((byte) 1);
            for (int i = 0; i < users.size(); i++) {
                users.get(i).put(b);
            }
        } else {
            b.put((byte) 0);
            for (int i = 0; i < userIds.size(); i++) {
                b.putInt(userIds.get(i));
            }
        }
    }

    @Override
    public User cStore() {
        CList list = getOrCreateList("users");
        list.clear();
        if (users != null) {
            remove("raw");
            for (int i = 0; i < users.size(); i++) {
                User user = users.get(i);
                user.cStore();
                list.add(user);
            }
        } else {
            put("raw", 1);
            for (int i = 0; i < userIds.size(); i++) {
                list.add(userIds.get(i));
            }
        }
        return super.cStore();
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
