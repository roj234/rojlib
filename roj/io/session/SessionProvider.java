package roj.io.session;

import roj.collect.MyBitSet;
import roj.crypt.Base64;
import roj.crypt.MT19937;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Roj234
 * @since 2023/5/15 0015 14:12
 */
public abstract class SessionProvider {
	private static final int SESSION_LENGTH = 32;
	private static volatile SessionProvider DEFAULT;

	public static SessionProvider getDefault() { return DEFAULT; }
	public static void setDefault(SessionProvider s) { DEFAULT = s; }

	static final MyBitSet VALID_ID = MyBitSet.from(Base64.B64_URL_SAFE);
	static { VALID_ID.remove(0); }
	public static boolean isValid(String id) {
		if (id.length() != SESSION_LENGTH) return false;
		for (int i = 0; i < SESSION_LENGTH; i++) {
			if (!VALID_ID.contains(id.charAt(i))) return false;
		}
		return true;
	}

	ReentrantLock createLock = new ReentrantLock();
	Random rnd = new MT19937();

	public String createSession() {
		ByteList ob = IOUtil.getSharedByteBuf();

		byte[] chars = Base64.B64_URL_SAFE;
		long time = System.nanoTime();

		createLock.lock();
		try {
			for (int i = 1; i <= SESSION_LENGTH; i++) {
				ob.put(chars[rnd.nextInt(64)]);
			}
		} finally {
			createLock.unlock();
		}

		return ob.toString();
	}

	public abstract Map<String,Object> loadSession(String id);
	public abstract void saveSession(String id, Map<String,Object> value);
	public abstract void destroySession(String id);
}
