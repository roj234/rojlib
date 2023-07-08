package roj.config;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.config.data.CEntry;
import roj.config.serial.CVisitor;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.StreamReader;
import roj.text.StreamWriter;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static roj.config.JSONParser.*;
import static roj.config.word.Word.EOF;

/**
 * @author Roj233
 * @since 2022/5/17 2:25
 */
public abstract class Parser<T extends CEntry> extends Tokenizer implements BinaryParser {
	public static final boolean LITERAL_UNSAFE = Boolean.getBoolean("roj.config.noRawCheck");

	protected Parser() {
		this(0);
	}
	protected Parser(int solidFlag) {
		this.flag = solidFlag;
		ipool = (solidFlag & INTERN) != 0 ? new MyHashSet<>() : null;
		if ((solidFlag & COMMENT) != 0) comment = new CharList();
	}

	public final T parse(CharSequence text) throws ParseException { return parse(text, 0); }
	@SuppressWarnings("unchecked")
	public T parse(CharSequence text, int flag) throws ParseException {
		this.flag = flag;
		init(text);

		CEntry entry;
		try {
			entry = element(flag);
			if ((flag & NO_EOF) == 0) except(EOF);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}

		return (T) entry;
	}
	CEntry element(int flag) throws ParseException {
		throw new UnsupportedOperationException("Not implemented");
	}

	public <C extends CVisitor> C parse(C cv, CharSequence text, int flag) throws ParseException {
		parse(text,flag).forEachChild(cv);
		return cv;
	}

	// auto detect
	public Charset charset = null;
	public final Parser<T> charset(Charset cs) {
		charset = cs;
		return this;
	}

	public final T parseRaw(File file, int flag) throws IOException, ParseException {
		try (StreamReader in = new StreamReader(file, charset)) {
			return parse(in, flag);
		} catch (ParseException e) {
			throw new ParseException(IOUtil.readString(file), e.getMessage(), e.getIndex(), e.getCause());
		}
	}
	public final <C extends CVisitor> C parseRaw(C cv, File file, int flag) throws IOException, ParseException {
		try (StreamReader text = new StreamReader(file, charset)) {
			return parse(cv, text, flag);
		} catch (ParseException e) {
			throw new ParseException(IOUtil.readString(file), e.getMessage(), e.getIndex(), e.getCause());
		}
	}
	public final T parseRaw(DynByteBuf buf, int flag) throws IOException, ParseException {
		return parseRaw(buf.asInputStream(), flag);
	}
	public final T parseRaw(InputStream in, int flag) throws IOException, ParseException {
		try (StreamReader text = new StreamReader(in, charset)) {
			return parse(text, flag);
		}
	}
	public final <C extends CVisitor> C parseRaw(C cv, InputStream in, int flag) throws IOException, ParseException {
		try (StreamReader text = new StreamReader(in, charset)) {
			return parse(cv, text, flag);
		}
	}

	public abstract int availableFlags();
	public abstract String format();

	public MyHashSet<CharSequence> ipool;
	protected Word formClip(short id, CharSequence s) {
		if (ipool!=null && s.getClass() != String.class) {
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

	public final String toString(CEntry entry) { return toString(entry, 0); }
	public final String toString(CEntry entry, int flag) { return append(entry, flag, IOUtil.ddLayeredCharBuf()).toStringAndFree(); }
	public CharList append(CEntry entry, int flag, CharList sb) { throw new UnsupportedOperationException(); }

	public final void serialize(CEntry entry, DynByteBuf out) throws IOException { serialize(entry, 0, out); }
	public final void serialize(CEntry entry, OutputStream out) throws IOException { serialize(entry, 0, out); }
	public void serialize(CEntry entry, int flag, OutputStream out) throws IOException {
		StreamWriter os = new StreamWriter(out, charset == null ? StandardCharsets.UTF_8 : charset);
		try {
			append(entry, flag, os);
		} finally {
			os.finish();
		}
	}
}
