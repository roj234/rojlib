package roj.config;

import roj.config.data.CEntry;
import roj.config.serial.CAdapter;
import roj.io.IOUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

import static roj.config.JSONParser.*;

/**
 * @author Roj234
 * @since 2023/3/17 0017 23:23
 */
public class ConfigMaster {
	BinaryParser bp;
	Parser<?> p;
	int flag;

	public ConfigMaster(String type) {
		switch (type.toLowerCase(Locale.ROOT)) {
			case "yml":
			case "yaml":
				flag = NO_DUPLICATE_KEY|LITERAL_KEY|COMMENT;
				bp = p = new CCYaml(flag);
				break;
			case "xml":
				flag = 0;
				bp = p = new XMLParser();
				break;
			case "json":
			case "json5":
				flag = NO_DUPLICATE_KEY|LITERAL_KEY|COMMENT|UNESCAPED_SINGLE_QUOTE;
				bp = p = new CCJson(flag);
				break;
			case "toml":
				flag = 0;
				bp = p = new TOMLParser();
				break;
			case "ini":
				flag = NO_DUPLICATE_KEY;
				bp = p = new IniParser();
				break;
			case "nbt": bp = new NBTParser(); break;
			case "bjson": bp = new VinaryParser(); break;
			case "torrent": bp = new BencodeParser(); break;
			case "csv": bp = p = new CsvParser(); break;
			default: throw new IllegalArgumentException("不支持的配置文件类型:"+type);
		}
	}

	public Parser<?> parser() { return p; }
	public BinaryParser binParser() { return bp; }
	public int flag() { return flag; }

	public static <T> T adapt(CAdapter<T> ser, File file) throws IOException, ParseException { return adapt(ser,file,null); }
	public static <T> T adapt(CAdapter<T> ser, File file, Charset cs) throws IOException, ParseException {
		ConfigMaster c = create(file);
		ser.reset();

		if (c.p != null) c.p.charset = cs;
		c.bp.parseRaw(ser, file, 0);

		if (!ser.finished()) throw new IllegalStateException("数据结构有误");
		return ser.result();
	}

	public static CEntry parse(File file) throws IOException, ParseException { return parse(file, null); }
	public static CEntry parse(File file, Charset cs) throws IOException, ParseException {
		ConfigMaster c = create(file);

		if (c.p != null) c.p.charset = cs;
		return c.bp.parseRaw(file, 0);
	}

	public static ConfigMaster create(File file) throws IOException, ParseException {
		return new ConfigMaster(IOUtil.extensionName(file.getName()));
	}
}
