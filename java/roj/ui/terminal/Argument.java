package roj.ui.terminal;

import org.jetbrains.annotations.Nullable;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.io.IOUtil;
import roj.reflect.EnumHelper;
import roj.text.CharList;
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
			public File parse(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				String pathStr = ctx.nextString();
				File path = new File(pathStr);

				if (completions != null) {
					if (!ctx.isWordEdge()) return null;

					String match, prefix;

					if (path.isFile()) return null;
					if (path.isDirectory()) {
						match = "";
						prefix = pathStr.endsWith(File.separator) ? "" : File.separator;
					} else {
						match = path.getName().toLowerCase();

						path = path.getParentFile();
						if (path == null) return null;

						prefix = "";
					}

					listPath(completions, path, match, prefix, pathStr.indexOf(' ') >= 0, pathStr);
					return null;
				}

				if (folder == null) {
					if (!path.exists()) throw ctx.error("文件(夹)不存在");
				} else if (folder) {
					if (!path.isDirectory() && !path.mkdirs()) throw ctx.error("文件夹不存在且无法创建");
				} else {
					if (!path.isFile()) throw ctx.error("文件不存在");
				}

				return path;
			}

			private void listPath(List<Completion> completions, File path, String match, String prefix, boolean hasSlash, String pathStr) {
				File[] list = path.listFiles();
				if (list == null) return;
				for (File file : list) {
					String fname = file.getName();
					if (!fname.toLowerCase().startsWith(match)) continue;

					CharList sb = IOUtil.getSharedCharBuf().append(prefix).append(fname);

					if (!file.isFile()) sb.append(File.separatorChar);
					else if (folder == Boolean.TRUE) continue;

					int offset = - match.length();

					boolean newSlash = fname.indexOf(' ') >= 0;
					if ((hasSlash|newSlash) && sb.endsWith("\\")) sb.append('\\');

					block1: {
						if (!hasSlash) {
							if (newSlash) {
								fname = "'"+pathStr.substring(0, pathStr.length()+offset)+sb+"'";
								offset = -pathStr.length();
								break block1;
							}
						} else {
							offset -= pathStr.endsWith("\\") ? 2 : 1;
							sb.append('\'');
						}

						fname = sb.toString();
					}

					AnsiString desc = path.isAbsolute() ? null : new AnsiString("绝对路径: ").append(new AnsiString(file.getAbsolutePath()).colorRGB(0xffccee));
					completions.add(new Completion(new AnsiString(fname).color16(file.isFile() ? CLIUtil.CYAN : CLIUtil.YELLOW), desc, offset));
				}
			}

			@Override
			public void example(List<Completion> completions) {
				File f = new File("").getAbsoluteFile();
				completions.add(new Completion(f.getAbsolutePath()));
				listPath(completions, f, "", ".".concat(File.separator), false, "");
			}

			@Override
			public String type() { return folder == null ? "path" : folder ? "folder" : "file"; }
		};
	}
	static Argument<String> string() { return new Argument<String>() {
		@Override
		public String parse(ArgumentContext ctx, List<Completion> completions) throws ParseException { return ctx.nextString(); }
		@Override
		public String type() { return "string"; }
	}; }
	static Argument<String> string(String... selection) {
		MyHashMap<String, String> map = new MyHashMap<>(selection.length);
		for (String s : selection) map.put(s,s);
		return oneOf(map);
	}
	static Argument<List<String>> stringFlags(String... flags) {
		MyHashMap<String, String> map = new MyHashMap<>(flags.length);
		for (String s : flags) map.put(s,s);
		return Helpers.cast(anyOf(map));
	}
	static <T extends Enum<T>> Argument<T> enumeration(Class<T> type) { return oneOf(Helpers.cast(EnumHelper.cDirAcc.enumConstantDirectory(type))); }
	static <T> Argument<T> oneOf(Map<String, T> map) { return new ArgSetOf<>(0, map); }
	static Argument<String> suggest(Map<String, String> map) { return new ArgSetOf<>(1, map); }
	static <T> Argument<T> someOf(Map<String, T> map) { return new ArgSetOf<>(2, map); }
	static <T> Argument<T> anyOf(Map<String, T> map) { return new ArgSetOf<>(3, map); }
	class ArgSetOf<T> implements Argument<T> {
		private final byte mode;
		protected final Map<String, T> choice;

		public ArgSetOf(int mode, Map<String, T> choice) {
			this.mode = (byte) mode;
			this.choice = choice;
		}

		@Override
		public T parse(ArgumentContext ctx, List<Completion> completions) throws ParseException {
			updateChoices();
			if (mode > 1) {
				List<T> arr = new SimpleList<>();
				while (true) {
					Word w = ctx.peekWord();
					if (w == null || !choice.containsKey(w.val())) {
						if (completions != null) {
							if (w != null && ctx.isWordEdge()) complete(w.val(), completions);
							return null;
						}

						if (mode == 2 && arr.isEmpty()) throw ctx.error("该参数"+type()+"不能为空");
						return Helpers.cast(arr);
					}
					arr.add(choice.get(ctx.nextString()));
				}
			} else {
				String val = ctx.nextString();
				T result = choice.get(val);
				if (result == null) {
					if (completions != null) {
						if (ctx.isEOF()) return Helpers.cast(IntMap.UNDEFINED);

						complete(val, completions);
						return null;
					}
					if (mode == 0) throw ctx.error("选择不存在");
					else return Helpers.cast(val);
				}
				return result;
			}
		}

		private void complete(String val, List<Completion> completions) {
			for (String name : choice.keySet()) {
				if (name.startsWith(val) && !name.equals(val)) completions.add(new Completion(name.substring(val.length())));
			}
		}

		@Override
		public void example(List<Completion> completions) { updateChoices(); for (String name : choice.keySet()) completions.add(new Completion(name)); }

		@Override
		public String type() { updateChoices(); return (mode==3?"anyOf":mode==2?"someOf":mode==1?"suggest":"oneOf")+"('"+TextUtil.join(choice.keySet(), "', '")+"')"; }

		protected void updateChoices() {}
	}
	static Argument<String> rest() {
		return (ctx, completions) -> {
			if (ctx.peekWord() == null) return "";

			CharList sb = IOUtil.getSharedCharBuf();
			while (true) {
				Word w = ctx.nextWord();
				if (w.type() == Word.STRING) Tokenizer.addSlashes(sb.append('"'), w.val()).append('"');
				else sb.append(w.val());
				w = ctx.peekWord();
				if (w == null) break;
				sb.append(' ');
			}
			return sb.toStringAndFree();
		};
	}
	static Argument<Integer> number(int min, int max) {
		return new Argument<>() {
			@Override
			public Integer parse(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				int val = ctx.nextInt();
				if (val < min) throw ctx.error("整数过小(可用的范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("整数过大(可用的范围是["+min+","+max+"])");
				return val;
			}

			@Override
			public String type() { return "int["+min+","+max+"]"; }
		};
	}
	static Argument<Long> Long(long min, long max) {
		return new Argument<>() {
			@Override
			public Long parse(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				long val = ctx.nextLong();
				if (val < min) throw ctx.error("整数过小(可用的范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("整数过大(可用的范围是["+min+","+max+"])");
				return val;
			}

			@Override
			public String type() { return "long["+min+","+max+"]"; }
		};
	}
	static Argument<Double> real(double min, double max) {
		return new Argument<Double>() {
			@Override
			public Double parse(ArgumentContext ctx, List<Completion> completions) throws ParseException {
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
		final MyHashSet<String> truly = new MyHashSet<>("true", "t", "yes", "y"), falsy = new MyHashSet<>("false", "f", "no", "n");
		return new Argument<Boolean>() {
			@Override
			public Boolean parse(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				String s = ctx.nextUnquotedString();
				if (truly.contains(s)) return true;
				if (falsy.contains(s)) return false;

				if (completions != null) {
					if ("true".startsWith(s)) completions.add(new Completion("true".substring(s.length())));
					if ("false".startsWith(s)) completions.add(new Completion("false".substring(s.length())));
					return null;
				}

				throw ctx.error(s+"不是有效的布尔值");
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

	T parse(ArgumentContext ctx, @Nullable List<Completion> completions) throws ParseException;
	default void example(List<Completion> completions) {}
	default String type() { return getClass().getSimpleName(); }

	default int color() { return type().hashCode(); }
}