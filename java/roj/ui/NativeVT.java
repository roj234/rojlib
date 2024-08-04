package roj.ui;

import roj.RojLib;
import roj.compiler.plugins.asm.ASM;
import roj.reflect.ReflectionUtils;
import roj.text.TextWriter;
import roj.text.UTF8;
import roj.text.UnsafeCharset;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.NativeException;
import roj.util.OS;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/7/21 0021 7:38
 */
public final class NativeVT implements ITerminal, Runnable {
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

	private static native int setConsoleMode0(int target, int mode, int flag) throws NativeException;

	private static final String VTERROR = "RojLib Error: 虚拟终端: ";
	private static byte cap;
	private static final InputStream sysIn;
	private static final OutputStream sysOut;
	static Charset charset;
	static UnsafeCharset ucs;

	private static final ITerminal instance = init();
	private static Thread reader;
	static {
		var out = Objects.requireNonNull(System.out);
		long offset;
		Object tmp;
		checkCharset:
		try {
			offset = ReflectionUtils.fieldOffset(PrintStream.class, "charOut");
			tmp = ReflectionUtils.u.getObject(out, offset);

			offset = ReflectionUtils.fieldOffset(tmp.getClass(), "se");
			tmp =  ReflectionUtils.u.getObject(tmp, offset);

			offset = ReflectionUtils.fieldOffset(tmp.getClass(), "cs");
			charset = (Charset) ReflectionUtils.u.getObject(tmp, offset);
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
		ucs = UnsafeCharset.getInstance(charset);
		if (ucs == null) {
			ucs = UTF8.CODER;
			System.err.println(VTERROR+"输出编码不受支持: "+charset);
		}

		if (instance != null) {
			var in = System.in;
			if (in instanceof FilterInputStream) {
				offset = ReflectionUtils.fieldOffset(FilterInputStream.class, "in");
				sysIn = (InputStream) ReflectionUtils.u.getObject(in, offset);
			} else {
				sysIn = in;
			}

			offset = ReflectionUtils.fieldOffset(FilterOutputStream.class, "out");
			tmp = ReflectionUtils.u.getObject(out, offset);
			if (tmp instanceof FilterOutputStream) tmp = ReflectionUtils.u.getObject(tmp, offset);
			sysOut = (OutputStream) tmp;
		} else {
			sysIn = Helpers.maybeNull();
			sysOut = Helpers.maybeNull();
		}
	}
	public static ITerminal getInstance(){return instance;}

	private boolean read;
	private NativeVT() {}
	private static ITerminal init() {
		if (Boolean.getBoolean("roj.noAnsi")) return null;
		// 避免初始化System.console()造成16KB的内存浪费
		if (RojLib.hasNative(RojLib.WIN32) ? GuiUtil.getConsoleWindow() == 0 : System.console() == null) return null;
		// current: win32 only
		if (RojLib.hasNative(RojLib.ANSI_READBACK)) {
			try {
				setConsoleMode0(STDOUT, MODE_SET, ENABLE_VIRTUAL_TERMINAL_PROCESSING|ENABLE_PROCESSED_OUTPUT|ENABLE_WRAP_AT_EOL_OUTPUT);
			} catch (NativeException e) {
				System.err.println(VTERROR+e.getMessage());
				return null;
			}
			try {
				setConsoleMode0(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT);
				cap = 1;
				setConsoleMode0(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT|ENABLE_EXTENDED_FLAGS|ENABLE_QUICK_EDIT_MODE);
			} catch (NativeException e) {
				System.err.println(VTERROR+e.getMessage());
			}
		} else if (OS.CURRENT != OS.WINDOWS) {

		}

		var io = new NativeVT();

		var t = new Thread(io, "RojLib - ANSI输入代理");
		t.setPriority(Thread.MAX_PRIORITY);
		t.setDaemon(true);
		t.start();
		try {
			synchronized (io) {io.wait(30);}
		} catch (InterruptedException e) {
			System.err.println(VTERROR+"Interrupt");
			return null;
		}

		if (!io.read) {
			io.read = true;
			if (cap != 0) {
				setConsoleMode0(STDIN, MODE_SET, ENABLE_PROCESSED_INPUT|ENABLE_ECHO_INPUT|ENABLE_LINE_INPUT);
				cap = 0;
			}

			System.err.println(VTERROR+"Timeout");
		}
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
			// block until NativeVT and Terminal initialized
			tw = new TextWriter(sysOut, Terminal.nativeCharset);

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
				Terminal.onInput(ib, ucs);
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}

		System.out.println(VTERROR+"Fatal");
		System.exit(1);
	}

	@Override
	public boolean readBack(boolean sync) throws IOException {
		if (!sync) {
			if (Thread.currentThread() != reader) return false;
			int r = sysIn.read(ib.list, 0, 256);
			ib.rIndex = 0;
			ib.wIndex(r);
		}
		Terminal.onInput(ib, ucs);
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