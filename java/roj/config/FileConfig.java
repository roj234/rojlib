package roj.config;

import roj.config.data.CMap;

import java.io.File;
import java.io.IOException;

/**
 * 配置文件
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public abstract class FileConfig {
	ConfigMaster type;
	final File file;
	CMap map;

	public FileConfig(File file) { this(file, true); }
	public FileConfig(File file, boolean loadNow) {
		this.type = ConfigMaster.fromExtension(file);
		this.file = file;
		if (loadNow) load();
	}

	public final void load() {
		if (!file.isFile()) init();
		else reload();
	}
	public final void reload() {
		try {
			load(map = type.parse(file).asMap().toCommentable());
		} catch (IOException | ParseException | ClassCastException e) {
			e.printStackTrace();
			if (map == null) {
				file.renameTo(new File(file.getPath()+".broken."+System.currentTimeMillis()));
				System.err.println("配置文件 " + file + " 读取失败! 重新生成配置!");
				init();
			}
		}
	}
	public final void save() {
		save(getConfig());
		try {
			type.toFile(map, file);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected void init() {
		load(getConfig());
		save();
	}
	protected abstract void load(CMap map);
	protected void save(CMap map) { load(map); }

	public final CMap getConfig() {
		if (map == null) map = new CMap.Commentable();
		return map;
	}

	public final File getFile() { return file; }
}