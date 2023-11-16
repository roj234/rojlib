package roj.ui.terminal;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.reflect.EnumHelper;
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
		};
	}
	static Argument<String> string() { return (context, b) -> context.nextString(); }
	static Argument<String> string(String... selection) {
		MyHashMap<String, String> map = new MyHashMap<>(selection.length);
		for (String s : selection) map.put(s,s);
		return setOf(map);
	}
	static <T extends Enum<T>> Argument<T> enumeration(Class<T> type) { return setOf(Helpers.cast(EnumHelper.cDirAcc.enumConstantDirectory(type))); }
	static <T> Argument<T> setOf(Map<String, T> map) {
		return new Argument<T>() {
			@Override
			public T parse(ArgumentContext ctx, boolean complete) throws ParseException {
				String val = ctx.nextString();
				T result = map.get(val);
				if (result == null) throw ctx.error("枚举不存在");
				return result;
			}

			@Override
			public void complete(ArgumentContext ctx, List<Completion> completions) throws ParseException {
				String val = ctx.nextString();
				for (String name : map.keySet()) {
					if (name.startsWith(val)) completions.add(new Completion(name));
				}
			}

			@Override
			public void example(List<Completion> completions) { for (String name : map.keySet()) completions.add(new Completion(name)); }
		};
	}
	static Argument<Integer> number(int min, int max) {
		return (ctx, b) -> {
			int val = ctx.nextInt();
			if (val < min) throw ctx.error("整数过小(可用的范围是["+min+","+max+"])");
			else if (val > max) throw ctx.error("整数过大(可用的范围是["+min+","+max+"])");
			return val;
		};
	}
	static Argument<Double> real(double min, double max) {
		return (ctx, b) -> {
			double val = ctx.nextDouble();
			if (val < min) throw ctx.error("实数过小(可用的范围是["+min+","+max+"])");
			else if (val > max) throw ctx.error("实数过大(可用的范围是["+min+","+max+"])");
			return val;
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
				if ("true".startsWith(s)) completions.add(new Completion("true"));
				if ("false".startsWith(s)) completions.add(new Completion("false"));
			}

			@Override
			public void example(List<Completion> completions) {
				completions.add(new Completion("true"));
				completions.add(new Completion("false"));
			}
		};
	}

	T parse(ArgumentContext ctx, boolean complete) throws ParseException;
	default void complete(ArgumentContext ctx, List<Completion> completions) throws ParseException {}
	default void example(List<Completion> completions) {}
}
