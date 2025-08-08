package roj.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Token;
import roj.io.IOUtil;
import roj.reflect.EnumHelper;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/11/20 15:06
 */
public interface Argument<T> {
	static Argument<File> path() {return file(0);}
	static Argument<File> folder() {return file(1);}
	static Argument<File> file() {return file(2);}
	static Argument<File> fileOptional(boolean file) {return file(file?4:3);}
	static Argument<File> file(int filter) {return new FileArgument(filter);}
	static Argument<?> fileIn(File base) {
		return new FileArgument(2) {
			@Override
			protected @NotNull File getPath(String pathStr) {
				return IOUtil.relativePath(base, pathStr);
			}
		};
	}
	static Argument<List<File>> files(int filter) {return new ListArgument<>(file(filter), 1, Integer.MAX_VALUE);}
	class ListArgument<T> implements Argument<List<T>> {
		private final Argument<T> each;
		private final int min, max;

		public ListArgument(Argument<T> each, int min, int max) {
			this.each = each;
			this.min = min;
			this.max = max;
		}

		@Override
		public List<T> parse(CommandArgList ctx, @Nullable List<Completion> completions) throws ParseException {
			if (completions != null) {
				while (true) {
					if (ctx.isEndAtWordEnd()) {
						each.parse(ctx, completions);
						return null;
					} else if (ctx.isEndAtWordStart()) {
						each.example(completions);
						return null;
					} else {
						each.parse(ctx, null);
					}
				}
			} else {
				var list = new ArrayList<T>();
				do {
					list.add(each.parse(ctx, null));
					if (list.size() == max) break;
				} while (!ctx.isRealEOF());
				if (list.size() < min) throw ctx.error("最少需要"+min+"长度的列表, 提供了"+list.size());
				return list;
			}
		}

		@Override public void example(List<Completion> completions) {each.example(completions);}

		@Override public String format(String name, int mode) {
			var str = min > 0 ? "<...>" : "[...]";
			var sb = new CharList().append(str.charAt(0)).append(name).append(str, 1, str.length());
			if (mode != 0) {
				sb.append(": 重复[").append(min == 0 ? "" : min).append(",").append(max == Integer.MAX_VALUE ? "" : max).append("]");
			}
			return sb.toStringAndFree();
		}

		@Override public int color() {return each.color();}
	}
	static Argument<String> string() { return new Argument<>() {
		@Override public String parse(CommandArgList ctx, List<Completion> completions) throws ParseException {return ctx.nextString();}
		@Override public String format(String name, int mode) {return "字符串";}
		@Override public int color() {return 0x1BCC00;}
	}; }
	static Argument<String> string(String... selection) {
		HashMap<String, String> map = new HashMap<>(selection.length);
		for (String s : selection) map.put(s,s);
		return oneOf(map);
	}
	static Argument<List<String>> stringFlags(String... flags) {
		HashMap<String, String> map = new HashMap<>(flags.length);
		for (String s : flags) map.put(s,s);
		return Helpers.cast(anyOf(map));
	}
	static <T extends Enum<T>> Argument<T> enumeration(Class<T> type) { return oneOf(Helpers.cast(EnumHelper.CONSTANTS.enumConstantDirectory(type))); }
	static <T> Argument<T> oneOf(Map<String, T> map) { return new ArgSetOf<>(0, map); }
	static Argument<String> suggest(Map<String, String> map) { return new ArgSetOf<>(1, map); }
	static <T> Argument<List<T>> someOf(Map<String, T> map) { return Helpers.cast(new ArgSetOf<>(2, map)); }
	static <T> Argument<List<T>> anyOf(Map<String, T> map) { return Helpers.cast(new ArgSetOf<>(3, map)); }

	class FileArgument implements Argument<File> {
		private final int filter;
		public FileArgument(int filter) {this.filter = filter;}

		@Override
		public File parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
			boolean edge = ctx.isEndAtWordEnd();
			String pathStr = ctx.nextString();
			File path = getPath(pathStr);

			if (completions != null) {
				if (!edge) return path;

				String match, prefix;

				if (path.isFile()) return null;
				if (path.isDirectory() && !pathStr.endsWith(".")) {
					match = "";
					prefix = pathStr.endsWith("/") ? "" : "/";
				} else {
					match = path.getName().toLowerCase();

					path = path.getParentFile();
					if (path == null) return null;

					prefix = "";
				}

				listPath(completions, path, match, prefix, pathStr.indexOf(' ') >= 0, pathStr);
				return null;
			}

