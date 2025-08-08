package roj.config.table;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.Int2IntMap;
import roj.config.ParseException;
import roj.config.Parser;
import roj.config.Token;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CNull;
import roj.config.serial.CVisitor;
import roj.io.source.Source;
import roj.text.CharList;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public final class CsvParser extends Parser implements TableParser {
	private static final short separator = 9, line = 10;

	private static final BitSet CSV_LENDS = BitSet.from("\r\n,;");
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

	private List<CEntry> tmpList;

	@Override public void table(File file, Charset charset, TableReader listener) throws IOException, ParseException {
		try (var tw = TextReader.from(file, charset)) {
			listener.onSheet(0, "csvTable", null);
			forEachLine(tw, listener);
		}
	}
	@Override public void table(Source file, Charset charset, TableReader listener) throws IOException, ParseException {
		try (var tw = TextReader.from(file.asInputStream(), charset)) {
			listener.onSheet(0, "csvTable", null);
			forEachLine(tw, listener);
		}
	}

	public void forEachLine(CharSequence text, TableReader c) throws ParseException {
		init(text);
		tmpList = new ArrayList<>();

		int i = 1;
		while (true) {
			Token w = next();
			short type = w.type();
			if (type == Token.EOF) break;
			else if (type == line) continue;

			try {
				c.onRow(i, fastLine(w));
			} catch (ParseException e) {
				throw e.addPath("$["+i+"]");
			}
			i++;
		}

		tmpList = null;
		input = "";
	}
	private List<String> fastLine(Token w) throws ParseException {
		List<String> list = Helpers.cast(tmpList); list.clear();

		boolean hasValue = false;
		for(;; w = next()) {
			var type = w.type();
			if (type >= 0 && type < 8) {
				if (hasValue) unexpected(w.toString(), "分隔符");
				hasValue = true;

				list.add(w.text());
			} else {
				if (!hasValue) list.add(null);
				if (type != separator) break;
				hasValue = false;
			}
		}

		return list;
	}

	public CsvParser() {}

	public <C extends CVisitor> C parse(CharSequence text, int flags, C cv) throws ParseException {
		this.flag = flags;
		init(text);
		tmpList = new ArrayList<>();

		int i = 0;

		cv.valueList();

		var keys = new ArrayList<>(fastLine(next()));
		List<String> list = Helpers.cast(tmpList);

		while (true) {
			Token w = next();
			short type = w.type();
			if (type == Token.EOF) break;
			else if (type == line) continue;

			try {
				list.clear();
				int j = 0;
				boolean hasValue = false;

				cv.valueMap();
				for(;; w = next(), j++) {
					type = w.type();
					if (type >= 0 && type < 8) {
						if (hasValue) unexpected(w.toString(), "分隔符");
						hasValue = true;

						cv.key(keys.get(j));
						switch (w.type()) {
							case Token.LITERAL -> cv.value(w.text());
							case Token.INTEGER -> cv.value(w.asInt());
							case Token.DOUBLE -> cv.value(w.asDouble());
							case Token.LONG -> cv.value(w.asLong());
						}
					} else {
						if (!hasValue) list.add(null);
						if (type != separator) break;
						hasValue = false;
					}
				}
				cv.pop();
			} catch (ParseException e) {
				throw e.addPath("$["+i+"]");
			}
			i++;
		}

		cv.pop();
		init(null);
		return cv;
	}

	@Override
	@Deprecated
	public CList parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);
		tmpList = new ArrayList<>();

		int i = 0;
		var list = new CList();
		while (true) {
			Token w = next();
			short type = w.type();
			if (type == Token.EOF) break;
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
	private CList csvLine(Token w) throws ParseException {
		var buf = tmpList; buf.clear();

		var hasValue = false;
		for(;; w = next()) {
			var type = w.type();
			mySegmentBlock: {
				CEntry entry;
				switch (type) {
					default: break mySegmentBlock;
					case Token.LITERAL: entry = CEntry.valueOf(w.text());break;
					case Token.INTEGER: entry = CEntry.valueOf(w.asInt());break;
					case Token.DOUBLE:  entry = CEntry.valueOf(w.asDouble());break;
					case Token.LONG:    entry = CEntry.valueOf(w.asLong());break;
				}
				buf.add(entry);

				if (hasValue) unexpected(w.toString(), "分隔符");
				hasValue = true;
				continue;
			}

			if (!hasValue) buf.add(CNull.NULL);
			if (type != separator) break;
			hasValue = false;
		}

		return new CList(new ArrayList<>(buf));
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Token readWord() throws ParseException {
		CharSequence in = input;
		int i = index;
		if (i >= in.length()) return eof();

		switch (CSV_C2C.getOrDefaultInt(in.charAt(i++), -1)) {
			default: return readLiteral();
			case 0: index = i; return csvQuote();
			case 1: if (i < in.length() && in.charAt(i) == '\n') i++;
			case 2: index = i; return formClip(line, "\n");
			case 3: index = i; return formClip(separator, ",");
			case 4: index = i-1; return digitReader(false,0);
		}
	}

	@SuppressWarnings("fallthrough")
	private Token csvQuote() throws ParseException {
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

		return formClip(Token.LITERAL, v.append(in, prevI, i-1));
	}
}