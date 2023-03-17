package roj.config;

import roj.math.MathUtils;
import roj.text.CharList;

/**
 * Signals that an error has been reached unexpectedly
 * while parsing.
 *
 * @see Exception
 */
public final class ParseException extends Exception {
	private static final long serialVersionUID = 3703218443322787635L;

	/**
	 * The zero-based character offset into the string being parsed at which
	 * the error was found during parsing.
	 */
	private final int index;

	private int line = -2, linePos;
	private CharSequence lineContent;
	private CharList path;

	public ParseException(CharSequence all, String reason, int index, Throwable cause) {
		super(filter(reason), cause, true, true);
		this.index = index;
		this.lineContent = all;
	}

	public ParseException(String reason, int index) {
		super(reason, null, true, true);
		this.index = index;
		noDetail();
	}

	private static String filter(String reason) {
		for (int i = 0; i < reason.length(); i++) {
			char c = reason.charAt(i);
			if (c < 32) {
				StringBuilder sb = new StringBuilder().append(reason, 0, i);
				for (int j = i; j < reason.length(); j++) {
					c = reason.charAt(j);
					if (c < 32) {
						sb.append("#").append((int) c);
					} else {
						sb.append(c);
					}
				}
				return sb.toString();
			}
		}
		return reason;
	}

	public int getIndex() {
		return index;
	}

	public int getLine() {
		return line;
	}

	public int getLineOffset() {
		return linePos;
	}

	public CharSequence getPath() {
		return path;
	}

	public ParseException addPath(CharSequence pathSeq) {
		if (path == null) path = new CharList();
		path.insert(0, pathSeq);
		return this;
	}

	@Override
	public String getMessage() {
		return !(getCause() instanceof ParseException) ? super.getMessage() : getCause().getMessage();
	}

	public String getLineContent() {
		return lineContent.toString();
	}

	@SuppressWarnings("fallthrough")
	private void __lineParser() {
		if (this.line != -2) return;

		CharList chars = new CharList(20);

		CharSequence keys = this.lineContent;

		int target = index;
		if (target > keys.length() || target < 0) {
			noDetail();
			return;
		}

		int line = 1, linePos = 0;
		int i = 0;

		for (; i < target; i++) {
			char c1 = keys.charAt(i);
			switch (c1) {
				case '\r':
					if (i + 1 < keys.length() && keys.charAt(i + 1) == '\n') // \r\n
					{i++;}
				case '\n':
					linePos = 0;
					line++;
					chars.clear();
					break;
				default:
					linePos++;
					chars.append(c1);
			}
		}

		o:
		for (; i < keys.length(); i++) {
			char c1 = keys.charAt(i);
			switch (c1) {
				case '\r':
				case '\n':
					break o; // till this line end
				default:
					chars.append(c1);
			}
		}

		this.line = line;
		this.linePos = linePos - 1;
		this.lineContent = chars.toString();
	}

	private void noDetail() {
		line = 0;
		linePos = 0;
		lineContent = "<无数据>";
	}

	@Override
	public String toString() {
		String msg = getMessage() == null ? (getCause() == null ? "<未提供>" : getCause().toString()) : getMessage();

		try {
			__lineParser();
		} catch (Exception ignored) {
			noDetail();
		}

		String line = getLineContent();

		CharList k = new CharList().append("解析错误:\r\n  Line ").append(this.line).append(": ");

		if (line.length() > 512) {
			k.append("当前行偏移量 ").append(this.linePos);
		} else {
			k.append(line).append("\r\n");
			int off = this.linePos + 10 + MathUtils.digitCount(this.line);
			for (int i = 0; i < off; i++) {
				k.append('-');
			}

			for (int i = linePos; i >= 0; i--) {
				char c = line.charAt(i);
				if (c > 255) // 双字节我直接看做中文了, 再说吧
					k.append('-');
				else if(c == 9) // 制表符, cmd中显示4个
					k.append("----");
			}
			k.append('^');
		}

		k.append("\r\n总偏移量: ").append(index);
		if (path != null) {
			k.append("\r\n对象位置: ").append(path);
		}
		return k.append("\r\n原因: ").append(msg).append("\r\n").toString();
	}
}
