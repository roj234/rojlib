package roj.plugins.ci.minecraft;

import roj.asm.MemberDescriptor;
import roj.asmx.mapper.Mapping;
import roj.collect.ArrayList;
import roj.collect.IntList;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.ui.Terminal;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj233
 * @since 2022/3/3 12:39
 */
final class YarnMapping extends Mapping {
	public Mapping load(File intermediary, File mapping, String version) throws IOException {
		ArrayList<String> tmp = new ArrayList<>();
		readIntermediaryMap(intermediary.getName(), TextReader.auto(intermediary), tmp);

		YarnMapping map1 = new YarnMapping();
		map1.readYarnMap(mapping, tmp, version, null);

		extend(map1);
		return map1;
	}

	public void readYarnMap(File file, List<String> tmp, String version, Map<MemberDescriptor, List<String>> paramMap) throws IOException {
		boolean any = false;
		try (ZipFile zf = new ZipFile(file)) {
			Enumeration<? extends ZipEntry> e = zf.entries();
			String fp = version == null ? "yarn-" : "yarn-"+version+"/mappings/";

			while (e.hasMoreElements()) {
				ZipEntry ze = e.nextElement();
				String name = ze.getName();
				if (name.startsWith(fp) && name.endsWith(".mapping")) {
					any = true;
					try (TextReader r = new TextReader(zf.getInputStream(ze), StandardCharsets.UTF_8)) {
						readYarnEntry(name, r, tmp, paramMap);
					}
				}
			}
		}
		if (!any) throw new RuntimeException("Not found any yarn for mc" + version);
	}

	public void readYarnEntry(String name, TextReader slr, List<String> tmp, Map<MemberDescriptor, List<String>> paramMap) {
		IntList stack = new IntList();
		CharList srcClass = new CharList();
		CharList dstClass = new CharList();
		String srcCls = null;
		MemberDescriptor method = null;

		int ln = 0;
		for (String line : slr) {
			ln++;

			int level = 0;
			for (; level < line.length(); level++) {
				if (line.charAt(level) != '\t') {
					break;
				}
			}
			if (level == line.length()) continue;

			while (stack.size() > 0 && level <= stack.get(stack.size()-1)) {
				stack.remove(stack.size()-1);
				dstClass.setLength(stack.remove(stack.size()-1));
				srcClass.setLength(stack.remove(stack.size()-1));
			}

			tmp.clear();
			TextUtil.split(tmp, line.substring(level), ' ', 5);
			// CLASS -> FIELD
			// CLASS -> METHOD -> PARAM
			// CLASS -> CLASS
			switch (tmp.get(0)) {
				case "CLASS" -> {
					stack.add(srcClass.length());
					stack.add(dstClass.length());
					stack.add(level);
					int j = tmp.size() <= 2 ? 1 : 2;
					if (srcClass.length() > 0) {
						srcClass.append('$');
						dstClass.append('$');
					}
					srcCls = srcClass.append(tmp.get(1)).toString();
					classMap.put(srcCls, dstClass.append(tmp.get(j)).toString());
					method = null;
				}
				case "METHOD" -> {
					if (tmp.size() > 3) {
						methodMap.put(method = new MemberDescriptor(srcCls, tmp.get(1), tmp.get(3)), tmp.get(2));
					} else {
						method = new MemberDescriptor(srcCls, tmp.get(1), tmp.get(2));
					}
				}
				case "ARG" -> {
					if (method == null) throw new IllegalArgumentException("ARG出现在METHOD前");
					// ARG index name
					if (paramMap != null) {
						List<String> list = paramMap.computeIfAbsent(method, Helpers.fnArrayList());

						// simply ignore index to support new ParamNameMapper
						list.add(tmp.get(2));
						/*int argNo = Integer.parseInt(tmp.get(1));
						while (list.size() <= argNo) list.add(null);
						list.set(argNo, tmp.get(2));*/
					}
				}
				case "FIELD" -> {
					// FIELD intermediary_name new_name type
					if (tmp.size() > 3) {
						fieldMap.put(new MemberDescriptor(srcCls, tmp.get(1), checkFieldType ? tmp.get(3) : ""), tmp.get(2));
					}
				}
				case "COMMENT" -> {
				}
				default -> Terminal.error(name + ":" + ln + ": 未知标记类型. " + tmp);
			}
		}
	}

	public void readIntermediaryMap(String name, LineReader slr, List<String> tmp) throws IOException {
		int i = 2;

		slr.readLine();
		for (String line : slr) {
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
					methodMap.put(new MemberDescriptor(tmp.get(1), tmp.get(3), tmp.get(2)), tmp.get(4));
					break;
				case "FIELD":
					// FIELD owner original_name type intermediary_name
					fieldMap.put(new MemberDescriptor(tmp.get(1), tmp.get(3), checkFieldType?tmp.get(2):""), tmp.get(4));
					break;
				default:
					Terminal.error(name + ":" + i + ": 未知标记类型. " + tmp);
					break;
			}
			i++;
		}
	}
}