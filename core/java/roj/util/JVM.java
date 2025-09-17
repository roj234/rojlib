package roj.util;

import roj.concurrent.FastThreadLocal;
import roj.io.IOUtil;
import roj.optimizer.FastVarHandle;
import roj.reflect.Handles;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Roj234
 * @since 2023/12/26 9:09
 */
public final class JVM {
	public static final int VERSION;
	public static final boolean BIG_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.BIG_ENDIAN;
	static {
		String v = System.getProperty("java.specification.version");
		// 事实上我们只关心它是否大于8，所以其实不是那么必要/doge
		if (v.startsWith("1.")) v = v.substring(2);
		VERSION = Integer.parseInt(v);
	}

	@FastVarHandle
	private static final class SIP {
		private static final VarHandle HOOKS = Handles.lookup().findStaticVarHandle(Handles.findClass("java.lang.ApplicationShutdownHooks"), "hooks", IdentityHashMap.class);
		private static boolean isShutdownInProgress() {return HOOKS.get() != null;}
	}
	public static boolean isShutdownInProgress() {return SIP.isShutdownInProgress();}

	private static Boolean isRoot;
	public static synchronized boolean isRoot() {
		if (isRoot == null) {
			try {
				if (OS.CURRENT == OS.WINDOWS) {
					int exitCode = new ProcessBuilder("REG","QUERY","HKU\\S-1-5-19").start().waitFor();
					isRoot = exitCode == 0;
				} else {
					var id = new ProcessBuilder("id").start();
					ByteList buf = IOUtil.getSharedByteBuf().readStreamFully(id.getInputStream());
					isRoot = buf.readAscii(buf.readableBytes()).contains("0(root)");
				}
			} catch (Exception e) {
				isRoot = false;
			}
		}
		return isRoot;
	}

	public static long usableMemory() { Runtime r = Runtime.getRuntime(); return r.maxMemory() - r.totalMemory() + r.freeMemory(); }

	/**
	 * 使Thread.sleep, LockSupport.parkNanos等定时器的精度能达到~1ms
	 */
	public static void useAccurateTiming() {
		if (!AccurateTimer.started) {
			AccurateTimer.started = true;
			new AccurateTimer().start();
		}
	}
	public static final class AccurateTimer extends Thread {
		private AccurateTimer() {setName("睡美人");setDaemon(true);}

		private static boolean started;

		public static void parkForMe() {
			FastThreadLocal.clear();
			started = true;
			currentThread().setName("睡美人");
			for(;;) LockSupport.parkNanos(Long.MAX_VALUE);
		}

		@Override public void run() {
			for(;;) LockSupport.parkNanos(Long.MAX_VALUE);
		}
	}
}