package roj.ui;

import roj.RojLib;
import roj.compiler.plugins.asm.ASM;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.reflect.Unaligned;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.TextWriter;
import roj.util.ByteList;
import roj.util.NativeException;
import roj.util.OS;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/7/21 7:38
 */
public final class NativeVT implements StdIO, Runnable {
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

	private static native int SetConsoleMode(int target, int mode, int flag) throws NativeException;
	public static native long GetConsoleWindow();

	private static final String VTERROR = "RojLib Error: 虚拟终端: ";
	private static byte cap;

	private static final InputStream sysIn;
	private static final OutputStream sysOut;
	static Charset charset;
	static FastCharset ucs;
	static {
		var out = Objects.requireNonNull(System.out);
		long offset;
		Object tmp;
		checkCharset:
		try {
			offset = Unaligned.fieldOffset(PrintStream.class, "charOut");
			tmp = Unaligned.U.getObject(out, offset);

			offset = Unaligned.fieldOffset(tmp.getClass(), "se");
			tmp =  Unaligned.U.getObject(tmp, offset);

			offset = Unaligned.fieldOffset(tmp.getClass(), "cs");
			charset = (Charset) Unaligned.U.getObject(tmp, offset);
		} catch (Exception e) {
			if (ASM.TARGET_JAVA_VERSION >= 17) {
				var c = System.console();
				if (c != null) {
					charset = c.charset();
					break checkCharset;
				}
			}

			var encoding = System.getProperty("stdout.encoding");
			charset = encoding == null ? Charset.defaultCharset() : Charset.forName(encoding);
		}
		ucs = FastCharset.getInstance(charset);
		if (ucs == null) System.err.println(VTERROR+"输出编码不受支持: "+charset);

		var in = System.in;
		if (in instanceof FilterInputStream) {
			offset = Unaligned.fieldOffset(FilterInputStream.class, "in");
			sysIn = (InputStream) Unaligned.U.getObject(in, offset);
		} else {
			sysIn = in;
		}

		offset = Unaligned.fieldOffset(FilterOutputStream.class, "out");
		tmp = Unaligned.U.getObject(out, offset);
		if (tmp instanceof FilterOutputStream) tmp = Unaligned.U.getObject(tmp, offset);
		sysOut = (OutputStream) tmp;
	}

	private static Thread reader;
	private static final StdIO instance = init();
	public static StdIO getInstance(){return instance;}

	public static final class Fallback extends DelegatedPrintStream implements StdIO, Runnable {
		private final PrintStream sout;

		public Fallback() {
			this.sout = System.out;
		}

		@Override
		public void run() {
			var tmp = IOUtil.getSharedByteBuf();
			while (true) {
				try {
					int i = sysIn.read(tmp.list);
					if (i < 0) {
						Terminal.onInput(new CharList().append('\3'));
						continue;
					}
					tmp.wIndex(i);
					Terminal.onInput(tmp, ucs);
				} catch (Throwable e) {
					System.err.println(VTERROR+"Fatal");
					e.printStackTrace();
					if (!(e instanceof IOException))
						System.exit(2);
					break;
				}
			}
		}
		public void start() {
			Thread thread = new Thread(this, "RojLib 异步标准输入(Fallback)");
			thread.setDaemon(true);
			thread.start();
		}

		@Override public boolean read(boolean sync) {return false;}
		@Override public void write(CharSequence str) {
			CharList x = new CharList(str);
			sout.print(Terminal.stripAnsi(x).toStringAndFree());
		}
		@Override protected void partialLine() {sout.print(Terminal.stripAnsi(sb));sb.clear();}
		@Override protected void newLine() {sout.println(Terminal.stripAnsi(sb));sb.clear();}
	}

