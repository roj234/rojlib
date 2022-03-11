package roj.net.mychat;

import roj.collect.IntSet;
import roj.config.data.CUnescapedString;
import roj.config.serial.StreamSerializer;
import roj.config.serial.ToJson;
import roj.util.ByteList;

/**
 * @author Roj233
 * @since 2022/3/9 13:31
 */
public abstract class AbstractUser {
    public int id;
    public String name, face, desc;
    public byte flag;

    public abstract void put(ByteList b);

    public abstract void put(StreamSerializer ser);

    public abstract void addMoreInfo(IntSet known);

    public abstract void postMessage(Context c, Message m, boolean sys);

    public final CUnescapedString put() {
        ToJson ser = new ToJson();
        ser.valueMap();
        put(ser);
        ser.pop();
        return new CUnescapedString(ser.getValue());
    }
}
