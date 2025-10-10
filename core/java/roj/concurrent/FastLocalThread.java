package roj.concurrent;

import roj.util.ArrayCache;

/**
 * @author Roj233
 * @since 2021/9/13 12:49
 */
public class FastLocalThread extends Thread {
	public FastLocalThread() {super();}
	public FastLocalThread(Runnable runnable) {super(runnable);}
	public FastLocalThread(Runnable runnable, String name) {super(runnable, name);}
	Object[] localDataArray = ArrayCache.OBJECTS;
	final Object arrayLock = new Object();
}