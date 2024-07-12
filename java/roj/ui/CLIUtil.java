package roj.ui;

import org.jetbrains.annotations.NotNull;
import roj.NativeLibrary;
import roj.collect.*;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.text.*;
import roj.ui.terminal.*;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.NativeException;

import java.io.*;
import java.nio.channels.ClosedByInterruptException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.awt.event.KeyEvent.*;

/**
 * <a href="https://learn.microsoft.com/zh-cn/windows/console/console-virtual-terminal-sequences">控制台虚拟终端序列参考</a>
 * Sequence	代码	说明	行为
 * ESC [ <n> A	CUU	光标向上	光标向上 <n> 行
 * ESC [ <n> B	CUD	光标向下	光标向下 <n> 行
 * ESC [ <n> C	CUF	光标向前	光标向前（右）<n> 行
 * ESC [ <n> D	CUB	光标向后	光标向后（左）<n> 行
 * ESC [ <n> E	CNL	光标下一行	光标从当前位置向下 <n> 行
 * ESC [ <n> F	CPL	光标当前行	光标从当前位置向上 <n> 行
 * ESC [ <n> G	CHA	绝对光标水平	光标在当前行中水平移动到第 <n> 个位置
 * ESC [ <n> d	VPA	绝对垂直行位置	光标在当前列中垂直移动到第 <n> 个位置
 * ESC [ <y> ; <x> H	CUP	光标位置	游标移动到视区中的 <x>；<y> 坐标，其中 <x> 是 <y> 行的列
 * ESC [ <y> ; <x> f	HVP	水平垂直位置	游标移动到视区中的 <x>；<y> 坐标，其中 <x> 是 <y> 行的列
 * <p>
 * 对于以下行，参数 <n> 有 3 个有效值：
 * 0 的擦除范围是从当前光标位置（含）到行/显示的末尾
 * 1 的擦除范围是从行/显示开始到当前光标位置（含）
 * 2 的擦除范围是整行/显示
 * <p>
 * ESC [ <n> J	ED	显示中的擦除	将 <n> 指定的当前视区/屏幕中的所有文本替换为空格字符
 * ESC [ <n> K	EL	行中的擦除	将行上的所有文本替换为由 <n> 指定的光标与空格字符
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class CLIUtil implements Runnable {
	public static final boolean ANSI;

	// initialized at last
	public static final BufferedReader in;
	private static final InputStream sysIn = new FileInputStream(FileDescriptor.in);
	public static final PrintStream sysOut = System.out, sysErr = System.err;

	public static final int BLACK = 30, BLUE = 34, GREEN = 32, CYAN = 36, RED = 31, PURPLE = 35, YELLOW = 33, WHITE = 37;
	public static final int HIGHLIGHT = 60;

	public static final class MinecraftColor {
		private static final IntBiMap<String> MC_COLOR_JSON = new IntBiMap<>();
		private static final byte[] MC_COLOR = new byte[16];
		static {
			Object[] arr = new Object[] {
				"BLACK", '0', BLACK,
				"DARK_BLUE", '1', BLUE,
				"DARK_GREEN", '2', GREEN,
				"DARK_AQUA", '3', CYAN,
				"DARK_RED", '4', RED,
				"DARK_PURPLE", '5', PURPLE,
				"GOLD", '6', YELLOW,
				"GRAY", '7', WHITE,

				"DARK_GRAY", '8', BLACK+60,
				"BLUE", '9', BLUE+60,
				"GREEN", 'a', GREEN+60,
				"AQUA", 'b', CYAN+60,
				"RED", 'c', RED+60,
				"LIGHT_PURPLE", 'd', PURPLE+60,
				"YELLOW", 'e', YELLOW+60,
				"WHITE", 'f', WHITE+60
			};
			for (int i = 0; i < arr.length;) {
				String k = arr[i++].toString().toLowerCase();
				Character v = (Character) arr[i++];
				int color = ((Number) arr[i++]).intValue();
				MC_COLOR[TextUtil.h2b(v)] = (byte) color;
				MC_COLOR_JSON.putInt(color, k);
			}
		}

		public static String getByConsoleCode(int color) { return MC_COLOR_JSON.get(color); }
		public static AnsiString minecraftJsonStyleToString(CMap map) {
			AnsiString sts = minecraftRawStyleToString(map.getString("text"));

			int colorCode = MC_COLOR_JSON.getInt(map.getString("color").toLowerCase());
			if (colorCode != 0) sts.color16(colorCode);
			if (map.containsKey("italic")) sts.italic(map.getBool("italic"));
			if (map.containsKey("bold")) sts.bold(map.getBool("bold"));
			if (map.containsKey("underlined")) sts.underline(map.getBool("underlined"));
			if (map.containsKey("strikethrough")) sts.deleteLine(map.getBool("strikethrough"));
			if (map.containsKey("obfuscated")) sts.reverseColor(map.getBool("obfuscated"));
			if (map.containsKey("extra")) {
				CList list = map.get("extra").asList();
				for (int i = 0; i < list.size(); i++) {
					sts.append(minecraftJsonStyleToString(list.get(i).asMap()));
				}
			}

			return sts;
		}

		private static final Pattern MINECRAFT_U00A7 = Pattern.compile("(?:\u00a7[0-9a-fA-FlmnokrLMNOKR])+");
		public static AnsiString minecraftRawStyleToString(String raw) {
			Matcher m = MINECRAFT_U00A7.matcher(raw);
			if (!m.find()) return new AnsiString(raw);

			int prevI = 0;
			AnsiString root = null;

			do {
				int i = m.start();

				AnsiString tmp = new AnsiString(raw.substring(prevI, i));
				if (root == null) root = tmp;
				else root.append(tmp);

				do {
					char c = raw.charAt(++i);
					if (c <= 'f') {
						tmp.color16(MC_COLOR[TextUtil.h2b(c)]).clear();
					} else {
						switch (c) {
							case 'l': tmp.bold(true); break;
							case 'n': tmp.underline(true); break;
							case 'm': tmp.deleteLine(true); break;
							case 'o': tmp.italic(true); break;
							case 'k': tmp.shiny(true); break;
							case 'r': tmp.clear(); break;
						}
					}
				} while (++i < m.end());

				prevI = m.end();
			} while (m.find(prevI));

			if (prevI < raw.length()) root.append(new AnsiString(raw.substring(prevI)));
			return root;
		}

		public static final char[] rainbow = new char[] {'c', '6', 'e', 'a', 'b', '9', 'd'};
		public static final char[] sonic = new char[] {'7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
													   '9', '9', '9', '9', 'f', '9', 'f', 'f', '9', 'f',
													   'f', '9', 'b', 'f', '7', '7', '7', '7', '7', '7',
													   '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
													   '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
													   '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
													   '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
													   '7', '7', '7', '7', '7', '7', '7', '7', '7', '7'};

		public static void minecraftTooltip(char[] codex, String str, float charTimeSec, CharList sb) {
			int si = (int) (((int) System.currentTimeMillis() & Integer.MAX_VALUE) / (charTimeSec*1000) % codex.length);
			for (int i = 0; i < str.length(); i++) {
				sb.append("\u001B[;").append(MC_COLOR[TextUtil.h2b(codex[si++ % codex.length])]).append('m').append(str.charAt(i));
			}
			sb.append("\u001B[0m");
		}

		public static void rainbow(String s, CharList sb) {
			if (!ANSI) sb.append(s);
			else minecraftTooltip(rainbow, s, 0.05f, sb);
		}

		public static void sonic(String s, CharList sb) {
			if (!ANSI) sb.append(s);
			else minecraftTooltip(sonic, s, 0.07f, sb);
		}
	}

	private static final UnsafeCharset CE = GB18030.is(TextUtil.ConsoleCharset) ? GB18030.CODER : UTF8MB4.CODER;
	private static final ByteList SEQ = new ByteList(256);

	private static final CLIUtil reader = new CLIUtil();
	private static Thread thread;

	private static boolean initialize() {
		if (Boolean.getBoolean("roj.noAnsi")) return false;
		if (System.console() == null) return false;

		if (NativeLibrary.hasFunction(NativeLibrary.ANSI_CONSOLE)) {
			try {
				setConsoleMode0(STDOUT, MODE_SET, ENABLE_VIRTUAL_TERMINAL_PROCESSING|ENABLE_PROCESSED_OUTPUT|ENABLE_WRAP_AT_EOL_OUTPUT);
			} catch (NativeException e) {
				System.err.println("Failed to initialize VT output: " + e.getMessage());
				return false;
			}
			enableDirectInput(true);
		}

		CLIUtil con = reader;

		Thread t = new Thread(con, "Console-Reader");
		t.setPriority(Thread.MAX_PRIORITY);
		t.setDaemon(true);
		t.start();

		synchronized (con) {
			try {
				con.wait(20);
			} catch (InterruptedException e) {
				return false;
			}
		}

		if (con.state <= 0) {
			con.state = -1;
			enableDirectInput(false);
			return false;
		}

		thread = Thread.currentThread();
		try {
			checkResize();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		thread = t;

		AnsiOut out = new AnsiOut();
		System.setOut(out);
		System.setErr(out);
		return true;
	}

	private static final int STDIN = 0, STDOUT = 1, STDERR = 2;
	private static final int MODE_GET = 0, MODE_SET = 1, MODE_ADD = 2, MODE_REMOVE = 3, MODE_XOR = 4;
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
	private static void enableDirectInput(boolean enable) {
		if (NativeLibrary.hasFunction(NativeLibrary.ANSI_CONSOLE)) {
			try {
				if (enable) {
					setConsoleMode0(STDIN, MODE_SET, 0);
					setConsoleMode0(STDIN, MODE_SET, ENABLE_VIRTUAL_TERMINAL_INPUT);
				} else {
					setConsoleMode0(STDIN, MODE_SET, ENABLE_PROCESSED_INPUT|ENABLE_ECHO_INPUT|ENABLE_LINE_INPUT);
				}
			} catch (NativeException e) {
				System.err.println("Failed to initialize VT input: " + e.getMessage());
			}
		}
	}
	private static native int setConsoleMode0(int target, int mode, int flag) throws NativeException;
	public static void enableQuickEditMode() {
		if (ANSI && NativeLibrary.hasFunction(NativeLibrary.ANSI_CONSOLE)) setConsoleMode0(STDIN, MODE_ADD, ENABLE_QUICK_EDIT_MODE|ENABLE_EXTENDED_FLAGS);
	}

	public static void reset() { if (ANSI) System.out.print("\u001B[0m"); }

	public static void fg(int fg, boolean hl) { if (ANSI) System.out.print("\u001B["+(fg+(hl?HIGHLIGHT:0))+'m'); }

	private static void color(String s, int fg, boolean reset, boolean println) {
		PrintStream o = System.out;
		if (ANSI) o.print("\u001B[;"+fg+'m');
		if (println) o.println(s); else o.print(s);
		if (ANSI&reset) o.print("\u001B[0m");
	}

	public static void info(String s) { info(s, true); }
	public static void info(String s, boolean println) { color(s, WHITE+HIGHLIGHT, true, println); }

	public static void success(String s) { success(s, true); }
	public static void success(String s, boolean println) { color(s, GREEN+HIGHLIGHT, true, println); }

	public static void warning(String s) { warning(s, true); }
	public static void warning(String s, boolean println) { color(s, YELLOW+HIGHLIGHT, true, println); }
	public static void warning(String s, Throwable err) {
		color(s, YELLOW+HIGHLIGHT, false, true);
		err.printStackTrace();
		reset();
	}

	public static void error(String s) { error(s, true); }
	public static void error(String s, boolean println) { color(s, RED+HIGHLIGHT, true, println); }
	public static void error(String s, Throwable err) {
		color(s, RED+HIGHLIGHT, false, true);
		err.printStackTrace();
		reset();
	}

	/**
	 * @param min 最小值(包括)
	 * @param max 最大值(不包括)
	 */
	public static int readInt(int min, int max) { return getCommandResult("您的选择: ", Argument.number(min, max)); }
	public static boolean readBoolean(String info) throws IOException { return getCommandResult(info, Argument.bool()); }
	public static File readFile(String name) { return getCommandResult("请输入"+name+"的路径", Argument.file()); }
	public static int readChosenFile(List<File> files, String name1) {
		if (files.size() == 1) return 0;
		info("有多个 "+name1+" , 请选择(输入编号)");

		for (int i = 0; i < files.size(); i++) {
			String name = files.get(i).getName();
			int k = name.lastIndexOf(File.separatorChar);
			fg(WHITE, (i & 1) == 1);
			System.out.println(i + ". " + (k == -1 ? name : name.substring(0, k)));
			reset();
		}

		return readInt(0, files.size());
	}
	public static void pause() { readString("按回车继续"); }
	public static String readString(String info) { return getCommandResult(info, Argument.rest()).trim(); }
	// relatively safe when ANSI not available
	public static char[] readPassword() { return System.console().readPassword(""); }
	public static <T> T getCommandResult(String prompt, Argument<T> argument) {
		if (!ANSI) {
			while (true) {
				System.out.print(prompt);
				try {
					String s = in.readLine();
					ArgumentContext ctx = new ArgumentContext(null);
					ctx.init(s, Tokenizer.arguments().split(s));
					return argument.parse(ctx, null);
				} catch (Exception e) {
					System.out.println("输入不合法:"+e);
				}
			}
		}
		return awaitCommand(new CommandConsole("\u001B[;"+(WHITE+HIGHLIGHT)+'m'+prompt), argument);
	}
	@SuppressWarnings("unchecked")
	public static <T> T awaitCommand(CommandConsole c, Argument<T> arg) {
		Console prev = console;

		Object[] ref = new Object[1];
		CommandNode node = CommandNode.argument("arg", arg).executes(ctx -> {
			synchronized (ref) {
				ref[0] = ctx.argument("arg", Object.class);
				ref.notify();
				setConsole(prev);
			}
		});
		c.register(node);
		Thread t = Thread.currentThread();
		c.onKeyboardInterrupt(t::interrupt);
		c.ctx.executor = null;

		setConsole(c);
		try {
			synchronized (ref) { ref.wait(); }
		} catch (InterruptedException e) {
			return Helpers.maybeNull();
		} finally {
			c.onKeyboardInterrupt(null);
			c.unregister(node);
			setConsole(prev);
		}

		return (T) ref[0];
	}

	public static char awaitCharacter(MyBitSet allow) { return awaitCharacter(allow, true); }
	public static char awaitCharacter(MyBitSet allow, boolean exitOnCancel) {
		Console prev = console;

		char[] ref = new char[1];
		Console c = new Console() {
			public void registered() {
				if (line != null) renderBottomLine(line, false, getStringWidth(line)+1);
			}
			public void unregistered() {
				if (line != null) removeBottomLine(line, true);
			}
			public void keyEnter(int keyCode, boolean isVirtualKey) {
				synchronized (ref) {
					ref[0] = (char) keyCode;
					ref.notify();
				}
			}
		};

		setConsole(c);
		try {
			while (true) {
				synchronized (ref) {
					ref.wait();

					char ch = ref[0];
					if (ch == (VK_CTRL|VK_C)) return 0;
					if (allow == null || allow.contains(ch)) return ch;
				}
				beep();
			}
		} catch (InterruptedException e) {
			if (exitOnCancel) System.exit(1);
			return 0;
		} finally {
			setConsole(prev);
		}
	}

	public static void beep() { sysOut.write(7); sysOut.flush(); }

	public static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B(?:\\[[^a-zA-Z]+?[a-zA-Z]|].*?\u0007)");
	public static ByteList stripAnsi(ByteList b) {
		Matcher m = ANSI_ESCAPE.matcher(b);
		ByteList out = b.slice(0, b.length()); out.clear();

		int i = 0;
		while (m.find(i)) {
			out.put(b, i, m.start()-i);
			i = m.end();
		}
		out.put(b, i, b.length()-i);
		b.wIndex(out.wIndex());
		return b;
	}
	public static CharList stripAnsi(CharList b) {
		Matcher m = ANSI_ESCAPE.matcher(b);

		int i = 0;
		while (m.find(i)) {
			b.delete(m.start(), m.end());
			i = m.start();
		}
		return b;
	}

	private static Int2IntMap CharLength = new Int2IntMap();
	public static int getCharWidth(char c) {
		if (c < 32) return c == '\t' ? 8 : 0;
		if (TextUtil.isPrintableAscii(c)) return 1;
		if (TextUtil.isChinese(c)) return 2;

		if (!ANSI || CharLength == null) return ChineseCharsetDetector.标点.contains(c) ? 2 : 1;

		int len = CharLength.getOrDefaultInt(c, -1);
		if (len >= 0) return len;

		try {
			reader.getCursorPos();
		} catch (Exception e) {
			CharLength = null;
			return 2;
		}

		// https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters
		// 这也太邪门了...... 但是可行，速度还飞快。。
		synchronized (sysOut) {
			ByteList bb = SEQ.putAscii("\u001b7\u001b[1E");
			CE.encodeFixedIn(IOUtil.getSharedCharBuf().append(c), bb);
			writeSeq(bb);

			try {
				len = (reader.getCursorPos()&0xFF)-1;
				CharLength.put(c, len);
			} catch (Exception e) {
				assert false;
			}

			writeSeq(bb.putAscii("\u001b[1K\u001b8"));
			return len;
		}
	}
	public static int getStringWidth(CharSequence s) {
		s = ANSI_ESCAPE.matcher(s).replaceAll("");

		int len = 0, maxLen = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\r' || c == '\n') { maxLen = Math.max(maxLen, len); len = 0; }
			if (Character.isHighSurrogate(c)) {
				// 控制台支持么，大概是否定的
				i++;
				len += 2;
				continue;
			}
			len += getCharWidth(c);
		}
		return Math.max(maxLen, len);
	}
	public static List<String> splitByWidth(String str, int width) {
		Matcher m = str.indexOf('\u001b') >= 0 ? ANSI_ESCAPE.matcher(str) : null;

		List<String> out = new SimpleList<>();
		int prevI = 0, tmpWidth = 0;
		for (int i = 0; i < str.length(); ) {
			char c = str.charAt(i);
			if (c == '\n') {
				out.add(str.substring(prevI, i));
				prevI = ++i;
				tmpWidth = 0;
			} else if (c == '\u001b') {
				if (m.find(i)) i = m.end();
				else throw new RuntimeException("未识别的转义"+Tokenizer.addSlashes(str)+",请更新ANSI_ESCAPE PATTERN");
			} else {
				int w = getCharWidth(c);
				if (tmpWidth+w > width) {
					out.add(str.substring(prevI, i));
					prevI = i;
					tmpWidth = w;
				} else {
					tmpWidth += w;
				}

				i++;
			}
		}
		if (prevI < str.length()) out.add(str.substring(prevI));
		return out;
	}

	private CLIUtil() {}

	// region bottom line
	static final SimpleList<CharList> LINES = new SimpleList<>();
	static int LineCursor, PrevWidth;

	public static boolean hasBottomLine(CharList prompt) { return LINES.indexOfAddress(prompt) >= 0; }

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

		synchronized (System.out) {
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
							bb.putAscii("\u001b["+(LineCursor = cursor)+"G");
						}
					} else {
						//bb.put('\n');
						CE.encodeFixedIn(b, bb);
						LINES.add(0, b);
					}
				} else {
					if (pos > 0) bb.putAscii("\u001b7\u001b["+pos+"F");
					else bb.putAscii("\u001b[1G");
					CE.encodeFixedIn(b, bb);
					bb.putAscii("\u001b[0K");
					if (pos > 0) bb.putAscii("\u001b8");
					else bb.putAscii("\u001b["+(LineCursor = cursor)+"G");
				}

				writeSeq(bb.putAscii("\u001b[?25h"));
			}
		}
	}
	public static void removeBottomLine(CharList b, boolean clearText) {
		synchronized (System.out) {
			synchronized (sysOut) {
				int pos = LINES.indexOfAddress(b);
				if (pos < 0) return;
				LINES.remove(pos);

				writeSeq(SEQ.putAscii(LINES.isEmpty() & PrevWidth == 0 ? "\u001b[1M" : "\u001b7\u001b["+(pos-1)+"F\u001b[1M\u001b8\u001bM"));
				if (!clearText) System.out.println(b);
			}
		}
	}
	static void writeSeq(ByteList b) {
		sysOut.write(b.list, 0, b.wIndex());
		b.clear();
	}

	static final class AnsiOut extends DelegatedPrintStream {
		private boolean started;

		AnsiOut() {}

		@Override
		protected void flushBytes() {}
		@Override
		protected void newLine() {
			begin();
			PrevWidth = 0;
			writeSeq(bb.put('\n'));
		}
		@Override
		protected void partialLine() {
			begin();

			int width = getStringWidth(bb);
			if (width > windowWidth) {
				CharList buf = IOUtil.getSharedCharBuf();
				CE.decodeFixedIn(bb, bb.readableBytes(), buf);
				List<String> lines = splitByWidth(buf.toString(), windowWidth);

				for (int i = 0; i < lines.size(); i++) {
					bb.clear();
					CE.encodeFixedIn(lines.get(i), bb);

					// soft warp...
					sysOut.write(bb.list, 0, bb.wIndex());
				}

				width = getStringWidth(bb);
			} else {
				sysOut.write(bb.list, 0, bb.wIndex());
			}

			if (width > 0) sysOut.write('\n');
			PrevWidth = width;
		}
		private void begin() {
			CE.encodeFixedIn(sb, bb);sb.clear();
			if (!ANSI) stripAnsi(bb);
			else {
				int advance = LINES.size() + (PrevWidth > 0 ? 1 : 0);
				if (advance > 0 && !started) {
					SEQ.putAscii("\u001b[?25l");
					if (advance > 1) SEQ.putAscii("\u001b["+(advance-1)+"F");
					else SEQ.putAscii("\u001b[1G");
					writeSeq(SEQ.putAscii("\u001b[2K"));
					started = true;
				}
			}
		}

		@Override
		public void flush() {
			if (started) {
				ByteList s = SEQ;
				int i = LINES.size()-1;
				if (i >= 0) while (true) {
					try {
						CE.encodeFixedIn(LINES.get(i), s);
					} catch (Throwable ignored) {}
					s.putAscii("\u001b[0K");
					if (i-- == 0) {
						if (LineCursor > 0) s.putAscii("\u001b["+LineCursor+"G");
						break;
					}
					s.put('\n');
				}
				writeSeq(s.putAscii("\u001b[?25h"));
			}
			started = false;
		}
	}
	// endregion

	private volatile int state;
	public void run() {
		if (state == 0) {
			try {
				System.out.print("\u001b[6n");
				int r = System.in.read();
				if (r < 0) {
					state = -1;
					return;
				}

				if (state == 0) state = 1;
				else sysErr.println("对System.in.read()的调用超时");
			} catch (Throwable e) {
				Helpers.athrow(e);
			} finally {
				synchronized (this) { notify(); }
			}
		}

		ByteList.Slice shellB = IOUtil.SharedCoder.get().shellB;
		byte[] buf = new byte[260];

		while (true) {
			int r;
			try {
				r = sysIn.read(buf, 0, 256);
			} catch (ArrayIndexOutOfBoundsException e) {
				sysErr.println("Windows Terminal又在打电动了:"+e.getMessage());
				continue;
			} catch (Throwable e) {
				e.printStackTrace(sysErr);
				break;
			}

			if (r < 0) {
				buf[0] = 0x1a;
				r = 1;
			}

			try {
				if (ANSI) processInput(buf, 0, r, shellB);
				else for (int i = 0; i < r; i++)
					console.keyEnter(buf[i]&0xFF, buf[i] == '\n');
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		sysErr.println("Console handler terminated!");
	}
	// region PipeInputStream
	private static final int RING_BUFFER_CAPACITY = 4096;
	private final byte[] Pipe = new byte[RING_BUFFER_CAPACITY];
	private int rPtr, wPtr, waiters;

	synchronized void write(byte[] buf, int off, int len) {
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

		notify();
	}
	private static final Console PIPE = new DefaultConsole("") {
		@Override
		public void keyEnter(int keyCode, boolean isVirtualKey) {
			if (keyCode == (VK_CTRL|VK_C)) {
				sysOut.println("Received SIGINT");
				System.exit(0);
			}

			if (reader.waiters == 0) return;
			super.keyEnter(keyCode, isVirtualKey);
		}

		@Override
		protected boolean evaluate(String cmd) {
			System.out.println(cmd);

			ByteList b = IOUtil.getSharedByteBuf().putUTFData(cmd).put('\n');
			reader.write(b.list, 0, b.wIndex());
			return true;
		}
	};
	// endregion

	@NotNull
	private static volatile Console console = PIPE;
	public static void setConsole(Console c) {
		if (c == null) c = PIPE;

		synchronized (CLIUtil.class) {
			Console prev = console;
			if (prev == c) return;
			prev.unregistered();

			console = c;
		}

		c.registered();
	}
	public static Console getConsole() {return console == PIPE ? null : console;}

	// region keyboard handling
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

	private final MyHashMap.Entry<CInt, Integer> matcher = new MyHashMap.Entry<>(new CInt(), null);
	private void processInput(byte[] buf, int off, int len, ByteList.Slice inBuf) {
		inBuf.setR(buf, off, len);
		int i = 0;

		while (i < len) {
			KeyMap.match(inBuf, i, len, matcher);
			int matchLen = matcher.getKey().value;
			if (matchLen < 0) {
				int keyCode = buf[i++];
				console.keyEnter(keyCode, false);
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
				console.keyEnter(matcher.v, true);
			}

			i += matchLen;
		}
	}
	private void escEnter(ByteList buf) {
		int type = buf.getU(buf.wIndex()-1);
		if (type == 'R') {
			int i = TextUtil.gIndexOf(buf, ';');
			int height = TextUtil.parseInt(buf, 0, i);
			int width = TextUtil.parseInt(buf, i+1, buf.wIndex()-1);
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
	// endregion
	// region cursor position
	public static int windowHeight, windowWidth;

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
						lcb.wait(30);
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
				int winSize = reader.getCursorPos();
				windowWidth = winSize&0xFFFF;
				windowHeight = winSize >>> 16;
			} finally {
				writeSeq(SEQ.putAscii("\u001b8\u001b[?25h"));
			}
		}
	}
	// endregion

	static {
		ANSI = initialize();
		System.setIn(new MBInputStream() {
			@Override
			public int read(@NotNull byte[] b, int off, int len) throws IOException {
				ArrayUtil.checkRange(b, off, len);
				if (len == 0) return 0;

				synchronized (reader) {
					if (reader.waiters++ == 0 && console == PIPE) PIPE.registered();
					try {
						while (reader.rPtr == reader.wPtr) {
							try {
								reader.wait();
							} catch (InterruptedException e) {
								throw new ClosedByInterruptException();
							}
						}
					} finally {
						if (--reader.waiters == 0 && console == PIPE) PIPE.unregistered();
					}

					int read = Math.min(len, available());
					System.arraycopy(reader.Pipe, reader.rPtr, b, off, read);
					reader.rPtr = (reader.rPtr+read) & (RING_BUFFER_CAPACITY - 1);

					if (reader.rPtr != reader.wPtr) reader.notify();
					return read;
				}
			}
			@Override
			public int available() {
				int readLen = reader.wPtr - reader.rPtr;
				return readLen < 0 ? readLen+RING_BUFFER_CAPACITY : readLen;
			}
		});
		in = new BufferedReader(new InputStreamReader(System.in));
	}
}