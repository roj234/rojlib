package roj.text;

import roj.collect.IntList;
import roj.collect.SimpleList;
import roj.io.IOUtil;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/11/29 0029 21:34
 * @deprecated Write a better template language via lavac
 */
public final class Template {
	private char[] data;
	private int[] pos;
	private String[] names;
	private int[] intNames;

	public static CharList replaceOnce(Map<String, String> env, CharSequence tag, CharList out) {
		CharList tmp = IOUtil.getSharedCharBuf();

		int prevI = 0;
		for (int str = 0; str < tag.length(); str++) {
			int c = tag.charAt(str);
			if (c == '$' && tag.length() - str >= 3) {
				if (tag.charAt(++str) != '{') {
					str--; continue;
				}

				int end = ++str;
				loop1: {
					while (end < tag.length()) {
						if (tag.charAt(end) == '}') break loop1;
						end++;
					}
					end = -1;
				}
				if (end < 0) break;

				tmp.clear();
				String replacement = env.get(tmp.append(tag, str, end));
				if (replacement == null) throw new IllegalArgumentException("Param '" + tmp + "' missing");

				out.append(tag, prevI, str-2).append(replacement);
				prevI = end+1;
			}
		}

		return out.append(tag, prevI, tag.length());
	}

	public static Template compile(CharSequence tag) {
		CharList tmp = IOUtil.getSharedCharBuf();
		IntList pos = new IntList();
		SimpleList<String> names = new SimpleList<>();

		int prevI = 0;
		for (int str = 0; str < tag.length(); str++) {
			int c = tag.charAt(str);
			if (c == '$' && tag.length() - str >= 3) {
				if (tag.charAt(++str) != '{') {
					str--; continue;
				}

				int end = ++str;
				loop1: {
					while (end < tag.length()) {
						if (tag.charAt(end) == '}') break loop1;
						end++;
					}
					end = -1;
				}
				if (end < 0) break;

				names.add(tag.subSequence(str, end).toString());

				tmp.append(tag, prevI, str-2);
				pos.add(str-2-prevI);

				prevI = end+1;
			}
		}
		tmp.append(tag, prevI, tag.length());

		Template tmpl = new Template();
		tmpl.data = tmp.toCharArray();
		tmpl.pos = pos.toArray();
		tmpl.names = names.toArray(new String[names.size()]);
		return tmpl;
	}

	/**
	 * replace via string name
	 * @param env Map&lt;String, {@link Consumer&lt;CharList&gt;}|{@link CharSequence}&gt;
	 * @return sb
	 */
	@SuppressWarnings("unchecked")
	public CharList replace(Map<String, ?> env, CharList sb) {
		int i = 0;
		for (int j = 0; j < pos.length; j++) {
			Object val = env.get(names[j]);
			if (val == null) throw new IllegalArgumentException("参数 '" + names[j] + "' 缺失");

			sb.append(data, i, i += pos[j]);

			if (val instanceof BiConsumer) ((BiConsumer<Object, CharList>) val).accept(env, sb);
			else sb.append(val);
		}
		return sb.append(data, i, data.length);
	}

	/**
	 * replace via integral name
	 * @param env List&lt;{@link Consumer&lt;CharList&gt;}|{@link CharSequence}&gt;
	 * @return sb
	 */
	@SuppressWarnings("unchecked")
	public CharList replace(List<?> env, CharList sb) {
		try {
			if (intNames == null) makeIntName();
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("有参数名称不是数字:"+e.getMessage());
		}

		int i = 0;
		for (int j = 0; j < pos.length; j++) {
			int name = intNames[j];
			if (name >= env.size()) throw new IllegalArgumentException("参数 '" + names[j] + "' 缺失");

			Object val = env.get(name);
			if (val == null) throw new IllegalArgumentException("参数 '" + names[j] + "' 缺失");

			sb.append(data, i, i += pos[j]);

			if (val instanceof BiConsumer) ((BiConsumer<Object, CharList>) val).accept(env, sb);
			else sb.append(val);
		}
		return sb.append(data, i, data.length);
	}

	private void makeIntName() {
		int[] in = new int[names.length];
		for (int i = 0; i < names.length; i++) {
			in[i] = Integer.parseInt(names[i]);
		}
		intNames = in;
	}
}