			switch (filter) {
				case 0 -> {
					if (!path.exists()) throw ctx.error("路径不存在");
				}
				case 1 -> {
					if (!path.isDirectory()) throw ctx.error("文件夹不存在");
				}
				case 2 -> {
					if (!path.isFile()) throw ctx.error("文件不存在");
				}
			}
			return path;
		}

		@NotNull
		protected File getPath(String pathStr) {return new File(pathStr).getAbsoluteFile();}

		private void listPath(List<Completion> completions, File path, String match, String prefix, boolean hasSlash, String pathStr) {
			File[] list = path.listFiles();
			if (list == null) return;
			for (File file : list) {
				String fname = file.getName();
				if (!fname.toLowerCase().startsWith(match)) continue;

				CharList sb = IOUtil.getSharedCharBuf().append(prefix).append(fname);

				if (!file.isFile()) sb.append('/');
				else if (filter == 1 || filter == 3) continue;

				int offset = -match.length();

				boolean newSlash = fname.indexOf(' ') >= 0;
				if ((hasSlash | newSlash) && sb.endsWith("\\")) sb.append('\\');

				block1:
				{
					if (!hasSlash) {
						if (newSlash) {
							fname = "'" + pathStr.substring(0, pathStr.length() + offset) + sb + "'";
							offset = -pathStr.length();
							break block1;
						}
					} else {
						offset -= pathStr.endsWith("\\") ? 2 : 1;
						sb.append('\'');
					}

					fname = sb.toString();
				}

				Text desc = path.isAbsolute() ? null : new Text("绝对路径: ").append(new Text(file.getAbsolutePath()).colorRGB(0xffccee));
				completions.add(new Completion(new Text(fname).color16(file.isFile() ? Tty.CYAN : Tty.YELLOW), desc, offset));
			}
		}

		@Override
		public void example(List<Completion> completions) {
			File f = getPath("");
			int size = completions.size();
			listPath(completions, f, "", "", false, "");
			if (completions.size() == size)
				completions.add(new Completion("./"));
		}

		@Override
		public String type() {
			return switch (filter) {
				default -> "?";
				case 0 -> "存在的路径";
				case 1 -> "存在的目录";
				case 2 -> "存在的文件";
				case 3 -> "目录";
				case 4 -> "路径";
			};
		}

