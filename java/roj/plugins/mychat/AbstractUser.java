package roj.plugins.mychat;

import roj.collect.IntSet;
import roj.config.serial.CRawString;
import roj.config.serial.ToJson;
import roj.config.serial.ToSomeString;
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

	public abstract void put(ToSomeString ser);

	public abstract void addMoreInfo(IntSet known);

	public abstract void postMessage(Context c, Message m, boolean sys);

	public final CRawString put() {
		ToJson ser = new ToJson();
		ser.valueMap();
		put(ser);
		ser.pop();
		return new CRawString(ser.getValue());
	}
}