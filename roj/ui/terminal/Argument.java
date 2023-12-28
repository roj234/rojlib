package roj.ui.terminal;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.reflect.EnumHelper;
import roj.text.TextUtil;
import roj.ui.AnsiString;
import roj.ui.CLIUtil;
import roj.util.Helpers;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:06
 */
public interface Argument<T> {
	static Argument<File> file() { return file(null); }
	static Argument<File> file(Boolean folder) {
		return new Argument<File>() {
			@Override
			public File parse(ArgumentContext ctx, boolean complete) throws ParseException {
				File file = new File(ctx.nextString());
				if (folder == null) {
					if (!file.exists()) throw ctx.error("文件(夹)不存在");
				} else if (folder) {
					if (!file.isDirectory()) throw ctx.error("文件夹不存在");
				} else {
					if (!file.isFile()) throw ctx.error("文件不存在");
				}

				if (file.isDirectory() && complete && ctx.peekWord() == null) return null;
				return file;
			}

			@Override
			public void complete(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				File path = new File(ctx.nextString());
				if (path.isFile()) return;

				File parent;
				String match;
				if (path.isDirectory()) {
					parent = path;
					match = "";
				} else {
					parent = path.getParentFile();
					match = path.getName().toLowerCase();
					if (parent == null) return;
				}

				File[] list = parent.listFiles();
				if (list == null) return;
				for (File file : list) {
					if (file.getName().toLowerCase().startsWith(match)) {
						String name = file.getName().substring(match.length());
						AnsiString desc = new AnsiString(file.getAbsolutePath());
						if (file.isFile()) {
							completions.add(new Completion(new AnsiString(name).color16(CLIUtil.CYAN), desc));
						} else {
							completions.add(new Completion(new AnsiString(name+File.separatorChar).color16(CLIUtil.YELLOW), desc));
						}
					}
				}
			}

			@Override
			public void example(List<Completion> completions) { completions.add(new Completion(new File("").getAbsolutePath())); }

			@Override
			public String type() { return folder == null ? "path" : folder ? "folder" : "file"; }
		};
	}
	static Argument<String> string() { return new Argument<String>() {
		@Override
		public String parse(ArgumentContext ctx, boolean complete) throws ParseException { return ctx.nextString(); }
		@Override
		public String type() { return "string"; }
	}; }
	static Argument<String> string(String... selection) {
		MyHashMap<String, String> map = new MyHashMap<>(selection.length);
		for (String s : selection) map.put(s,s);
		return setOf(map, false);
	}
	static Argument<List<String>> stringFlags(String... flags) {
		MyHashMap<String, String> map = new MyHashMap<>(flags.length);
		for (String s : flags) map.put(s,s);
		return Helpers.cast(setOf(map, true));
	}
	static <T extends Enum<T>> Argument<T> enumeration(Class<T> type) { return setOf(Helpers.cast(EnumHelper.cDirAcc.enumConstantDirectory(type)), false); }
	static <T> Argument<T> setOf(Map<String, T> map, boolean multi) { return new ArgSetOf<>(multi, map); }
	class ArgSetOf<T> implements Argument<T> {
		private final boolean multi;
		protected final Map<String, T> choice;

		public ArgSetOf(boolean multi, Map<String, T> choice) {
			this.multi = multi;
			this.choice = choice;
		}

		@Override
		public T parse(ArgumentContext ctx, boolean complete) throws ParseException {
			updateChoices();
			if (multi) {
				List<T> arr = new SimpleList<>();
				while (true) {
					Word w = ctx.peekWord();
					if (w == null || !choice.containsKey(w.val())) {
						if (complete) {
							ctx.nextString();
							return null;
						}

						return Helpers.cast(arr);
					}
					arr.add(choice.get(ctx.nextString()));
				}
			} else {
				String val = ctx.nextString();
				T result = choice.get(val);
				if (result == null) throw ctx.error("选择不存在");
				return result;
			}
		}

		@Override
		public void complete(ArgumentContext ctx, List<Completion> completions) throws ParseException {
			updateChoices();
			if (multi) {
				while (true) {
					Word w = ctx.peekWord();
					if (w == null) return; // only first argument give example
					else if (!choice.containsKey(w.val())) break;

					ctx.nextWord();
				}
			}

			String val = ctx.nextString();
			for (String name : choice.keySet()) {
				if (name.startsWith(val)) completions.add(new Completion(name.substring(val.length())));
			}
		}

		@Override
		public void example(List<Completion> completions) { updateChoices(); for (String name : choice.keySet()) completions.add(new Completion(name)); }

		@Override
		public String type() { updateChoices(); return (multi?"anyOf":"oneOf")+"('"+TextUtil.join(choice.keySet(), "', '")+"')"; }

		protected void updateChoices() {}
	}
	static Argument<Integer> number(int min, int max) {
		return new Argument<Integer>() {
			@Override
			public Integer parse(ArgumentContext ctx, boolean b) throws ParseException {
				int val = ctx.nextInt();
				if (val < min) throw ctx.error("整数过小(可用的范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("整数过大(可用的范围是["+min+","+max+"])");
				return val;
			}

			@Override
			public String type() { return "int["+min+","+max+"]"; }
		};
	}
	static Argument<Double> real(double min, double max) {
		return new Argument<Double>() {
			@Override
			public Double parse(ArgumentContext ctx, boolean b) throws ParseException {
				double val = ctx.nextDouble();
				if (val < min) throw ctx.error("实数过小(可用的范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("实数过大(可用的范围是["+min+","+max+"])");
				return val;
			}

			@Override
			public String type() { return "real["+min+","+max+"]"; }
		};
	}
	static Argument<Boolean> bool() {
		return new Argument<Boolean>() {
			@Override
			public Boolean parse(ArgumentContext ctx, boolean complete) throws ParseException {
				String s = ctx.nextUnquotedString();
				if (s.equals("true")) return true;
				if (s.equals("false")) return false;
				throw ctx.error(s+"不是有效的布尔值");
			}

			@Override
			public void complete(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				String s = ctx.nextUnquotedString();
				if ("true".startsWith(s)) completions.add(new Completion("true".substring(s.length())));
				if ("false".startsWith(s)) completions.add(new Completion("false".substring(s.length())));
			}

			@Override
			public void example(List<Completion> completions) {
				completions.add(new Completion("true"));
				completions.add(new Completion("false"));
			}

			@Override
			public String type() { return "bool"; }
		};
	}

	T parse(ArgumentContext ctx, boolean complete) throws ParseException;
	default void complete(ArgumentContext ctx, List<Completion> completions) throws ParseException {}
	default void example(List<Completion> completions) {}
	default String type() { return getClass().getSimpleName(); }
}