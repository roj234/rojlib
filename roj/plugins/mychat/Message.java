package roj.plugins.mychat;

import roj.config.serial.ToSomeString;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
public class Message {
	public static final int STYLE_DEFAULT = 0, STYLE_ERROR = 1, STYLE_WARNING = 2, STYLE_SUCCESS = 3, STYLE_NONE = 4, STYLE_BAR = 8;

	public int uid;
	public String text;
	public long time;

	public Message() {}

	public Message(int uid, String text) {
		this.uid = uid;
		this.text = text;
		this.time = System.currentTimeMillis();
	}

	public void serialize(ToSomeString ser) {
		ser.valueMap();

		ser.key("uid");
		ser.value(uid);

		ser.key("text");
		ser.value(text);

		ser.key("time");
		ser.value(time);

		ser.pop();
	}
}
