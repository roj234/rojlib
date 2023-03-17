package roj.io;

import roj.io.misc.FDRW;
import roj.reflect.DirectAccessor;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.SocketOption;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2020/12/6 14:15
 */
public final class NIOUtil {
	private static FDRW SCN, DCN;
	private static NUT UTIL;
	private static Consumer<Object> CLEAN;

	public static NUT natives() {
		return UTIL;
	}
	public static FDRW udpFdRW() {
		return DCN;
	}
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

		ServerSocketChannel ssc = ServerSocketChannel.open();
		ssc.close();

		DatagramChannel dc = DatagramChannel.open();
		dc.close();

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

			da.access(sc.getClass(), new String[] {"nd","fd"}, new String[] {"sChNd","tcpFD"}, null)
			  .access(dc.getClass(), new String[] {"nd","fd"}, new String[] {"dChNd","udpFD"}, null);

			da.delegate(Class.forName("sun.nio.ch.IOUtil"), "configureBlocking");

			Class<?> net = Class.forName("sun.nio.ch.Net");
			da.delegate(net, new String[] {"localAddress", "remoteAddress",
										   "getSocketOption", "setSocketOption",
										   "isIPv6Available", "shutdown"},
				new String[] {"SF_localAddress", "SF_remoteAddress",
							  "SF_getOption", "SF_setOption",
							  "supportIpv6", "SF_tcp_shutdown"});
			da.access(net, "UNSPEC", "SF_unspec_sentry", null);
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
		UTIL = da.build();

		try {
			String[] ss1 = new String[] {"read", "readv", "write", "writev"};
			String[] ss2 = new String[] {"read0", "readv0", "write0", "writev0"};
			DCN = DirectAccessor.builder(FDRW.class).delegate(UTIL.dChNd().getClass(), ss2, ss1).build();
			SCN = DirectAccessor.builder(FDRW.class).delegate(UTIL.sChNd().getClass(), ss2, ss1).build();
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}

		try {
			CLEAN = Helpers.cast(DirectAccessor.builder(Consumer.class).delegate(UTIL.cleaner(b).getClass(), "clean", "accept").build());
			clean(b);
		} catch (Throwable e1) {
			if (e == null) e = e1;
			else e.addSuppressed(e1);
		}
	}

	public static boolean available() {
		return e == null;
	}

	public static final int EOF = -1;
	public static final int UNAVAILABLE = -2;
	public static final int INTERRUPTED = -3;
	public static final int UNSUPPORTED = -4;
	public static final int THROWN = -5;
	public static final int UNSUPPORTED_CASE = -6;

	public static FileDescriptor udpFD(DatagramChannel ch) {
		return UTIL.udpFD(ch);
	}

	public static FileDescriptor tcpFD(SocketChannel ch) {
		return UTIL.tcpFD(ch);
	}

	public interface NUT {
		void fdFd(FileDescriptor fd, int fd2);
		int fdVal(FileDescriptor fd);
		void fdClose(FileDescriptor fd, Closeable releaser) throws IOException;

		Object sChNd();
		Object dChNd();

		FileDescriptor udpFD(DatagramChannel ch);
		FileDescriptor tcpFD(SocketChannel ch);

		long address(Object buf);
		Object attachment(Object buf);
		Object cleaner(Object buf);

		void configureBlocking(FileDescriptor fd, boolean var1) throws IOException;

		InetSocketAddress SF_localAddress(FileDescriptor fd) throws IOException;
		InetSocketAddress SF_remoteAddress(FileDescriptor fd) throws IOException;

		ProtocolFamily SF_unspec_sentry();

		Object SF_getOption(FileDescriptor fd, ProtocolFamily family, SocketOption<?> key) throws IOException;
		void SF_setOption(FileDescriptor fd, ProtocolFamily family, SocketOption<?> key, Object val) throws IOException;

		void SF_tcp_shutdown(FileDescriptor fd, int direction);

		boolean supportIpv6();
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
		Object cl = UTIL.cleaner(topMost(shared));
		if (cl != null) CLEAN.accept(cl);
	}
}
