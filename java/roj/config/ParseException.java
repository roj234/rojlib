package roj.config;

import roj.text.CharList;
import roj.text.TextUtil;
import roj.ui.Terminal;

/**
 * Signals that an error has been reached unexpectedly
 * while parsing.
 *
 * @see Exception
 */
public class ParseException extends Exception {
	/**
	 * The zero-based character offset into the string being parsed at which
	 * the error was found during parsing.
	 */
	private final int index;

	private int line = -2, column;
	private CharSequence lineContent;
	private CharList path;

	public static ParseException noTrace(CharSequence all, String reason, int index) {
		return new ParseException(all, reason, index) {
			public Throwable fillInStackTrace() { return this; }
		};
	}

	public ParseException(CharSequence all, String reason, int index) { this(all,reason,index,null); }
	public ParseException(CharSequence all, String reason, int index, Throwable cause) {
		super(filter(reason), cause, true, true);
		this.index = index;
		this.lineContent = all;
	}

	private static String filter(String reason) {
		for (int i = 0; i < reason.length(); i++) {
			char c = reason.charAt(i);
			if (c < 32) {
				var sb = new CharList().append(reason, 0, i);
				for (int j = i; j < reason.length(); j++) {
					c = reason.charAt(j);
					if (c < 32 && c != '\t' && c != '\n') {
						sb.append("#").append((int) c);
					} else {
						sb.append(c);
					}
				}
				return sb.toStringAndFree();
			}
		}
		return reason;
	}

	public int getIndex() { return index; }
	public int getLine() { return line; }
	public int getColumn() { return column; }
	public String getLineContent() { return lineContent.toString(); }

	public CharSequence getPath() { return path; }

	public ParseException addPath(CharSequence pathSeq) {
		if (path == null) path = new CharList();
		path.insert(0, pathSeq);
		return this;
	}

	@Override
	public String getMessage() {
		return !(getCause() instanceof ParseException) ? super.getMessage() : getCause().getMessage();
	}

	private void parseLine() {
		if (line != -2) return;

		CharSequence lines = lineContent;
		int pos = index;
		if (pos > lines.length() || pos < 0) {
			noDetail();
			return;
		}

		int ln = 1;
		int i = 0;
		while (true) {
			int j = TextUtil.gNextCRLF(lines, i);
			if (j > pos || j < 0) {
				CharList sb = new CharList().append(lines, i, j < 0 ? lines.length() : j).trimLast();
				int myLen = sb.length(), myLen2 = sb.replace("\t", "    ").length();
				line = ln;
				column = Math.min(pos-i + myLen2-myLen, sb.length());
				lineContent = sb.toStringAndFree();
				break;
			}

			i = j;
			ln++;
		}
	}

	private void noDetail() {
		line = 0;
		column = 0;
		lineContent = "<无数据>";
	}

	@Override
	public String toString() {
		String msg = getMessage() == null ? (getCause() == null ? "<未提供>" : getCause().toString()) : getMessage();

		try {
			parseLine();
		} catch (Exception ignored) {
			noDetail();
		}

		CharList k = new CharList().append("解析失败: ").append(msg).append("\n第").append(line).append("行: ");

		String line = getLineContent();
		if (column < 0 || column > line.length() || line.length() > 220) {
			k.append("偏移: ").append(column);
		} else {
			k.append(line).append("\n");
			int off = 6 + TextUtil.digitCount(this.line) + Terminal.getStringWidth(line.substring(0, column));
			for (int i = 0; i < off; i++) k.append('-');

			k.append('^');
		}

		k.append("\n总偏移: ").append(index);
		if (path != null) k.append("\n对象位置: ").append(path);
		return k.toStringAndFree();
	}
}