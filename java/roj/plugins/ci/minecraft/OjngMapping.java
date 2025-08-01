package roj.plugins.ci.minecraft;

import roj.asm.ClassUtil;
import roj.asm.MemberDescriptor;
import roj.asm.type.TypeHelper;
import roj.asmx.mapper.Mapping;
import roj.collect.FilterList;
import roj.collect.ArrayList;
import roj.io.FastFailException;
import roj.text.LineReader;
import roj.text.TextReader;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/3/3 12:39
 */
final class OjngMapping extends Mapping {
	public void load(File client, File server) throws IOException {
		ArrayList<String> tmp = new ArrayList<>(5);

		if (client != null) {
			try (TextReader tr = TextReader.auto(client)) {
				readMojangMap(client.getName(), tr, tmp);
			}
			tmp.clear();
		}
		if (server != null) {
			try (TextReader tr = TextReader.auto(server)) {
				readMojangMap(server.getName(), tr, tmp);
			}
		}
	}

	public void readMojangMap(String map, LineReader slr, List<String> tmp) throws IOException {
		ClassUtil U = ClassUtil.getInstance();

		String[] currentClass = null;

		boolean[] found = new boolean[1];
		FilterList<String> list = new FilterList<>((s, t1) -> {
			if (s != null) found[0] = true;
			return true;
		});

		int i = 1;
		while (true) {
			String line = slr.readLine();
			if (line == null) break;
			if (line.length() == 0 || line.startsWith("#")) continue;
			char c = line.charAt(0);
			if (c == ' ') {
				if (currentClass == null) throw new FastFailException(map + ':' + i + ": 无效的元素开始.");

				found[0] = false;
				list.found = null;
				FilterList<String> arr1 = (FilterList<String>) TextUtil.split(list, line.trim(), ':');
				String val = arr1.found;

				if (!found[0]) {
					if (TextUtil.gLastIndexOf(val, ')') != -1) {
						// SBMJ, no line number
						found[0] = true;
					} else {
						// field type: arr[0]
						tmp.clear();
						final String s = TextUtil.split(tmp, val, ' ', 2).get(1);
						int index = s.indexOf(" -> ");

						fieldMap.putIfAbsent(new MemberDescriptor(currentClass[1], s.substring(index + 4)), s.substring(0, index));
						continue;
					}
				}

				tmp.clear();
				List<String> arr = TextUtil.split(tmp, val, ' ', 2);
				final String s = arr.get(1);
				int index = s.indexOf(" -> ");

				String arr20 = s.substring(0, index);

				//void <init>(int) -> <init>

				int j = arr20.indexOf('(');

				String param = TypeHelper.dehumanize(arr20.substring(j + 1, arr20.length() - 1), arr.get(0));
				// ' -> '.length
				String dstName = s.substring(index + 4);

				String srcName = arr20.substring(0, j);
				if (!srcName.equals(dstName)) {
					methodMap.putIfAbsent(new MemberDescriptor(currentClass[1], dstName, param), srcName);
				}
			} else {
				int index = line.indexOf(" -> ");
				line = line.replace('.', '/');
				currentClass = new String[] {
					line.substring(0, index), line.substring(index + 4, line.length() - 1)
				};
				classMap.putIfAbsent(currentClass[1], currentClass[0]);
			}
			i++;
		}

		// might have ConcurrentModificationException
		for (Iterator<Map.Entry<MemberDescriptor, String>> itr = methodMap.entrySet().iterator(); itr.hasNext(); ) {
			Map.Entry<MemberDescriptor, String> entry = itr.next();
			MemberDescriptor k = entry.getKey();
			String param = U.mapMethodParam(classMap.flip(), k.rawDesc);
			if (param != k.rawDesc) {
				itr.remove();

				k.rawDesc = param;
				String v = entry.getValue();
				methodMap.put(k, v);
			}
		}
	}
}