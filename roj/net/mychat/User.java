package roj.net.mychat;

import roj.collect.IntSet;
import roj.config.data.CMapping;
import roj.util.ByteList;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class User extends CMapping {
    public int id;
    public String name, username, face, desc;
    public byte flag;

    public static final byte F_ONLINE = 1, F_ALWAYS_OFFLINE = 2, F_NOT_SEE_ME = 4, F_NOT_SEE_HIM = 8, F_BLOCKED = 16;

    public void put(ByteList b) {
        b.putInt(id).putJavaUTF(name);
        b.putJavaUTF(username);
        b.putJavaUTF(face);
        b.putJavaUTF(desc);
        if (getClass() == User.class) b.put(flag);
    }

    public User cStore() {
        put("id", id);
        put("name", name);
        put("username", username);
        put("face", face);
        put("desc", desc);
        if (getClass() == User.class) put("online", flag);
        return this;
    }

    public void addMoreInfo(IntSet known) {}
}
