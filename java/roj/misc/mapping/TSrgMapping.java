package roj.misc.mapping;

import roj.asm.type.Desc;
import roj.asmx.mapper.Mapping;
import roj.config.JSONParser;
import roj.config.ParseException;
import roj.config.data.CMap;
import roj.io.IOUtil;
import roj.text.LinedReader;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.Terminal;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj233
 * @since 2022/3/3 12:39
 */
public final class TSrgMapping extends Mapping {
	public boolean readMcpConfig(File mapFile, Map<Desc, List<String>> paramMap, List<String> tmp) throws IOException {
		MCPConfig cfg = new MCPConfig(mapFile);
		InputStream in = cfg.getData("mappings");
		if (in == null) {
			Terminal.warning("不是有效的MCP Config");
			return false;
		}

		try (TextReader tr = TextReader.from(in, StandardCharsets.UTF_8)) {
			switch (cfg.version()) {
				case 1: tsrgV1(tr, tmp); break;
				case 2: case 3: tSrgV2(tr, tmp, paramMap); break;
				default: Terminal.warning("不支持的版本 " + cfg.version());
			}
		} finally {
			cfg.close();
		}

		return true;
	}

	public boolean tsrgV1(LinedReader slr, List<String> tmp) throws IOException {
		String prevF = null;

		int ln = 1;
		slr.skipLines(1);
		for (String line : slr) {
			ln++;

			int level = 0;
			for (; level < line.length(); level++) {
				if (line.charAt(level) != '\t') {
					break;
				}
			}
			if (level == line.length()) continue;

			// gj net/minecraft/nbt/NBTUtil
			//	a field_193591_a
			//	a (Lawt;Laxj;Ljava/lang/String;Lfy;Lfy;)Lawt; func_193590_a
			tmp.clear();
			TextUtil.split(tmp, line.substring(level), ' ', 3);
			if (level == 0) {
				prevF = tmp.get(0);
				if (!prevF.equals(tmp.get(1))) {
					classMap.put(prevF, tmp.get(1));
				}
			} else {
				if (prevF == null) {
					Terminal.error("#!config/joined.tsrg:"+ln+": 无效的元素开始.");
					return false;
				}

				if (tmp.size() == 2) {
					if (!tmp.get(0).equals(tmp.get(1)))
						fieldMap.put(new Desc(prevF, tmp.get(0)), tmp.get(1));
				} else {
					if (!tmp.get(0).equals(tmp.get(2)))
						methodMap.put(new Desc(prevF, tmp.get(0), tmp.get(1)), tmp.get(2));
				}
			}
		}
		return true;
	}

	public boolean tSrgV2(LinedReader slr, List<String> tmp, Map<Desc, List<String>> paramMap) throws IOException {
		String prevF = null;
		Desc method = null;

		int ln = 1;
		slr.skipLines(1);
		for (String line : slr) {
			ln++;

			int level = 0;
			for (; level < line.length(); level++) {
				if (line.charAt(level) != '\t') {
					break;
				}
			}
			if (level == line.length()) continue;

			tmp.clear();
			TextUtil.split(tmp, line.substring(level), ' ', 4);
			if (level == 0) {
				method = null;
				prevF = tmp.get(0);
				if (!prevF.equals(tmp.get(1))) {
					classMap.put(prevF, tmp.get(1));
				}
			} else if (level == 1) {
				if (prevF == null) {
					Terminal.error("#!config/joined.tsrg:"+ln+": 无效的元素开始.");
					return false;
				}

				if (tmp.size() == 2) {
					if (!tmp.get(0).equals(tmp.get(1)))
						fieldMap.put(new Desc(prevF, tmp.get(0)), tmp.get(1));
				} else {
					method = new Desc(prevF, tmp.get(0), tmp.get(1));
					if (!tmp.get(0).equals(tmp.get(2))) {
						methodMap.put(method, tmp.get(2));
					}
				}
			} else {
				if (method == null) {
					Terminal.error("#!config/joined.tsrg:"+ln+": 无效的元素开始.");
					return false;
				}
				if (tmp.get(0).equals("static")) {
					Terminal.warning("TSrgMapping:141: "+tmp);
					continue;
				}

				if (paramMap != null) {
					List<String> list = paramMap.computeIfAbsent(method, Helpers.fnArrayList());
					int index = Integer.parseInt(tmp.get(0));
					while (list.size() <= index) list.add(null);
					list.set(index, tmp.get(2));
				}
			}
		}
		return true;
	}

	public static final class MCPConfig implements Closeable {
		private ZipFile zf;
		private CMap manifest;
		public MCPConfig(File file) throws IOException {
			zf = new ZipFile(file);
			try {
				manifest = JSONParser.parses(IOUtil.readUTF(zf.getInputStream(zf.getEntry("config.json")))).asMap();
			} catch (ParseException e) {
				throw new IOException("reading manifest", e);
			}
			manifest.dot(true);
		}

		public int version() {
			return manifest.getInteger("spec");
		}

		public InputStream getData(String key) throws IOException {
			ZipEntry entry = zf.getEntry(manifest.getString("data." + key));
			return entry == null ? null : zf.getInputStream(entry);
		}

		public void close() throws IOException {
			zf.close();
		}
	}
}