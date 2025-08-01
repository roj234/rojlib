package roj.plugins.ci.minecraft;

import roj.asm.ClassUtil;
import roj.asm.MemberDescriptor;
import roj.asmx.mapper.Mapping;
import roj.collect.HashMap;
import roj.io.IOUtil;
import roj.text.LineReader;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.Terminal;
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
final class MCPMapping extends Mapping {
	private final File file;
	private final Map<MemberDescriptor, List<String>> paramMap;
	public static Boolean printAll;

	public MCPMapping(File file, Map<MemberDescriptor, List<String>> paramMap) {
		this.file = file;
		this.paramMap = paramMap;
	}

	public void apply(List<String> tmp, Mapping srg, Mapping save) throws IOException {
		classMap = srg.getClassMap();
		checkFieldType = srg.checkFieldType;

		save.getClassMap().putAll(classMap);

		Map<String, List<MemberDescriptor>> fields = new HashMap<>(srg.getFieldMap().size());
		for (Map.Entry<MemberDescriptor, String> entry : srg.getFieldMap().entrySet()) {
			if (entry.getValue().startsWith("field_"))
				fields.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(entry.getKey());
		}

		Map<String, List<MemberDescriptor>> methods = new HashMap<>(srg.getMethodMap().size());
		for (Map.Entry<MemberDescriptor, String> entry : srg.getMethodMap().entrySet()) {
			if (entry.getValue().startsWith("func_"))
				methods.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(entry.getKey());
		}

		try (ZipFile zf = new ZipFile(file)) {
			parseMoF(zf, "fields.csv", tmp, fields, save.getFieldMap());
			parseMoF(zf, "methods.csv", tmp, methods, save.getMethodMap());
			if (paramMap != null) {
				HashMap<String, String> tmpParams = new HashMap<>();
				parseParam(IOUtil.readUTF(zf.getInputStream(zf.getEntry("params.csv"))), tmp, tmpParams);
				formatParamMap(tmpParams, paramMap, tmp, methods, save);
			}
		}
	}

	private static void parseMoF(ZipFile zf, String name, List<String> tmp, Map<String, List<MemberDescriptor>> map, Map<MemberDescriptor, String> target) throws IOException {
		try (TextReader slr = TextReader.from(zf.getInputStream(zf.getEntry(name)), StandardCharsets.UTF_8)) {
			int ln = 1;
			slr.skipLines(1);

			for (String line : slr) {
				ln++;
				if (line.isEmpty() || line.startsWith("#")) continue;

				tmp.clear();
				if (TextUtil.split(tmp, line, ',').size() < 2) {
					Terminal.warning(name+":"+ln+": 未知标记: " + line);
					continue;
				}

				List<MemberDescriptor> desc = map.get(tmp.get(0));
				if (desc == null) {
					if (printAll != Boolean.FALSE) Terminal.warning(name+":"+ln+": 不存在的Srg: "+tmp.get(0));
					if (printAll == null) printAll = false;
				} else {
					for (MemberDescriptor d : desc) {
						target.put(d, tmp.get(1));
					}
				}
			}
		}
	}

	private static void parseParam(String csv, List<String> tmp, Map<String, String> paramMap) {
		LineReader.Impl slr = LineReader.create(csv);

		slr.skipLines(1);
		int i = 2;
		while (slr.hasNext()) {
			String line = slr.next();
			if (line.isEmpty() || line.startsWith("#")) continue;

			tmp.clear();
			if (TextUtil.split(tmp, line, ',').size() < 2) {
				throw new IllegalArgumentException("params.csv:" + i + ": 未知标记: " + line);
			}
			paramMap.put(tmp.get(0), tmp.get(1));
			i++;
		}
	}

	private void formatParamMap(Map<String, String> mcpParams, Map<MemberDescriptor, List<String>> params, List<String> tmp, Map<String, List<MemberDescriptor>> methods, Mapping srgName) {
		ClassUtil U = ClassUtil.getInstance();

		Map<String, Set<MemberDescriptor>> methodData = new HashMap<>(1000);

		// func_10001_i
		for (Map.Entry<String, List<MemberDescriptor>> entry : methods.entrySet()) {
			String key = entry.getKey();

			tmp.clear();
			TextUtil.split(tmp, key, '_');

			if (tmp.size() < 3) {
				Terminal.warning("格式非法 " + key);
				continue;
			}

			for (MemberDescriptor desc : entry.getValue()) {
				String name = srgName.getMethodMap().get(desc);
				if (name == null) continue;

				Set<MemberDescriptor> list = methodData.computeIfAbsent(tmp.get(1), Helpers.fnHashSet());

				String param = U.mapMethodParam(classMap, desc.rawDesc);
				list.add(new MemberDescriptor(classMap.get(desc.owner), name, param));
			}
		}

		// p_10001_1
		for (Map.Entry<String, String> entry : mcpParams.entrySet()) {
			tmp.clear();
			TextUtil.split(tmp, entry.getKey(), '_');

			Set<MemberDescriptor> data = methodData.get(tmp.get(1));
			if (data == null) {
				if (!tmp.get(1).startsWith("i")) {
					if (printAll != Boolean.FALSE) Terminal.warning("参数不存在: " + tmp.get(1));
					if (printAll == null) printAll = false;
				}
				continue;
			}

			int slotId = TextUtil.parseInt(tmp.get(2));

			for (MemberDescriptor desc : data) {
				List<String> list = params.computeIfAbsent(desc, Helpers.fnArrayList());
				while (list.size() <= slotId) list.add(null);
				list.set(slotId, entry.getValue());
			}
		}
	}
}