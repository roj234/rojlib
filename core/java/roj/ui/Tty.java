package roj.ui;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import roj.RojLib;
import roj.collect.*;
import roj.compiler.plugins.eval.Constexpr;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;

import java.io.*;
import java.util.List;
import java.util.Random;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.awt.event.KeyEvent.*;

/**
 * <a href="https://learn.microsoft.com/zh-cn/windows/console/console-virtual-terminal-sequences">控制台虚拟终端序列参考</a>
 * Sequence	代码	说明	行为
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
public final class Tty extends DelegatedPrintStream {
	//region 带颜色的打印和一些辅助类什么的
	public static final int BLACK = 30, BLUE = 34, GREEN = 32, CYAN = 36, RED = 31, PURPLE = 35, YELLOW = 33, WHITE = 37;
	public static final int HIGHLIGHT = 60;

	public static final class TextEffect {
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
				MC_COLOR_JSON.put(color, k);
			}
		}

		public static String getByConsoleCode(int color) { return MC_COLOR_JSON.get(color); }
		public static Text minecraftJsonStyleToString(CMap map) {
			Text sts = minecraftRawStyleToString(map.getString("text"));

			int colorCode = MC_COLOR_JSON.getByValue(map.getString("color").toLowerCase());
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
		public static Text minecraftRawStyleToString(String raw) {
			Matcher m = MINECRAFT_U00A7.matcher(raw);
			if (!m.find()) return new Text(raw);

			int prevI = 0;
			Text root = null;

			do {
				int i = m.start();

				Text tmp = new Text(raw.substring(prevI, i));
				if (root == null) root = tmp;
				else root.append(tmp);

				do {
					char c = raw.charAt(++i);
					if (c <= 'f') {
						tmp.color16(MC_COLOR[TextUtil.h2b(c)]).reset();
					} else {
						switch (c) {
							case 'l': tmp.bold(true); break;
							case 'n': tmp.underline(true); break;
							case 'm': tmp.deleteLine(true); break;
							case 'o': tmp.italic(true); break;
							case 'k': tmp.shiny(true); break;
							case 'r': tmp.reset(); break;
						}
					}
				} while (++i < m.end());

				prevI = m.end();
			} while (m.find(prevI));

			if (prevI < raw.length()) root.append(new Text(raw.substring(prevI)));
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

		public static void minecraftTooltip(char[] codex, String str, int speed, CharList sb) {
			int si = codex.length - (((int) System.currentTimeMillis() & Integer.MAX_VALUE) / speed) % codex.length;
			for (int i = 0; i < str.length(); i++) {
				sb.append("\033[;").append(MC_COLOR[TextUtil.h2b(codex[si++ % codex.length])]).append('m').append(str.charAt(i));
			}
			sb.append("\033[0m");
		}

		public static CharList rainbow(String s, CharList sb) {
			if (!IS_RICH) sb.append(s);
			else {
				//minecraftTooltip(rainbow, s, 70, sb);
				var length = s.length();
				var str = new Text("");

				float hue = (float) ((int) System.currentTimeMillis() / 70 % length) / length;
				for (int i = 0; i < length; i++) {
					int rgb = HueToRGB(hue);
					hue += 0.02f;
					str.append(new Text(String.valueOf(s.charAt(i))).colorRGB(rgb));
				}
				str.writeAnsi(sb);
			}
			return sb;
		}

		public static int HueToRGB(float hue) {
			int r = 0, g = 0, b = 0;
			float h = (hue - (float)Math.floor(hue)) * 6.0f;
			float f = h - (float) Math.floor(h);
			float q = (1.0f - f);
			switch ((int) h) {
				case 0:
					r = 255;
					g = (int) (f * 255.0f + 0.5f);
					break;
				case 1:
					r = (int) (q * 255.0f + 0.5f);
					g = 255;
					break;
				case 2:
					g = 255;
					b = (int) (f * 255.0f + 0.5f);
					break;
				case 3:
					g = (int) (q * 255.0f + 0.5f);
					b = 255;
					break;
				case 4:
					r = (int) (f * 255.0f + 0.5f);
					b = 255;
					break;
				case 5:
					r = 255;
					b = (int) (q * 255.0f + 0.5f);
					break;
			}
			return (r << 16) | (g << 8) | b;
		}

		public static CharList sonic(String s, CharList sb) {
			if (!IS_RICH) sb.append(s);
			else minecraftTooltip(sonic, s, 50, sb);
			return sb;
		}

		public static String shuffle(String in) {
			char[] arr = IOUtil.getSharedCharBuf().append(in).list;
			Random r = new Random();
			for (int i = 0; i < in.length(); i++) {
				int j = r.nextInt(in.length());
				char t = arr[i];
				arr[i] = arr[j];
				arr[j] = t;
			}
			return new String(arr, 0, in.length());
		}

		public static String shiftLeft(String name) {
			char[] arr = IOUtil.getSharedCharBuf().append(name).list;
			System.arraycopy(arr, 1, arr, 0, name.length() - 1);
			arr[name.length() - 1] = name.charAt(0);
			return new String(arr, 0, name.length());
		}
	}

	public static final String reset = "\033[0m";
	public static void reset() {if (IS_RICH) write(reset);}
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
		if (IS_RICH) o.print("\033[;"+fg+'m');
		o.println(s);
		if (IS_RICH &reset) o.print("\033[0m");
	}

	/**
	 * 转义序列生成.
	 * 这里的绝对坐标从零开始
	 */
	public static class Cursor {
		public static final String hide = "\033[?25l", show = "\033[?25h";
		// 一个可选的替代： \033[s和\033[u
		public static final String save = "\0337", restore = "\0338";

		/** 移动到本行开头 */
		public static final String toLeft = "\033[G";
		@Constexpr public static String toHorizontal(int width) {return "\033["+width+"G";}
		/** 绝对移动 */
		@Constexpr public static String to(@Range(from = 0, to = Integer.MAX_VALUE) int x, @Range(from = -1, to = Integer.MAX_VALUE) int y) {
			return y == -1/* 不修改 */ ? "\033["+(x+1)+"G" : "\033["+(y+1)+";"+(x+1)+"H";
		}

		/** 相对移动 */
		@Constexpr public static String move(int rx, int ry) {
			var sb = new StringBuilder();
			if (rx != 0) sb.append("\033[").append(rx < 0 ? (-rx)+"D" : rx+"C");
			if (ry != 0) sb.append("\033[").append(ry < 0 ? (-ry)+"A" : ry+"B");
			return sb.toString();
		}
		/** 移动到相对dy行的开头 */
		@Constexpr public static String moveVertical(int dy) {return dy == 0 ? "" : dy < 0 ? "\033["+(-dy)+"F" : "\033["+dy+"E";}
	}
	public static final class Screen {
		public static final String clearScreen = "\033[2J", clearBefore = "\033[1J", clearAfter = "\033[J";
		public static final String clearLine = "\033[2K", clearLineBefore = "\033[1K", clearLineAfter = "\033[K";

		@Constexpr public static String scrollUp(@Range(from = 1, to = Integer.MAX_VALUE) int lines) {return "\033["+lines+"S";}
		@Constexpr public static String scrollDown(@Range(from = 1, to = Integer.MAX_VALUE) int lines) {return "\033["+lines+"T";}
	}

	public static void beep() {
		if (!IS_RICH) {
			System.out.write(7);
			System.out.flush();
		} else {
			synchronized (instance) {
				instance.writeAll("\7");
				instance.flushAll();
			}
		}
	}
	public static void write(CharSequence s) {
		if (!IS_RICH) {
			System.out.print(stripAnsi(s instanceof CharList c ? c : new CharList().append(s)));
		} else {
			synchronized (instance) {
				instance.writeAll(s);
				instance.flushAll();
			}
		}
	}
	//endregion

	public static final boolean IS_INTERACTIVE, IS_RICH;

	public static Tty getInstance() {return instance;}

	public static boolean isFullUnicode() {return !instance.terminals.isEmpty() && (instance.terminals.get(0).characteristics()&ITty.UNICODE) != 0;}

	private static final PrintStream serr = System.err;
	/**
	 * 给文本部分的调试设计，sout可能会Stackoverflow
	 */
	public static void printError(String text) {serr.println(text);}

	//region 旧版 静态方法
	public static boolean hasBottomLine(CharList prompt) {return instance.hasBottomLine1(prompt);}
	public static void renderBottomLine(CharList b) {renderBottomLine(b, false, 0);}
	/**
	 * 渲染【一条线】.
	 * 这条线会按注册时的顺序渲染在屏幕底部，并且不受System.xxx.println的影响
	 * @param atTop 仅未注册时有效：true在上方插入，否则下方
	 * @param cursor 仅b为最下方时有效：光标位置 (0为最后, 从1开始计数)
	 */
	public static void renderBottomLine(CharList line, boolean atTop, int cursor) {
		if (!IS_RICH) {System.out.println(stripAnsi(line));return;}
		instance.renderBottomLine1(line, atTop, cursor);
	}
	public static void removeBottomLine(CharList b) {instance.removeBottomLine1(b);}
	/**
	 * 计算字符的长度(按终端英文记)
	 */
	public static int getCharWidth(char c) {return instance.getCharWidth1(c);}
	/**
	 * 计算字符串的长度(按终端英文记)，忽略其中的ANSI转义序列.
	 */
	public static int getStringWidth(CharSequence s) {return instance.getStringWidth1(s);}
	/**
	 * 根据width切分字符串，目前的版本可能会把ANSI转义序列弄丢.
	 * @param width 长度，按终端英文记
	 */
	public static List<String> splitByWidth(String s, int width) {return instance.splitByWidth1(s, width);}

	public static @NotNull KeyHandler getHandler() {return instance.getHandler1();}
	public static void pushHandler(KeyHandler c) {instance.pushHandler1(c);}
	public static void setHandler(KeyHandler c) {instance.setHandler1(c);}
	public static KeyHandler popHandler() {return instance.popHandler1();}

	public static int getColumns() {return instance.columns;}
	public static int getRows() {return instance.rows;}
	//endregion

	public static final Pattern ANSI_ESCAPE = Pattern.compile("\033(?:\\[[^a-zA-Z]*?[a-zA-Z]|].*?\u0007)");
	public static CharList stripAnsi(CharList b) {
		Matcher m = ANSI_ESCAPE.matcher(b);

		int i = 0;
		while (m.find(i)) {
			b.delete(m.start(), m.end());
			i = m.start();
		}
		return b;
	}

	private Tty() {}

	final Object inLock = new Object();
	final CharList inputBuf = new CharList(256);
	final CharList outputBuf = new CharList(256);
	private void writeSeq(CharList b) {writeAll(b);b.clear();}

	//region 进度条管理 (BottomLine)
	private final ArrayList<CharList> lines = new ArrayList<>();
	private final IntList lineCursors = new IntList();
	private int prevCursor;
	private boolean started;

	@Override
	protected void newLine() {
		begin();
		writeAll(sb.append("\r\n")); // [0K /r/n
		sb.clear();
		prevCursor = 0;
	}
	@Override
	protected void partialLine() {
		begin();
		writeAll(sb);

		int width = getStringWidth(sb);
		if (width > columns) {
			var lines = splitByWidth(sb.toString(), columns);
			var last = lines.get(lines.size()-1);
			sb.delete(0, sb.length() - last.length());
			width = getStringWidth(sb);
		}

		prevCursor = width;
	}
	private void begin() {
		flushBytes();

		var seq = outputBuf;
		if (!IS_RICH) stripAnsi(sb);
		else {
			int advance = lines.size() + (prevCursor > 0 ? 1 : 0);
			if (advance > 0 && !started) {
				seq.append(Cursor.hide);

				if (advance > 1) seq.append("\033[").append(advance-1).append('F');
				else seq.append("\033[1G");
				writeSeq(seq.append(Screen.clearLine));
				started = true;
			}
		}
	}
	@Override
	protected void flushBytes() {
		NativeVT.decode(bb, sb);
		if (bb.isReadable()) sb.append(FastCharset.REPLACEMENT);
		bb.clear();
	}
	@Override
	public void flush() {
		if (started) {
			var seq = outputBuf;
			int i = lines.size()-1;
			if (i >= 0) {
				if (prevCursor > 0) seq.append("\r\n");
				while (true) {
					seq.append(lines.get(i));
					seq.append("\033[0K");
					if (i-- == 0) {
						int cursor = lineCursors.get(0);
						if (cursor > 0) seq.append("\033[").append(cursor).append('G');
						break;
					}
					seq.append("\r\n");
				}
			}
			writeSeq(seq.append(Cursor.show));
			started = false;
		}
		if (terminals != null) flushAll();
	}

	public boolean hasBottomLine1(CharList prompt) {return lines.indexOfAddress(prompt) >= 0;}
	public void renderBottomLine1(CharList b) {renderBottomLine1(b, false, 0);}
	/**
	 * 渲染【一条线】.
	 * 这条线会按注册时的顺序渲染在屏幕底部，并且不受System.xxx.println的影响
	 * @param atTop 仅未注册时有效：true在上方插入，否则下方
	 * @param cursor 仅b为最下方时有效：光标位置 (0为最后, 从1开始计数)
	 */
	public synchronized void renderBottomLine1(CharList line, boolean atTop, int cursor) {
		var sb = outputBuf.append("\033[?25l");

		int pos = lines.indexOfAddress(line);
		if (pos < 0) {
			if (lines.size() >= rows) {println(line);return;}

			if (atTop || lines.isEmpty()) {
				lines.add(line);
				lineCursors.add(cursor);
				if (lines.size() > 1) {
					int i = lines.size()-1;
					sb.append("\033[").append(i).append("F\r\n");
					while (true) {
						sb.append(lines.get(i)).append("\033[0K");
						if (i-- == 0) break;
						sb.append("\r\n");
					}
				} else {
					if (prevCursor > 0) sb.append("\r\n");
					sb.append(line).append("\033[").append(cursor).append('G');
				}
			} else {
				sb.append('\n').append(line);
				lines.add(0, line);
				lineCursors.add(0, cursor);
			}
		} else {
			lineCursors.set(pos, cursor);
			if (pos > 0) sb.append("\0337\033[").append(pos).append('F');
			else sb.append("\033[1G");
			sb.append(line).append("\033[0K");
			if (pos > 0) sb.append("\0338");
			else sb.append("\033[").append(cursor).append('G');
		}

		writeSeq(sb.append("\033[?25h"));
		flushAll();
	}
	public synchronized void removeBottomLine1(CharList b) {
		int pos = lines.indexOfAddress(b);
		if (pos < 0) return;
		lines.remove(pos);
		lineCursors.remove(pos);

		var seq = lines.isEmpty() & prevCursor == 0 ? outputBuf.append("\033[1M") : outputBuf.append("\0337\033[").append(pos-1).append("F\033[1M\0338\033M");
		int cursor = !lineCursors.isEmpty() ? lineCursors.get(0) : prevCursor +1;
		if (cursor > 1) seq.append("\033[").append(cursor).append('G');
		writeSeq(seq);
		flushAll();
	}
	//endregion
	//region 转义序列解析
	public static final int VK_CTRL = 0x100, VK_SHIFT = 0x200;
	private static final TrieTree<Integer> KeyMap = new TrieTree<>();
	static {
		key(VK_BACK_SPACE,  "\u007f");
		key(VK_ESCAPE, "\33");
		key(VK_TAB, "\t");
		key(VK_ENTER, "\r");
		// for fallback
		key(VK_ENTER, "\n");
		key(VK_ENTER, "\r\n");

		key(VK_F1, "\33OP");
		key(VK_F2, "\33OQ");
		key(VK_F3, "\33OR");
		key(VK_F4, "\33OS");
		key(VK_F5, "\33[15~");
		key(VK_F6, "\33[17~");
		key(VK_F7, "\33[18~");
		key(VK_F8, "\33[19~");
		key(VK_F9, "\33[20~");
		key(VK_F10, "\33[21~");
		key(VK_F11, "\33[23~");
		key(VK_F12, "\33[24~");

		var keys = new byte[] {VK_UP,VK_DOWN,VK_LEFT,VK_RIGHT,VK_HOME,VK_END};
		var values = "ABDCHF";
		for (int i = 0; i < keys.length; i++) {
			int key = keys[i]&0xFF;
			key(key, "\33["+values.charAt(i));
			key(VK_SHIFT|key, "\33[1;2"+values.charAt(i));
			key(VK_CTRL|key, "\33[1;5"+values.charAt(i));
			key(VK_CTRL|VK_SHIFT|key, "\33[1;6"+values.charAt(i));
		}

		keys = new byte[] {(byte)VK_INSERT, VK_DELETE, VK_PAGE_UP, VK_PAGE_DOWN};
		values = "2356";
		for (int i = 0; i < keys.length; i++) {
			int key = keys[i]&0xFF;
			key(key, "\33["+values.charAt(i)+"~");
			key(VK_SHIFT|key, "\33["+values.charAt(i)+";2~");
			key(VK_CTRL|key, "\33["+values.charAt(i)+";5~");
			key(VK_CTRL|VK_SHIFT|key, "\33["+values.charAt(i)+";6~");
		}

		for (int i = 0; i < 26; i++) {
			key(VK_CTRL | VK_A + i, String.valueOf((char) (i + 1)));
		}
	}
	private static void key(int vk, String seq) {KeyMap.putIfAbsent(new ByteList().putAscii(seq), vk);}

	private final HashMap.Entry<CInt, Integer> matcher = new HashMap.Entry<>(new CInt(), null);
	private void processInput(CharList inBuf) {
		var buf = inBuf.list;
		int i = 0;
		int len = inBuf.length();
		while (i < len) {
			KeyMap.match(inBuf, i, len, matcher);
			int matchLen = matcher.getKey().value;
			if (matchLen < 0) {
				int keyCode = buf[i++];
				handler.keyEnter(keyCode, false);
				continue;
			}

			found: {
				failed:
				if (matcher.value == VK_ESCAPE) {
					if (i+1 < len && buf[i+1] == '[') {
						int j = i+1;
						while (true) {
							if (j+1 >= len) break failed;
							var b = buf[++j];
							if (b >= 'a' && b <= 'z') break;
							if (b >= 'A' && b <= 'Z') break;
						}

						matchLen = j-i+1;
						escEnter(inBuf, i+2, j);
						break found;
					}
				}
				handler.keyEnter(matcher.value, true);
			}

			i += matchLen;
		}
	}
	private void escEnter(CharList buf, int start, int end) {
		if (buf.charAt(end) == 'R') {
			int i = TextUtil.indexOf(buf, ";", start, end);
			if (i >= 0) {
				int height = TextUtil.parseInt(buf, start, i);
				int width = TextUtil.parseInt(buf, i+1, end);
				cursorPos = (height << 16) | width;
				return;
			}
		} else if (buf.charAt(start) == '<') {
			int i1 = buf.indexOf(";", start+1);
			int i2 = buf.indexOf(";", i1+1);
			int i3 = i2+1;
			while (buf.charAt(i3) >= '0' && buf.charAt(i3) <= '9') i3++;

			if ((i1|i2|i3) > 0) {
				int button = TextUtil.parseInt(buf, start+1, i1);
				int x = TextUtil.parseInt(buf, i1+1, i2)-1;
				int y = TextUtil.parseInt(buf, i2+1, i3)-1;
				boolean press = buf.charAt(i3) == 'M';

				int event;
				if ((button & 64) != 0) {
					event = MOUSE_SCROLL | (button & 1) * MOUSE_SCROLL_DOWN;
				} else if ((button & 32) != 0) {
					event = MOUSE_MOVE;
				} else {
					if (press) {
						event = MOUSE_PRESSED;
						mouseButton |= 1<<button;
					} else {
						event = MOUSE_RELEASED;
						mouseButton &= ~(1<<button);
					}
					event |= 4 + (button << 2); // [0,2]
				}
				event |= mouseButton << 4;
				event |= (x & 0xfff) << 7;
				event |= (y & 0xfff) << 20;

				mouseX = x;
				mouseY = y;

				if (mouseEventListener != null) mouseEventListener.accept(event);
				return;
			}
		}
		printError("未识别的ANSI转义: "+Tokenizer.escape(buf.substring(start, end))+" (考虑报告该问题)");
	}
	//endregion
	//region 交互式终端 (KeyHandler)
	private static final KeyHandler DUMMY = (keyCode, isVirtualKey) -> {
		if (keyCode == (VK_CTRL|VK_C)) System.exit(0);
	};

	@NotNull
	private volatile KeyHandler handler = DUMMY;
	private final ArrayList<KeyHandler> handlers = new ArrayList<>();

	public @NotNull KeyHandler getHandler1() {return handler;}
	public synchronized void pushHandler1(KeyHandler h) {
		handlers.add(handler);
		setHandler(h);
	}
	public synchronized void setHandler1(KeyHandler h) {
		if (h == null) h = DUMMY;

		var prev = handler;
		if (prev == h) return;
		handler = h;

		prev.unregistered();
		h.registered();
	}
	public synchronized KeyHandler popHandler1() {
		var h = handler;
		var prev = handlers.pop();
		if (prev == null) throw new IllegalStateException("Stack is empty");
		setHandler(prev);
		return h;
	}

	public void onInput(DynByteBuf buf, FastCharset ucs) {
		var iseq = inputBuf; iseq.clear();
		ucs.decodeLoop(buf, buf.readableBytes(), iseq, Integer.MAX_VALUE, true);
		onInput(iseq);
		buf.compact();
	}
	public void onInput(CharList buf) {
		processInput(buf);
		handler.render();
	}
	//endregion
	//region 鼠标
	private int mouseX, mouseY;
	private byte mouseButton;

	/** @return 最后记录的鼠标X坐标（0起） */
	@Range(from = 0, to = Long.MAX_VALUE)
	public int getMouseX() { return mouseX; }
	/** @return 最后记录的鼠标Y坐标（0起） */
	@Range(from = 0, to = Long.MAX_VALUE)
	public int getMouseY() { return mouseY; }
	public static final int MOUSE_BUTTON_LEFT_PRESSED = 1, MOUSE_BUTTON_MIDDLE_PRESSED = 2, MOUSE_BUTTON_RIGHT_PRESSED = 4;
	/**
	 * @return 当前按下按钮的状态掩码
	 * @see #MOUSE_BUTTON_LEFT_PRESSED
	 */
	@MagicConstant(flags = {
			MOUSE_BUTTON_LEFT_PRESSED,
			MOUSE_BUTTON_MIDDLE_PRESSED,
			MOUSE_BUTTON_RIGHT_PRESSED
	})
	public byte getMouseButton() { return mouseButton; }

	// event
	public static final int MOUSE_PRESSED = 0, MOUSE_RELEASED = 1, MOUSE_MOVE = 2, MOUSE_SCROLL = 3;
	public static final int MOUSE_BUTTON_LEFT = 4, MOUSE_BUTTON_MIDDLE = 8, MOUSE_BUTTON_RIGHT = 12;
	public static final int MOUSE_SCROLL_DOWN = 4, MOUSE_SCROLL_UP = 0;

	private IntConsumer mouseEventListener;
	/**
	 * 设置鼠标事件监听器
	 * @param listener 接收32位编码鼠标事件的回调接口
	 *
	 * <p>回调事件示例解析：
	 * <pre>{@code
	 * void handleMouseEvent(int event) {
	 *     int type = event & 0x03;  // 事件类型
	 *     int data = event & 0x0C; // 按钮ID
	 *     int buttons = (event >>> 4) & 7; // 已按下的按钮
	 *     int x = (event >>> 7) & 0xFFF; // X坐标
	 *     int y = (event >>> 20) & 0xFFF; // Y坐标
	 * }
	 * }</pre>
	 */
	public void setMouseEventListener(IntConsumer listener) {mouseEventListener = listener;}

	public static final int MOUSE_EVENT_OFF = 0, MOUSE_EVENT_CLICK = 1, MOUSE_EVENT_MOVE = 3;
	public void setMouseMode(@MagicConstant(intValues = {MOUSE_EVENT_OFF, MOUSE_EVENT_CLICK, MOUSE_EVENT_MOVE}) int mouseMode) {
		var t = terminals.get(0);
		if (mouseMode == MOUSE_EVENT_OFF) {
			t.write("\u001b[?1006l\u001b[?1003l");
		} else {
			if ((mouseMode & 1) != 0) t.write("\u001b[?1006h");
			if ((mouseMode & 2) != 0) t.write("\u001b[?1003h");
		}
		t.flush();
	}
	//endregion
	//region 光标和宽高
	private volatile int cursorPos;

	// 宽度是字符，高度是行，下面仅仅是windows的默认值作为fallback，没有特殊意义
	public int columns = 120, rows = 30;

	public int getCursor(CharSequence str) {
		synchronized (inLock) {
			if (cursorPos == 0) throw new IllegalStateException("递归读取光标?");
			cursorPos = 0;

			ITty t = terminals.get(0);
			t.write(str);
			t.flush();

			int val;
			if (!t.readSync()) {
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
	}

	public static final String GET_CONSOLE_DIMENSION = "\033[?25l\0337\033[999E\033[999C\033[6n\0338\033[?25h";
	public void updateConsoleSize() {terminals.get(0).getConsoleSize(this);}
	//endregion
	//region 计算字符宽度
	// 16KB for 65536x [EXIST,WIDTH] bit
	private final BitArray IsWidthChar = new BitArray(2, 65536);
	/**
	 * 计算字符的长度(按终端英文记)
	 */
	public int getCharWidth1(char c) {
		if (c < 32) return c == '\t' ? 8 : 0;
		if (TextUtil.isPrintableAscii(c)) return 1;
		// 大概是乱码，而且在能渲染的终端中，两个合起来正好是方形
		if (Character.isSurrogate(c)) return 1;

		var data = IsWidthChar.get(c);
		if ((data & 2) == 0) {
			// 这个是二分查找，所以也缓存吧
			var ub = Character.UnicodeBlock.of(c);
			if (ub == null || ub.toString().startsWith("CJK"))
				data = 3;
			else {
				// https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters
				// 这也太邪门了……
				try {
					data = getCursor(outputBuf.append("\0337\033[1E").append(c).append("\033[6n\033[1K\0338"));
					data = (data & 0xFFFF) - 1;
					if (data > 1) throw new FastFailException("一个字符的宽度既不是1也不是2: "+data);

					data |= 2;
				} catch (Exception e) {
					data = 3;
				}
			}

			IsWidthChar.set(c, data);
		}

		return 1 + (data & 1);
	}
	/**
	 * 计算字符串的长度(按终端英文记)，忽略其中的ANSI转义序列.
	 */
	public int getStringWidth1(CharSequence s) {
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
			len += getCharWidth1(c);
		}
		return Math.max(maxLen, len);
	}
	/**
	 * 根据width切分字符串，目前的版本可能会把ANSI转义序列弄丢.
	 * @param width 长度，按终端英文记
	 */
	public List<String> splitByWidth1(String s, int width) {
		var m = s.indexOf('\033') >= 0 ? ANSI_ESCAPE.matcher(s) : null;

		List<String> out = new ArrayList<>();
		int prevI = 0, tmpWidth = 0;
		for (int i = 0; i < s.length(); ) {
			char c = s.charAt(i);
			if (c == '\n') {
				out.add(s.substring(prevI, i));
				prevI = ++i;
				tmpWidth = 0;
			} else if (c == '\033') {
				//noinspection all
				if (m.find(i)) i = m.end();
				else throw new RuntimeException("未识别的转义"+Tokenizer.escape(s)+",请更新ANSI_ESCAPE PATTERN");
			} else {
				int w = getCharWidth1(c);
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
	//region ITty Handlers
	private final ArrayList<ITty> terminals = new ArrayList<>();
	public void addListener(ITty t) {
		int i = terminals.indexOfAddress(t);
		if (i < 0) terminals.add(t);
	}
	public void removeListener(ITty t) {terminals.remove(t);}

	private void writeAll(CharSequence b) {
		for (int i = 0; i < terminals.size(); i++) {
			var t = terminals.get(i);
			try {
				t.write(b);
			} catch (Exception ignored) {}
		}
	}
	private void flushAll() {
		for (int i = 0; i < terminals.size(); i++) {
			var t = terminals.get(i);
			try {
				t.flush();
			} catch (Exception ignored) {}
		}
	}
	//endregion

	//instance必须最后创建
	private static final Tty instance = new Tty();
	static {
		String state = System.getProperty("roj.tty", "enable");
		var tty = state.equals("enable") ? NativeVT.initialize() : null;
		if (tty == null) tty = (ITty) RojLib.getObj("roj.ui.Terminal.fallback");
		if (tty != null) {
			instance.addListener(tty);

			IS_RICH = (tty.characteristics()&ITty.ANSI) != 0;
			IS_INTERACTIVE = (tty.characteristics()&ITty.INTERACTIVE) != 0;
		} else {
			IS_RICH = false;
			IS_INTERACTIVE = false;
		}

		if (IS_RICH) {
			System.setOut(instance);
			System.setErr(instance);
			Runtime.getRuntime().addShutdownHook(new Thread(() -> write(reset+"\u001b[?1006l\u001b[?1003l"+Cursor.show), "Tty Reset"));

			if (IS_INTERACTIVE) {
				System.setIn(new InputStream() {
					public int read() {throw new IllegalStateException("一旦注册过KeyHandler，那么System.in不再可用\n请使用roj.ui.TUI的函数");}
				});
			}
		} else if (!state.equals("disable")) {
			var fallback = new NoAnsi();
			instance.addListener(fallback);
			fallback.start();

			System.setOut(fallback);
			System.setErr(fallback);
		}
	}

	public static final class NoAnsi extends DelegatedPrintStream implements ITty, Runnable {
		static final InputStream in = System.in;
		static final PrintStream out = System.out;

		@Override
		public void run() {
			var tmp = IOUtil.getSharedByteBuf();
			while (true) {
				try {
					int i = in.read(tmp.list);

					var instance = getInstance();
					var ib = instance.inputBuf;
					ib.clear();

					if (i < 0) {
						instance.onInput(ib);
						continue;
					}

					tmp.rIndex = 0;
					tmp.wIndex(i);
					NativeVT.decode(tmp, ib);
					instance.onInput(ib);
				} catch (Throwable e) {
					RojLib.debug("NativeVT", "Fatal");
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

		@Override public int characteristics() {return 0;}
		@Override public void write(CharSequence str) {out.print(stripAnsi(IOUtil.getSharedCharBuf().append(str)));}
		@Override protected void partialLine() {out.print(stripAnsi(sb));sb.clear();}
		@Override protected void newLine() {out.println(stripAnsi(sb));sb.clear();}

		@Override
		public void flush() {
			super.flush();
			out.flush();
		}
	}
}