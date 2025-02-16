package roj.io;

import roj.RojLib;
import roj.net.SelectorLoop;
import roj.reflect.Bypass;
import roj.reflect.ReflectionUtils;
import roj.util.NativeException;
import roj.util.OS;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.FileSystems;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Consumer;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2020/12/6 14:15
 */
public final class NIOUtil {
	private static LLIO SCN;
	private static final NUT UTIL;

	@Deprecated
	public static LLIO tcpFdRW() { return SCN; }

	static {
		Bypass<NUT> da = Bypass.builder(NUT.class).inline().unchecked();

		try {
			da.access(FileDescriptor.class, "fd", "fdVal", "fdFd")
			  .delegate(FileDescriptor.class, "closeAll", "fdClose");
		} catch (Throwable e1) {
			SelectorLoop.LOGGER.error("无法加载模块 {}", e1, "GetFdVal");
		}

		try {
			SocketChannel sc = SocketChannel.open(); sc.close();
			da.access(sc.getClass(), new String[] {"fd","nd"}, new String[] {"tcpFD","sChNd"}, null);
		} catch (Throwable e1) {
			SelectorLoop.LOGGER.error("无法加载模块 {}", e1, "GetChannelFD");
		}

		try {
			DatagramChannel dc = DatagramChannel.open(); dc.close();
			da.access(dc.getClass(), new String[] {"fd","nd"}, new String[] {"udpFD","dChNd"}, null);
		} catch (Throwable e1) {
			SelectorLoop.LOGGER.error("无法加载模块 {}", e1, "GetChannelFD");
		}

		try {
			ServerSocketChannel sc = ServerSocketChannel.open(); sc.close();
			da.access(sc.getClass(), "fd", "tcpsFD", null);
		} catch (Throwable e1) {
			SelectorLoop.LOGGER.error("无法加载模块 {}", e1, "GetServerChannelFD");
		}

		UTIL = da.build();

		String[] ss1 = new String[] {"read", "readVector", "write", "writeVector"};
		String[] ss2 = new String[] {"read0", "readv0", "write0", "writev0"};
		try {
			SCN = Bypass.builder(LLIO.class).inline().delegate(UTIL.sChNd().getClass(), ss2, ss1).build();
		} catch (Throwable e1) {
			SelectorLoop.LOGGER.error("无法加载模块 {}", e1, "TCP_LLIO");
		}
	}

	public static boolean available() { return SCN != null; }

	public static final int UNAVAILABLE = -2;

	public static FileDescriptor tcpFD(SocketChannel ch) {return UTIL.tcpFD(ch);}
	public static int fdVal(FileDescriptor fd) {return UTIL.fdVal(fd);}

	// on windows reuse is NOT ignored
	public static void setReusePort(DatagramChannel so, boolean on) throws IOException {
		if (OS.CURRENT != OS.WINDOWS) so.setOption(StandardSocketOptions.SO_REUSEPORT, on);
		else SetSockOpt(UTIL.udpFD(so), on);
	}
	public static void setReusePort(SocketChannel so, boolean on) throws IOException {
		if (OS.CURRENT != OS.WINDOWS) so.setOption(StandardSocketOptions.SO_REUSEPORT, on);
		else SetSockOpt(UTIL.tcpFD(so), on);
	}
	public static void setReusePort(ServerSocketChannel so, boolean on) throws IOException {
		if (OS.CURRENT != OS.WINDOWS) so.setOption(StandardSocketOptions.SO_REUSEPORT, on);
		else SetSockOpt(UTIL.tcpsFD(so), on);
	}

	private static void SetSockOpt(FileDescriptor fd, boolean enabled) throws IOException {
		if (!RojLib.hasNative(RojLib.WIN32)) throw new NativeException("not available");
		int error = SetSockOpt(UTIL.fdVal(fd), enabled);
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
	private static native int SetSockOpt(int fd, boolean enable);

	public interface NUT {
		void fdFd(FileDescriptor fd, int fdVal);
		int fdVal(FileDescriptor fd);
		void fdClose(FileDescriptor fd, Closeable releaser) throws IOException;

		Object sChNd();
		Object dChNd();
		FileDescriptor udpFD(DatagramChannel ch);
		FileDescriptor tcpFD(SocketChannel ch);
		FileDescriptor tcpsFD(ServerSocketChannel ch);
	}

	public interface LLIO {
		int read(FileDescriptor fd, long _Address, int len) throws IOException;
		int write(FileDescriptor fd, long _Address, int len) throws IOException;

		long readVector(FileDescriptor fd, long _IoVec_Length, int len) throws IOException;
		long writeVector(FileDescriptor fd, long _IoVec_Length, int len) throws IOException;

		void close(FileDescriptor fd) throws IOException;
	}

	private static long pendingKeys_offset;
	/**
	 * 让consumer能在事件监听线程上拿到独占(synchronized)的WatchKey，少开一个线程<br>
	 * 请勿做长耗时操作，否则可能造成未预料的事件溢出 (等等，LinkedBlockingDeque不是也有锁么)<br>
	 * 你不能在该线程上调用WatchService的close和WatchKey的cancel方法，将会(而不是可能)造成死锁<br>
	 * 另外请勿在任何线程上调用WatchService的take或poll方法，会无限期等待<br>
	 */
	public static WatchService syncWatchPoll(String threadName, Consumer<WatchKey> c) throws IOException {
		var watcher = FileSystems.getDefault().newWatchService();
		try {
			long off;
			if ((off=pendingKeys_offset) == 0) {
				off = pendingKeys_offset = ReflectionUtils.fieldOffset(Class.forName("sun.nio.fs.AbstractWatchService"), "pendingKeys");
			}
			if (off > 0) {
				U.putObject(watcher, off, new LinkedBlockingDeque<>() {
					@Override
					public boolean offer(Object o) {
						c.accept((WatchKey) o);
						return true;
					}
				});
				return watcher;
			}
		} catch (Exception e) {
			pendingKeys_offset = -1;
		}

		var t = new Thread(() -> {
			while (true) {
				WatchKey key;
				try {
					key = watcher.take();
					if (key.watchable() == null) break;
				} catch (Exception e) {
					break;
				}
				c.accept(key);
			}
		});
		if (threadName == null) threadName = "FileWatcher2-"+watcher.hashCode();
		t.setName(threadName);
		t.setDaemon(true);
		t.start();

		return watcher;
	}
}