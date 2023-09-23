package roj.text;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * @author Roj234
 * @since 2021/5/27 0:12
 */
@Deprecated
public class LineReader implements Iterable<String>, Iterator<String>, AutoCloseable, LinedReader {
	private final CharSequence str;
	private boolean keepEmpty, reuse;
	private int index;
	private int lineNumber;

	public LineReader(InputStream in) throws IOException {
		str = new TextReader(in);
	}
	public LineReader(InputStream in, Charset cs) throws IOException {
		str = new TextReader(in, cs);
	}
	public LineReader(InputStream in, Charset cs, boolean cleanEmpty) throws IOException {
		this.str = new TextReader(in);
		this.keepEmpty = !cleanEmpty;
	}

	public LineReader(CharSequence string) {
		this(string, true);
	}
	public LineReader(CharSequence s, boolean cleanEmpty) {
		this.str = s;
		this.keepEmpty = !cleanEmpty;
		this.reuse = !(s instanceof TextReader);
	}

	@SuppressWarnings("fallthrough")
	public static List<String> slrParserV2(CharSequence keys, boolean clean) {
		List<String> list = new ArrayList<>();

		int r = 0, i = 0, prev = 0;
		while (i < keys.length()) {
			switch (keys.charAt(i)) {
				case '\r':
					if (i + 1 >= keys.length() || keys.charAt(i + 1) != '\n') {
						break;
					} else {
						r = 1;
						i++;
					}
				case '\n':
					if (prev < i || !clean) {
						list.add(prev == i ? "" : keys.subSequence(prev, i - r).toString());
					}
					prev = i + 1;
					r = 0;
					break;
			}
			i++;
		}

		if (prev < i) list.add(keys.subSequence(prev, i).toString());

		return list;
	}

	@SuppressWarnings("fallthrough")
	public static String readSingleLine(CharSequence keys, int line) {
		int r = 0, i = 0, prev = 0;
		while (i < keys.length()) {
			switch (keys.charAt(i)) {
				case '\r':
					if (i + 1 >= keys.length() || keys.charAt(i + 1) != '\n') {
						break;
					} else {
						r = 1;
						i++;
					}
				case '\n':
					if (--line == 0) {
						return prev == i ? "" : keys.subSequence(prev, i - r).toString();
					}
					prev = i + 1;
					r = 0;
					break;
			}
			i++;
		}

		return --line == 0 ? prev == i ? "" : keys.subSequence(prev, i).toString() : null;
	}

	@Nonnull
	@Override
	public Iterator<String> iterator() {
		if (!reuse && index > 0) throw new IllegalArgumentException("Set 'reusable' to TRUE when reading stream");
		index = 0;
		lineNumber = 0;
		cur = null;
		return this;
	}

	static final String EOF = new String();
	private String cur;

	@Override
	@SuppressWarnings("fallthrough")
	public boolean hasNext() {
		if (cur != null) {
			return cur != EOF;
		} else if (index >= str.length()) {
			if (keepEmpty) lineNumber++;
			int size = lineNumber;
			cur = EOF;
			return false;
		}

		int r = 0, i = index;
		CharSequence s = str;
		if (!reuse) ((TextReader) s).releaseBefore(i);
		while (i < s.length()) {
			switch (s.charAt(i)) {
				case '\r':
					if (i >= s.length() || s.charAt(i + 1) != '\n') {
						break;
					} else {
						r = 1;
						i++;
					}
				case '\n':
					if (i > index + r || keepEmpty) {
						CharSequence seq = index == i ? "" : s.subSequence(index, i - r);
						index = i + 1;
						lineNumber++;
						cur = seq.toString();
						return true;
					}
					index = i + 1;
					r = 0;
					break;
			}
			i++;
		}

		if (i > index || keepEmpty) {
			CharSequence seq = index == i ? "" : s.subSequence(index, i);
			index = i + 1;
			cur = seq.toString();
			return true;
		} else {
			index = i;
			cur = EOF;
			return false;
		}
	}

	public String readLine() { return hasNext() ? next() : null; }
	public boolean readLine(CharList buf) throws IOException {
		String s = readLine();
		if (s == null) return false;
		buf.append(s);
		return true;
	}

	@Override
	public String next() {
		hasNext();
		if (cur == EOF) {
			throw new NoSuchElementException();
		} else {
			String c = cur;
			cur = null;
			return c;
		}
	}

	@Deprecated
	public int skipLines(int oLines) {
		int lines = oLines;
		int r = 0, prev = index, i = index;
		CharSequence s = str;
		if (!reuse) ((TextReader) s).releaseBefore(i);
		while (i < s.length()) {
			switch (s.charAt(i)) {
				case '\r':
					if (i >= s.length() || s.charAt(i + 1) != '\n') {
						break;
					} else {
						r = 1;
						i++;
					}
				case '\n':
					if (i > prev + r || keepEmpty) {
						lineNumber++;
						if (--lines <= 0) {
							index = i+1;
							return oLines;
						}
						r = 0;
					}
					prev = i + 1;
					break;
			}
			i++;
		}

		if (i > prev || keepEmpty) {
			lineNumber++;
			if (--lines <= 0) {
				index = i+1;
				return oLines;
			}
		}
		cur = EOF;
		return oLines - lines;
	}

	public int lineNumber() {
		return lineNumber;
	}

	@Override
	public void close() throws IOException {
		if (!reuse) ((TextReader) str).close();
	}
}
