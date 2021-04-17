package roj.config;

import roj.config.data.CCommMap;
import roj.config.data.CMapping;
import roj.io.IOUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * 配置文件
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class FileConfig extends ConfigMaster {
	final File file;
	CMapping map;

	public FileConfig(File file) {
		this(file, true);
	}
	public FileConfig(File file, boolean loadNow) {
		super(IOUtil.extensionName(file.getName()));
		this.file = file;

		if (loadNow) load();
	}

	public final void load() {
		if (!file.isFile()) {
			init();
		} else {
			reload();
		}
	}
	public final void reload() {
		try {
			p.charset = null;
			load(map = p.parseRaw(file, flag).asMap().withComments());
		} catch (IOException | ParseException | ClassCastException e) {
			e.printStackTrace();
			if (map == null) {
				file.renameTo(new File(file.getPath() + ".broken." + System.currentTimeMillis()));
				System.err.println("配置文件 " + file + " 读取失败! 重新生成配置!");
				init();
			}
		}
	}
	public final void save() {
		save(getConfig());
		try (OutputStream fos = new FileOutputStream(file)) {
			p.serialize(map, 1, fos);
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
		return file;
	}
}
