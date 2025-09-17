package roj.config;

import roj.collect.HashMap;
import roj.collect.LinkedHashMap;
import roj.config.node.ConfigValue;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.ParseException;
import roj.text.TextReader;
import roj.text.Tokenizer;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/5/17 2:25
 */
public abstract class Parser extends Tokenizer implements BinaryParser {
	public static final boolean ALWAYS_ESCAPE = Boolean.getBoolean("roj.config.noRawCheck");

	/**
	 * 解析注释<b>初始化Flag</b> <br>
	 * 适用于: JSON YAML CCJSON CCYAML TOML
	 */
	public static final int COMMENT = 1;

	/**
	 * 弱化部分解析限制 <br>
	 * 适用于: YAML CCYAML TOML XML
	 */
	public static final int LENIENT = 1;
	/**
	 * 检测并拒绝CMap中重复的key <br>
	 * 适用于: JSON YAML INI
	 */
	public static final int NO_DUPLICATE_KEY = 2;
	/**
	 * 使用{@link LinkedHashMap}充当CMap中的map来保留配置文件key的顺序 <br>
	 * 适用于: JSON YAML TOML INI
	 */
	public static final int ORDERED_MAP = 4;

	protected Parser() {}
	protected Parser(int _flag) {if ((_flag & COMMENT) != 0) comment = new CharList();}

	public final ConfigValue parse(CharSequence text) throws ParseException { return parse(text, 0); }
	public ConfigValue parse(CharSequence text, int flags) throws ParseException {
		this.flag = flags;
		init(text);
		try {
			return element(flags);
		} catch (ParseException e) {
			throw e.addPath("$");
		} finally {
			init(null);
		}
	}
	public void parse(CharSequence text, int flags, ValueEmitter emitter) throws ParseException {
		parse(text, flags).accept(emitter);
	}

	protected ConfigValue element(int flags) throws ParseException { throw new UnsupportedOperationException(); }

	// auto detect
	public Charset charset = null;
	public final Parser charset(Charset cs) { charset = cs; return this; }

	public final ConfigValue parse(File file, int flags) throws IOException, ParseException {
		try (TextReader in = new TextReader(file, charset)) {
			return parse(in, flags);
		} catch (ParseException e) {
			throw wrapWithFullText(file, e);
		}
	}
	public final void parse(File file, int flags, ValueEmitter emitter) throws IOException, ParseException {
		try (var text = new TextReader(file, charset)) {
			parse(text, flags, emitter);
		} catch (ParseException e) {
			throw wrapWithFullText(file, e);
		}
	}
	public final ConfigValue parse(DynByteBuf buf, int flags) throws IOException, ParseException {
		try (var text = new TextReader(buf, charset)) {
			return parse(text, flags);
		}
	}
	public final ConfigValue parse(InputStream in, int flags) throws IOException, ParseException {
		try (var text = new TextReader(in, charset)) {
			return parse(text, flags);
		}
	}
	public final void parse(InputStream in, int flag, ValueEmitter emitter) throws IOException, ParseException {
		try (var text = new TextReader(in, charset)) {
			parse(text, flag, emitter);
		}
	}

	private static ParseException wrapWithFullText(File file, ParseException e) throws ParseException, IOException {
		if (file.length() > 1048576) throw e;

		var ex = new ParseException(IOUtil.readString(file), e.getMessage(), e.getIndex(), e.getCause());
		ex.addPath(e.getPath());
		ex.setStackTrace(e.getStackTrace());
		return ex;
	}

	protected int flag;
	public final Map<String, String> getComment(Map<String, String> map, String v) {
		if (comment == null || comment.length() == 0) return map;
		if (map == null) map = new HashMap<>();
		map.put(v, comment.toString());
		comment.clear();
		return map;
	}
	public final void clearComment() {
		if (comment == null) return;
		comment.clear();
	}
}