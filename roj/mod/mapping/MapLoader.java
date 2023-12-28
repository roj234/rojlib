package roj.mod.mapping;

import roj.asm.type.Desc;
import roj.asmx.mapper.Mapper;
import roj.asmx.mapper.Mapping;
import roj.collect.MyHashMap;
import roj.config.data.CMapping;
import roj.text.LineReader;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/1/7 0007 22:12
 */
public final class MapLoader {
	final String name, type;
	final MapSource source;
	boolean paramMap;

	MapLoader(String name, String type, MapSource source, boolean map) {
		this.name = name;
		this.type = type;
		this.source = source;
		this.paramMap = map;
	}

	Mapping load(Map<String, Object> cfg, File tmp) throws Exception {
		File file = new File(tmp, cfg.get("version") + "_" + name + "." + cfg.get("id"));
		if (!file.isFile() && source != null) file = source.download(cfg, file);
		List<String> tmp1 = Helpers.cast(cfg.get("tmp"));
		switch (type) {
			case "TMP": return new Mapping();
			case "VAR": return (Mapping) cfg.get("map." + name);
			case "MCP":
				Map<Desc, List<String>> map;
				if (paramMap) {
					map = new MyHashMap<>();
					cfg.put("param", map);
				} else {
					map = null;
				}
				return new MCPMapping(file, map);
			case "OJNG":
				OjngMapping m = new OjngMapping();
				m.readMojangMap(file.getName(), new LineReader(new FileInputStream(file)), tmp1);
				return m;
			case "TSRG":
				TSrgMapping m1 = new TSrgMapping();
				if (paramMap) {
					map = new MyHashMap<>();
					cfg.put("param", map);
				} else {
					map = null;
				}
				m1.readMcpConfig(file, map, tmp1);
				return m1;
			case "INTR":
				YarnMapping m2 = new YarnMapping();
				m2.readIntermediaryMap(file.getName(), new LineReader(new FileInputStream(file)), tmp1);
				return m2;
			case "YARN":
				YarnMapping m3 = new YarnMapping();
				if (paramMap) {
					map = new MyHashMap<>();
					cfg.put("param", map);
				} else {
					map = null;
				}
				m3.readYarnMap(file, tmp1, cfg.get("version").toString(), map);
				return m3;
			case "SRG":
				Mapping m4 = new Mapping();
				m4.loadMap(new LineReader(new FileInputStream(file)), false);
				return m4;
			case "BIN":
				Mapper m5 = new Mapper();
				m5.readCache(0, file);
				return m5;
			default:
				throw new IllegalStateException("Unexpected value: " + type);
		}
	}

	static MapLoader parse(String name, CMapping cfg) {
		return new MapLoader(name, cfg.getString("format"), MapSource.parse(cfg.getOrCreateMap("src")), cfg.getBool("param_map"));
	}

	@Override
	public String toString() {
		return type + "Mapping{" + name + (paramMap ? ", param" : "") + '}';
	}
}