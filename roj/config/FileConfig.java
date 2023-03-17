package roj.config;

/**
 * JSON格式配置文件包装
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */

import roj.config.data.CCommMap;
import roj.config.data.CMapping;
import roj.config.word.StreamAsChars;
import roj.io.BOMInputStream;
import roj.io.FileUtil;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Locale;

import static roj.config.JSONParser.*;

public abstract class FileConfig {
	final File config;
	CMapping map;

	private final Parser p;
	private final int flag;

	public FileConfig(File config) {
		this(config, true);
	}
	public FileConfig(File config, boolean loadNow) {
		this.config = config;
		switch (FileUtil.extensionName(config.getName()).toLowerCase(Locale.ROOT)) {
			case "yml":
			case "yaml":
				p = new YAMLParser();
				flag = NO_DUPLICATE_KEY|LITERAL_KEY|COMMENT;
				break;
			case "xml":
				p = new XMLParser();
				flag = UNESCAPED_SINGLE_QUOTE;
				break;
			case "json":
			case "json5":
				p = new JSONParser();
				flag = NO_DUPLICATE_KEY|LITERAL_KEY|COMMENT|UNESCAPED_SINGLE_QUOTE;
				break;
			case "toml":
				p = new TOMLParser();
				flag = 0;
				break;
			case "ini":
				p = new IniParser();
				flag = NO_DUPLICATE_KEY;
				break;
			default: throw new IllegalArgumentException("代码错误/未知的配置文件扩展名:"+config.getName());
		}

		if (loadNow) load();
	}

	public final void load() {
		if (!config.isFile()) {
			init();
		} else {
			reload();
		}
	}
	public final void reload() {
		try (BOMInputStream fis = new BOMInputStream(new FileInputStream(config), "UTF-8")) {
			StreamAsChars str = new StreamAsChars(fis, Charset.forName(fis.getEncoding()));
			load(map = p.parse(str, flag).asMap().withComments());
		} catch (IOException | ParseException | ClassCastException e) {
			e.printStackTrace();
			if (map == null) {
				config.renameTo(new File(config.getPath() + ".broken." + System.currentTimeMillis()));
				System.err.println("配置文件 " + config + " 读取失败! 重新生成配置!");
				init();
			}
		}
	}
	public final void save() {
		save(getConfig());
		try {
			IOUtil.write(p.toString(map), config);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void init() {
		load(getConfig());
		save();
	}
	protected abstract void load(CMapping map);
	protected void save(CMapping map) {
		load(map);
	}

	public final CMapping getConfig() {
		if (map == null) map = new CCommMap();
		return map;
	}

	public final File getFile() {
		return config;
	}
}
