package roj.ui;

import roj.collect.SimpleList;
import roj.config.data.CInt;
import roj.config.serial.CVisitor;
import roj.config.serial.ToJson;
import roj.config.serial.ToSomeString;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/20 0020 1:35
 */
public class AnsiString {
	private final CharSequence value;
	private int fgColor, bgColor;
	private short flag;

	private List<AnsiString> extra = Collections.emptyList();

	public AnsiString(CharSequence value) { this.value = value; }
	public AnsiString(AnsiString copy) { this(copy, copy.value); }
	public AnsiString(AnsiString copy, CharSequence altValue) {
		this.value = altValue;
		this.fgColor = copy.fgColor;
		this.bgColor = copy.bgColor;
		this.flag = copy.flag;
		this.extra = copy.extra.isEmpty() ? Collections.emptyList() : new SimpleList<>(copy.extra);
	}

	public CharList writeAnsi(CharList sb) {
		int i = 0;
		while (true) {
			writeSGI(sb);
			i = TextUtil.gAppendToNextCRLF(value, i, sb);
			if (i < value.length()) sb.append('\n');
			else break;
		}

		if (i > 0 && value.charAt(i-1) == '\n') {
			writeSGI(sb);
			sb.append('\n');
		}

		for (i = 0; i < extra.size(); i++)
			extra.get(i).writeAnsi(sb);
		return sb;
	}
	public CharList writeRaw(CharList sb) {
		sb.append(value);
		for (int i = 0; i < extra.size(); i++)
			extra.get(i).writeRaw(sb);
		return sb;
	}
	protected String getMinecraftType() { return "text"; }
	public void writeJson(CVisitor ser) {
		ser.valueMap();
		ser.key(getMinecraftType());
		ser.value(value.toString());

		if (flag != 0) {
			if (isClear()) {
				ser.key("reset");
				ser.value(true);
			}
			addFlag(ser, 1, "bold");
			addFlag(ser, 4, "italic");
			addFlag(ser, 16, "underline");
			addFlag(ser, 256, "obfuscated");
			addFlag(ser, 1024, "strikethrough");
		}

		if (fgColor != 0) {
			ser.key("color");
			if (isColorRGB()) {
				ser.value("#"+Integer.toHexString(fgColor));
			} else {
				ser.value(Terminal.MinecraftColor.getByConsoleCode(fgColor&0xFF));
			}
		}

		if (extra.size() > 0) {
			ser.key("extra");
			ser.valueList(extra.size());
			for (int i = 0; i < extra.size(); i++) {
				extra.get(i).writeJson(ser);
			}
			ser.pop();
		}
	}

	public String toString() { return extra == null ? value.toString() : writeRaw(IOUtil.getSharedCharBuf()).toString(); }
	public String toAnsiString() { return isSimple() ? value.toString() : writeAnsi(IOUtil.getSharedCharBuf()).toString(); }
	public String toMinecraftJson() {
		ToSomeString ser = new ToJson().sb(IOUtil.getSharedCharBuf());
		writeJson(ser);
		return ser.getValue().toString();
	}
	private boolean isSimple() {return extra == null && (fgColor|bgColor|flag) == 0;}

	public void flat(List<AnsiString> str) {
		str.add(this);
		for (int i = 0; i < extra.size(); i++)
			extra.get(i).flat(str);
	}
	public List<AnsiString> lines() {
		List<AnsiString> flat = new SimpleList<>();
		flat(flat);
		List<AnsiString> out = new SimpleList<>();

		AnsiString currentLine = flat.get(0);
		for (int i = 1; i < flat.size(); i++) {
			CharSequence v = flat.get(i).value;
			if (TextUtil.gIndexOf(v, '\n') < 0) {
				currentLine.append(flat.get(i));
				continue;
			}

			CharList sb = new CharList();
			int j = 0;
			do {
				sb.clear();
				j = TextUtil.gAppendToNextCRLF(v, j, sb);
				out.add(currentLine = new AnsiString(flat.get(i), sb.append('\n').toString()));
			} while (j < v.length());

			if (v.charAt(j-1) == '\n') out.add(currentLine = new AnsiString(flat.get(i), ""));
		}

		out.add(currentLine);
		return out;
	}
	public AnsiString writeLimited(CharList sb, CInt maxWidth, boolean ansi) {
		int width = Terminal.getStringWidth(value);
		if (width > maxWidth.value) {
			int i = 0;
			width = 0;
			while (i < value.length()) {
				int w = Terminal.getCharWidth(value.charAt(i));
				if (width+w > maxWidth.value) {
					sb.append(value, 0, i);
					return new AnsiString(this, value.subSequence(i, value.length()));
				}

				i++;
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
	public boolean isBgColorRGB() { return (bgColor&0xFF000000) == 0xFF000000; }
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
	private void addFlag(CVisitor sb, int bit, String yes) {
		if ((flag&(bit<<1)) == 0) return;
		sb.key(yes);
		sb.value((flag & bit) != 0);
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