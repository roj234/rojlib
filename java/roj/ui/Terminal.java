package roj.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.RojLib;
import roj.collect.*;
import roj.compiler.plugins.annotations.AsMethod;
import roj.compiler.plugins.eval.Constexpr;
import roj.config.Tokenizer;
import roj.config.data.CInt;
import roj.config.data.CList;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.TextUtil;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.*;
import java.nio.charset.Charset;
import java.util.List;
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
public final class Terminal extends DelegatedPrintStream {
	public static final int BLACK = 30, BLUE = 34, GREEN = 32, CYAN = 36, RED = 31, PURPLE = 35, YELLOW = 33, WHITE = 37;
	public static final int HIGHLIGHT = 60;

	public static final class Color {
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
		public static Text minecraftJsonStyleToString(CMap map) {
			Text sts = minecraftRawStyleToString(map.getString("text"));

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
			if (!ANSI_OUTPUT) sb.append(s);
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
			if (!ANSI_OUTPUT) sb.append(s);
			else minecraftTooltip(sonic, s, 50, sb);
			return sb;
		}
	}

	private Terminal() {}

	//region AnsiOutput基础
	public static void reset() {if (ANSI_OUTPUT) System.out.print("\033[0m");}
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
		if (ANSI_OUTPUT) o.print("\033[;"+fg+'m');
		o.println(s);
		if (ANSI_OUTPUT&reset) o.print("\033[0m");
	}

	/**
	 * 转义序列生成.
	 * 这里的绝对坐标从零开始
	 */
	public static class Cursor {
		@AsMethod public static final String hide = "\033[?25l", show = "\033[?25h";
		// 一个可选的替代： \033[s和\033[u
		@AsMethod public static final String save = "\0337", restore = "\0338";

		/** 移动到本行开头 */
		@AsMethod public static final String toLeft = "\033[G";
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
		@AsMethod public static final String clearScreen = "\033[2J", clearBefore = "\033[1J", clearAfter = "\033[J";
		@AsMethod public static final String clearLine = "\033[2K", clearLineBefore = "\033[1K", clearLineAfter = "\033[K";

		@Constexpr public static String scrollUp(@Range(from = 1, to = Integer.MAX_VALUE) int lines) {return "\033["+lines+"S";}
		@Constexpr public static String scrollDown(@Range(from = 1, to = Integer.MAX_VALUE) int lines) {return "\033["+lines+"T";}
	}

	public static void beep() {
		if (!ANSI_OUTPUT) {
			System.out.write(7);
			System.out.flush();
		} else {
			synchronized (out) {
				write("\7");
				_flush();
			}
		}
	}
	public static void directWrite(CharSequence s) {
		if (!ANSI_OUTPUT) {
			System.out.print(stripAnsi(s instanceof CharList c ? c : new CharList().append(s)));
		} else {
			synchronized (out) {
				write(s);
				_flush();
			}
		}
	}

	public static final Pattern ANSI_ESCAPE = Pattern.compile("\033(?:\\[[^a-zA-Z]*?[a-zA-Z]|].*?\u0007)");
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
		if (bb.isReadable()) sb.append(FastCharset.REPLACEMENT);
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
			var sb = OSEQ.append("\033[?25l");

			int pos = LINES.indexOfAddress(line);
			if (pos < 0) {
				if (LINES.size() >= windowHeight) {out.println(line);return;}

				if (atTop || LINES.isEmpty()) {
					LINES.add(line);
					CURSORS.add(cursor);
					if (LINES.size() > 1) {
						int i = LINES.size()-1;
						sb.append("\033[").append(i).append("F\r\n");
						while (true) {
							sb.append(LINES.get(i)).append("\033[0K");
							if (i-- == 0) break;
							sb.append("\r\n");
						}
					} else {
						if (PrevWidth > 0) sb.append("\r\n");
						sb.append(line).append("\033[").append(cursor).append('G');
					}
				} else {
					sb.append('\n').append(line);
					LINES.add(0, line);
					CURSORS.add(0, cursor);
				}
			} else {
				CURSORS.set(pos, cursor);
				if (pos > 0) sb.append("\0337\033[").append(pos).append('F');
				else sb.append("\033[1G");
				sb.append(line).append("\033[0K");
				if (pos > 0) sb.append("\0338");
				else sb.append("\033[").append(cursor).append('G');
			}

			writeSeq(sb.append("\033[?25h"));
			_flush();
		}
	}
	public static void removeBottomLine(CharList b) {
		synchronized (out) {
			int pos = LINES.indexOfAddress(b);
			if (pos < 0) return;
			LINES.remove(pos);
			CURSORS.remove(pos);

			var seq = LINES.isEmpty() & PrevWidth == 0 ? OSEQ.append("\033[1M") : OSEQ.append("\0337\033[").append(pos-1).append("F\033[1M\0338\033M");
			int cursor = !CURSORS.isEmpty() ? CURSORS.get(0) : PrevWidth+1;
			if (cursor > 1) seq.append("\033[").append(cursor).append('G');
			writeSeq(seq);
			_flush();
		}
	}

	private boolean started;
	@Override
	protected void newLine() {
		begin();
		write(sb.append("\r\n")); // [0K /r/n
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
				seq.append("\033[?25l");
				if (advance > 1) seq.append("\033[").append(advance-1).append('F');
				else seq.append("\033[1G");
				writeSeq(seq.append("\033[2K"));
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
					seq.append("\033[0K");
					if (i-- == 0) {
						int cursor = CURSORS.get(0);
						if (cursor > 0) seq.append("\033[").append(cursor).append('G');
						break;
					}
					seq.append("\r\n");
				}
			}
			writeSeq(seq.append("\033[?25h"));
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
							throw IOUtil.rethrowAsIOException(e);
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
	private static final KeyHandler PIPE = new VirtualTerminal("") {
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
		public void render() {
			if (ALT.reader != 0) super.render();
		}
	};
	// endregion
	@NotNull
	private static volatile KeyHandler console = PIPE;
	public static void setConsole(KeyHandler c) {
		if (c == null) c = PIPE;

		synchronized (Terminal.class) {
			var prev = console;
			if (prev == c) return;
			prev.unregistered();

			console = c;
		}

		if (c != PIPE) c.registered();
	}
	public static KeyHandler getConsole() {return console;}
	//region AnsiInput - 处理字符键入
	public static final int VK_CTRL = 0x100;
	public static final int VK_SHIFT = 0x200;
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

	private static final CharList ISEQ = new CharList(256);
	public static void onInput(DynByteBuf buf, FastCharset ucs) {
		var iseq = ISEQ; iseq.clear();
		ucs.decodeLoop(buf, buf.readableBytes(), iseq, Integer.MAX_VALUE, true);
		onInput(iseq);
		buf.compact();
	}
	// 这个方法不加锁是有意的，加锁的话获取字符串宽度会变得非常麻烦
	public static void onInput(CharList buf) {
		out.processInput(buf);
		console.render();
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
						escEnter(inBuf, i+2, j-i);
						break found;
					}
				}
				console.keyEnter(matcher.value, true);
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
		printError("未识别的ANSI转义: "+Tokenizer.escape(buf)+" (考虑报告该问题)");
	}
	//endregion
	//region AnsiInput基础
	public static void pause() {readChar(null, new CharList("按任意键继续"), true);}
	/**
	 * @param min 最小值(包括)
	 * @param max 最大值(包括)
	 */
	public static int readInt(int min, int max) {return readLine("您的选择: ", Argument.number(min, max));}
	public static boolean readBoolean(String info) {return readLine(info, Argument.bool());}
	public static File readFile(String name) {return readLine(name+"的路径: ", Argument.path());}
	public static int readChosenFile(List<?> files, String desc) {
		if (files.size() <= 1) return 0;
		info("在多个 "+desc+" 中输入序号来选择");

		for (int i = 0; i < files.size(); i++) {
			String name = files.get(i).toString();
			if (ANSI_OUTPUT) System.out.print("\033["+(WHITE+(i&1)*HIGHLIGHT)+'m');
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
		T v = readLine(new Shell("\033[;"+(WHITE+HIGHLIGHT)+'m'+prompt), arg);
		//if (v == null) System.exit(1);
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
	public static <T> T readLine(Shell c, Argument<T> arg) {
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
	public static char readChar(String chars) {return readChar(chars, "");}
	public static char readChar(String chars, String prefix) {return readChar(MyBitSet.from(chars), new CharList(prefix), false);}
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
		KeyHandler c = (keyCode, isVirtualKey) -> {
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
			if (prefix != null) removeBottomLine(prefix);
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
		if (!terminals.get(0).read(false)) {
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
	public static final String GET_CONSOLE_DIMENSION = "\033[?25l\0337\033[999E\033[999C\033[6n\0338\033[?25h";
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
	private static boolean terminalResponse;
	// 16KB for EXIST|WIDTH bits, 原先Int2IntMap一个字符20字节Entry
	private static final BitArray IsWidthChar = new BitArray(2, 65536);
	/**
	 * 计算字符的长度(按终端英文记)
	 */
	public static int getCharWidth(char c) {
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
				if (!terminalResponse) return 2;

				var t = terminals.get(0);
				// https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters
				// 这也太邪门了……
				synchronized (CURSOR_LOCK) {
					if (out.cursorPos == 0) throw new IllegalStateException("递归读取光标?");
					out.cursorPos = 0;

					synchronized (out) {
						t.write(OSEQ.append("\0337\033[1E").append(c).append("\033[6n\033[1K\0338"));
						OSEQ.clear();
						t.flush();
					}

					try {
						data = (out.readCursor() & 0xFFFF) - 1;
						if (data > 1) throw new IllegalArgumentException("一个字符的宽度既不是1也不是2: " + data);

						data |= 2;
					} catch (Exception e) {
						data = 3;
					}
				}
			}

			IsWidthChar.set(c, data);
		}

		return 1 + (data & 1);
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
	/**
	 * 根据width切分字符串，目前的版本可能会把ANSI转义序列弄丢.
	 * @param width 长度，按终端英文记
	 */
	public static List<String> splitByWidth(String s, int width) {
		var m = s.indexOf('\033') >= 0 ? ANSI_ESCAPE.matcher(s) : null;

		List<String> out = new SimpleList<>();
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
	private static final BufferedReader in;
	private static final PrintStream serr = System.err;
	/**
	 * 给文本部分的调试设计，sout可能会Stackoverflow
	 */
	public static void printError(String text) {serr.println(text);}

	private static final SimpleList<StdIO> terminals = new SimpleList<>();
	public static void addListener(StdIO t) {
		int i = terminals.indexOfAddress(t);
		if (i < 0) terminals.add(t);
	}
	public static void removeListener(StdIO t) {terminals.remove(t);}
	public static boolean isFullUnicode() {return terminals.get(0).unicode();}

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
	static {
		nativeCharset = NativeVT.charset;
		var t = NativeVT.getInstance();
		if (t == null) t = (StdIO) RojLib.inject("roj.ui.Terminal.fallback");
		if (t != null) {
			addListener(t);
			ALT = new AnsiIn();

			ANSI_OUTPUT = true;
			try {
				ANSI_INPUT = t.read(true);
			} catch (IOException e) {
				throw new IllegalStateException(e);
			}

			if (ANSI_INPUT && !Boolean.getBoolean("roj.noAnsiInput")) {
				System.setIn(ALT);
			}
			System.setOut(out);
			System.setErr(out);

			int winSize = out.cursorPos;
			if (winSize > 0) {
				windowWidth = winSize&0xFFFF;
				windowHeight = winSize >>> 16;
				terminalResponse = true;
			}

			in = null;
		} else {
			ANSI_INPUT = false;
			ANSI_OUTPUT = false;
			ALT = new AnsiIn();

			var fallback = new NativeVT.Fallback();
			addListener(fallback);
			fallback.start();

			in = new BufferedReader(new InputStreamReader(System.in), 1024);

			System.setOut(fallback);
			System.setErr(fallback);
		}
	}
}