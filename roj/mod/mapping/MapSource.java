package roj.mod.mapping;

import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.io.down.DownloadTask;
import roj.io.down.ProgressMulti;
import roj.mod.MCLauncher;
import roj.text.CharList;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/7 0007 22:12
 */
public interface MapSource {
	File download(Map<String, Object> cfg, File tmp) throws Exception;

	static MapSource parse(CMapping cfg) {
		switch (cfg.getString("type")) {
			case "NONE":
				return null;
			case "MINECRAFT":
				String side = cfg.getString("side").equalsIgnoreCase("SERVER") ? "downloads.server_mappings" : "downloads.client_mappings";
				return (cfg1, tmp) -> {
					CMapping entry = ((CMapping) cfg1.get("mc_json")).getDot(side).asMap();
					if (entry.size() == 0) throw new IllegalStateException("数据不存在于JSON中，尝试重新下载客户端");
					MCLauncher.downloadMinecraftFile(entry, tmp, cfg1.getOrDefault("mirror.MINECRAFT", "").toString());
					return tmp;
				};
			case "FILE":
				File file = new File(cfg.getString("file"));
				if (!file.isFile()) CmdUtil.warning("file类型要求的文件不存在. " + cfg.getString("file"));
				return (cfg1, tmp) -> file;
			case "URL":
				String url = cfg.getString("url");
				Map<String, CEntry> header;
				if (cfg.containsKey("header")) {
					header = Helpers.cast(cfg.get("header").unwrap());
				} else {
					header = Collections.emptyMap();
				}
				String mirrorK, mirrorV;
				if (cfg.containsKey("mirror")) {
					String a = cfg.getString("mirror");
					int pos = a.indexOf('|');

					mirrorV = a.substring(pos + 1);
					mirrorK = a.substring(0, pos);
				} else {
					mirrorK = mirrorV = null;
				}
				Map<String, String> vars;
				if (cfg.containsKey("var")) {
					vars = Helpers.cast(cfg.getOrCreateMap("var").raw());
					for (Map.Entry<String, CEntry> entry : cfg.getOrCreateMap("var").entrySet()) {
						entry.setValue(Helpers.cast(entry.getValue().asString()));
					}
				} else {
					vars = Collections.emptyMap();
				}
				if (cfg.containsKey("repo")) {
					url += IOUtil.mavenPath(cfg.getString("repo"));
				}

				String finalUrl = url;
				return (cfg1, tmp) -> {
					CharList cl = new CharList();
					if (mirrorK != null) {
						Object entry = cfg1.get("mirror." + mirrorK);
						cl.append(entry != null ? entry.toString() : mirrorV);
					}
					cl.append(finalUrl);
					for (Map.Entry<String, String> entry : vars.entrySet()) {
						cl.replace("[" + entry.getKey() + "]", entry.getValue().equals("!") ? cfg1.get("var." + entry.getKey()).toString() : entry.getValue());
					}
					cl.replace("[MCVERSION]", cfg1.get("version").toString());

					System.out.println("下载 " + cl);
					for (int i = 0; i < 3; i++) {
						try {
							DownloadTask task = DownloadTask.createTask(cl.toString(), tmp, new ProgressMulti());
							task.ETag = false;
							task.client.headers(Helpers.cast(header));
							DownloadTask.QUERY.pushTask(task);
							task.waitFor();
						} catch (Exception e) {
							CmdUtil.warning("下载失败,重试", e);
							continue;
						}
						break;
					}
					return tmp;
				};
			default:
				throw new IllegalStateException("Unexpected value: " + cfg.getString("type"));
		}
	}
}
