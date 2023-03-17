package roj.config;

import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.config.data.*;
import roj.config.word.Word;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.List;
import java.util.function.Consumer;

import static roj.config.JSONParser.FALSE;
import static roj.config.JSONParser.TRUE;

/**
 * @author Roj233
 * @since 2022/1/6 13:46
 */
public final class CsvParser extends Parser {
	private static final short comma = 11, delim = 12, line = 13;
	private List<CEntry> tmpList;

	private static final MyBitSet CSV_LENDS = MyBitSet.from("\r\n,;");
	private static final Int2IntMap CSV_C2C = new Int2IntMap();
	static {
		CSV_C2C.putInt('"', 0);
		CSV_C2C.putInt('\r', 1);
		CSV_C2C.putInt('\n', 2);
		CSV_C2C.putInt(',', 3);
		CSV_C2C.putInt(';', 4);
	}
	{ literalEnd = CSV_LENDS; }

	public static CList parses(CharSequence string) throws ParseException {
		return new CsvParser().parse(string, 0);
	}

	public void forEachLine(CharSequence text, Consumer<List<String>> c) throws ParseException {
		init(text);
		tmpList = new SimpleList<>();

		int i = 0;
		label:
		while (hasNext()) {
			Word w = next();
			switch (w.type()) {
				case Word.EOF: break label;
				case line: continue;
			}
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

	public CsvParser() {}

	@Override
	public CList parse(CharSequence text, int flags) throws ParseException {
		init(text);
		tmpList = new SimpleList<>();

		SimpleList<CEntry> internalList = new SimpleList<>(16);
		internalList.capacityType = 2;
		CList list = new CList(internalList);
		label:
		while (hasNext()) {
			Word w = next();
			switch (w.type()) {
				case Word.EOF: break label;
				case line: continue;
			}
			try {
				list.add(csvLine(w));
			} catch (ParseException e) {
				throw e.addPath("$["+list.size()+"]");
			}
		}

		tmpList = null;
		input = "";
		return list;
	}

	private CList csvLine(Word w) throws ParseException {
		List<CEntry> list = tmpList; list.clear();

		loop:
		while (true) {
			switch (w.type()) {
				case comma: case delim:
					list.add(CNull.NULL);
					if (!hasNext()) break loop;
					w = next();
					continue;
				default:
					try {
						list.add(element(w));
					} catch (ParseException e) {
						throw e.addPath("["+list.size()+"]");
					}
					if (!hasNext()) break loop;
					break;
			}

			w = next();
			switch (w.type()) {
				case line: case Word.EOF:
					break loop;
				case comma: case delim:
					w = next();
					break;
				default: unexpected(w.val(), ",");
			}
		}

		return new CList(new SimpleList<>(list));
	}

	private List<String> fastLine(Word w) throws ParseException {
		List<String> list = Helpers.cast(tmpList); list.clear();

		loop:
		while (true) {
			switch (w.type()) {
				case comma: case delim:
					list.add(null);
					if (!hasNext()) break loop;
					w = next();
				continue;
				default:
					list.add(w.val());
					if (!hasNext()) break loop;
					break;
			}

			w = next();
			switch (w.type()) {
				case line: case Word.EOF:
					break loop;
				case comma: case delim:
					w = next();
					break;
				default: unexpected(w.val(), ",");
			}
		}

		return list;
	}

	@Override
	public int acceptableFlags() {
		return 0;
	}

	@Override
	public String format() {
		return "CSV";
	}

	CEntry element(int flag) { return null; }
	private CEntry element(Word w) throws ParseException {
		switch (w.type()) {
			case Word.LITERAL:
			case Word.STRING: return CString.valueOf(w.val());
			case Word.DOUBLE:
			case Word.FLOAT: return CDouble.valueOf(w.asDouble());
			case Word.INTEGER: return CInteger.valueOf(w.asInt());
			case Word.LONG: return CLong.valueOf(w.asLong());
			case TRUE:
			case FALSE: return CBoolean.valueOf(w.type() == TRUE);
			default: unexpected(w.val()); return null;
		}
	}

	@Override
	@SuppressWarnings("fallthrough")
	public Word readWord() throws ParseException {
		CharSequence in = input;
		int i = index;
		if (i >= in.length()) return eof();

		switch (CSV_C2C.getOrDefaultInt(in.charAt(i++), -1)) {
			default: return readLiteral();
			case 0: index = i; return readCSVZ();
			case 1: if (i < in.length() && in.charAt(i) == '\n') i++;
			case 2: index = i; return formClip(line, "\n");
			case 3: index = i; return formClip(comma, ",");
			case 4: index = i; return formClip(delim, ";");
		}
	}

	@SuppressWarnings("fallthrough")
	private Word readCSVZ() throws ParseException {
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
	protected Word formLiteralClip(CharSequence v) {
		if (v.length() == 4 && v.equals("true")) {
			return formClip(TRUE, "true");
		} else if (v.length() == 5 && v.equals("false")) {
			return formClip(FALSE, "false");
		}
		return super.formLiteralClip(v);
	}
}
