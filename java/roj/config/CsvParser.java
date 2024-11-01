package roj.config;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.serial.CVisitor;
import roj.config.table.TableParser;
import roj.config.table.TableReader;
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
	private static final short seperator = 9, line = 10;

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
		tmpList = new SimpleList<>();

		int i = 1;
		while (true) {
			Word w = next();
			short type = w.type();
			if (type == Word.EOF) break;
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
	private List<String> fastLine(Word w) throws ParseException {
		List<String> list = Helpers.cast(tmpList); list.clear();

		for(;; w = next()) {
			var type = w.type();
			if (type == seperator) {list.add(null);continue;}
			list.add(w.val());

			w = next();
			type = w.type();

			if (type == seperator) continue;
			if (type == line || type == Word.EOF) break;
			unexpected(w.toString(), "分隔符");
		}

		return list;
	}

	public CsvParser() {}

	public <C extends CVisitor> C parse(CharSequence text, int flags, C cv) throws ParseException {
		this.flag = flags;
		init(text);
		tmpList = new SimpleList<>();

		int i = 0;

		cv.valueList();

		var keys = new SimpleList<>(fastLine(next()));
		List<String> list = Helpers.cast(tmpList);

		while (true) {
			Word w = next();
			short type = w.type();
			if (type == Word.EOF) break;
			else if (type == line) continue;

			try {
				list.clear();
				int j = 0;

				cv.valueMap();
				for(;; w = next(), j++) {
					if (w.type() == seperator) continue;

					cv.key(keys.get(j));
					switch (w.type()) {
						case Word.LITERAL -> cv.value(w.val());
						case Word.INTEGER -> cv.value(w.asInt());
						case Word.DOUBLE -> cv.value(w.asDouble());
						case Word.LONG -> cv.value(w.asLong());
					}

					w = next();
					type = w.type();

					if (type == seperator) continue;
					if (type == line || type == Word.EOF) break;
					unexpected(w.val(), "分隔符");
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
		tmpList = new SimpleList<>();

		int i = 0;
		var list = new CList();
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
		var buf = tmpList; buf.clear();

		for(;; w = next()) {
			var type = w.type();
			buf.add(switch (type) {
				case Word.LITERAL -> CString.valueOf(w.val());
				case Word.INTEGER -> CInt.valueOf(w.asInt());
				case Word.DOUBLE -> CDouble.valueOf(w.asDouble());
				case Word.LONG -> CLong.valueOf(w.asLong());
				default -> CNull.NULL;
			});
			if (type == seperator) continue;

			w = next();
			type = w.type();

			if (type == seperator) continue;
			if (type == line || type == Word.EOF) break;
			unexpected(w.val(), "分隔符");
		}

		return new CList(new SimpleList<>(buf));
	}

	public ConfigMaster format() {return ConfigMaster.CSV;}

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
	protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException { return readLiteral(); }
}