	private boolean read;
	private NativeVT() {}
	private static StdIO init() {
		if (Boolean.getBoolean("roj.noAnsi") || Boolean.getBoolean("roj.disableNativeVT")) return null;
		// 避免初始化System.console()造成16KB的内存浪费
		if (RojLib.hasNative(RojLib.WIN32) ? GetConsoleWindow() == 0 : System.console() == null) return null;
		// current: win32 only
		if (RojLib.hasNative(RojLib.ANSI_READBACK)) {
			try {
				SetConsoleMode(STDOUT, MODE_SET, ENABLE_VIRTUAL_TERMINAL_PROCESSING|ENABLE_PROCESSED_OUTPUT|ENABLE_WRAP_AT_EOL_OUTPUT);
			} catch (NativeException e) {
				System.err.println(VTERROR+e.getMessage());
				return null;
			}
			try {
				SetConsoleMode(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT);
				cap = 1;
				SetConsoleMode(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT|ENABLE_EXTENDED_FLAGS|ENABLE_QUICK_EDIT_MODE);
			} catch (NativeException e) {
				System.err.println(VTERROR+e.getMessage());
			}
		} else if (OS.CURRENT != OS.WINDOWS) {

		}

		var io = new NativeVT();

		var t = new Thread(io, "RojLib 异步标准输入");
		t.setPriority(Thread.MAX_PRIORITY);
		t.setDaemon(true);
		t.start();
		try {
			synchronized (io) {io.wait(100);}
		} catch (InterruptedException e) {
			System.err.println(VTERROR+"Interrupt");
			return null;
		}

		if (!io.read) {
			io.read = true;
			if (cap != 0) {
				SetConsoleMode(STDIN, MODE_SET, ENABLE_PROCESSED_INPUT|ENABLE_ECHO_INPUT|ENABLE_LINE_INPUT);
				cap = 0;
			}

			System.err.println(VTERROR+"Timeout");
			return null;
		}
		io.tw = new TextWriter(sysOut, charset, BufferPool.UNPOOLED);
		reader = t;
		return io;
	}

	private final ByteList ib = new ByteList(260);
	@Override
	public void run() {
		try {
			var ib = this.ib;
			byte[] buf = ib.list;

			System.out.print(Terminal.GET_CONSOLE_DIMENSION);
			int r = System.in.read(buf);
			read = true;
			ib.wIndex(r);
			synchronized (this) {notify();}
			// 类加载锁, 会卡在这里直到Terminal初始化
			Terminal.getConsole();

			while (true) {
				int off = ib.rIndex;
				try {
					r = sysIn.read(buf, off, 256 - off);
				} catch (ArrayIndexOutOfBoundsException e) {
					System.out.println(VTERROR+"Baka:"+e.getMessage());
					continue;
				}

				if (r < 0) {
					buf[off] = 0x1a;
					r = 1;
				}

				ib.wIndex(ib.wIndex()+r);
				if (ucs == null) {
					CharBuffer decode = charset.newDecoder().decode(ByteBuffer.wrap(buf, 0, off + r));
					CharList buf1 = new CharList(decode.array());
					buf1.setLength(decode.length());
					Terminal.onInput(buf1);
				} else {
					Terminal.onInput(ib, ucs);
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}

		System.out.println(VTERROR+"Fatal");
		System.exit(1);
	}

	@Override
	public boolean read(boolean sync) throws IOException {
		if (!sync) {
			if (Thread.currentThread() != reader) return false;
			int r = sysIn.read(ib.list, 0, 256);
			ib.rIndex = 0;
			ib.wIndex(r);
		}
		if (ucs == null) {
			CharBuffer decode = charset.newDecoder().decode(ByteBuffer.wrap(ib.list, 0, ib.wIndex()));
			CharList buf1 = new CharList(decode.array());
			buf1.setLength(decode.length());
			Terminal.onInput(buf1);
		} else {
			Terminal.onInput(ib, ucs);
		}
		return true;
	}

	private TextWriter tw;
	@Override
	public void write(CharSequence str) {
		try {
			// 不用PrintStream，因为它会把str转换为String
			tw.append(str);
		} catch (Exception e) {
			cap |= 2;
		}
	}
	@Override
	public void flush() {
		try {
			tw.flush();
		} catch (Exception e) {
			cap |= 2;
		}
	}
}