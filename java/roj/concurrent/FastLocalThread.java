package roj.concurrent;

import roj.util.ArrayCache;

/**
 * @author Roj233
 * @since 2021/9/13 12:49
 */
public class FastLocalThread extends Thread {
	public FastLocalThread() { super(); }
	@Deprecated
	public FastLocalThread(Runnable r) { super(r); }

	Object[] localDataArray = ArrayCache.OBJECTS;
	final Object arrayLock = new Object();
}
