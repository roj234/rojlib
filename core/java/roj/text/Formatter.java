package roj.text;

import roj.collect.ArrayList;
import roj.io.IOUtil;

import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * A functional interface for formatting templates with variable substitution.
 * @see roj.text.logging.LogContext#setComponents(List)
 * @author Roj234
 * @since 2022/11/29 21:34
 */
@FunctionalInterface
public interface Formatter {
	/**
	 * Formats the template using variables from the environment and writes the result to the provided CharList.
	 *
	 * @param env a map containing variable names as keys and their corresponding values.
	 *            Values can be {@link CharSequence} instances or {@link Formatter}
	 * @param sb  the CharList to which the formatted result will be appended
	 * @return the CharList containing the formatted result
	 * @throws IllegalArgumentException if the environment contains invalid values or variables
	 */
	CharList format(Map<String, ?> env, CharList sb) throws IllegalArgumentException;

	/**
	 * 是否是常量模板。
	 * @see #constant(String)
	 */
	default boolean isConstant() {return false;}

	/**
	 * Creates a constant formatter that always outputs the specified value.
	 *
	 * @param value the constant string value to output
	 * @return a Formatter instance that always returns the specified value
	 */
	static Formatter constant(String value) {
		return new Formatter() {
			@Override public boolean isConstant() {return true;}
			@Override public CharList format(Map<String, ?> env, CharList sb) {return sb.append(value);}
		};
	}

	/**
	 * Creates a simple variable template formatter that supports variable references using {@code ${variableName}} syntax.
	 *
	 * @param template the template string containing variable placeholders in the format {@code ${variableName}}
	 * @return a Formatter instance capable of substituting variables in the template
	 * @throws IllegalArgumentException if the template contains unclosed variable brackets
	 */
	static Formatter simple(String template) {
		var tmp = IOUtil.getSharedCharBuf();
		var pos = new CharList();
		var names = new ArrayList<String>();

		int prevI = 0;
		while (true) {
			int i = template.indexOf("${", prevI);
			if (i < 0) break;

			int end = template.indexOf('}', i);
			if (end < 0) throw new IllegalArgumentException("未闭合的花括号("+i+"): "+template);

			names.add(template.substring(i+2, end));
			tmp.append(template, prevI, i);
			pos.append((char)(i - prevI));

			prevI = end+1;
		}

		if (names.isEmpty()) return constant(template);

		String[] names1 = names.toArray(new String[names.size()]);
		char[] data1 = tmp.append(template, prevI, template.length()).toCharArray();
		char[] pos1 = pos.toCharArray();

		return (env, sb) -> {
			int i = 0;
			for (int j = 0; j < pos1.length; j++) {
				Object val = env.get(names1[j]);

				sb.append(data1, i, i += pos1[j]);

				if (val == null) sb.append("${").append(names1[j]).append('}');
				else if (val instanceof Formatter f) f.format(env, sb);
				else sb.append(val);
			}
			return sb.append(data1, i, data1.length);
		};
	}

	/**
	 * Creates a formatter that always returns the current time formatted according to the specified pattern.
	 *
	 * @param format the date/time format pattern to use
	 * @see DateFormat#format(String, long, TimeZone, CharList)
	 * @return a Formatter instance that outputs the current time in the specified format
	 */
	static Formatter time(String format) { return (env, sb) -> DateFormat.format(format, System.currentTimeMillis(), DateFormat.getLocalTimeZone(), sb); }
}