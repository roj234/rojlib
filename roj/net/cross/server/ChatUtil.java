package roj.net.cross.server;

/**
 * @author Roj233
 * @since 2022/1/24 3:12
 */
public class ChatUtil {
	public static final int RECENT_MESSAGE_COUNT;

	static {
		int c;
		try {
			c = Integer.parseInt(System.getProperty("AE.chat.msgCount", "1000"));
			if (c > 9999 || c < 0) c = 1000;
		} catch (Exception e) {
			c = 1000;
		}
		RECENT_MESSAGE_COUNT = c;
	}
}
