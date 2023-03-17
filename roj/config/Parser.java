package roj.config;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.data.CEntry;
import roj.config.word.StreamAsChars;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static roj.config.JSONParser.COMMENT;
import static roj.config.JSONParser.INTERN;

/**
 * @author Roj233
 * @since 2022/5/17 2:25
 */
public abstract class Parser extends Tokenizer {
	public static final boolean LITERAL_UNSAFE = Boolean.getBoolean("roj.config.noRawCheck");

	protected Parser() {
		this(0);
	}
	protected Parser(int solidFlag) {
		this.flag = (byte) solidFlag;
		ipool = (solidFlag & INTERN) != 0 ? new MyHashSet<>() : null;
		if ((solidFlag & COMMENT) != 0) comment = new CharList();
	}

	public CEntry parse(CharSequence text) throws ParseException {
		return parse(text, 0);
	}
	public abstract CEntry parse(CharSequence text, int flag) throws ParseException;

	public Charset charset = StandardCharsets.UTF_8;
	public Parser charset(Charset le) {
		charset = le;
		return this;
	}
	public final CEntry parseRaw(File file) throws IOException, ParseException {
		return parseRaw(file, 0);
	}
	public CEntry parseRaw(File file, int flag) throws IOException, ParseException {
		return parse(StreamAsChars.from(file, charset), flag);
	}
	public final CEntry parseRaw(DynByteBuf buf) throws ParseException {
		return parseRaw(buf, 0);
	}
	public final CEntry parseRaw(DynByteBuf buf, int flag) throws ParseException {
		try {
			return parseRaw(buf.asInputStream(), flag);
		} catch (IOException e) {
			return Helpers.nonnull();
		}
	}
	public CEntry parseRaw(InputStream in, int flag) throws IOException, ParseException {
		return parse(new StreamAsChars(in, charset), flag);
	}

	abstract CEntry element(int flag) throws ParseException;

	public abstract int acceptableFlags();
	public abstract String format();

	public MyHashSet<CharSequence> ipool;
	protected Word formClip(short id, CharSequence s) {
		if (ipool!=null) {
			CharSequence word = ipool.find(s);
			if (word == s) {
				ipool.add(s = s.toString());
			} else {
				s = word;
			}
		}
		return wd.init(id, index, s.toString());
	}
	public final void clearIPool() {
		if (ipool!=null)ipool.clear();
	}

	int flag;

	final Map<String, String> addComment(Map<String, String> map, String v) {
		if (comment == null || comment.length() == 0) return map;
		if (map == null) map = new MyHashMap<>();
		map.put(v, comment.toString());
		comment.clear();
		return map;
	}

	final void clearComment() {
		if (comment == null) return;
		comment.clear();
	}

	public final CharSequence toString(CEntry entry) {
		return toString(entry, 0);
	}
	public CharSequence toString(CEntry entry, int flag) {
		throw new UnsupportedOperationException("Not implemented yet");
	}
}
