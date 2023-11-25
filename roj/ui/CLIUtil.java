package roj.ui;

import roj.NativeLibrary;
import roj.collect.IntBiMap;
import roj.collect.SimpleList;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.NativeException;
import roj.util.OS;

import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
 * ESC 7		保存光标
 * ESC 8		还原光标
 * ESC [ ? 12 h	ATT160		开始光标闪烁
 * ESC [ ? 12 l	ATT160		停止闪烁光标
 * ESC [ ? 25 h	DECTCEM		显示光标
 * ESC [ ? 25 l	DECTCEM		隐藏光标
 *
 *  备注
 * *<x> 和 <y> 参数的限制与上面的 <n> 相同。 如果省略 <x> 和 <y>，则将其设置为 1;1。
 *
 * 对于以下行，参数 <n> 有 3 个有效值：
 * 0 的擦除范围是从当前光标位置（含）到行/显示的末尾
 * 1 的擦除范围是从行/显示开始到当前光标位置（含）
 * 2 的擦除范围是整行/显示
 *
 * ESC [ <n> J	ED	显示中的擦除	将 <n> 指定的当前视区/屏幕中的所有文本替换为空格字符
 * ESC [ <n> K	EL	行中的擦除	将行上的所有文本替换为由 <n> 指定的光标与空格字符
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CLIUtil {
	public static final boolean ANSI;
	public static final BufferedReader in;
	protected static final InputStream sysIn;
	public static final PrintStream sysOut, sysErr;

	public static final int BLACK = 30, BLUE = 34, GREEN = 32, CYAN = 36, RED = 31, PURPLE = 35, YELLOW = 33, WHITE = 37;

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

		public static AnsiString minecraftJsonStyleToString(CMapping map) {
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

	static final int STDIN = 0, STDOUT = 1, STDERR = 2;
	static final int MODE_GET = 0, MODE_SET = 1, MODE_ADD = 2, MODE_REMOVE = 3, MODE_XOR = 4;
	static final int
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

	public static void ensureInited() {}
	static {
		sysIn = new FileInputStream(FileDescriptor.in);
		sysOut = System.out;
		sysErr = System.err;
		ANSI = initialize();
		in =  new BufferedReader(new InputStreamReader(System.in));
	}
	private static boolean initialize() {
		if (Boolean.getBoolean("roj.disableAnsi")) return false;

		if (OS.CURRENT == OS.WINDOWS) {
			if (NativeLibrary.loaded) {
				try {
					setConsoleMode0(STDOUT, MODE_SET, ENABLE_VIRTUAL_TERMINAL_PROCESSING|ENABLE_PROCESSED_OUTPUT|ENABLE_WRAP_AT_EOL_OUTPUT);
				} catch (NativeException e) {
					System.err.println("Failed to initialize VT output: " + e.getMessage());
				}
			}
		}

		enableDirectInput(true);
		return CLIConsole.initialize();
	}
	static void enableDirectInput(boolean enable) {
		if (OS.CURRENT == OS.WINDOWS) {
			if (NativeLibrary.loaded) {
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
	}
	private static native int setConsoleMode0(int target, int mode, int flag) throws NativeException;

	public static void enableQuickEditMode() {
		setConsoleMode0(STDIN, MODE_ADD, ENABLE_QUICK_EDIT_MODE|ENABLE_EXTENDED_FLAGS);
	}

	public static void printColor(String string, int fg, boolean reset, boolean println, boolean light) {
		PrintStream o = System.out;
		if (ANSI) o.print("\u001B[;"+(fg+(light?60:0))+'m');
		if (println) o.println(string); else o.print(string);
		if (ANSI &reset) o.print("\u001B[0m");
	}

	public static void reset() { if (ANSI) System.out.print("\u001B[0m"); }
	public static void clearScreen() { if (ANSI) System.out.print("\u001b[1;1H\u001b[2J"); }

	public static void bg(int bg, boolean hl) { if (ANSI) System.out.print("\u001B[" + (bg + (hl ? 70 : 10)) + 'm'); }
	public static void fg(int fg, boolean hl) { if (ANSI) System.out.print("\u001B[" + (fg + (hl ? 60 : 0)) + 'm'); }

	public static void info(String string) {
		info(string, true);
	}
	public static void info(String string, boolean println) {
		printColor(string, WHITE, true, println, true);
	}

	public static void success(String string) {
		success(string, true);
	}
	public static void success(String string, boolean println) {
		printColor(string, GREEN, true, println, true);
	}

	public static void warning(String string) {
		warning(string, true);
	}
	public static void warning(String string, boolean println) {
		printColor(string, YELLOW, true, println, true);
	}
	public static void warning(String string, Throwable err) {
		printColor(string, YELLOW, false, true, true);
		err.printStackTrace();
		reset();
	}

	public static void error(String string) {
		error(string, true);
	}
	public static void error(String string, boolean println) {
		printColor(string, RED, true, println, true);
	}
	public static void error(String string, Throwable err) {
		printColor(string, RED, false, true, true);
		err.printStackTrace();
		reset();
	}

	/**
	 * @param min 最小值(包括)
	 * @param max 最大值(不包括)
	 */
	public static int getNumberInRange(int min, int max) throws IOException {
		String s;
		int i;
		do {
			do {
				info("您的选择: ", false);
				fg(YELLOW, true);
				s = in.readLine();
				reset();
				if (s == null) System.exit(-2);
				if (TextUtil.isNumber(s) == 0) break;
				warning("输入的不是数字!", true);
			} while (true);
			try {
				i = Integer.parseInt(s);
				if (i >= min && i < max) {
					break;
				}
			} catch (NumberFormatException ignored) {
			}
			warning("输入的数太大或者太小!", true);
		} while (true);
		return i;
	}

	public static File readFile(String name) throws IOException {
		info("请键入, 粘贴或者拖动" + name + "到这里并按下回车!");
		File file;
		do {
			fg(YELLOW, true);
			String s = in.readLine();
			reset();
			if (s == null) System.exit(-2);
			if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
			file = new File(s);
			if (file.exists()) return file;
			warning("文件不存在，请重试!", true);
		} while (!file.exists());
		return file;
	}

	public static boolean readBoolean(String info) throws IOException {
		boolean enableAt;
		do {
			info(info, false);
			fg(YELLOW, true);
			String s = in.readLine();
			if (s == null) System.exit(-2);
			reset();
			Boolean b = null;
			switch (s.toLowerCase(Locale.ROOT)) {
				case "y": case "yes": case "ok":
				case "t": case "true": case "是":
					b = true;
					break;
				case "n": case "no": case "not":
				case "false": case "cancel":
				case "f": case "否":
					b = false;
					break;
			}
			if (b != null) {
				enableAt = b;
				break;
			}
			warning("不是true或false");
		} while (true);
		return enableAt;
	}

	public static String userInput(String info, Function<String, Boolean> verifier) throws IOException {
		info(info, false);
		String string;
		do {
			fg(YELLOW, true);
			string = in.readLine();
			if (string == null) System.exit(-2);
			if (verifier.apply(string) == Boolean.TRUE) {
				break;
			}
			System.out.println("输入不正确!");
			reset();
		} while (true);
		return string.trim();
	}

	public static String userInput(String info) throws IOException {
		info(info, false);
		fg(YELLOW, true);
		String string = in.readLine();
		reset();
		if (string == null) System.exit(-2);
		return string.trim();
	}

	public static char[] readPassword() {
		return System.console().readPassword("");
	}

	public static void pause() {
		try {
			userInput("按回车继续");
		} catch (IOException ignored) {}
	}

	public static int selectOneFile(List<File> files, String name1) throws IOException {
		if (files.size() == 1) return 0;
		info("有多个 " + name1 + " , 请选择(输入编号)");

		for (int i = 0, forgeVersionsSize = files.size(); i < forgeVersionsSize; i++) {
			String name = files.get(i).getName();
			int k = name.lastIndexOf(File.separatorChar);
			fg(WHITE, (i & 1) == 1);
			System.out.println(i + ". " + (k == -1 ? name : name.substring(0, k)));
			reset();
		}

		return getNumberInRange(0, files.size());
	}

	public static final Pattern ANSI_ESCAPE = Pattern.compile("\u001b\\[[^a-zA-Z]+?[a-zA-Z]");
	public static ByteList stripAnsi(ByteList b) {
		Matcher m = ANSI_ESCAPE.matcher(b);
		ByteList out = b.slice(0, b.length()); out.clear();

		int i = 0;
		while (m.find(i)) {
			out.put(b, i, m.start()-i);
			i = m.end();
		}
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

	public static int getDisplayWidth(CharSequence s) {
		s = ANSI_ESCAPE.matcher(s).replaceAll("");

		int len = 0, maxLen = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '\r') {}
			else if(c == '\n') { maxLen = Math.max(maxLen, len); len = 0; }
			len += CLIConsole.getCharLength(c);
		}
		return Math.max(maxLen, len);
	}
	// TODO ansi
	public static List<String> splitByWidth(String str, int width) {
		List<String> out = new SimpleList<>();
		int prevI = 0, tmpWidth = 0;
		for (int i = 0; i < str.length(); i++) {
			char c = str.charAt(i);
			if (c == '\n') {
				out.add(str.substring(prevI, i));
				prevI = i+1;
				tmpWidth = 0;
				continue;
			}

			int w = CLIConsole.getCharLength(c);
			if (tmpWidth+w > width) {
				out.add(str.substring(prevI, i));
				prevI = i;
				tmpWidth = w;
			} else {
				tmpWidth += w;
			}
		}
		if (prevI < str.length()) out.add(str.substring(prevI));
		return out;
	}
}
