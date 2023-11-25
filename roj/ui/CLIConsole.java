package roj.ui;

import roj.collect.Int2IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.*;
import roj.text.pattern.MyPattern;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static java.awt.event.KeyEvent.*;
import static roj.ui.CLIUtil.*;

/**
 * 我也没想到我会去兼容一个几十年前的标准...
 * @author Roj234
 * @since 2023/11/18 0018 15:39
 */
public final class CLIConsole extends InputStream implements Runnable {
	private static CLIConsole instance;
	private static Thread thread;

	private static volatile int inited;
	public static int windowHeight, windowWidth;

	static boolean initialize() {
		try {
			Method m = java.io.Console.class.getDeclaredMethod("istty");
			m.setAccessible(true);
			if (!((Boolean) m.invoke(null, ArrayCache.OBJECTS))) return false;
		} catch (Exception e) {
			if (System.console() == null) return false;
		}

		CLIConsole con = new CLIConsole();
		instance = con;

		Thread t = thread = new Thread(con);
		t.setName("终端模拟器");
		t.setDaemon(true);
		t.start();

		synchronized (t) {
			try {
				t.wait(20);
			} catch (InterruptedException e) {
				return false;
			}
		}

		if (inited == 0) {
			inited = -1;
			System.err.println("不是控制台。 注:谁教教我怎么检测STDIN有没有东西,要能跨平台的");
			enableDirectInput(false);
			return false;
		}

		AnsiOut out = new AnsiOut(1024);

		System.setIn(con);
		System.setOut(out);
		System.setErr(out);

		return true;
	}

	public static boolean hasBottomLine(CharList line) { return LINES.contains(line); }

	private CLIConsole() {}

	// region PipeInputStream
	private static final AtomicInteger IN_READ = new AtomicInteger();
	private static final int RING_BUFFER_CAPACITY = 4096;
	private final byte[] Pipe = new byte[RING_BUFFER_CAPACITY];
	private int rPtr, wPtr;

	private byte[] b1;
	@Override
	public int read() throws IOException {
		byte[] b = b1;
		if (b == null) b = b1 = new byte[1];
		return read(b) > 0 ? b[0] : -1;
	}
	@Override
	public int read(@Nonnull byte[] b, int off, int len) throws IOException {
		ArrayUtil.checkRange(b, off, len);
		if (len == 0) return 0;

		while (true) {
			synchronized (this) {
				if (rPtr == wPtr) {
					synchronized (IN_READ) {
						IN_READ.incrementAndGet();
						IN_READ.notify();
						IN_READ.decrementAndGet();
					}

					try {
						wait();
					} catch (InterruptedException e) {
						throw new ClosedByInterruptException();
					}
				}

				int read = Math.min(len, available());
				if (read > 0) {
					System.arraycopy(Pipe, rPtr, b, off, read);
					rPtr = (rPtr+read) & 4095;
					return read;
				}
			}
		}
	}
	@Override
	public int available() {
		int readLen = wPtr - rPtr;
		return readLen < 0 ? readLen+RING_BUFFER_CAPACITY : readLen;
	}

	private synchronized void pipe(byte[] buf, int off, int len) {
		while (true) {
			int write = Math.min(len, Pipe.length - wPtr);
			System.arraycopy(buf, off, Pipe, wPtr, write);

			len -= write;
			if (len == 0) {
				wPtr += write;
				break;
			}

			wPtr = 0;
			off += write;
		}

		notifyAll();
	}
	public static void writeToSystemIn(byte[] b, int off, int len) { instance.pipe(b, off, len); }
	// endregion

	// region bottom line
	private static final UnsafeCharset CE = GB18030.is(Charset.defaultCharset()) ? GB18030.CODER : UTF8MB4.CODER;
	private static final ByteList SEQ = new ByteList(256);
	private static final SimpleList<CharList> LINES = new SimpleList<>();

