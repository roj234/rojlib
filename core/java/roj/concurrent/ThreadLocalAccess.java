package roj.concurrent;

import roj.ci.annotation.Public;
import roj.reflect.Bypass;
import roj.reflect.Handles;

/**
 * @author Roj234
 * @since 2025/09/19 20:22
 */
@Public
interface ThreadLocalAccess {
	ThreadLocalAccess INSTANCE = Bypass.builder(ThreadLocalAccess.class)
			.delegate_o(ThreadLocal.class, "getMap")
			.delegate_o(Handles.findClass("java.lang.ThreadLocal$ThreadLocalMap"), new String[]{"remove", "getEntry"})
			.access(Handles.findClass("java.lang.ThreadLocal$ThreadLocalMap$Entry"), "value", "getValue", null)
			.build();

	Object getMap(Object threadLocal, Thread thread);
	Object getEntry(Object threadLocalMap, Object threadLocal);
	Object getValue(Object threadLocalMapEntry);
	void remove(Object threadLocalMap, Object threadLocal);
}
