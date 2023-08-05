package roj.plugins.mychat;

import roj.config.serial.ToSomeString;

/**
 * @author Roj233
 * @since 2022/3/11 21:04
 */
public class SpaceEntry extends Message {
	public int id;
	public byte perm;

	public SpaceEntry() {}

	public void serialize(ToSomeString ser) {
		ser.valueList();

		ser.value(id);
		ser.value(uid);
		ser.value(time);
		ser.value(text);
		ser.value("10万+");
		ser.value("2.1万");
		ser.value(1);

		ser.pop();
	}
}
