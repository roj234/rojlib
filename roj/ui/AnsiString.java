package roj.ui;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/20 0020 1:35
 */
public class AnsiString {
	private final String value;
	private int fgColor, bgColor;
	private short flag;

	private List<AnsiString> extra = Collections.emptyList();

	public AnsiString(String value) { this.value = value; }
	public AnsiString(AnsiString copy) { this(copy, copy.value); }
	public AnsiString(AnsiString copy, String altValue) {
		this.value = altValue;
		this.fgColor = copy.fgColor;
		this.bgColor = copy.bgColor;
		this.flag = copy.flag;
		this.extra = copy.extra.isEmpty() ? Collections.emptyList() : new SimpleList<>(copy.extra);
	}

	public CharList writeAnsi(CharList sb) {
		writeSGI(sb);
		sb.append(value);
		for (int i = 0; i < extra.size(); i++)
			extra.get(i).writeAnsi(sb);
		return sb;
	}
	public CharList writeRaw(CharList sb) {
		sb.append(value);
		for (int i = 0; i < extra.size(); i++)
			extra.get(i).writeRaw(sb);
		return sb;
	}
	public String toString() { return writeRaw(IOUtil.getSharedCharBuf()).toString(); }
	public String toAnsiString() { return writeAnsi(IOUtil.getSharedCharBuf()).toString(); }

	public void flat(List<AnsiString> str) {
		str.add(this);
		for (int i = 0; i < extra.size(); i++)
			extra.get(i).flat(str);
	}
	public List<AnsiString> lines() {
		List<AnsiString> flat = new SimpleList<>();
		flat(flat);
		List<AnsiString> out = new SimpleList<>();

		int prevI = 0;
		for (int i = 0; i < flat.size(); i++) {
			String v = flat.get(i).value;
			if (v.indexOf('\n') >= 0) {
				List<String> lines = TextUtil.split(v, '\n');

				List<AnsiString> extra = new SimpleList<>();
				for (int j = prevI+1; j < i; j++) {
					extra.add(new AnsiString(flat.get(j)));
				}
				extra.add(new AnsiString(flat.get(i), lines.get(0)));

				AnsiString e = new AnsiString(flat.get(prevI));
				out.add(e);
				e.extra = extra;

				for (int j = 0; j < lines.size(); j++)
					out.add(new AnsiString(flat.get(i), lines.get(j)));
			}
		}
		return out;
	}
	public AnsiString writeLimited(CharList sb, MutableInt maxWidth, boolean ansi) {
		int width = CLIUtil.getDisplayWidth(value);
		if (width > maxWidth.value) {
			int i = 0;
			width = 0;
			while (i < value.length()) {
				int w = CLIConsole.getCharLength(value.charAt(i));
				if (width+w > maxWidth.value) {
					sb.append(value, 0, i);
					return new AnsiString(this, value.substring(i));
				}
			}
		} else {
			sb.append(value);
			maxWidth.value -= width;
			for (int i = 0; i < extra.size(); i++) {
				AnsiString split = extra.get(i).writeLimited(sb, maxWidth, ansi);
				if (split != null) {
					split.extra.addAll(extra.subList(i+1, extra.size()));
					return split;
				}
			}
		}
		return null;
	}
	private void writeSGI(CharList sb) {
		if ((fgColor|bgColor|flag) != 0) {
			sb.append("\u001b[");
			if (isClear()) sb.append("0;");
			// BOLD = 1, ITALIC = 3, UNDERLINE = 4, SHINY = 5, REVERSE = 7, DELETE = 9;
			addFlag(sb, 1, "1");
			addFlag(sb, 4, "3");
			addFlag(sb, 16, "4");
			addFlag(sb, 64, "5");
			addFlag(sb, 256, "7");
			addFlag(sb, 1024, "9");
			if (fgColor != 0) {
				if (isColorRGB()) addColor(sb, fgColor, 38);
				else sb.append(fgColor&0xFF).append(';');
			}
			if (bgColor != 0) {
				if (isBgColorRGB()) addColor(sb, bgColor, 48);
				else sb.append(bgColor&0xFF).append(';');
			}
			sb.set(sb.length()-1, 'm');
		}
	}

	// return this+s
	public AnsiString append(AnsiString s) {
		extra().add(s);
		return this;
	}
	// return s+this
	public AnsiString prepend(AnsiString s) {
		s.extra().add(this);
		return s;
	}

	private List<AnsiString> extra() { return extra.isEmpty() ? extra = new SimpleList<>() : extra; }

	public AnsiString color16(int c) { fgColor = (c&0xFF) | 0x80000000; return this; }
	public AnsiString bgColor16(int c) { bgColor = (10 + (c&0xFF)) | 0x80000000; return this; }
	public AnsiString colorRGB(int c) { fgColor = c|0xFF000000; return this; }
	public AnsiString bgColorRGB(int c) { bgColor = c|0xFF000000; return this; }
	public boolean isColorRGB() { return (fgColor&0xFF000000) == 0xFF000000; }
	public boolean isBgColorRGB() { return (fgColor&0xFF000000) == 0xFF000000; }
	public int getFgColor() { return fgColor&0xFFFFFF; }
	public int getBgColor() { return bgColor&0xFFFFFF; }

	public AnsiString bold(Boolean b) { return setFlag(1, b); }
	public AnsiString italic(Boolean b) { return setFlag(4, b); }
	public AnsiString underline(Boolean b) { return setFlag(16, b); }
	public AnsiString shiny(Boolean b) { return setFlag(64, b); }
	public AnsiString reverseColor(Boolean b) { return setFlag(256, b); }
	public AnsiString deleteLine(Boolean b) { return setFlag(1024, b); }
	public AnsiString reset() { flag = 0; bgColor = fgColor = 0; return this; }
	public AnsiString clear() { flag = 4096; return this; }

	public Boolean bold() { return getFlag(1); }
	public Boolean italic() { return getFlag(4); }
	public Boolean underline() { return getFlag(16); }
	public Boolean shiny() { return getFlag(64); }
	public Boolean reverseColor() { return getFlag(256); }
	public Boolean deleteLine() { return getFlag(1024); }
	public boolean isClear() { return (flag&4096) != 0; }

	private AnsiString setFlag(int bit, Boolean b) {
		if (b != null) {
			flag |= bit<<1;
			if (b) flag |= bit;
			else flag &= ~bit;
		} else {
			flag &= ~(bit<<1);
		}
		return this;
	}
	private Boolean getFlag(int bit) {
		if ((flag&(bit<<1)) == 0) return null;
		return (flag&bit) != 0;
	}
	private void addFlag(CharList sb, int bit, String yes) {
		if ((flag&(bit<<1)) == 0) return;
		if ((flag&bit) != 0) {
			sb.append(yes).append(';');
		} else {
			sb.append('2').append(yes).append(';');
		}
	}

	private static void addColor(CharList sb, int rgb, int type) {
		sb.append(type).append(";2;").append((rgb >>> 16)&0xFF).append(';').append((rgb >>> 8)&0xFF).append(';').append(rgb &0xFF).append(';');
	}

	public int length() {
		int len = value.length();
		for (int i = 0; i < extra.size(); i++)
			len += extra.get(i).length();
		return len;
	}
}
