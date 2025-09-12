package roj.config.table;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.collect.Int2IntMap;
import roj.config.Parser;
import roj.config.ValueEmitter;
import roj.config.node.ConfigValue;
import roj.config.node.ListValue;
import roj.config.node.NullValue;
import roj.io.source.Source;
import roj.text.CharList;
import roj.text.ParseException;
import roj.text.TextReader;
import roj.text.Token;
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

	private List<ConfigValue> tmpList;

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

	public <E extends ValueEmitter> E parse(CharSequence text, int flags, E emitter) throws ParseException {
		this.flag = flags;
		init(text);
		tmpList = new ArrayList<>();

		int i = 0;

		emitter.emitList();

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

				emitter.emitMap();
				for(;; w = next(), j++) {
					type = w.type();
					if (type >= 0 && type < 8) {
						if (hasValue) unexpected(w.toString(), "分隔符");
						hasValue = true;

						emitter.key(keys.get(j));
						switch (w.type()) {
							case Token.LITERAL -> emitter.emit(w.text());
							case Token.INTEGER -> emitter.emit(w.asInt());
							case Token.DOUBLE -> emitter.emit(w.asDouble());
							case Token.LONG -> emitter.emit(w.asLong());
						}
					} else {
						if (!hasValue) list.add(null);
						if (type != separator) break;
						hasValue = false;
					}
				}
				emitter.pop();
			} catch (ParseException e) {
				throw e.addPath("$["+i+"]");
			}
			i++;
		}

		emitter.pop();
		init(null);
		return emitter;
	}

	@Override
	@Deprecated
	public ListValue parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);
		tmpList = new ArrayList<>();

		int i = 0;
		var list = new ListValue();
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
	private ListValue csvLine(Token w) throws ParseException {
		var buf = tmpList; buf.clear();

		var hasValue = false;
		for(;; w = next()) {
			var type = w.type();
			mySegmentBlock: {
				ConfigValue entry;
				switch (type) {
					default: break mySegmentBlock;
					case Token.LITERAL: entry = ConfigValue.valueOf(w.text());break;
					case Token.INTEGER: entry = ConfigValue.valueOf(w.asInt());break;
					case Token.DOUBLE:  entry = ConfigValue.valueOf(w.asDouble());break;
					case Token.LONG:    entry = ConfigValue.valueOf(w.asLong());break;
				}
				buf.add(entry);

				if (hasValue) unexpected(w.toString(), "分隔符");
				hasValue = true;
				continue;
			}

			if (!hasValue) buf.add(NullValue.NULL);
			if (type != separator) break;
			hasValue = false;
		}

		return new ListValue(new ArrayList<>(buf));
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