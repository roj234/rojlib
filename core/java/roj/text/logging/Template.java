package roj.text.logging;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.config.node.IntValue;
import roj.text.CharList;
import roj.text.Formatter;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Implementation of {@link Formatter} that parses and processes template strings
 * with function substitutions in the specified simple format.
 *
 * @author Roj234
 * @since 2025/11/06 04:54
 */
public class Template implements Formatter {
	private final List<Formatter> parts;
	private final Map<String, BiFunction<String, Formatter, Formatter>> registry;

	public Template(String template) {
		this(new HashMap<>(), template);
	}

	/**
	 * Constructs a Template with a custom registry.
	 *
	 * @param registry the function registry
	 * @param template the template string to parse
	 */
	public Template(Map<String, BiFunction<String, Formatter, Formatter>> registry, String template) {
		this.registry = registry;
		this.parts = new ArrayList<>();

		IntValue pos = new IntValue(0);
		parse(template, pos);
		if (pos.value != template.length()) {
			throw new IllegalArgumentException("Unparsed content at position " + pos.value);
		}
	}

	private void parse(String s, IntValue pos) {
		int i = pos.value;
		while (i < s.length()) {
			if (s.charAt(i) != '%') {
				int next = s.indexOf('%', i+1);
				if (next == -1) next = s.length();
				parts.add(Formatter.constant(s.substring(i, next)));
				i = next;
			} else {
				pos.value = i;
				parts.add(parseVar(s, pos));
				i = pos.value;
			}
		}
		pos.value = i;
	}

	private Formatter parseVar(String s, IntValue pos) {
		int literalStart = pos.value;
		int i = literalStart + 1; // Skip %

		int nameStart = i;
		while (i < s.length() && isLetter(s.charAt(i))) i++;

		if (nameStart == i) throw new IllegalArgumentException("Excepting name at position "+nameStart);
		String name = s.substring(nameStart, i);

		pos.value = i;
		String params = parseParams(s, pos);
		i = pos.value;

		var function = registry.get(name);
		if (function != null) {
			// Known function: parse inner if present
			Formatter inner = null;
			if (i < s.length() && s.charAt(i) == '%') {
				pos.value = i;
				inner = parseVar(s, pos);
			}
			return function.apply(params, inner);
		}

		// Unknown: check if simple variable or literal
		if (params.isEmpty() && (i >= s.length() || s.charAt(i) != '%')) {
			// Simple variable %name (no params, no chain)
			return new DynamicVar(name);
		}

		// Literal: prefix + consume chain literally
		pos.value = i;
		consumeLiteralChain(s, pos);
		i = pos.value;
		return Formatter.constant(s.substring(literalStart, i));
	}

	private static String parseParams(String s, IntValue pos) {
		int i = pos.value;

		String params = "";
		if (i < s.length() && s.charAt(i) == '{') {
			i++; // Skip {
			int paramStart = i;
			while (i < s.length() && s.charAt(i) != '}') {
				i++;
			}
			if (i >= s.length() || s.charAt(i) != '}') {
				throw new IllegalArgumentException("Unclosed { at position " + (i - 1));
			}
			params = s.substring(paramStart, i);
			i++; // Skip }
		}

		pos.value = i;
		return params;
	}

	private static void consumeLiteralChain(String s, IntValue pos) {
		int i = pos.value;
		while (i < s.length() && s.charAt(i) == '%') {
			i++; // Skip %
			int nameStart = i;
			while (i < s.length() && isLetter(s.charAt(i))) i++;

			if (i <= nameStart) break;

			pos.value = i;
			parseParams(s, pos);
			i = pos.value;
		}

		pos.value = i;
	}

	private static boolean isLetter(char c) {
		return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
	}

	@Override
	public @NotNull CharList format(@NotNull Map<String, ?> env, @NotNull CharList sb) {
		for (Formatter part : parts) {
			part.format(env, sb);
		}
		return sb;
	}

	@Override
	public boolean isConstant() {
		for (Formatter part : parts) {
			if (!part.isConstant()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Registers a function creator in the registry.
	 *
	 * @param name the function name
	 * @param creator the creator (params -> inner -> formatter)
	 */
	public void register(String name, BiFunction<String, Formatter, Formatter> creator) {
		registry.put(name, creator);
	}

	// Inner class for variables
	private static class DynamicVar implements Formatter {
		private final String name;

		DynamicVar(String name) {
			this.name = name;
		}

		@Override
		public @NotNull CharList format(@NotNull Map<String, ?> env, @NotNull CharList sb) {
			Object val = env.get(name);
			if (val == null) {
				sb.append("%").append(name);
			} else if (val instanceof Formatter f) {
				f.format(env, sb);
			} else {
				sb.append(val);
			}
			return sb;
		}
	}
}
