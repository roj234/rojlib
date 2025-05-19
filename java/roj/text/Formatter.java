package roj.text;

import roj.collect.SimpleList;
import roj.io.IOUtil;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * @author Roj234
 * @since 2022/11/29 21:34
 */
public interface Formatter {
	/**
	 * 创建一个简单变量模板，在它的模板字符串中，可以用${变量名}引用变量
	 * @param template 模板字符串
	 * @return 简单变量模板实例
	 */
	static Formatter simple(String template) {return new Simple(template);}

	/**
	 * 这是否是动态模板，也就是说，将会使用env中的任何变量.
	 */
	default boolean isDynamic() {return true;}
	/**
	 * 用env中的变量填充模板，并将结果写入sb.
	 * @param env Map&lt;String, {@link CharList Consumer&lt;CharList&gt;}|{@link CharSequence}&gt;
	 * @return 填充后的模板
	 */
	CharList format(Map<String, ?> env, CharList sb) throws IllegalArgumentException;

	final class Simple implements Formatter {
		private final String[] names;
		private final char[] data, pos;

		Simple(String template) {
			var tmp = IOUtil.getSharedCharBuf();
			var pos = new CharList();
			var names = new SimpleList<String>();

			int prevI = 0;
			while (true) {
				int i = template.indexOf("${", prevI);
				if (i < 0) break;

				int end = template.indexOf('}', i);
				if (end < 0) throw new IllegalStateException("未闭合的括号");

				names.add(template.substring(i+2, end));
				tmp.append(template, prevI, i);
				pos.append((char)(i - prevI));

				prevI = end+1;
			}

			data = tmp.append(template, prevI, template.length()).toCharArray();
			this.pos = pos.toCharArray();
			this.names = names.toArray(new String[names.size()]);
		}

		public boolean isDynamic() {return names.length > 0;}

		@SuppressWarnings("unchecked")
		public CharList format(Map<String, ?> env, CharList sb) {
			int i = 0;
			for (int j = 0; j < pos.length; j++) {
				Object val = env.get(names[j]);

				sb.append(data, i, i += pos[j]);

				if (val == null) sb.append("${").append(names[j]).append("}");
				else if (val instanceof BiConsumer) ((BiConsumer<Object, CharList>) val).accept(env, sb);
				else sb.append(val);
			}
			return sb.append(data, i, data.length);
		}
	}
}