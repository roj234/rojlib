package roj.compiler.resolve;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Member;
import roj.asm.Opcodes;
import roj.collect.ArrayList;
import roj.collect.FlagSet;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.diagnostic.Kind;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/5/12 14:43
 */
public final class ImportList {
	// 在受限导入下也可以允许使用的java.lang包中的类名
	public static final FlagSet DEFAULT_ALLOWLIST = new FlagSet();
	static {
		try (TextReader tr = new TextReader(ImportList.class.getClassLoader().getResourceAsStream("roj/compiler/resolve/Allowlist.cfg"), StandardCharsets.UTF_8)) {
			parseAllowList(tr, DEFAULT_ALLOWLIST);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static void parseAllowList(LineReader lr, FlagSet set) throws IOException {
		var sb = IOUtil.getSharedCharBuf();
		while (lr.readLine(sb)) {
			if (sb.trim().length() == 0 || sb.startsWith("#")) continue;
			int start = 0, end = sb.length();

			int flag;
			boolean inherit = false;
			if (sb.charAt(0) == '-') {
				start++;
				flag = 0;
			} else {
				flag = 1;
			}

			if (sb.endsWith("/**")) {
				end -= 2;
			} else if (sb.endsWith("/*")) {
				sb.set(end-1, '/');
			} else if (sb.endsWith("/")) {
				throw new IllegalArgumentException("cannot end with /");
			}

			set.add(sb.substring(start, end), flag, false, inherit);
		}
	}
	public static FlagSet getDefaultAllowlist() {
		FlagSet flagSet = new FlagSet();
		flagSet.addAll(DEFAULT_ALLOWLIST);
		return flagSet;
	}

	private boolean importAny, resolved;
	private FlagSet restriction;
	private final HashMap<String, Object> importClass = new HashMap<>(), importStatic = new HashMap<>();
	private final ArrayList<String> importPackage = new ArrayList<>(), importStaticClass = new ArrayList<>();
	private final ArrayList<String> importModules = new ArrayList<>();

	public ImportList() {}
	/**
	 * format: [c | alias] = a.b.c
	 */
	public HashMap<String, String> getImportClass() {return Helpers.cast(importClass);}
	/**
	 * format: of = java.util.List
	 */
	public HashMap<String, String> getImportStatic() {return Helpers.cast(importStatic);}
	public List<String> getImportPackage() {return importPackage;}
	public List<String> getImportStaticClass() {return importStaticClass;}
	public List<String> getImportModules() {return importModules;}
	public void setImportAny(boolean importAny) {this.importAny = importAny;}

	@Deprecated(forRemoval = true)
	public void setRestricted(boolean restricted) {
		CompileContext.get().report(Kind.ERROR, "package-restricted指令已移除，请使用新的setRestriction API");
	}

	public FlagSet getRestriction() {return restriction;}
	public void setRestriction(FlagSet restriction) {this.restriction = restriction;}
	public boolean isRestricted() {return restriction != null;}

	public HashMap<String, ClassNode> getImportClassRaw() {return Helpers.cast(importClass);}
	public HashMap<String, CompileContext.Import> getImportStaticRaw() {return Helpers.cast(importStatic);}

	public void clear() {
		importClass.clear();
		importStatic.clear();
		importPackage.clear();
		importStaticClass.clear();
		importAny = false;
		resolved = false;
	}

	public void resolve(CompileContext ctx) {
		if (resolved) return;
		resolved = true;

		var gc = ctx.compiler;
		for (var itr = importClass.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			// 嵌入其它项目中可能会直接操作它
			if (entry.getValue() instanceof ClassNode) continue;

			String name = (String) entry.getValue();
			if (name == null) continue;

			var info = gc.resolve(name);
			if (info == null) info = findChild(ctx, name);
			if (info == null) {
				itr.remove();
				ctx.reportNoSuchType(Kind.ERROR, name);
			} else {
				ctx.canAccessType(info, true);
				entry.setValue(info);
			}
		}

		for (var itr = importStatic.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			// 嵌入其它项目中可能会直接操作它
			if (entry.getValue() instanceof CompileContext.Import) continue;

			String name = (String) entry.getValue();
			if (name == null) continue;

			int pos = name.lastIndexOf('/');

			String thatName = name.substring(pos+1);
			name = name.substring(0, pos);

			var info = gc.resolve(name);
			if (info == null) info = findChild(ctx, name);
			if (info == null) {
				itr.remove();
				ctx.reportNoSuchType(Kind.ERROR, name);
			} else {
				//ctx.canAccessSymbol(info, info.fields.get(info.getField(thatName)), true, true);
				entry.setValue(CompileContext.Import.staticCall(info, thatName));
			}
		}
	}

	// 处理内部类导入，例如import java.util.Map.Entry
	// 这里是从后往前逐渐缩短类名，因为通常包名比内部类名称长的多，这是一个优化
	// 由于顺序与javac相反所以遇到com/example/A$B和com/example/A/B时结果不同
	// 上述为未定义行为，并且不允许编译，唯一能触发是分开编译然后加入classpath
	private static ClassNode findChild(CompileContext ctx, String name) {
		var compiler = ctx.compiler;
		var sb = ctx.getTmpSb();

		sb.append(name);
		int slash = name.length();
		while (true) {
			slash = name.lastIndexOf('/', slash-1);
			if (slash < 0) return null;
			sb.setLength(slash);

			ClassNode owner = compiler.resolve(sb);
			if (owner != null) {
				var innerClass = compiler.getInnerClassInfo(owner).get("!"+name.substring(slash+1));
				if (innerClass != null) {
					ClassNode child = compiler.resolve(innerClass.self);
					if (child == null) {
						ctx.reportNoSuchType(Kind.INTERNAL_ERROR, innerClass.self);
						return null;
					}

					if (ctx.canAccessType(owner, true))
						ctx.canAccessType(child, true);
					return child;
				}
			}
		}
	}

	/**
	 * 将短名称解析为全限定名称
	 */
	public ClassNode resolve(CompileContext ctx, String name) {
		var entry = ctx.importCache.getEntry(name);
		if (entry != null) return entry.getValue();

		// 尝试父类或父接口中的内部类 不过要检测accessible吗？
		// 这优先级真是高
		if (ctx.currentStage > 21) {
			var innerClassItem = ctx.compiler.getInnerClassInfo(ctx.file).get("!"+name);
			if (innerClassItem != null) {
				var info = ctx.compiler.resolve(innerClassItem.self);
				ctx.importCache.put(name, info);
				return info;
			}
		}

		var info = resolve1(ctx, name);

		if (info == null) {
			int slash = name.indexOf('/');
			if (slash >= 0) {
				// 如果以短名称开始
				var starter = resolve1(ctx, name.substring(0, slash));
				if (starter != null) info = ctx.compiler.resolve(starter.name() + name.substring(slash).replace('/', '$'));
				else info = findChild(ctx, name);
			}
		}

		ctx.importCache.put(name, info);
		return info;
	}
	private ClassNode resolve1(CompileContext ctx, String name) {
		// 具名和unimport (delete)
		var entry = importClass.getEntry(name);
		if (entry != null) return (ClassNode) entry.getValue();

		LavaCompiler gc = ctx.compiler;
		ClassNode c;

		// 已经是全限定名
		if ((c = gc.resolve(name)) != null) return c;

		if (name.indexOf('/') >= 0) return null;

		List<String> packages = gc.getPackageNameByShortName(name);

		// 在导入的包中搜索
		String qualifiedName = null;
		for (int i = 0; i < packages.size(); i++) {
			String pkg = packages.get(i);

			if (importPackage.contains(pkg)) {
				if (qualifiedName == null) {
					qualifiedName = pkg+'/'+name;
				} else {
					ctx.report(Kind.WARNING, "import.conflict", pkg, name, packages);
					return null;
				}
			}
		}

		if (qualifiedName == null) {
			// 下列注释中: myName = A$B$C
			String myName = ctx.file.name();

			// 看看是不是对自身的引用
			// A$B$C B$C C => self
			checkSelf:
			if (myName.endsWith(name)) {
				int i = myName.length() - name.length() - 1;
				if (i >= 0) {
					char c1 = myName.charAt(i);
					if (c1 != '/' && c1 != '$') break checkSelf;
				}
				return ctx.file;
			}

			// 再看看是不是内部类
			// A$B B => inner
			CharList sb = IOUtil.getSharedCharBuf().append(myName);
			int i = myName.lastIndexOf('$');

			// longer
			c = gc.resolve(sb.append('$').append(name));
			if (c != null) return c;

			// shorter
			if (i > 0) {
				do {
					sb.setLength(i+1);
					c = gc.resolve(sb.append(name));
					if (c != null) return c;

					i = sb.lastIndexOf("$", i-1);
				} while (i > 0);
			}

			// 最后在当前包查找
			// A => root
			i = myName.lastIndexOf('/')+1;
			if (i > 0) {
				sb.clear();
				c = gc.resolve(sb.append(myName, 0, i).append(name));
				if (c != null) return c;
			}

			// java.lang.* 和 importAny
			if (importAny && (packages.size() == 1/* || onlyOneInsideImportedModules(packages)*/)) qualifiedName = packages.get(0)+"/"+name;
			else if (packages.contains("java/lang")) {
				qualifiedName = "java/lang/"+name;
			}
		}

		if (qualifiedName != null) c = gc.resolve(qualifiedName);
		else /*if (packages == Collections.EMPTY_LIST) */{
			// 有的Library可能没实现content()那就只能慢一些了
			// 而且在最后节约资源也没有必要，因为找不到直接报错了
			for (String pkg : importPackage) {
				if ((c = gc.resolve(pkg+'/'+name)) != null) break;
			}
		}
		return c;
	}

	/**
	 * @return [ConstantData owner, FieldNode node]
	 */
	public CompileContext.Import resolveField(CompileContext ctx, String name, List<FieldNode> out2) {
		var imported = (CompileContext.Import) importStatic.get(name);
		if (imported != null) return imported;

		var cache = ctx.importCacheField;
		if (cache.isEmpty()) {
			cache = new HashMap<>();

			HashSet<String> oneClass = new HashSet<>(), allClass = new HashSet<>();
			for (String klassName : importStaticClass) {
				ClassNode info = ctx.compiler.resolve(klassName);
				for (Member node : info.fields()) {
					if ((node.modifier()&(Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC)) != Opcodes.ACC_STATIC) continue;

					var nodeName = node.name();
					if (oneClass.add(nodeName)) {
						cache.put(nodeName, allClass.add(nodeName) ? new Object[] {info, node} : null);
					}
				}
				oneClass.clear();
			}
		}

		var entry = cache.getEntry(name);
		if (entry != null) {
			Object[] arr = entry.getValue();
			if (arr == null) {
				ctx.report(Kind.ERROR, "memberAccess.incompatible.plural", name, importStaticClass);
				return null;
			}

			if (ctx.canAccessSymbol((ClassNode) arr[0], (Member) arr[1], true, false)) {
				//out2.add((FieldNode) arr[1]);
				return CompileContext.Import.staticCall((ClassNode) arr[0], name);
			}
		}
		return null;
	}

	public CompileContext.Import resolveMethod(CompileContext ctx, String name) {
		var imported = (CompileContext.Import) importStatic.get(name);
		if (imported != null) return imported;

		var cache = ctx.importCacheMethod;
		if (cache.isEmpty()) {
			HashSet<String> oneClass = new HashSet<>(), allClass = new HashSet<>();
			for (String klassName : importStaticClass) {
				ClassNode info = ctx.compiler.resolve(klassName);
				for (Member node : info.methods()) {
					if ((node.modifier()&(Opcodes.ACC_STATIC|Opcodes.ACC_SYNTHETIC|Opcodes.ACC_BRIDGE)) != Opcodes.ACC_STATIC) continue;
					var nodeName = node.name();
					if (nodeName.startsWith("<")) continue;

					if (oneClass.add(nodeName)) {
						cache.put(nodeName, allClass.add(nodeName) ? info : null);
					}
				}
				oneClass.clear();
			}
		}

		var entry = cache.getEntry(name);
		if (entry != null) {
			if (entry.getValue() == null) {
				ctx.report(Kind.ERROR, "invoke.incompatible.plural", name, importStaticClass);
			}

			return CompileContext.Import.staticCall(entry.getValue(), name);
		}
		return null;
	}
}