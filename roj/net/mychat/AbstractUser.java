package roj.net.mychat;

import roj.collect.IntSet;
import roj.config.data.CMapping;
import roj.util.ByteList;

/**
 * @author Roj233
 * @since 2022/3/9 13:31
 */
public abstract class AbstractUser extends CMapping {
    public int id;
    public String name, face, desc;
    public byte flag;

    public abstract void put(ByteList b);

    public abstract AbstractUser cStore();

    public abstract void addMoreInfo(IntSet known);

    public abstract void postMessage(Context c, Message m, boolean sys);
}
