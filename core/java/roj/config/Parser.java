package roj.config;

import roj.collect.HashMap;
import roj.config.data.CEntry;
import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

import static roj.config.Flags.COMMENT;

/**
 * 删除INTERN功能，如果碰到很多的重复字符串，您可能需要Serializer而不是解析到Entry
 * @author Roj233
 * @since 2022/5/17 2:25
 */
public abstract class Parser extends Tokenizer implements BinaryParser {
	public static final boolean ALWAYS_ESCAPE = Boolean.getBoolean("roj.config.noRawCheck");

	protected Parser() {}
	protected Parser(int _flag) {if ((_flag & COMMENT) != 0) comment = new CharList();}

	public final CEntry parse(CharSequence text) throws ParseException { return parse(text, 0); }
	public CEntry parse(CharSequence text, int flag) throws ParseException {
		this.flag = flag;
		init(text);
		try {
			return element(flag);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
	}
	CEntry element(int flag) throws ParseException { throw new UnsupportedOperationException(); }

	public <C extends CVisitor> C parse(CharSequence text, int flag, C cv) throws ParseException {
		parse(text,flag).accept(cv);
		return cv;
	}

	// auto detect
	public Charset charset = null;
	public final Parser charset(Charset cs) { charset = cs; return this; }

	public final CEntry parse(File file, int flag) throws IOException, ParseException {
		try (TextReader in = new TextReader(file, charset)) {
			return parse(in, flag);
		} catch (ParseException e) {
			if (file.length() > 1048576) throw e;

			ParseException ex = new ParseException(IOUtil.readString(file), e.getMessage(), e.getIndex(), e.getCause());
			ex.addPath(e.getPath());
			ex.setStackTrace(e.getStackTrace());
			throw ex;
		}
	}
	public final <C extends CVisitor> C parse(File file, int flag, C cv) throws IOException, ParseException {
		try (TextReader text = new TextReader(file, charset)) {
			return parse(text, flag, cv);
		} catch (ParseException e) {
			ParseException exc = new ParseException(IOUtil.readString(file), e.getMessage(), e.getIndex(), e.getCause());
			exc.setStackTrace(e.getStackTrace());
			throw exc;
		}
	}
	public final CEntry parse(DynByteBuf buf, int flag) throws IOException, ParseException {
		try (TextReader text = new TextReader(buf, charset)) {
			return parse(text, flag);
		}
	}
	public final CEntry parse(InputStream in, int flag) throws IOException, ParseException {
		try (TextReader text = new TextReader(in, charset)) {
			return parse(text, flag);
		}
	}
	public final <C extends CVisitor> C parse(InputStream in, int flag, C cv) throws IOException, ParseException {
		try (TextReader text = new TextReader(in, charset)) {
			return parse(text, flag, cv);
		}
	}

	protected int flag;
	protected final Map<String, String> addComment(Map<String, String> map, String v) {
		if (comment == null || comment.length() == 0) return map;
		if (map == null) map = new HashMap<>();
		map.put(v, comment.toString());
		comment.clear();
		return map;
	}
	protected final void clearComment() {
		if (comment == null) return;
		comment.clear();
	}
}