		@Override
		public int color() {
			return switch (filter) {
				default -> 0xFF0000;
				case 0,4 -> 0x6495ED;
				case 1,3 -> 0x228B22;
				case 2 -> 0xFFA500;
			};
		}
	}
	class ArgSetOf<T> implements Argument<T> {
		private final byte mode;
		protected final Map<String, T> choice;

		public ArgSetOf(int mode, Map<String, T> choice) {
			this.mode = (byte) mode;
			this.choice = choice;
		}

		@Override
		public T parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
			updateChoices();
			if (mode > 1) {
				List<T> arr = new ArrayList<>();
				Token w = null;
				while (true) {
					var nextW = ctx.peekWord();
					if (nextW == null) break;
					w = nextW;

					if (!choice.containsKey(w.text())) break;
					arr.add(choice.get(ctx.nextString()));
				}

				if (completions != null) {
					if (w != null && ctx.isEndAtWordEnd()) complete(w.text(), completions);
					return null;
				}

				if (mode == 2 && arr.isEmpty()) throw ctx.error(format("参数", 1)+"不能为空");
				return Helpers.cast(arr);
			} else {
				String val = ctx.nextString();
				T result = choice.get(val);
				if (result == null) {
					if (completions != null) {
						if (ctx.isEndAtWordStart()) return Helpers.cast(IntMap.UNDEFINED);

						complete(val, completions);
						return null;
					}
					if (mode == 0) throw ctx.error("选择不存在");
					else return Helpers.cast(val);
				}

				if (completions != null && ctx.isEndAtWordEnd()) complete(val, completions);
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
		public String format(String name, int mode) {
			updateChoices();
			var str = switch (this.mode) {
				default -> "<>";
				case 1 -> "[]";
				case 2 -> "<...>";
				case 3 -> "[...]";
			};
			var sb = new CharList().append(str.charAt(0)).append(name).append(str, 1, str.length());
			if (mode == 0) return sb.toStringAndFree();

			sb.append(": ");

			var itr = choice.keySet().iterator();
			if (!itr.hasNext()) return sb.append("无可选项").toStringAndFree();

			int count = mode == 1 ? 4 : 0;
			sb.append("('");
			while (itr.hasNext()) {
				sb.append(itr.next());
				if (++count == 5) return sb.append("', ...)").toStringAndFree();
				if (!itr.hasNext()) break;
				sb.append((CharSequence) "', '");
			}

			return sb.append("')").toStringAndFree();
		}

		protected void updateChoices() {}
	}
	static Argument<String> rest() {
		return new Argument<>() {
			@Override
			public String parse(CommandArgList ctx, @Nullable List<Completion> completions) throws ParseException {
				if (ctx.peekWord() == null) return "";

				CharList sb = IOUtil.getSharedCharBuf();
				while (true) {
					Token w = ctx.nextWord();
					if (w.type() == Token.STRING) Tokenizer.escape(sb.append('"'), w.text()).append('"');
					else sb.append(w.text());
					w = ctx.peekWord();
					if (w == null) break;
					sb.append(' ');
				}
				return sb.toStringAndFree();
			}

			@Override
			public String type() {return "剩余的输入";}
		};
	}
	static Argument<String[]> restArray() {
		return new Argument<>() {
			@Override
			public String[] parse(CommandArgList ctx, @Nullable List<Completion> completions) throws ParseException {
				List<String> tmp = new ArrayList<>();
				while (true) {
					var w = ctx.peekWord();
					if (w == null) break;
					tmp.add(w.text());
					ctx.nextWord();
				}
				return tmp.toArray(new String[tmp.size()]);
			}

			@Override
			public String type() {return "剩余的输入";}
		};
	}
	static Argument<Integer> number(int min, int max) {
		return new Argument<>() {
			@Override
			public Integer parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
				int val = ctx.nextInt();
				if (val < min) throw ctx.error("整数过小(范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("整数过大(范围是["+min+","+max+"])");
				return val;
			}

			@Override public String type() { return "整数["+(min==Integer.MIN_VALUE?"":min)+","+(max==Integer.MAX_VALUE?"":max)+"]"; }
			@Override public int color() {return 0x00BFFF;}
		};
	}
	static Argument<Long> Long(long min, long max) {
		return new Argument<>() {
			@Override
			public Long parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
				long val = ctx.nextLong();
				if (val < min) throw ctx.error("整数过小(范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("整数过大(范围是["+min+","+max+"])");
				return val;
			}

			@Override public String type() { return "长整数["+(min==Long.MIN_VALUE?"":min)+","+(max==Long.MAX_VALUE?"":max)+"]"; }
			@Override public int color() {return 0x00CED1;}
		};
	}
	static Argument<Long> dataUnit() {
		return new Argument<>() {
			@Override
			public Long parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
				try {
					return (long) TextUtil.unscaledNumber1024(ctx.nextUnquotedString());
				} catch (Exception e) {
					throw ctx.error(e.getMessage());
				}
			}

			@Override
			public void example(List<Completion> completions) {
				completions.add(new Completion("1MB"));
				completions.add(new Completion("65536"));
				completions.add(new Completion("4Gb"));
			}

			@Override public String type() { return "数据单位"; }
			@Override public int color() {return 0x00CED1;}
		};
	}
	static Argument<Double> real(double min, double max) {
		return new Argument<Double>() {
			@Override
			public Double parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
				double val = ctx.nextDouble();
				if (val < min) throw ctx.error("实数过小(范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("实数过大(范围是["+min+","+max+"])");
				return val;
			}

			@Override public String type() { return "实数["+min+","+max+"]"; }
			@Override public int color() {return 0xFFD700;}
		};
	}
	static Argument<Boolean> bool() {
		final HashSet<String> truly = new HashSet<>("true", "t", "yes", "y"), falsy = new HashSet<>("false", "f", "no", "n");
		return new Argument<Boolean>() {
			@Override
			public Boolean parse(CommandArgList ctx, List<Completion> completions) throws ParseException {
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

			@Override public String type() {return "布尔";}
			@Override public int color() {return 0x808000;}
		};
	}

	T parse(CommandArgList ctx, @Nullable List<Completion> completions) throws ParseException;
	default void example(List<Completion> completions) {}

	default String format(String name, int mode) {
		if (name.isEmpty()) return type();

		if (mode == 0) return "<"+name+">";
		return "<"+name+">: "+type();
	}
	default String type() {return getClass().getSimpleName();}

	default int color() {
		float hue = format("", 1).hashCode() / (float) Integer.MAX_VALUE;
		if (hue < 0) hue = -hue;
		hue = 0.5f/6f + hue * 5/6f;
		return Tty.TextEffect.HueToRGB(hue);
	}
}