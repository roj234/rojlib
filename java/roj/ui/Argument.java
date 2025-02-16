package roj.ui;

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
import roj.util.Helpers;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:06
 */
public interface Argument<T> {
	static Argument<File> path() {return file(0);}
	static Argument<File> folder() {return file(1);}
	static Argument<File> file() {return file(2);}
	static Argument<File> fileOptional(boolean file) {return file(file?4:3);}
	static Argument<File> file(int filter) {
		return new Argument<>() {
			@Override
			public File parse(CommandParser ctx, List<Completion> completions) throws ParseException {
				boolean edge = ctx.isEndAtWordEnd();
				String pathStr = ctx.nextString();
				File path = new File(pathStr);

				if (completions != null) {
					if (!edge) return path;

					String match, prefix;

					if (path.isFile()) return null;
					if (path.isDirectory() && !pathStr.endsWith(".")) {
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

				switch (filter) {
					case 0 -> {
						if (!path.exists()) throw ctx.error("路径不存在");
					}
					case 1 -> {
						if (!path.isDirectory() && !path.mkdirs()) throw ctx.error("文件夹不存在且无法创建");
					}
					case 2 -> {
						if (!path.isFile()) throw ctx.error("文件不存在");
					}
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

					AnsiString desc = path.isAbsolute() ? null : new AnsiString("绝对路径: ").append(new AnsiString(file.getAbsolutePath()).colorRGB(0xffccee));
					completions.add(new Completion(new AnsiString(fname).color16(file.isFile() ? Terminal.CYAN : Terminal.YELLOW), desc, offset));
				}
			}

			@Override
			public void example(List<Completion> completions) {
				File f = new File("").getAbsoluteFile();
				completions.add(new Completion(f.getAbsolutePath()));
				listPath(completions, f, "", ".".concat(File.separator), false, "");
			}

			@Override
			public String type() {
				return switch (filter) {
					default -> "illegal";
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
		};
	}
	static Argument<List<File>> files(int filter) {
		var file = file(filter);
		return new Argument<>() {
			@Override
			public List<File> parse(CommandParser ctx, @Nullable List<Completion> completions) throws ParseException {
				if (completions != null) {
					while (true) {
						if (ctx.isEndAtWordEnd()) {
							file.parse(ctx, completions);
							return null;
						} else if (ctx.isEndAtWordStart()) {
							file.example(completions);
							return null;
						} else {
							file.parse(ctx, null);
						}
					}
				} else {
					List<File> arr = new SimpleList<>();
					do {
						arr.add(file.parse(ctx, null));
					} while (!ctx.isRealEOF());
					return arr;
				}
			}
			@Override public void example(List<Completion> completions) {file.example(completions);}
			@Override public String type() {return "列表["+file.type()+"]";}
			@Override public int color() {return file.color();}
		};
	}
	static Argument<String> string() { return new Argument<>() {
		@Override public String parse(CommandParser ctx, List<Completion> completions) throws ParseException {return ctx.nextString();}
		@Override public String type() {return "字符串";}
		@Override public int color() {return 0x1BCC00;}
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
	static <T> Argument<List<T>> someOf(Map<String, T> map) { return Helpers.cast(new ArgSetOf<>(2, map)); }
	static <T> Argument<List<T>> anyOf(Map<String, T> map) { return Helpers.cast(new ArgSetOf<>(3, map)); }
	class ArgSetOf<T> implements Argument<T> {
		private final byte mode;
		protected final Map<String, T> choice;

		public ArgSetOf(int mode, Map<String, T> choice) {
			this.mode = (byte) mode;
			this.choice = choice;
		}

		@Override
		public T parse(CommandParser ctx, List<Completion> completions) throws ParseException {
			updateChoices();
			if (mode > 1) {
				List<T> arr = new SimpleList<>();
				while (true) {
					Word w = ctx.peekWord();
					if (w == null || !choice.containsKey(w.val())) {
						if (completions != null) {
							if (w != null && ctx.isEndAtWordEnd()) complete(w.val(), completions);
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
						if (ctx.isEndAtWordStart()) return Helpers.cast(IntMap.UNDEFINED);

						complete(val, completions);
						return null;
					}
					if (mode == 0) throw ctx.error("选择不存在");
					else return Helpers.cast(val);
				}

				if (completions != null && ctx.isEndAtWordEnd() && ctx.peekWord(true) == null) complete(val, completions);
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
		public String type() {
			updateChoices();
			CharList sb = new CharList(switch (mode) {
				default -> "illegal";
				case 0 -> "[选择1个]";
				case 1 -> "[建议]";
				case 2 -> "[选择1至多个]";
				case 3 -> "[选择0至多个]";
			}).append("('");

			int count = 0;
			var itr = choice.keySet().iterator();
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
			public String parse(CommandParser ctx, @Nullable List<Completion> completions) throws ParseException {
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
			}

			@Override
			public String type() {return "剩余的输入";}
		};
	}
	static Argument<String[]> restArray() {
		return new Argument<>() {
			@Override
			public String[] parse(CommandParser ctx, @Nullable List<Completion> completions) throws ParseException {
				List<String> tmp = new SimpleList<>();
				while (true) {
					var w = ctx.peekWord();
					if (w == null) break;
					tmp.add(w.val());
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
			public Integer parse(CommandParser ctx, List<Completion> completions) throws ParseException {
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
			public Long parse(CommandParser ctx, List<Completion> completions) throws ParseException {
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
			public Long parse(CommandParser ctx, List<Completion> completions) throws ParseException {
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

			@Override public String type() { return "格式化数据单位"; }
			@Override public int color() {return 0x00CED1;}
		};
	}
	static Argument<Double> real(double min, double max) {
		return new Argument<Double>() {
			@Override
			public Double parse(CommandParser ctx, List<Completion> completions) throws ParseException {
				double val = ctx.nextDouble();
				if (val < min) throw ctx.error("实数过小(范围是["+min+","+max+"])");
				else if (val > max) throw ctx.error("实数过大(范围是["+min+","+max+"])");
				return val;
			}

			@Override public String type() { return "real["+min+","+max+"]"; }
			@Override public int color() {return 0xFFD700;}
		};
	}
	static Argument<Boolean> bool() {
		final MyHashSet<String> truly = new MyHashSet<>("true", "t", "yes", "y"), falsy = new MyHashSet<>("false", "f", "no", "n");
		return new Argument<Boolean>() {
			@Override
			public Boolean parse(CommandParser ctx, List<Completion> completions) throws ParseException {
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

	T parse(CommandParser ctx, @Nullable List<Completion> completions) throws ParseException;
	default void example(List<Completion> completions) {}
	default String type() { return getClass().getSimpleName(); }

	default int color() { return type().hashCode(); }
}