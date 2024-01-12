package roj.text;

import org.jetbrains.annotations.NotNull;
import roj.collect.SimpleList;
import roj.config.word.Tokenizer;
import roj.io.IOUtil;
import roj.util.Helpers;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2021/5/27 0:12
 */
public class LineReader implements Iterator<String>, LinedReader {
	private final CharSequence str;
	private final boolean keepEmpty;
	private int i;

	public LineReader(CharSequence string) { this(string, true); }
	private LineReader(CharSequence s, boolean cleanEmpty) {
		this.str = s;
		this.keepEmpty = !cleanEmpty;
	}
	public static LinedReader create(CharSequence str) { return new LineReader(str, true); }
	public static LinedReader create(CharSequence str, boolean removeEmpty) { return new LineReader(str, removeEmpty); }

	public static List<String> toLines(CharSequence str, boolean clean) {
		List<String> list = new SimpleList<>();
		CharList sb = IOUtil.getSharedCharBuf();
		int i = 0;
		do {
			i = TextUtil.gAppendToNextCRLF(str, i, sb, -1);
			if (!clean || sb.length() > 0) {
				list.add(Tokenizer.addSlashes(sb.toString()));
				sb.clear();
			}
		} while (i >= 0);
		return list;
	}

	/**
	 * line1为第一行
	 */
	public static String getLine(CharSequence keys, int line) {
		int i = 0;
		while (--line > 0) {
			i = TextUtil.gNextCRLF(keys, i);
			if (i < 0) return null;
		}

		CharList sb = IOUtil.getSharedCharBuf();
		TextUtil.gAppendToNextCRLF(keys, i, sb, -1);
		return sb.toString();
	}

	private String tmp;
	@NotNull
	@Override
	public Iterator<String> iterator() { i = 0; return this; }
	@Override
	public boolean hasNext() {
		if (tmp == null) tmp = readLine();
		return tmp != null;
	}
	@Override
	public String next() {
		String t = tmp;
		tmp = null;
		if (t == null) throw new NoSuchElementException();
		return t;
	}

	@Override
	public int skipLines(int oLines) {
		try {
			return LinedReader.super.skipLines(oLines);
		} catch (IOException e) {
			return Helpers.nonnull();
		}
	}

	@Override
	public String readLine() {
		try {
			return LinedReader.super.readLine();
		} catch (IOException e) {
			return Helpers.nonnull();
		}
	}

	@Override
	public boolean readLine(CharList buf) {
		int prevLen = buf.length();
		while (true) {
			if (i < 0) return false;
			i = TextUtil.gAppendToNextCRLF(str, i, buf, -1);
			if (keepEmpty || buf.length() > prevLen) return true;
		}
	}
}