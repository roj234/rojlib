package roj.io;

import roj.NativeLibrary;
import roj.reflect.DirectAccessor;
import roj.text.logging.Logger;
import roj.util.NativeException;
import roj.util.NativeMemory;
import roj.util.OS;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
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
			SocketChannel sc = SocketChannel.open(); sc.close();
			da.access(sc.getClass(), new String[] {"fd","nd"}, new String[] {"tcpFD","sChNd"}, null);
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "GetChannelFD");
		}

		try {
			DatagramChannel dc = DatagramChannel.open(); dc.close();
			da.access(dc.getClass(), new String[] {"fd","nd"}, new String[] {"udpFD","dChNd"}, null);
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "GetChannelFD");
		}

		try {
			ServerSocketChannel sc = ServerSocketChannel.open(); sc.close();
			da.access(sc.getClass(), "fd", "tcpsFD", null);
		} catch (Throwable e1) {
			Logger.getLogger("NIOUtil").error("无法加载模块 {}", e1, "GetServerChannelFD");
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
	public static FileDescriptor tcpFD(ServerSocketChannel ch) { return UTIL.tcpsFD(ch); }

	// on windows reuse is NOT ignored
	public static void setReusePort(DatagramChannel so, boolean on) throws IOException {
		if (OS.CURRENT != OS.WINDOWS) so.setOption(StandardSocketOptions.SO_REUSEPORT, on);
		else setReusePortW(UTIL.udpFD(so), on);
	}
	public static void setReusePort(SocketChannel so, boolean on) throws IOException {
		if (OS.CURRENT != OS.WINDOWS) so.setOption(StandardSocketOptions.SO_REUSEPORT, on);
		else setReusePortW(UTIL.tcpFD(so), on);
	}
	public static void setReusePort(ServerSocketChannel so, boolean on) throws IOException {
		if (OS.CURRENT != OS.WINDOWS) so.setOption(StandardSocketOptions.SO_REUSEPORT, on);
		else setReusePortW(UTIL.tcpsFD(so), on);
	}

	private static void setReusePortW(FileDescriptor fd, boolean enabled) throws IOException {
		if (!NativeLibrary.loaded()) throw new NativeException("native library not available");
		int error = windowsOnlyReuseAddr(UTIL.fdVal(fd), enabled);
		if (error != 0) {
			switch (error) {
				case 10036: throw new IOException("WSAEINPROGRESS");
				case 10038: throw new IOException("WSAENOTSOCK");
				case 10042: throw new IOException("WSAENOPROTOOPT");
				case 10050: throw new IOException("WSAENETDOWN");
				default: throw new IOException("native setsockopt returns "+error);
			}
		}
	}
	private static native int windowsOnlyReuseAddr(int fd, boolean enable);

	public interface NUT {
		void fdFd(FileDescriptor fd, int fdVal);
		int fdVal(FileDescriptor fd);
		void fdClose(FileDescriptor fd, Closeable releaser) throws IOException;

		Object sChNd();
		Object dChNd();
		FileDescriptor udpFD(DatagramChannel ch);
		FileDescriptor tcpFD(SocketChannel ch);
		FileDescriptor tcpsFD(ServerSocketChannel ch);

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