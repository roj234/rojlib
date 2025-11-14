package roj.util;

import roj.io.IOUtil;
import roj.optimizer.FastVarHandle;
import roj.reflect.Telescope;

import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;

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
		private static final VarHandle HOOKS$STATIC = Telescope.trustedLookup().findStaticVarHandle(Telescope.findClass("java.lang.ApplicationShutdownHooks"), "hooks", IdentityHashMap.class);
		private static boolean isShutdownInProgress() {return HOOKS$STATIC.get() == null;}
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
			new AccurateTimer(true).start();
		}
	}
	public static final class AccurateTimer extends Thread {
		private AccurateTimer(boolean daemon) {setName("RojLib 睡美人");setDaemon(daemon);}

		private static boolean started;

		public static void setEventDriven() {
			if (started) {
				System.err.println("AccurateTimer already started");
			} else {
				started = true;
			}
			new AccurateTimer(false).start();
		}

		@Override public void run() {
			try {
				for(;;) Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException ignored) {}
		}
	}
}