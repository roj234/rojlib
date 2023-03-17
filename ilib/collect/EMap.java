package ilib.collect;

import java.util.HashMap;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/21 22:51
 */
public class EMap extends HashMap<String, Object> {
	public static final long serialVersionUID = 23472305L;

	public EMap() {
	}

	//@SafeVarargs
	public EMap(String key, Object value) {
		put(key, value);
	}

	public EMap(String[] keys, Object[] values) {
		for (int i = 0; i < keys.length; i++) {
			put(keys[i], values[i]);
		}
	}
}
