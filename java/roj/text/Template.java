package roj.text;

import roj.collect.SimpleList;
import roj.io.IOUtil;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2022/11/29 21:34
 */
public final class Template implements Formatter {
	private final String[] names;
	private final char[] data, pos;

	public static Template compile(String tag) {return new Template(tag);}
	public Template(String tag) {
		CharList tmp = IOUtil.getSharedCharBuf();
		CharList pos = new CharList();
		SimpleList<String> names = new SimpleList<>();

		int prevI = 0;
		while (true) {
			int i = tag.indexOf("${", prevI);
			if (i < 0) break;

			int end = tag.indexOf('}', i);
			if (end < 0) throw new IllegalStateException();

			names.add(tag.substring(i+2, end));
			tmp.append(tag, prevI, i);
			pos.append((char)(i - prevI));

			prevI = end+1;
		}
		tmp.append(tag, prevI, tag.length());

		data = tmp.toCharArray();
		this.pos = pos.toCharArray();
		this.names = names.toArray(new String[names.size()]);
	}

	/**
	 * replace via string name
	 * @param env Map&lt;String, {@link Consumer&lt;CharList&gt;}|{@link CharSequence}&gt;
	 * @return sb
	 */
	@SuppressWarnings("unchecked")
	public CharList format(Map<String, ?> env, CharList sb) {
		int i = 0;
		for (int j = 0; j < pos.length; j++) {
			Object val = env.get(names[j]);
			if (val == null) throw new IllegalArgumentException("参数 '"+names[j]+"' 缺失");

			sb.append(data, i, i += pos[j]);

			if (val instanceof BiConsumer) ((BiConsumer<Object, CharList>) val).accept(env, sb);
			else sb.append(val);
		}
		return sb.append(data, i, data.length);
	}
}