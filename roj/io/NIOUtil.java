package roj.io;

import roj.NativeLibrary;
import roj.reflect.DirectAccessor;
import roj.text.logging.Logger;
import roj.util.NativeMemory;
import roj.util.OS;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketImpl;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * @author Roj234
 * @since 2020/12/6 14:15
 */
public final class NIOUtil {
	private static LLIO SCN;
	private static final NUT UTIL;

	public static LLIO tcpFdRW() { return SCN; }

	static {
		ByteBuffer b = ByteBuffer.allocateDirect(1);

		DirectAccessor<NUT> da = DirectAccessor.builder(NUT.class).unchecked();
		try {
			Class<?> itf = b.getClass().getInterfaces()[0];
			da.delegate_o(itf, "attachment", "cleaner", "address");
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").warn("无法加载模块 {}", e1, "BufferCleaner");
		}

		try {
			da.access(FileDescriptor.class, "fd", "fdVal", "fdFd")
			  .delegate(FileDescriptor.class, "closeAll", "fdClose");
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "GetFdVal");
		}

		try {
			da.access(Socket.class, "impl", "getImplSocket", null)
			  .access(ServerSocket.class, "impl", "getImplServerSocket", null)
			  .access(SocketImpl.class, "fd", "getSocketFd", null);
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "GetSocketFD");
		}

		try {
			SocketChannel sc = SocketChannel.open(); sc.close();
			da.access(sc.getClass(), new String[] {"fd","nd"}, new String[] {"tcpFD","sChNd"}, null);
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "GetChannelFD");
		}

		UTIL = da.build();
		clean(b);

		String[] ss1 = new String[] {"read", "readVector", "write", "writeVector"};
		String[] ss2 = new String[] {"read0", "readv0", "write0", "writev0"};
		try {
			SCN = DirectAccessor.builder(LLIO.class).delegate(UTIL.sChNd().getClass(), ss2, ss1).build();
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "TCP_LLIO");
		}
	}

	public static boolean available() { return SCN != null; }

	public static final int UNAVAILABLE = -2;

	public static FileDescriptor tcpFD(SocketChannel ch) { return UTIL.tcpFD(ch); }
	public static FileDescriptor socketFD(Socket so) { return UTIL.getSocketFd(UTIL.getImplSocket(so)); }
	public static FileDescriptor socketFD(ServerSocket so) { return UTIL.getSocketFd(UTIL.getImplServerSocket(so)); }

	// on windows reuse is NOT ignored
	public static int windowsSetReusePort(FileDescriptor fd) throws IOException {
		if (OS.CURRENT != OS.WINDOWS || !NativeLibrary.loaded()) throw new FastFailException("windows native library not loaded");
		return windowsOnlyReuseAddr(UTIL.fdVal(fd), true);
	}
	private static native int windowsOnlyReuseAddr(int fd, boolean enable) throws IOException;

	public interface NUT {
		void fdFd(FileDescriptor fd, int fdVal);
		int fdVal(FileDescriptor fd);
		void fdClose(FileDescriptor fd, Closeable releaser) throws IOException;

		SocketImpl getImplSocket(Socket o);
		SocketImpl getImplServerSocket(ServerSocket o);
		FileDescriptor getSocketFd(SocketImpl o);

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

		// Java做了多次运行的处理，无须担心
		Object cleaner = UTIL.cleaner(topMost(shared));
		if (cleaner != null) NativeMemory.cleanNativeMemory(cleaner);
	}

	public interface LLIO {
		int read(FileDescriptor fd, long _Address, int len) throws IOException;
		int write(FileDescriptor fd, long _Address, int len) throws IOException;

		long readVector(FileDescriptor fd, long _IoVec_Length, int len) throws IOException;
		long writeVector(FileDescriptor fd, long _IoVec_Length, int len) throws IOException;

		void close(FileDescriptor fd) throws IOException;
	}
}