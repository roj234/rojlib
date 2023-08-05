package roj.mod.mapping;

import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.mapper.Mapping;
import roj.mapper.util.Desc;
import roj.text.LineReader;
import roj.text.LinedReader;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj233
 * @since 2022/3/3 12:39
 */
public final class YarnMapping extends Mapping {
	public Mapping load(File intermediary, File mapping, String version) throws IOException {
		SimpleList<String> tmp = new SimpleList<>();
		readIntermediaryMap(intermediary.getName(), new LineReader(new FileInputStream(intermediary)), tmp);

		YarnMapping map1 = new YarnMapping();
		map1.readYarnMap(mapping, tmp, version, null);

		extend(map1);
		return map1;
	}

	public void readYarnMap(File file, List<String> tmp, String version, Map<Desc, List<String>> paramMap) throws IOException {
		UTFCoder uc = IOUtil.SharedCoder.get();

		boolean any = false;
		try (ZipFile zf = new ZipFile(file)) {
			Enumeration<? extends ZipEntry> e = zf.entries();
			String fp = version == null ? "yarn-" : "yarn-"+version+"/mappings/";

			while (e.hasMoreElements()) {
				ZipEntry ze = e.nextElement();
				String name = ze.getName();
				if (name.startsWith(fp) && name.endsWith(".mapping")) {
					any = true;
					uc.decodeFrom(zf.getInputStream(ze));
					readYarnEntry(name, new LineReader(uc.charBuf, false), tmp, paramMap);
				}
			}
		}
		if (!any) throw new RuntimeException("Not found any yarn for mc" + version);
	}

	public void readYarnEntry(String name, LineReader slr, List<String> tmp, Map<Desc, List<String>> paramMap) {
		int i = 1;

		String srcCls = null;
		SimpleList<String> srcLevel = new SimpleList<>();
		SimpleList<String> dstLevel = new SimpleList<>();
		Desc method = null;
		int prevCL = 0;

		for (String line : slr) {
			int level = 0;
			for (; level < line.length(); level++) {
				if (line.charAt(level) != '\t') {
					break;
				}
			}
			if (level == line.length()) continue;

			tmp.clear();
			TextUtil.split(tmp, line.substring(level), ' ', 5);
			switch (tmp.get(0)) {
				case "CLASS":
					int j = tmp.size() <= 2 ? 1 : 2;

					srcLevel.removeRange(prevCL + 1, srcLevel.size());
					dstLevel.removeRange(prevCL + 1, dstLevel.size());

					String dstCls;
					if (dstLevel.isEmpty()) {
						srcLevel.add(srcCls = tmp.get(1));
						dstLevel.add(dstCls = tmp.get(j));
					} else {
						srcLevel.add(srcCls = IOUtil.getSharedCharBuf().append(srcLevel.get(srcLevel.size() - 1)).append('$').append(tmp.get(1)).toString());
						dstLevel.add(dstCls = IOUtil.getSharedCharBuf().append(dstLevel.get(dstLevel.size() - 1)).append('$').append(tmp.get(j)).toString());
					}

					if (tmp.size() > 2) classMap.put(srcCls, dstCls);
					prevCL = level;

					method = null;
					break;
				case "METHOD":
					if (tmp.size() > 3) {
						methodMap.put(method = new Desc(srcCls, tmp.get(1), tmp.get(3)), tmp.get(2));
					} else {
						method = new Desc(srcCls, tmp.get(1), tmp.get(2));
					}
					break;
				case "ARG":
					if (method == null) throw new IllegalArgumentException("ARG出现在METHOD前");
					// ARG index name
					if (paramMap != null) {
						List<String> list = paramMap.computeIfAbsent(method, Helpers.fnArrayList());

						int argNo = Integer.parseInt(tmp.get(1));
						while (list.size() <= argNo) list.add(null);
						list.set(argNo, tmp.get(2));
					}
					break;
				case "FIELD":
					// FIELD intermediary_name new_name type
					if (tmp.size() > 3) {
						fieldMap.put(new Desc(srcCls, tmp.get(1), checkFieldType?tmp.get(3):""), tmp.get(2));
					}
					break;
				case "COMMENT": break;
				default: CLIUtil.error(name + ":" + i + ": 未知标记类型. " + tmp);
			}
			i++;
		}
	}

	public void readIntermediaryMap(String name, LinedReader slr, List<String> tmp) throws IOException {
		int i = 2;

		slr.readLine();
		while (true) {
			String line = slr.readLine();
			if (line == null) break;

			line = line.trim();
			if (line.length() == 0 || line.startsWith("#")) continue;

			tmp.clear();
			TextUtil.split(tmp, line, '\t', 5);
			switch (tmp.get(0)) {
				case "CLASS":
					classMap.put(tmp.get(1), tmp.get(2));
					break;
				case "METHOD":
					// METHOD owner original_name param intermediary_name
					methodMap.put(new Desc(tmp.get(1), tmp.get(3), tmp.get(2)), tmp.get(4));
					break;
				case "FIELD":
					// FIELD owner original_name type intermediary_name
					fieldMap.put(new Desc(tmp.get(1), tmp.get(3), checkFieldType?tmp.get(2):""), tmp.get(4));
					break;
				default:
					CLIUtil.error(name + ":" + i + ": 未知标记类型. " + tmp);
					break;
			}
			i++;
		}
	}
}
