package roj.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.RojLib;
import roj.collect.*;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.text.CharList;
import roj.text.GB18030;
import roj.text.TextUtil;
import roj.text.UnsafeCharset;
import roj.ui.terminal.*;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.*;
import java.nio.channels.ClosedByInterruptException;
import java.nio.charset.Charset;
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
public final class Terminal extends DelegatedPrintStream {
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
			if (!ANSI_OUTPUT) sb.append(s);
			else minecraftTooltip(rainbow, s, 0.05f, sb);
		}

		public static void sonic(String s, CharList sb) {
			if (!ANSI_OUTPUT) sb.append(s);
			else minecraftTooltip(sonic, s, 0.07f, sb);
		}
	}

	private Terminal() {}

	//region AnsiOutput基础
	public static void reset() {if (ANSI_OUTPUT) System.out.print("\u001B[0m");}
	public static void info(String s) {color(s, WHITE+HIGHLIGHT, true);}
	public static void success(String s) {color(s, GREEN+HIGHLIGHT, true);}
	public static void warning(String s) {color(s, YELLOW+HIGHLIGHT, true);}
	public static void error(String s) {color(s, RED+HIGHLIGHT, true);}
	public static void warning(String s, Throwable err) {
		color(s, YELLOW+HIGHLIGHT, false);
		err.printStackTrace();
		reset();
	}
	public static void error(String s, Throwable err) {
		color(s, RED+HIGHLIGHT, false);
		err.printStackTrace();
		reset();
	}
	private static void color(String s, int fg, boolean reset) {
		var o = System.out;
		if (ANSI_OUTPUT) o.print("\u001B[;"+fg+'m');
		o.println(s);
		if (ANSI_OUTPUT&reset) o.print("\u001B[0m");
	}

	// [BEL]
	public static void beep() {
		if (!ANSI_OUTPUT) {
			System.out.write(7);
			System.out.flush();
		} else {
			synchronized (out) {
				write("\7");
			}
		}
	}
	public static void directWrite(CharSequence s) {
		if (!ANSI_OUTPUT) {
			System.out.print(stripAnsi(s instanceof CharList c ? c : new CharList().append(s)));
		} else {
			synchronized (out) {
				write(s);
			}
		}
	}

	public static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B(?:\\[[^a-zA-Z]+?[a-zA-Z]|].*?\u0007)");
	private static ByteList stripAnsi(ByteList b) {
		var m = ANSI_ESCAPE.matcher(b);
		var out = b.slice(); out.clear();

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
	//endregion
	//region AnsiOutput - System.out.write(byte[]) 的字符集转换
	public static final Charset nativeCharset;
	@Override
	protected void flushBytes() {
		NativeVT.ucs.decodeLoop(bb, bb.readableBytes(), sb, Integer.MAX_VALUE, true);
		if (bb.isReadable()) sb.append(UnsafeCharset.INVALID);
		bb.clear();
	}
	//endregion
	// region AnsiOutput进阶 - BottomLine
	private static final CharList OSEQ = new CharList(256);
	private static void writeSeq(CharList b) {write(b);b.clear();}

	private static final SimpleList<CharList> LINES = new SimpleList<>();
	private static final IntList CURSORS = new IntList();
	private static int PrevWidth;

	public static boolean hasBottomLine(CharList prompt) {return LINES.indexOfAddress(prompt) >= 0;}
	public static void renderBottomLine(CharList b) {renderBottomLine(b, false, 0);}
	/**
	 * 渲染【一条线】.
	 * 这条线会按注册时的顺序渲染在屏幕底部，并且不受System.xxx.println的影响
	 * @param atTop 仅未注册时有效：true在上方插入，否则下方
	 * @param cursor 仅b为最下方时有效：光标位置 (0为最后, 从1开始计数)
	 */
	public static void renderBottomLine(CharList line, boolean atTop, int cursor) {
		if (!ANSI_OUTPUT) {System.out.println(stripAnsi(line));return;}
		synchronized (out) {
			var sb = OSEQ.append("\u001b[?25l");

			int pos = LINES.indexOfAddress(line);
			if (pos < 0) {
				if (LINES.size() >= windowHeight) {out.println(line);return;}

				if (atTop || LINES.isEmpty()) {
					LINES.add(line);
					CURSORS.add(cursor);
					if (LINES.size() > 1) {
						int i = LINES.size()-1;
						sb.append("\u001b[").append(i).append("F\r\n");
						while (true) {
							sb.append(LINES.get(i)).append("\u001b[0K");
							if (i-- == 0) break;
							sb.append("\r\n");
						}
					} else {
						if (PrevWidth > 0) sb.append("\r\n");
						sb.append(line).append("\u001b[").append(cursor).append('G');
					}
				} else {
					sb.append(line);
					LINES.add(0, line);
					CURSORS.add(0, cursor);
				}
			} else {
				CURSORS.set(pos, cursor);
				if (pos > 0) sb.append("\u001b7\u001b[").append(pos).append('F');
				else sb.append("\u001b[1G");
				sb.append(line).append("\u001b[0K");
				if (pos > 0) sb.append("\u001b8");
				else sb.append("\u001b[").append(cursor).append('G');
			}

			writeSeq(sb.append("\u001b[?25h"));
			_flush();
		}
	}
	public static void removeBottomLine(CharList b, boolean clearText) {
		synchronized (out) {
			int pos = LINES.indexOfAddress(b);
			if (pos < 0) return;
			LINES.remove(pos);
			CURSORS.remove(pos);

			var seq = LINES.isEmpty() & PrevWidth == 0 ? OSEQ.append("\u001b[1M") : OSEQ.append("\u001b7\u001b[").append(pos-1).append("F\u001b[1M\u001b8\u001bM");
			int cursor = !CURSORS.isEmpty() ? CURSORS.get(0) : PrevWidth+1;
			if (cursor > 1) seq.append("\u001b[").append(cursor).append('G');
			writeSeq(seq);

			if (!clearText) out.println(b);
			else _flush();
		}
	}

	private boolean started;
	@Override
	protected void newLine() {
		begin();
		write(sb.append("\r\n"));
		sb.clear();
		PrevWidth = 0;
	}
	@Override
	protected void partialLine() {
		begin();
		write(sb);

		int width = getStringWidth(sb);
		if (width > windowWidth) {
			var lines = splitByWidth(sb.toString(), windowWidth);
			var last = lines.get(lines.size()-1);
			sb.delete(0, sb.length() - last.length());
			width = getStringWidth(sb);
		}

		PrevWidth = width;
	}
	private void begin() {
		flushBytes();

		var seq = OSEQ;
		if (!ANSI_OUTPUT) stripAnsi(sb);
		else {
			int advance = LINES.size() + (PrevWidth > 0 ? 1 : 0);
			if (advance > 0 && !started) {
				seq.append("\u001b[?25l");
				if (advance > 1) seq.append("\u001b[").append(advance-1).append('F');
				else seq.append("\u001b[1G");
				writeSeq(seq.append("\u001b[2K"));
				started = true;
			}
		}
	}

	@Override
	public void flush() {
		var seq = OSEQ;
		if (started) {
			int i = LINES.size()-1;
			if (i >= 0) {
				if (PrevWidth > 0) seq.append("\r\n");
				while (true) {
					seq.append(LINES.get(i));
					seq.append("\u001b[0K");
					if (i-- == 0) {
						int cursor = CURSORS.get(0);
						if (cursor > 0) seq.append("\u001b[").append(cursor).append('G');
						break;
					}
					seq.append("\r\n");
				}
			}
			writeSeq(seq.append("\u001b[?25h"));
			started = false;
		}
		_flush();
	}
	// endregion

	// region System.in "回调"
	private static AnsiIn ALT;
	private static final class AnsiIn extends MBInputStream {
		private final ByteList buf = new ByteList(1024);
		private int reader;

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			ArrayUtil.checkRange(b, off, len);
			if (len == 0) return 0;

			var _buf = buf;
			synchronized (this) {
				if (console != PIPE) throw new IOException("Stream closed (只有在未注册Console时，才能使用System.in)");
				if (reader++ == 0) PIPE.registered();
				try {
					while (_buf.readableBytes() == 0) {
						try {
							wait();
						} catch (InterruptedException e) {
							throw new ClosedByInterruptException();
						}
					}
				} finally {
					if (--reader == 0) PIPE.unregistered();
				}

				int read = Math.min(len, _buf.readableBytes());
				_buf.readFully(b, off, read);
				// 还有数据，唤醒另一个线程
				if (_buf.readableBytes() > 0) notify();
				return read;
			}
		}

		@Override
		public int available() {return buf.readableBytes();}

		synchronized void write(DynByteBuf b) {
			buf.compact().put(b);
			notify();
		}
	}
	private static final Console PIPE = new DefaultConsole("") {
		@Override
		public void keyEnter(int keyCode, boolean isVirtualKey) {
			if (keyCode == (VK_CTRL|VK_C)) {
				System.out.println("SIGINT");
				System.exit(0);
			}

			if (ALT.reader == 0) return;
			super.keyEnter(keyCode, isVirtualKey);
		}

		@Override
		protected boolean evaluate(String cmd) {
			System.out.println(cmd);
			ALT.write(IOUtil.getSharedByteBuf().putUTFData(cmd).putShort(0x0D0A));
			return true;
		}

		@Override
		public void idleCallback() {
			if (ALT.reader != 0) super.idleCallback();
		}
	};
	// endregion
	@NotNull
	private static volatile Console console = PIPE;
	public static void setConsole(Console c) {
		if (c == null) c = PIPE;

		synchronized (Terminal.class) {
			var prev = console;
			if (prev == c) return;
			prev.unregistered();

			console = c;
		}

		if (c != PIPE) c.registered();
	}
	public static Console getConsole() {return console;}
	//region AnsiInput - 处理字符键入
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
	private static void key(int vk, String seq) {KeyMap.put(TextUtil.hex2bytes(seq, new ByteList()), vk);}

	private static final CharList ISEQ = new CharList(256);
	public static void onInput(DynByteBuf buf, UnsafeCharset ucs) {
		var iseq = ISEQ; iseq.clear();
		ucs.decodeLoop(buf, buf.readableBytes(), iseq, Integer.MAX_VALUE, true);
		onInput(iseq);
		buf.compact();
	}
	// 这个方法不加锁是有意的，加锁的话获取字符串宽度会变得非常麻烦
	public static void onInput(CharList buf) {
		out.processInput(buf);
		console.idleCallback();
	}

	private final MyHashMap.Entry<CInt, Integer> matcher = new MyHashMap.Entry<>(new CInt(), null);
	private void processInput(CharList inBuf) {
		var buf = inBuf.list;
		int i = 0;
		int len = inBuf.length();
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
							var b = buf[++j];
							if (b >= 'a' && b <= 'z') break;
							if (b >= 'A' && b <= 'Z') break;
						}

						matchLen = j-i+1;
						escEnter(inBuf, i+2, j-i);
						break found;
					}
				}
				console.keyEnter(matcher.v, true);
			}

			i += matchLen;
		}
	}
	private void escEnter(CharList buf, int start, int end) {
		int type = buf.charAt(end);
		if (type == 'R') {
			int i = TextUtil.gIndexOf(buf, ";", start, end);
			if (i >= 0) {
				int height = TextUtil.parseInt(buf, start, i);
				int width = TextUtil.parseInt(buf, i+1, end);
				cursorPos = (height << 16) | width;
				return;
			}
		}
		serr.println("未识别的ANSI转义: "+buf.substring(start, end+1)+" (考虑报告该问题)");
	}
	//endregion
	//region AnsiInput基础
	public static void pause() {readString("按回车继续");}
	/**
	 * @param min 最小值(包括)
	 * @param max 最大值(包括)
	 */
	public static int readInt(int min, int max) {return readLine("您的选择: ", Argument.number(min, max));}
	public static boolean readBoolean(String info) {return readLine(info, Argument.bool());}
	public static File readFile(String name) {return readLine("请输入"+name+"的路径", Argument.file());}
	public static int readChosenFile(List<File> files, String desc) {
		if (files.size() <= 1) return 0;
		info("在多个 "+desc+" 中输入序号来选择");

		for (int i = 0; i < files.size(); i++) {
			String name = files.get(i).getName();
			if (ANSI_OUTPUT) System.out.print("\u001B["+(WHITE+(i&1)*HIGHLIGHT)+'m');
			System.out.println(i+". "+name);
			reset();
		}

		return readInt(0, files.size());
	}
	public static String readString(String info) {return readLine(info, Argument.rest()).trim();}
	/**
	 * 从用户输入中读取符合参数要求的对象.
	 * 如果输入Ctrl+C，退出程序
	 */
	private static <T> T readLine(String prompt, Argument<T> arg) {
		T v = readLine(new CommandConsole("\u001B[;"+(WHITE+HIGHLIGHT)+'m'+prompt), arg);
		if (v == null) System.exit(1);
		return v;
	}
	//endregion
	//region AnsiInput进阶 - 读取参数
	/**
	 * 从用户输入中读取符合参数要求的对象.
	 * 如果输入Ctrl+C，返回null
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	public static <T> T readLine(CommandConsole c, Argument<T> arg) {
		if (!ANSI_INPUT) {
			while (true) {
				System.out.print(c.getPrompt());
				try {
					String s = in.readLine();
					CommandParser ctx = new CommandParser(null);
					ctx.init(s, Tokenizer.arguments().split(s));
					return arg.parse(ctx, null);
				} catch (Exception e) {
					System.out.println("输入不合法:"+e);
				}
			}
		}
		var prev = console;

		var ref = new Object[1];
		CommandNode node = CommandNode.argument("", arg).executes(ctx -> {
			synchronized (ref) {
				ref[0] = ctx.argument("", Object.class);
				ref.notify();
				setConsole(prev);
			}
		});
		c.register(node);

		// 真是不优雅……
		var t = Thread.currentThread();
		c.onKeyboardInterrupt(t::interrupt);
		c.ctx.executor = null;

		setConsole(c);
		try {
			synchronized (ref) {ref.wait();}
		} catch (InterruptedException e) {
			return Helpers.maybeNull();
		} finally {
			c.onKeyboardInterrupt(null);
			c.unregister(node);
			setConsole(prev);
		}

		return (T) ref[0];
	}

	/**
	 * @see #readChar(MyBitSet, CharList, boolean)
	 */
	public static char readChar(MyBitSet chars) {return readChar(chars, null, true);}
	/**
	 * 从标准输入中读取一个被chars允许的字符.
	 * 如果输入的字符不允许，发出哔哔声
	 * 如果输入Ctrl+C，返回\0
	 * @param prefix 提示用户的前缀
	 * @param exitIfCancel 在等待输入时被中断，如果为真那么退出程序，否则返回\0. 在非ANSI_INPUT下该项可能无效
	 */
	public static char readChar(MyBitSet chars, CharList prefix, boolean exitIfCancel) {
		if (!ANSI_INPUT) {
			System.out.print(prefix == null ? "请输入一个字符并回车" : prefix);
			try {
				String s;
				for(;;) {
					s = in.readLine();
					if (s == null) return 0;
					if (s.length() <= 1 && chars == null || chars.contains(s.length() == 0 ? '\n' : s.charAt(0))) break;

					System.out.print("不允许这个字符,请再试一次: ");
				}
				return s.isEmpty() ? '\n' : s.charAt(0);
			} catch (Exception e) {
				if (exitIfCancel) System.exit(1);
				return 0;
			}
		}

		var ref = new char[1];
		Console c = (keyCode, isVirtualKey) -> {
			synchronized (ref) {
				ref[0] = (char) keyCode;
				ref.notifyAll();
			}
		};

		var prev = console;
		setConsole(c);
		if (prefix != null) renderBottomLine(prefix, false, getStringWidth(prefix)+1);
		try {
			while (true) {
				synchronized (ref) {
					ref.wait();

					char ch = ref[0];
					if (chars == null || chars.contains(ch)) return ch;
					if (ch == (VK_CTRL|VK_C)) return 0;
				}
				beep();
			}
		} catch (InterruptedException e) {
			if (exitIfCancel) System.exit(1);
			return 0;
		} finally {
			if (prefix != null) removeBottomLine(prefix, true);
			setConsole(prev);
		}
	}
	//endregion
	//region AnsiInput进阶 - 读取光标位置
	private static final Object CURSOR_LOCK = new Object();
	private volatile int cursorPos;
	// public版本等用到的时候再写
	private int readCursor() throws IOException {
		int val;
		if (!terminals.get(0).readBack(false)) {
			long endTime = System.currentTimeMillis() + 10;
			do {
				// 等待escEnter通知
				if ((val = cursorPos) > 0) break;
			} while (System.currentTimeMillis() < endTime);
		} else {
			val = cursorPos;
		}

		if (val == 0) throw new IllegalStateException("读取光标位置失败");
		return val;
	}

	// 宽度是字符，高度是行，下面仅仅是windows的默认值作为fallback，没有特殊意义
	public static int windowHeight = 30, windowWidth = 120;
	public static final String GET_CONSOLE_DIMENSION = "\u001b[?25l\u001b7\u001b[999E\u001b[999C\u001b[6n\u001b8\u001b[?25h";
	public static void updateConsoleSize() throws IOException {
		var t = terminals.get(0);
		synchronized (CURSOR_LOCK) {
			if (out.cursorPos == 0) throw new IllegalStateException("递归读取光标?");
			out.cursorPos = 0;

			synchronized (out) {
				t.write(GET_CONSOLE_DIMENSION);
				t.flush();
			}

			int winSize = out.readCursor();
			windowWidth = winSize&0xFFFF;
			windowHeight = winSize >>> 16;
		}
	}
	//endregion
	//region AnsiInput进阶 - 计算字符宽度以便换行
	private static final Int2IntMap CharLength = new Int2IntMap();
	/**
	 * 计算字符的长度(按终端英文记)
	 */
	public static int getCharWidth(char c) {
		if (c < 32) return c == '\t' ? 8 : 0;
		if (TextUtil.isPrintableAscii(c)) return 1;
		// 大概是乱码，而且在能渲染的终端中，两个合起来正好是方形
		if (Character.isSurrogate(c)) return 1;
		if (GB18030.isTwoByte(c) || CharLength.size() == 0) return 2;

		int len = CharLength.getOrDefaultInt(c, -1);
		if (len >= 0) return len;

		var t = terminals.get(0);
		// https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters
		// 这也太邪门了……
		synchronized (CURSOR_LOCK) {
			if (CharLength.size() == 0) return 2;

			if (out.cursorPos == 0) throw new IllegalStateException("递归读取光标?");
			out.cursorPos = 0;

			synchronized (out) {
				t.write(OSEQ.append("\u001b7\u001b[1E").append(c).append("\u001b[6n\u001b[1K\u001b8"));
				OSEQ.clear();
				t.flush();
			}

			try {
				len = (out.readCursor()&0xFFFF)-1;
				CharLength.put(c, len);
				return len;
			} catch (Exception e) {
				CharLength.clear();
				return 2;
			}
		}
	}
	/**
	 * 计算字符串的长度(按终端英文记)，忽略其中的ANSI转义序列.
	 */
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
	public static void enableAutoStringWidth() {synchronized (CURSOR_LOCK) {if (CharLength.size() == 0) CharLength.put(0, 0);}}
	public static void disableAutoStringWidth() {synchronized (CURSOR_LOCK) {CharLength.clear();}}
	/**
	 * 根据width切分字符串，目前的版本可能会把ANSI转义序列弄丢.
	 * @param width 长度，按终端英文记
	 */
	public static List<String> splitByWidth(String s, int width) {
		var m = s.indexOf('\u001b') >= 0 ? ANSI_ESCAPE.matcher(s) : null;

		List<String> out = new SimpleList<>();
		int prevI = 0, tmpWidth = 0;
		for (int i = 0; i < s.length(); ) {
			char c = s.charAt(i);
			if (c == '\n') {
				out.add(s.substring(prevI, i));
				prevI = ++i;
				tmpWidth = 0;
			} else if (c == '\u001b') {
				//noinspection all
				if (m.find(i)) i = m.end();
				else throw new RuntimeException("未识别的转义"+Tokenizer.addSlashes(s)+",请更新ANSI_ESCAPE PATTERN");
			} else {
				int w = getCharWidth(c);
				if (tmpWidth+w > width) {
					out.add(s.substring(prevI, i));
					prevI = i;
					tmpWidth = w;
				} else {
					tmpWidth += w;
				}

				i++;
			}
		}
		if (prevI < s.length()) out.add(s.substring(prevI));
		return out;
	}
	//endregion
	//region StdIO
	public static final boolean ANSI_INPUT, ANSI_OUTPUT;
	public static final BufferedReader in;
	public static final PrintStream serr = System.err;

	// T和其它终端不同之处在于，光标（字符长度）会通过它获取
	@Nullable
	private static final ITerminal T;
	private static final SimpleList<ITerminal> terminals = new SimpleList<>();
	public static void addListener(ITerminal t) {
		int i = terminals.indexOfAddress(t);
		if (i < 0) terminals.add(t);
	}
	public static void removeListener(ITerminal t) {
		if (T == t) throw new IllegalStateException("Cannot remove Main Terminal");
		int i = terminals.indexOfAddress(t);
		if (i >= 0) terminals.remove(t);
	}

	private static void write(CharSequence b) {
		for (var t : terminals) {
			try {
				t.write(b);
			} catch (Exception ignored) {}
		}
	}
	private static void _flush() {
		for (var t : terminals) {
			try {
				t.flush();
			} catch (Exception ignored) {}
		}
	}
	//endregion
	//out必须最后初始化
	public static final Terminal out = new Terminal();
	public static boolean hasNativeTerminal() {return T != null;}
	static {
		nativeCharset = NativeVT.charset;
		var t = T = NativeVT.getInstance();
		if (t == null) t = (ITerminal) RojLib.inject("roj.ui.Terminal.fallback");
		if (t != null) {
			addListener(t);
			CharLength.put(0, 0);

			System.setOut(out);
			System.setErr(out);
			System.setIn(ALT = new AnsiIn());

			ANSI_OUTPUT = true;
			try {
				ANSI_INPUT = t.readBack(true);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

			int winSize = out.cursorPos;
			if (winSize > 0) {
				windowWidth = winSize&0xFFFF;
				windowHeight = winSize >>> 16;
			}
		} else {
			ANSI_INPUT = false;
			ANSI_OUTPUT = false;
		}

		in = new BufferedReader(new InputStreamReader(System.in), 1024);
	}
}