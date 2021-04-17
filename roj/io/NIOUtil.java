package roj.io;

import roj.io.misc.FDRW;
import roj.reflect.DirectAccessor;
import roj.util.NativeMemory;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author Roj234
 * @since 2020/12/6 14:15
 */
public final class NIOUtil {
	private static FDRW SCN;
	private static NUT UTIL;

	public static FDRW tcpFdRW() {
		return SCN;
	}

	private static Throwable e;

	static {
		try {
			__init();
		} catch (IOException e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		if (e != null) e.printStackTrace();
	}

	private static void __init() throws IOException {
		ByteBuffer b = ByteBuffer.allocateDirect(1);
		Class<?> itf = b.getClass().getInterfaces()[0];

		SocketChannel sc = SocketChannel.open();
		sc.close();

		DirectAccessor<NUT> da = DirectAccessor.builder(NUT.class).unchecked();
		try {
			da.delegate_o(itf, new String[] {"attachment", "cleaner", "address"});
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}

		try {
			da.access(FileDescriptor.class, "fd", "fdVal", "fdFd")
			  .delegate(FileDescriptor.class, "closeAll", "fdClose");
			da.access(sc.getClass(), new String[] {"nd","fd"}, new String[] {"sChNd","tcpFD"}, null);
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		UTIL = da.build();
		clean(b);

		try {
			String[] ss1 = new String[] {"read", "readv", "write", "writev"};
			String[] ss2 = new String[] {"read0", "readv0", "write0", "writev0"};
			SCN = DirectAccessor.builder(FDRW.class).delegate(UTIL.sChNd().getClass(), ss2, ss1).build();
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
	}

	public static boolean available() {
		return e == null;
	}

	public static final int UNAVAILABLE = -2;

	public static FileDescriptor tcpFD(SocketChannel ch) {
		return UTIL.tcpFD(ch);
	}

	public interface NUT {
		void fdFd(FileDescriptor fd, int fd2);
		int fdVal(FileDescriptor fd);
		void fdClose(FileDescriptor fd, Closeable releaser) throws IOException;

		Object sChNd();
		FileDescriptor tcpFD(SocketChannel ch);

		long address(Object buf);
		Object attachment(Object buf);
		Object cleaner(Object buf);
	}

	private static Object topMost(Object o) {
		while (UTIL.attachment(o) != null) o = UTIL.attachment(o);
		return o;
	}

	public static void clean(Buffer shared) {
		if (!shared.isDirect()) return;

		if (UTIL == null) {
			e.printStackTrace();
			return;
		}

		// Java做了多次运行的处理，无须担心
		Object cleaner = UTIL.cleaner(topMost(shared));
		if (cleaner != null) NativeMemory.cleanNativeMemory(cleaner);
	}
}
