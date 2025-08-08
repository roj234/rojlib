package roj.compiler.resolve;

import roj.asm.*;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.diagnostic.Kind;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * @author Roj234
 * @since 2024/5/12 14:43
 */
public final class ImportList {
	private static final HashSet<String> JAVA_LANG_WHITELIST = new HashSet<>();
	static {
		try(var tr = TextReader.auto(ImportList.class.getClassLoader().getResourceAsStream("roj/compiler/resolve/JLAllow.txt"))) {
			for (var line : tr) JAVA_LANG_WHITELIST.add(line);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean importAny, restricted, inited;
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
	public void setRestricted(boolean restricted) {this.restricted = restricted;}
	public boolean isRestricted() {return restricted;}

	public HashMap<String, Object> getImportClassRaw() {return importClass;}
	public HashMap<String, Object> getImportStaticRaw() {return importStatic;}

	public void clear() {
		importClass.clear();
		importStatic.clear();
		importPackage.clear();
		importStaticClass.clear();
		importAny = false;
		restricted = false;
		inited = false;
	}

	// 多线程环境
	public void init(CompileContext ctx) {
		if (inited) return;
		synchronized (this) {
			if (inited) return;
			inited = true;
		}

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
			}
			else entry.setValue(info);
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
				entry.setValue(CompileContext.Import.staticCall(info, thatName));
			}
		}
	}

	// 处理内部类导入，例如import java.util.Map.Entry
	// TODO 正确的方案是找到第一个类，然后在其InnerClass属性中查找子类
	//  roj.compiler.context.CompileContext.resolveDotGet使用了类似的逻辑
	private static ClassNode findChild(CompileContext ctx, String name) {
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			CharList sb = ctx.getTmpSb();
			sb.append(name);

			do {
				sb.set(slash, '$');

				ClassNode info = ctx.compiler.resolve(sb);
				if (info != null) return info;

				slash = sb.lastIndexOf("/", slash-1);
			} while (slash >= 0);
		}

		return null;
	}

	/**
	 * 将短名称解析为全限定名称
	 */
	public ClassNode resolve(CompileContext ctx, String name) {
		var entry = ctx.importCache.getEntry(name);
		if (entry != null) return entry.getValue();

		var info = resolve1(ctx, name);
		if (info == null) {
			int slash = name.indexOf('/');
			if (slash >= 0) {
				// 如果以短名称开始
				var starter = resolve1(ctx, name.substring(0, slash));
				if (starter != null) info = ctx.compiler.resolve(starter.name() + name.substring(slash).replace('/', '$'));
				else info = findChild(ctx, name);

				if (restricted && info != null) info = checkRestriction(info);
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
		if ((c = gc.resolve(name)) != null) return restricted ? checkRestriction(c) : c;

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
				if (restricted) return checkRestriction(gc.resolve(qualifiedName));
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

	public ClassNode checkRestriction(ClassNode c) {
		if (importClass.containsValue(c)) return c;
		int index = c.name().lastIndexOf('/');
		if (index > 0) {
			String pkg = c.name().substring(0, index);
			if (importPackage.contains(pkg)) return c;

			String name = c.name().substring(index + 1);
			if (pkg.equals("java/lang")) {
				if (JAVA_LANG_WHITELIST.contains(name)) return c;
				if (name.equals("System")) {
					c = ClassNode.parseSkeleton(AsmCache.toByteArray(c));
					c.fields.removeIf(field -> !field.name().equals("out") && !field.name().equals("err"));
					c.methods.removeIf(method -> !method.name().equals("currentTimeMillis") && !method.name().equals("nanoTime") && !method.name().equals("arraycopy"));
					return c;
				}
			}
		}
		return null;
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
				ctx.report(Kind.ERROR, "dotGet.incompatible.plural", name, importStaticClass);
				return null;
			}

			if (ctx.checkAccessible((ClassNode) arr[0], (Member) arr[1], true, false)) {
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