package roj.plugins.mychat;

/**
 * @author Roj234
 * @since 2022/2/7 19:56
 */
final class Message {
	public static final int STYLE_DEFAULT = 0, STYLE_ERROR = 1, STYLE_WARNING = 2, STYLE_SUCCESS = 3, STYLE_NONE = 4, STYLE_BAR = 8;

	// uid or style
	public int uid;
	public String text;
	public long time;

	public Message() {}
	public Message(int uid, String text) {
		this.uid = uid;
		this.text = text;
		this.time = System.currentTimeMillis();
	}
}