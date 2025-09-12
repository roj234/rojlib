package roj.ui;

import roj.RojLib;
import roj.io.BufferPool;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.TextUtil;
import roj.text.TextWriter;
import roj.util.ByteList;
import roj.util.NativeException;
import roj.util.OS;

import java.io.*;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author Roj234
 * @since 2024/7/21 7:38
 */
final class NativeVT implements ITty, Runnable {
	private static final int STDIN = 0, STDOUT = 1, STDERR = 2;
	private static final int MODE_GET = 0, MODE_SET = 1;
	private static final int
		ENABLE_PROCESSED_INPUT = 0x1,
		ENABLE_LINE_INPUT = 0x2,
		ENABLE_ECHO_INPUT = 0x4,
		ENABLE_INSERT_MODE = 0x20,
		ENABLE_QUICK_EDIT_MODE = 0x40,
		ENABLE_EXTENDED_FLAGS = 0x80,
		ENABLE_AUTO_POSITION = 0x100,
		ENABLE_VIRTUAL_TERMINAL_INPUT = 0x200,

		ENABLE_PROCESSED_OUTPUT = 0x1,
		ENABLE_WRAP_AT_EOL_OUTPUT = 0x2,
		ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x4,
		DISABLE_NEWLINE_AUTO_RETURN = 0x8,
		ENABLE_LVB_GRID_WORLDWIDE = 0x10;

	public static native long GetConsoleWindow();
	private static native int GetConsoleSize();
	private static native int SetConsoleMode(int target, int mode, int flag) throws NativeException;

	private static final Charset charset;
	private static final FastCharset fc;
	static {
		charset = TextUtil.consoleCharset;
		fc = FastCharset.getInstance(TextUtil.consoleCharset);
		if (fc == null) RojLib.debug("NativeVT", "不支持的控制台编码"+TextUtil.consoleCharset);
	}
	static void decode(ByteList in, CharList out) {
		if (fc == null) {
			var cd = charset.newDecoder();
			out.ensureCapacity((int) (out.length() + in.readableBytes() * cd.averageCharsPerByte()));

			var ib = in.nioBuffer();
			var ob = CharBuffer.wrap(out.list);
			ob.position(out.length());

			while (true) {
				var cr = cd.decode(ib, ob, false);
				out.setLength(ob.position());
				if (!cr.isOverflow()) break;
				out.ensureCapacity(out.length() * 2 + 1);
				ob = CharBuffer.wrap(out.list);
				ob.position(out.length());
			}
			in.rIndex = ib.position();
		} else {
			fc.decodeLoop(in, in.readableBytes(), out, Integer.MAX_VALUE, true);
		}
	}

	static ITty initialize(boolean forceEnable) {
		if (!forceEnable && TextUtil.consoleAbsent) return null;

		Tty instance = Tty.getInstance();
		int flag = forceEnable ? ANSI : 0;

		checkAnsi:
		if (RojLib.hasNative(RojLib.ANSI_READBACK)) {
			int wh = GetConsoleSize();
			if (wh == 0) {
				RojLib.debug("NativeVT", "GetConsoleSize() = 0");
				break checkAnsi;
			}

			instance.columns = wh >>> 16;
			instance.rows = wh & 0xFFFF;

			if (OS.CURRENT == OS.WINDOWS) {
				try {
					SetConsoleMode(STDOUT, MODE_SET, ENABLE_VIRTUAL_TERMINAL_PROCESSING|ENABLE_PROCESSED_OUTPUT|ENABLE_WRAP_AT_EOL_OUTPUT);
					flag |= ANSI;
					SetConsoleMode(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT);
					flag |= INTERACTIVE;
					SetConsoleMode(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT|ENABLE_EXTENDED_FLAGS|ENABLE_QUICK_EDIT_MODE);
				} catch (NativeException e) {
					RojLib.debug("NativeVT", e.getMessage());
				}
			} else {
				flag = ANSI;
			}
		}

		if (flag == 0) return null;
		if (charset == StandardCharsets.UTF_8) flag |= UNICODE;

		var tty = new NativeVT();
		tty.flag = flag;

		var t = new Thread(tty, "RojLib 异步标准输入");
		t.setPriority(Thread.MAX_PRIORITY);
		t.setDaemon(true);
		t.start();

		tty.reader = Thread.currentThread();

		if ((tty.flag&INTERACTIVE) == 0) {
			try {
				synchronized (tty) {tty.wait(100);}
			} catch (InterruptedException ignored) {}

			if (tty.reader != null) {
				RojLib.debug("NativeVT", "Init Timeout");
			} else {
				try {
					if (tty.buffer.readUnsignedShort() == 0x1b5b) {
						while (tty.buffer.readUnsignedByte() != 0x52);
						tty.flag |= INTERACTIVE;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		tty.reader = t;
		return tty;
	}

	private final InputStream in;
	private final TextWriter out;
	private final ByteList buffer = new ByteList(260);

	private volatile Thread reader;
	private int flag;

	private NativeVT() {
		long offset = Unaligned.fieldOffset(FilterInputStream.class, "in");
		Object in = System.in;
		while (in instanceof FilterInputStream) in = Unaligned.U.getReference(in, offset);
		this.in = (InputStream) in;

		offset = Unaligned.fieldOffset(FilterOutputStream.class, "out");
		Object out = System.out;
		while (out instanceof FilterOutputStream) out = Unaligned.U.getReference(out, offset);

		this.out = new TextWriter((OutputStream) out, charset, BufferPool.UNPOOLED);
	}

	@Override
	public void run() {
		try {
			if ((flag&INTERACTIVE) == 0) {
				var buf = buffer;
				out.append(Tty.GET_CONSOLE_DIMENSION);
				out.flush();
				int r = in.read(buf.list);
				if (r > 0) buf.wIndex(r);
				reader = null;
				synchronized (this) {notify();}
			}

			// 类加载锁, 等待Tty#<clinit>结束
			Tty.isFullUnicode();

			while (true) read();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		RojLib.debug("NativeVT", "Fatal");
		System.exit(1);
	}

	private void read() throws IOException {
		var buf = buffer;
		byte[] b = buf.list;
		int off = buf.rIndex;
		int r;
		try {
			r = in.read(b, off, 256 - off);
		} catch (ArrayIndexOutOfBoundsException e) {
			RojLib.debug("NativeVT", "Workaround "+e.getMessage());
			return;
		}

		if (r < 0) {
			b[off] = 0x1a;
			r = 1;
		}

		buf.wIndex(buf.wIndex()+r);

		Tty instance = Tty.getInstance();
		CharList ib = instance.inputBuf;
		ib.clear();
		decode(buf, ib);
		buf.compact();
		instance.onInput(ib);
	}

	@Override
	public int characteristics() {return flag;}

	@Override
	public void getConsoleSize(Tty tty) {
		var winSize = GetConsoleSize();
		tty.columns = winSize&0xFFFF;
		tty.rows = winSize >>> 16;
	}

	@Override
	public boolean readSync() {
		if (Thread.currentThread() != reader) return false;
		try {
			read();
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	@Override
	public void write(CharSequence str) {
		try {
			out.append(str);
		} catch (Exception e) {
			Tty.printError(e.getMessage());
		}
	}
	@Override
	public void flush() {
		try {
			out.flush();
		} catch (Exception e) {
			Tty.printError(e.getMessage());
		}
	}
}