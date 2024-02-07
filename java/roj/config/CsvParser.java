package roj.config;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.word.Word;
import roj.text.CharList;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public final class CsvParser extends Parser<CList> {
	public static final int SKIP_FIRST_LINE = 1;

	private static final short seperator = 11, line = 12;

	private static final MyBitSet CSV_LENDS = MyBitSet.from("\r\n,;");
	private static final Int2IntMap CSV_C2C = new Int2IntMap(16);
	static {
		CSV_C2C.putInt('"', 0);
		CSV_C2C.putInt('\r', 1);
		CSV_C2C.putInt('\n', 2);
		CSV_C2C.putInt(',', 3);
		CSV_C2C.putInt(';', 3);
		fcFill(CSV_C2C, "0123456789", 4);
	}
	{ literalEnd = CSV_LENDS; }

	public static CList parses(CharSequence string) throws ParseException {
		return new CsvParser().parse(string, 0);
	}

	private List<CEntry> tmpList;

	public void forEachLine(File file, Consumer<List<String>> c) throws IOException, ParseException {
		forEachLine(file,c,0);
	}
	public void forEachLine(File file, Consumer<List<String>> c, int flag) throws IOException, ParseException {
		try (TextReader in = new TextReader(file, charset)) {
			forEachLine(in, c, flag);
		}
	}
	public void forEachLine(CharSequence text, Consumer<List<String>> c) throws ParseException {
		forEachLine(text,c,0);
	}
	public void forEachLine(CharSequence text, Consumer<List<String>> c, int flag) throws ParseException {
		init(text);
		tmpList = new SimpleList<>();

		int i = 0;
		if ((flag & SKIP_FIRST_LINE) != 0) {
			skipFirstLine();
			i = 1;
		}

		while (true) {
			Word w = next();
			short type = w.type();
			if (type == Word.EOF) break;
			else if (type == line) continue;

			try {
				c.accept(fastLine(w));
			} catch (ParseException e) {
				throw e.addPath("$["+i+"]");
			}
			i++;
		}

		tmpList = null;
		input = "";
	}

	private void skipFirstLine() throws ParseException {
		while (true) {
			Word w = next();
			short type = w.type();
			if (type == Word.EOF) break;
			else if (type == line) continue;

			try {
				fastLine(w);
			} catch (ParseException e) {
				throw e.addPath("$[0]");
			}
			break;
		}
	}

	private List<String> fastLine(Word w) throws ParseException {
		List<String> list = Helpers.cast(tmpList); list.clear();

		loop:
		while (true) {
			if (w.type() < 10) {
				list.add(w.val());
				w = next();
			} else {
				// really: after-seperator
				list.add(null);
			}

			switch (w.type()) {
				case line: case Word.EOF: break loop;
				case seperator: w = next(); break;
				default: unexpected(w.val(), "分隔符");
			}
		}

		return list;
	}

	public CsvParser() {}

	@Override
	public CList parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);
		tmpList = new SimpleList<>();

		int i = 0;
		if ((flag & SKIP_FIRST_LINE) != 0) {
			skipFirstLine();
			i = 1;
		}

		CList list = new CList();
		while (true) {
			Word w = next();
			short type = w.type();
			if (type == Word.EOF) break;
			else if (type == line) continue;

			try {
				list.add(csvLine(w));
			} catch (ParseException e) {
				throw e.addPath("$["+i+"]");
			}
			i++;
		}

		tmpList = null;
		init(null);
		return list;
	}

	private CList csvLine(Word w) throws ParseException {
		List<CEntry> buf = tmpList; buf.clear();

		loop:
		while (true) {
			CEntry entry;
			switch (w.type()) {
				case Word.LITERAL: entry = CString.valueOf(w.val()); break;
				case Word.INTEGER: entry = CInteger.valueOf(w.asInt()); break;
				case Word.DOUBLE: entry = CDouble.valueOf(w.asDouble()); break;
				case Word.LONG: entry = CLong.valueOf(w.asLong()); break;
				default: entry = CNull.NULL; break;
			}
			buf.add(entry);
			if (w.type() < 10) w = next();

			switch (w.type()) {
				case line: case Word.EOF: break loop;
				case seperator: w = next(); break;
				default: unexpected(w.val(), "分隔符");
			}
		}

		return new CList(new SimpleList<>(buf));
	}

	public int availableFlags() { return SKIP_FIRST_LINE; }
	public String format() { return "CSV"; }

	@Override
	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;
		if (i >= in.length()) return eof();

		switch (CSV_C2C.getOrDefaultInt(in.charAt(i++), -1)) {
			default: return readLiteral();
			case 0: index = i; return csvQuote();
			case 1: if (i < in.length() && in.charAt(i) == '\n') i++;
			case 2: index = i; return formClip(line, "\n");
			case 3: index = i; return formClip(seperator, ",");
			case 4: index = i-1; return digitReader(false,0);
		}
	}

	@SuppressWarnings("fallthrough")
	private Word csvQuote() throws ParseException {
		CharSequence in = input;
		int i = index;

		CharList v = found; v.clear();

		int prevI = i;
		eof: {
			while (i < in.length()) {
				char c = in.charAt(i++);
				if ('"' == c) {
					if (i < in.length() && in.charAt(i) == '"') {
						v.append(in, prevI, i-1);
						prevI = i++;
					} else {
						break eof;
					}
				}
			}

			throw err("未终止的 QUOTE", index);
		}
		index = i;

		return formClip(Word.LITERAL, v.append(in, prevI, i-1));
	}

	@Override
	protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException {
		return readLiteral();
	}
}