	public static void renderBottomLine(CharList b) { renderBottomLine(b, false, 0); }
	/**
	 * 渲染或重新渲染注册的一条线
	 * @param atTop 仅未注册时有效：true在上方插入，否则下方
	 * @param cursor 仅b为最下方时有效：光标位置 (0为最后, 从1开始计数)
	 */
	public static void renderBottomLine(CharList b, boolean atTop, int cursor) {
		ByteList bb = SEQ;
		if (!ANSI) {
			CE.encodeFixedIn(b, bb);
			writeSeq(stripAnsi(bb).put('\n'));
			return;
		}

		synchronized (sysOut) {
			bb.putAscii("\u001b[?25l");

			int pos = LINES.indexOfAddress(b);
			if (pos < 0) {
				if (LINES.size() >= windowHeight) {
					System.out.println(b);
					return;
				}

				if (atTop || LINES.isEmpty()) {
					LINES.add(b);
					if (LINES.size() > 1) {
						int i = LINES.size()-1;
						bb.putAscii("\u001b["+i+"F\n");
						while (true) {
							CE.encodeFixedIn(LINES.get(i), bb);
							bb.putAscii("\u001b[0K");
							if (i-- == 0) break;
							bb.put('\n');
						}
					} else {
						CE.encodeFixedIn(b, bb);
						bb.putAscii("\u001b["+cursor+"G");
					}
				} else {
					bb.put('\n');
					CE.encodeFixedIn(b, bb);
					LINES.add(0, b);
				}
			} else {
				if (pos > 0) bb.putAscii("\u001b7\u001b["+pos+"F");
				else bb.putAscii("\u001b[1G");
				CE.encodeFixedIn(b, bb);
				bb.putAscii("\u001b[0K");
				if (pos > 0) bb.putAscii("\u001b8");
				else bb.putAscii("\u001b["+cursor+"G");
			}

			writeSeq(bb.putAscii("\u001b[?25h"));
		}
	}
	public static void removeBottomLine(CharList b, boolean clearText) {
		synchronized (sysOut) {
			int pos = LINES.indexOfAddress(b);
			if (pos < 0) return;
			LINES.remove(pos);

			writeSeq(SEQ.putAscii(LINES.isEmpty() ? "\u001b[1M" : "\u001b7\u001b["+LINES.size()+"F\u001b[1M\u001b8\u001bM"));
			if (!clearText) System.out.println(b);
		}
	}
	static void writeSeq(ByteList b) {
		try {
			b.writeToStream(sysOut);
			b.clear();
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	static final class AnsiOut extends DelegatedPrintStream {
		AnsiOut(int max) { super(max); }
		@Override
		protected void newLine() {
			synchronized (sysOut) {
				if (!LINES.isEmpty()) {
					sysOut.print("\u001b[?25l\n\u001b["+LINES.size()+"F\u001b[0K");
				}

				flushBytes();
				CE.encodeFixedIn(sb, bb);
				sb.clear(); bb.put('\n');

				if (!LINES.isEmpty()) {
					int i = LINES.size()-1;
					while (true) {
						CE.encodeFixedIn(LINES.get(i), bb);
						bb.putAscii("\u001b[0K");
						if (i-- == 0) break;
						bb.put('\n');
					}
					bb.putAscii("\u001b[?25h");
				}

				flushBytes();
			}
		}
		@Override
		protected void flushBytes() { writeSeq(bb); }
		@Override
		protected void partialLine() { flushBytes(); }
	}
	// endregion

	public void run() {
		try {
			System.out.print("\u001b[6n");
			long time = System.currentTimeMillis();
			System.in.read();

			if (inited == 0) inited = 1;
			else {
				sysErr.println("对read()的调用返回。耗时："+(System.currentTimeMillis()-time)+"ms");
				return;
			}

			thread = Thread.currentThread();
			checkResize();
		} catch (Throwable e) {
			Helpers.athrow(e);
		}

		synchronized (this) { notify(); }
		inited = 2;
		synchronized (CLIConsole.class) { if (console == null) enableDirectInput(false); }

		ByteList.Slice shellB = IOUtil.SharedCoder.get().shellB;
		byte[] buf = new byte[260];

		while (true) {
			if (console == null) {
				synchronized (IN_READ) {
					if (IN_READ.get() == 0 || rPtr != wPtr) {
						try {
							IN_READ.wait();
						} catch (InterruptedException e) {
							break;
						}
					}
				}
			}

			int r;
			try {
				r = sysIn.read(buf, 0, 256);
			} catch (Throwable e) {
				e.printStackTrace(sysErr);
				break;
			}

			if (r < 0) {
				buf[0] = 0x1a;
				r = 1;
			}

			try {
				processInput(buf, 0, r, shellB);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		sysErr.println("Console handler has terminated!");
		LockSupport.parkNanos(1);
		System.exit(13102);
	}

	private static Console console;
	public static void setConsole(Console c) {
		synchronized (CLIConsole.class) {
			if (console != null) console.unregistered();
			console = c;
		}

		if (c != null) {
			CLIUtil.enableDirectInput(true);
			c.registered();
			synchronized (IN_READ) { IN_READ.notify(); }
		} else {
			CLIUtil.enableDirectInput(false);
		}
	}
	public static Console getConsole() { return console; }

	public static final int VK_CTRL = 0x100;
	private static final TrieTree<Integer> KeyMap = new TrieTree<>();
	static {
		key(VK_BACK_SPACE,  "7f");
		key(VK_ESCAPE,  "1b");
		key(VK_TAB, "09");
		key(VK_ENTER, "0d");

		key(VK_F1, "1b4f50");
		key(VK_F2, "1b4f51");
		key(VK_F3, "1b4f52");
		key(VK_F4, "1b4f53");
		key(VK_F5, "1b5b31357e");
		key(VK_F6, "1b5b31377e");
		key(VK_F7, "1b5b31387e");
		key(VK_F8, "1b5b31397e");
		key(VK_F9, "1b5b32307e");
		key(VK_F10, "1b5b32317e");
		key(VK_F11, "1b5b32337e");
		key(VK_F12, "1b5b32347e");

		key(VK_UP, "1b5b41");
		key(VK_DOWN, "1b5b42");
		key(VK_LEFT, "1b5b44");
		key(VK_RIGHT, "1b5b43");

		key(VK_INSERT, "1b5b327e");
		key(VK_HOME, "1b5b48");
		key(VK_PAGE_UP, "1b5b357e");
		key(VK_DELETE, "1b5b337e");
		key(VK_END, " 1b5b46");
		key(VK_PAGE_DOWN, "1b5b367e");

		key(VK_CTRL | VK_A, "01");
		key(VK_CTRL | VK_B, "02");
		key(VK_CTRL | VK_C, "03");
		key(VK_CTRL | VK_D, "04");
		key(VK_CTRL | VK_E, "05");
		key(VK_CTRL | VK_F, "06");
		key(VK_CTRL | VK_G, "07");
		key(VK_CTRL | VK_H, "08");
		key(VK_CTRL | VK_J, "0a");
		key(VK_CTRL | VK_K, "0b");
		key(VK_CTRL | VK_L, "0c");
		key(VK_CTRL | VK_N, "0e");
		key(VK_CTRL | VK_O, "0f");
		key(VK_CTRL | VK_P, "10");
		key(VK_CTRL | VK_Q, "11");
		key(VK_CTRL | VK_R, "12");
		key(VK_CTRL | VK_S, "13");
		key(VK_CTRL | VK_T, "14");
		key(VK_CTRL | VK_U, "15");
		key(VK_CTRL | VK_V, "16");
		key(VK_CTRL | VK_W, "17");
		key(VK_CTRL | VK_X, "18");
		key(VK_CTRL | VK_Y, "19");
		key(VK_CTRL | VK_Z, "1a");

		key(VK_CTRL | VK_UP, "1b5b313b3541");
		key(VK_CTRL | VK_DOWN, "1b5b313b3542");
		key(VK_CTRL | VK_LEFT, "1b5b313b3544");
		key(VK_CTRL | VK_RIGHT, "1b5b313b3543");
	}
	private static void key(int vk, String seq) { KeyMap.put(TextUtil.hex2bytes(seq, new ByteList()), vk); }

	private final MyHashMap.Entry<MutableInt, Integer> matcher = new MyHashMap.Entry<>(new MutableInt(), null);
	private void processInput(byte[] buf, int off, int len, ByteList.Slice inBuf) {
		inBuf.setR(buf, off, len);
		int i = 0;

		while (i < len) {
			KeyMap.match(inBuf, i, len, matcher);
			int matchLen = matcher.getKey().getValue();
			if (matchLen < 0) {
				keyEnter(buf[i++], false);
				continue;
			}

			found: {
				failed:
				if (matcher.v == VK_ESCAPE) {
					if (i+1 < len && buf[i+1] == '[') {
						int j = i+1;
						while (true) {
							if (j+1 >= len) break failed;
							byte b = buf[++j];
							if (b >= 'a' && b <= 'z') break;
							if (b >= 'A' && b <= 'Z') break;
						}

						matchLen = j-i+1;
						escEnter(inBuf.setR(buf, i+2, j-i-1));

						inBuf.setR(buf, off, len);
						break found;
					}
				}
				keyEnter(matcher.v, true);
			}

			i += matchLen;
		}

		if (console == null && inited == 2) pipe(buf, off, len);
	}
	private void keyEnter(int keyCode, boolean isVirtual) {
		Console line = console;
		if (line != null) line.keyEnter(keyCode, isVirtual);
		else if (keyCode == (VK_CTRL|VK_C)) {
			sysOut.println("Received SIGINT");
			System.exit(0);
		}
	}
	private void escEnter(ByteList buf) {
		int type = buf.getU(buf.wIndex()-1);
		if (type == 'R') {
			int i = TextUtil.gIndexOf(buf, ';');
			int height = TextUtil.parseInt(buf, 0, i, 10);
			int width = TextUtil.parseInt(buf, i+1, buf.wIndex()-1, 10);
			synchronized (lcb) {
				if (isGetCursor == -1) {
					isGetCursor = (height << 16) | width;
					lcb.notifyAll();
					return;
				}
			}
		}

		sysOut.println("额外的ESC转义组: "+buf.dump()+" (可以考虑报告该问题)");
	}

	private static final Int2IntMap CharLength = new Int2IntMap();
	public static int getCharLength(char c) {
		if (TextUtil.isChinese(c)) return 2;
		if (TextUtil.isPrintableAscii(c)) return 1;

		if (instance == null) {
			if (c == '\t') return 4;
			if (c <= 0xFF) return c < 16 ? 0 : 1;
			return MyPattern.标点.contains(c) ? 2 : 1;
		}

		int len = CharLength.getOrDefaultInt(c, -1);
		if (len >= 0) return len;

		// https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters
		// 这也太邪门了...... 但是可行，速度还飞快。。
		synchronized (sysOut) {
			ByteList bb = SEQ.putAscii("\u001b7\u001b[1E");
			CE.encodeFixedIn(IOUtil.getSharedCharBuf().append(c), bb);
			writeSeq(bb);

			try {
				len = (instance.getCursorPos()&0xFF)-1;
				CharLength.put(c, len);
			} catch (IOException e) {
				e.printStackTrace();
			}

			writeSeq(bb.putAscii("\u001b[1K\u001b8"));
			return len;
		}
	}

	private int isGetCursor;
	private final ByteList.Slice lcb = new ByteList.Slice(new byte[32], 0, 0);
	public int getCursorPos() throws IOException {
		if (Thread.currentThread() == thread) {
			byte[] b = lcb.list;
			lcb.set(b, 0, b.length).putAscii("\u001b[6n").writeToStream(sysOut);

			int len = sysIn.read(b);
			isGetCursor = -1;
			processInput(b, 0, len, lcb);
			return isGetCursor;
		}

		synchronized (lcb) {
			while (true) {
				if (0 == isGetCursor) {
					isGetCursor = -1;
					sysOut.write(new byte[] {0x1b, '[', '6', 'n' });
				}

				if (isGetCursor == -1) {
					try {
						lcb.wait(100);
					} catch (InterruptedException e) {
						throw new ClosedByInterruptException();
					}

					if (isGetCursor == -1) throw new IllegalStateException("GetCursorPos() failed");
					else if (isGetCursor == 0) continue;
				}

				int val = isGetCursor;
				isGetCursor = 0;
				return val;
			}
		}
	}

	public static void checkResize() throws IOException {
		synchronized (sysOut) {
			writeSeq(SEQ.putAscii("\u001b[?25l\u001b7\u001b[999E\u001b[999C"));
			try {
				int winSize = instance.getCursorPos();
				windowWidth = winSize&0xFFFF;
				windowHeight = winSize >>> 16;
			} finally {
				writeSeq(SEQ.putAscii("\u001b8\u001b[?25h"));
			}
		}
	}
}
