package roj.io;

import roj.io.misc.File_LLIO;
import roj.io.misc.Net_LLIO;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;
import roj.util.NativeMemory;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;

/**
 * @author Roj234
 * @since 2020/12/6 14:15
 */
public final class NIOUtil {
	private static Net_LLIO SCN, DCN;
	private static File_LLIO FCN;
	private static NUT UTIL;

	public static Net_LLIO tcpFdRW() {
		return SCN;
	}
	public static Net_LLIO udpFdRW() {
		return DCN;
	}
	public static File_LLIO fileFdRW() {
		return FCN;
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

		DirectAccessor<NUT> da = DirectAccessor.builder(NUT.class).unchecked();
		try {
			Class<?> itf = b.getClass().getInterfaces()[0];
			da.delegate_o(itf, new String[] {"attachment", "cleaner", "address"});
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}

		try {
			da.access(FileDescriptor.class, "fd", "fdVal", "fdFd")
			  .delegate(FileDescriptor.class, "closeAll", "fdClose");
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		try {
			SocketChannel sc = SocketChannel.open(); sc.close();
			da.access(sc.getClass(), new String[] {"fd","nd"}, new String[] {"tcpFD","sChNd"}, null);
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		try {
			DatagramChannel dc = DatagramChannel.open(); dc.close();
			da.access(dc.getClass(), new String[] {"nd"}, new String[] {"dChNd"}, null);
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		FileChannel fc = null;
		try {
			fc = FileChannel.open(Helpers.getJarByClass(NIOUtil.class).toPath()); fc.close();
			da.access(fc.getClass(), new String[] {"nd"}, new String[] {"fChNd"}, null);
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}

		UTIL = da.build();
		clean(b);

		String[] ss1 = new String[] {"read", "readVector", "write", "writeVector"};
		String[] ss2 = new String[] {"read0", "readv0", "write0", "writev0"};
		try {
			SCN = DirectAccessor.builder(Net_LLIO.class).delegate(UTIL.sChNd().getClass(), ss2, ss1).build();
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		try {
			DCN = DirectAccessor.builder(Net_LLIO.class).delegate(UTIL.dChNd().getClass(), ss2, ss1).build();
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		try {
			ss1 = new String[] {"read", "readVector", "readPositional", "write", "writeVector", "writePositional", "size"};
			ss2 = new String[] {"read0", "readv0", "pread0", "write0", "writev0", "pwrite0", "size0"};
			FCN = DirectAccessor.builder(File_LLIO.class).delegate(UTIL.fChNd(fc).getClass(), ss2, ss1).build();
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
		Object dChNd();
		Object fChNd(Object fch);
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
