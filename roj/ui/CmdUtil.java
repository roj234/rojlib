package roj.ui;

import roj.NativeLibrary;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.OS;

import java.io.PrintStream;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public class CmdUtil {
	public static class Color {
		public static final int BLACK = 30, BLUE = 34, GREEN = 32, CYAN = 36, RED = 31, PURPLE = 35, YELLOW = 33, WHITE = 37;

		public static final int BOLD = 1, ITALIC = 3, UNDERLINE = 4, SHINY = 5, REVERSE = 7, DELETE = 9;
		// +20关闭

		static final char[] MC2TERM = new char[16];
		public static String minecraftTooltip(char[] chars, String tip, float speed) {
			CharList sb = IOUtil.getSharedCharBuf();
			int x = chars.length * tip.length();
			int time = (((int) (System.currentTimeMillis()/33))&Integer.MAX_VALUE) % x;
			int si = (int) (time * speed);
			for (int i = 0; i < tip.length(); i++) {
				sb.append("\u001B[;").append((int) MC2TERM[TextUtil.h2b(chars[si++ % chars.length])]).append('m').append(tip.charAt(i));
			}
			return sb.append("\u001B[0m").toString();
		}
		public static void minecraftColor(CharList c) {
			int i = 0;
			while ((i = c.indexOf("\u00a7", i)) >= 0) {
				char c1 = c.charAt(i + 1);
				if (c1 <= 'f') {
					c1 = MC2TERM[TextUtil.h2b(c1)];
				} else {
					switch (c1) {
						case 'l': c1 = BOLD; break;
						case 'n': c1 = UNDERLINE; break;
						case 'm': c1 = DELETE; break;
						case 'o': c1 = ITALIC; break;
						case 'k': c1 = SHINY; break;
						case 'r': c1 = 0; break;
					}
				}
				c.replace(i, i+2, "\u001B[;"+(int)c1+"m");
			}
		}
		static {
			a('0', BLACK);
			a('1', BLUE);
			a('2', GREEN);
			a('3', CYAN);
			a('4', RED);
			a('5', PURPLE);
			a('6', YELLOW);
			a('7', WHITE);
		}
		private static void a(char c, int color) {
			MC2TERM[TextUtil.h2b(c)] = (char) color;
			MC2TERM[TextUtil.h2b(c)+8] = (char) (color+60);
		}
	}

	public static final boolean ENABLE;
	public static final PrintStream originalOut;
	public static PrintStream out;

	static {
		originalOut = System.out;
		boolean e = true;
		if (OS.CURRENT == OS.WINDOWS) {
			e = false;
			if (NativeLibrary.loaded) {
				boolean success = NativeLibrary.UI_enableWindowsANSI();
				if (!success) {
					System.err.println("Failed to enable ANSI parser for STDOUT");
				} else {
					e = true;
				}
			}
		}
		ENABLE = e;
		out = System.out;
	}

	public static boolean enabled() {
		return ENABLE;
	}

	public static void printColor(PrintStream o, String string, int fg, boolean reset, boolean println, boolean light) {
		if (ENABLE) o.print("\u001B[;" + (fg + (light ? 60 : 0)) + 'm');
		if (println) o.println(string); else o.print(string);
		if (ENABLE&reset) o.print("\u001B[0m");
	}

	private static final char[] rainbow = new char[] {'c', '6', 'e', 'a', 'b', '9', 'd'};
	private static final char[] sonic = new char[] {'9', '9', '9', '9', 'f', '9', 'f', 'f', '9', 'f',
													'f', '9', 'b', 'f', '7', '7', '7', '7', '7', '7',
													'7', '7', '7', '7', '7', '7', '7', '7', '7', '7'};

	public static void rainbow(String s) {
		if (!ENABLE) out.println(s);
		else out.println(Color.minecraftTooltip(rainbow, s, 0.5f));
	}

	public static void sonic(String s) {
		if (!ENABLE) out.println(s);
		else out.println(Color.minecraftTooltip(sonic, s, 0.75f));
	}

	public static void reset() {
		if (ENABLE) originalOut.print("\u001B[0m");
	}

	public static void bg(int bg) {
		bg(bg, false);
	}
	public static void bg(int bg, boolean hl) {
		if (ENABLE) originalOut.print("\u001B[" + (bg + (hl ? 70 : 10)) + 'm');
	}

	public static void setRGB(int r, int g, int b, boolean foreground) {
		if (ENABLE) originalOut.print("\u001B[;"+(38+(foreground?0:10))+";2;"+r+';'+g+';'+b+'m');
	}

	public static void cursorSet(int line, int column) { if (ENABLE) originalOut.print("\u001b["+line +';'+column+'H'); }
	public static void cursorUpCol0(int line) { cursor0('F', line); }
	public static void cursorDownCol0(int line) { cursor0('E', line); }
	public static void cursorUp(int line) { cursor0('A', line); }
	public static void cursorDown(int line) { cursor0('B', line); }
	public static void cursorRight(int line) { cursor0('C', line); }
	public static void cursorLeft(int line) { cursor0('D', line); }
	private static void cursor0(char cr, int line) {
		if (ENABLE) originalOut.print(("\u001b[" + line) + cr);
	}

	public static void cursorBackup() {
		// saves the current cursor position
		// \u001b[s
		// restores the cursor to the last saved position
		// \u001b[u
		originalOut.print("\u001b[s");
	}
	public static void cursorRestore() {
		originalOut.print("\u001b[u");
	}

	public static void getCursor() {
		if (ENABLE) originalOut.print("\u001b[6n");
		// ESC[n;mR
	}

	public static void clearScreen() { if (ENABLE) originalOut.print("\u001b[1;1H\u001b[2J"); }
	public static void clearScreenAfter() { cs0(0); }
	public static void clearScreenBefore() { cs0(1); }
	private static void cs0(int cat) {
		if (ENABLE) originalOut.print("\u001b["+cat+'J');
	}

	public static void clearLine() { cl0(2); }
	public static void clearLineAfter() { cl0(0); }
	public static void clearLineBefore() { cl0(1); }
	private static void cl0(int cat) {
		if (ENABLE) originalOut.print("\u001b["+cat+'K');
	}

	public static void fg(int fg) {
		fg(fg, false);
	}
	public static void fg(int fg, boolean hl) {
		if (ENABLE) out.print("\u001B[" + (fg + (hl ? 60 : 0)) + 'm');
	}

	public static void color(String s, int color) {
		printColor(out, s, color, true, false, true);
	}
	public static void colorL(String s, int color) {
		printColor(out, s, color, true, true, true);
	}

	public static void info(String string) {
		info(string, true);
	}
	public static void info(String string, boolean println) {
		printColor(out, string, Color.WHITE, true, println, true);
	}

	public static void success(String string) {
		success(string, true);
	}
	public static void success(String string, boolean println) {
		printColor(out, string, Color.GREEN, true, println, true);
	}

	public static void warning(String string) {
		warning(string, true);
	}
	public static void warning(String string, boolean println) {
		printColor(out, string, Color.YELLOW, true, println, true);
	}
	public static void warning(String string, Throwable err) {
		printColor(out, string, Color.YELLOW, false, true, true);
		err.printStackTrace(out);
		reset();
	}

	public static void error(String string) {
		error(string, true);
	}
	public static void error(String string, boolean println) {
		printColor(out, string, Color.RED, true, println, true);
	}
	public static void error(String string, Throwable err) {
		printColor(out, string, Color.RED, false, true, true);
		err.printStackTrace(out);
		reset();
	}
}
