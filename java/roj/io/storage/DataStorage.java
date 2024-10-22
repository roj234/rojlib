package roj.io.storage;

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
public abstract class DataStorage {
	private static final int ID_LENGTH = 32;

	static final MyBitSet VALID_ID = MyBitSet.from(Base64.B64_URL_SAFE);
	static { VALID_ID.remove(0); }
	public static boolean isValid(String id) {
		if (id.length() != ID_LENGTH) return false;
		for (int i = 0; i < ID_LENGTH; i++) {
			if (!VALID_ID.contains(id.charAt(i))) return false;
		}
		return true;
	}

	ReentrantLock idLock = new ReentrantLock();
	Random rnd = new MT19937();

	public String newId() {
		ByteList ob = IOUtil.getSharedByteBuf();

		byte[] chars = Base64.B64_URL_SAFE;

		idLock.lock();
		try {
			for (int i = 1; i <= ID_LENGTH; i++) {
				ob.put(chars[rnd.nextInt(64)]);
			}
		} finally {
			idLock.unlock();
		}

		return ob.toString();
	}

	public abstract Map<String,Object> get(String id);
	public abstract void put(String id, Map<String,Object> value);
	public abstract void remove(String id);
}
