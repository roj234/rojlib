package roj.mod.mapping;

import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.mapper.MapUtil;
import roj.mapper.Mapping;
import roj.mapper.util.Desc;
import roj.text.LineReader;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * @author Roj234
 * @since 2020/8/29 22:25
 */
public final class MCPMapping extends Mapping {
	private final File file;
	private final Map<Desc, List<String>> paramMap;
	public static Boolean printAll;

	public MCPMapping(File file, Map<Desc, List<String>> paramMap) {
		this.file = file;
		this.paramMap = paramMap;
	}

	public void apply(List<String> tmp, Mapping srg, Mapping save) throws IOException {
		classMap = srg.getClassMap();
		checkFieldType = srg.checkFieldType;

		save.getClassMap().putAll(classMap);

		Map<String, List<Desc>> fields = new MyHashMap<>(srg.getFieldMap().size());
		for (Map.Entry<Desc, String> entry : srg.getFieldMap().entrySet()) {
			if (entry.getValue().startsWith("field_"))
				fields.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(entry.getKey());
		}

		int len = srg.getMethodMap().size();
		Map<String, List<Desc>> methods = new MyHashMap<>(srg.getMethodMap().size());
		for (Map.Entry<Desc, String> entry : srg.getMethodMap().entrySet()) {
			if (entry.getValue().startsWith("func_"))
				methods.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(entry.getKey());
			len--;
		}

		try (ZipFile zf = new ZipFile(file)) {
			parseMoF(zf, "fields.csv", tmp, fields, save.getFieldMap());
			parseMoF(zf, "methods.csv", tmp, methods, save.getMethodMap());
			if (paramMap != null) {
				MyHashMap<String, String> tmpParams = new MyHashMap<>();
				parseParam(IOUtil.readUTF(zf.getInputStream(zf.getEntry("params.csv"))), tmp, tmpParams);
				formatParamMap(tmpParams, paramMap, tmp, methods, save);
			}
		}
	}

	private static void parseMoF(ZipFile zf, String name, List<String> tmp, Map<String, List<Desc>> map, Map<Desc, String> target) throws IOException {
		try (LineReader slr = new LineReader(zf.getInputStream(zf.getEntry(name)), StandardCharsets.UTF_8, true)) {
			slr.skipLines(1);
			while (slr.hasNext()) {
				String line = slr.next();
				if (line.startsWith("#")) continue;

				tmp.clear();
				if (TextUtil.split(tmp, line, ',').size() < 2) {
					CLIUtil.warning(name+":"+slr.lineNumber()+": 未知标记: " + line);
					continue;
				}

				List<Desc> desc = map.get(tmp.get(0));
				if (desc == null) {
					if (printAll != Boolean.FALSE) CLIUtil.warning(name+":"+slr.lineNumber() + ": 不存在的Srg: " + tmp.get(0));
					if (printAll == null) printAll = false;
				} else {
					for (Desc d : desc) {
						target.put(d, tmp.get(1));
					}
				}
			}
		}
	}

	private static void parseParam(String csv, List<String> tmp, Map<String, String> paramMap) {
		LineReader slr = new LineReader(csv);

		slr.skipLines(1);
		int i = 2;
		while (slr.hasNext()) {
			String line = slr.next();
			if (line.length() == 0 || line.startsWith("#")) continue;

			tmp.clear();
			if (TextUtil.split(tmp, line, ',').size() < 2) {
				throw new IllegalArgumentException("params.csv:" + i + ": 未知标记: " + line);
			}
			paramMap.put(tmp.get(0), tmp.get(1));
			i++;
		}
	}

	private void formatParamMap(Map<String, String> mcpParams, Map<Desc, List<String>> params, List<String> tmp, Map<String, List<Desc>> methods, Mapping srgName) {
		MapUtil U = MapUtil.getInstance();

		Map<String, Set<Desc>> methodData = new MyHashMap<>(1000);

		// func_10001_i
		for (Map.Entry<String, List<Desc>> entry : methods.entrySet()) {
			String key = entry.getKey();

			tmp.clear();
			TextUtil.split(tmp, key, '_');

			if (tmp.size() < 3) {
				CLIUtil.warning("格式非法 " + key);
				continue;
			}

			for (Desc desc : entry.getValue()) {
				String name = srgName.getMethodMap().get(desc);
				if (name == null) continue;

				Set<Desc> list = methodData.computeIfAbsent(tmp.get(1), Helpers.fnMyHashSet());

				String param = U.mapMethodParam(classMap, desc.param);
				list.add(new Desc(classMap.get(desc.owner), name, param));
			}
		}

		// p_10001_1
		for (Map.Entry<String, String> entry : mcpParams.entrySet()) {
			tmp.clear();
			TextUtil.split(tmp, entry.getKey(), '_');

			Set<Desc> data = methodData.get(tmp.get(1));
			if (data == null) {
				if (!tmp.get(1).startsWith("i")) {
					if (printAll != Boolean.FALSE) CLIUtil.warning("参数不存在: " + tmp.get(1));
					if (printAll == null) printAll = false;
				}
				continue;
			}

			int slotId = TextUtil.parseInt(tmp.get(2));

			for (Desc desc : data) {
				List<String> list = params.computeIfAbsent(desc, Helpers.fnArrayList());
				while (list.size() <= slotId) list.add(null);
				list.set(slotId, entry.getValue());
			}
		}
	}
}
