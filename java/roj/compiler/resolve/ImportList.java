package roj.compiler.resolve;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.RawNode;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextReader;
import roj.util.Helpers;

import java.io.IOException;
import java.util.List;

/**
 * @author Roj234
 * @since 2024/5/12 0012 14:43
 */
public final class ImportList {
	private static final MyHashSet<String> JAVA_LANG_WHITELIST = new MyHashSet<>();
	static {
		try(var tr = TextReader.auto(ImportList.class.getClassLoader().getResourceAsStream("roj/compiler/resolve/JLAllow.txt"))) {
			for (var line : tr) JAVA_LANG_WHITELIST.add(line);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private boolean importAny, restricted, inited;
	private final MyHashMap<String, Object> importClass = new MyHashMap<>(), importStatic = new MyHashMap<>();
	private final SimpleList<String> importPackage = new SimpleList<>(), importStaticClass = new SimpleList<>();
	private final SimpleList<String> importModules = new SimpleList<>();

	public ImportList() {}
	/**
	 * format: [c | alias] = a.b.c
	 */
	public MyHashMap<String, String> getImportClass() {return Helpers.cast(importClass);}
	/**
	 * format: of = java.util.List
	 */
	public MyHashMap<String, String> getImportStatic() {return Helpers.cast(importStatic);}
	public List<String> getImportPackage() {return importPackage;}
	public List<String> getImportStaticClass() {return importStaticClass;}
	public List<String> getImportModules() {return importModules;}
	public void setImportAny(boolean importAny) {this.importAny = importAny;}
	public void setRestricted(boolean restricted) {this.restricted = restricted;}
	public boolean isRestricted() {return restricted;}

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
	public void init(LocalContext ctx) {
		if (inited) return;
		synchronized (this) {
			if (inited) return;
			inited = true;
		}

		resolveImport(ctx, importClass);
		resolveImport(ctx, importStatic);
	}
	private void resolveImport(LocalContext ctx, MyHashMap<String, Object> importList) {
		var gc = ctx.classes;
		for (var itr = importList.entrySet().iterator(); itr.hasNext(); ) {
			var entry = itr.next();
			String name = (String) entry.getValue();
			if (name == null) continue;

			var info = gc.getClassInfo(name);
			if (info == null) info = fixStaticImport(ctx, name);
			if (info == null) {
				itr.remove();
				ctx.report(Kind.ERROR, "symbol.error.noSuchClass", name);
			}
			else entry.setValue(info);
		}
	}
	// 处理内部类导入，例如import java.util.Map.Entry
	// TODO 正确的方案不是我这样的，应该找到正数第一个类，然后在其InnerClass属性中查找子类
	private static ClassNode fixStaticImport(LocalContext ctx, String name) {
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			CharList sb = ctx.getTmpSb();
			sb.append(name);

			do {
				sb.set(slash, '$');

				ClassNode info = ctx.classes.getClassInfo(sb);
				if (info != null) return info;

				slash = sb.lastIndexOf("/", slash-1);
			} while (slash >= 0);
		}

		return null;
	}

	/**
	 * 将短名称解析为全限定名称
	 */
	public ClassNode resolve(LocalContext ctx, String name) {
		var entry = ctx.importCache.getEntry(name);
		if (entry != null) return entry.getValue();

		var info = resolve1(ctx, name);
		if (info == null) {
			int slash = name.indexOf('/');
			if (slash >= 0) {
				// 如果以短名称开始
				var starter = resolve1(ctx, name.substring(0, slash));
				if (starter != null) info = ctx.classes.getClassInfo(starter.name() + name.substring(slash).replace('/', '$'));
				else info = fixStaticImport(ctx, name);

				if (restricted && info != null) info = checkRestriction(info);
			}
		}

		ctx.importCache.put(name, info);
		return info;
	}
	private ClassNode resolve1(LocalContext ctx, String name) {
		// 具名和unimport (delete)
		var entry = importClass.getEntry(name);
		if (entry != null) return (ClassNode) entry.getValue();

		GlobalContext gc = ctx.classes;
		ClassNode c;

		// 已经是全限定名
		if ((c = gc.getClassInfo(name)) != null) return restricted ? checkRestriction(c) : c;

		if (name.indexOf('/') >= 0) return null;

		List<String> packages = gc.getAvailablePackages(name);

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

		block:
		if (qualifiedName == null) {
			if (restricted) {
				if (JAVA_LANG_WHITELIST.contains(name) && packages.contains("java/lang")) {
					qualifiedName = "java/lang/"+name;
				}
				break block;
			}
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
			c = gc.getClassInfo(sb.append('$').append(name));
			if (c != null) return c;

			// shorter
			if (i > 0) {
				do {
					sb.setLength(i+1);
					c = gc.getClassInfo(sb.append(name));
					if (c != null) return c;

					i = sb.lastIndexOf("$", i-1);
				} while (i > 0);
			}

			// 最后在当前包查找
			// A => root
			i = myName.lastIndexOf('/')+1;
			if (i > 0) {
				sb.clear();
				c = gc.getClassInfo(sb.append(myName, 0, i).append(name));
				if (c != null) return c;
			}

			// java.lang.* 和 importAny
			if (importAny && (packages.size() == 1/* || onlyOneInsideImportedModules(packages)*/)) qualifiedName = packages.get(0)+"/"+name;
			else if (packages.contains("java/lang")) qualifiedName = "java/lang/"+name;
		}

		if (qualifiedName != null) c = gc.getClassInfo(qualifiedName);
		else /*if (packages == Collections.EMPTY_LIST) */{
			// 有的Library可能没实现content()那就只能慢一些了
			// 而且在最后节约资源也没有必要，因为找不到直接报错了
			for (String pkg : importPackage) {
				if ((c = gc.getClassInfo(pkg+'/'+name)) != null) break;
			}
		}
		return c;
	}

	private ClassNode checkRestriction(ClassNode c) {
		if (importClass.containsValue(c.name())) return c;
		int index = c.name().lastIndexOf('/');
		if (index > 0 && importPackage.contains(c.name().substring(index))) return c;
		return null;
	}

	/**
	 * @return [ConstantData owner, FieldNode node]
	 */
	public ClassNode resolveField(LocalContext ctx, String name, List<FieldNode> out2) {
		var imported = (ClassNode) importStatic.get(name);
		if (imported != null && imported.getField(name) >= 0) return imported;

		var fieldCache = ctx.importCacheField;
		if (fieldCache.isEmpty()) {
			fieldCache = new MyHashMap<>();

			MyHashSet<String> oneClass = new MyHashSet<>(), allClass = new MyHashSet<>();
			for (String klassName : importStaticClass) {
				ClassNode info = ctx.classes.getClassInfo(klassName);
				for (RawNode node : info.fields()) {
					if ((node.modifier()&Opcodes.ACC_STATIC) == 0) continue;

					name = node.name();
					if (oneClass.add(name)) {
						fieldCache.put(name, allClass.add(name) ? new Object[] {info, node} : null);
					}
				}
				oneClass.clear();
			}
		}

		var entry = fieldCache.getEntry(name);
		if (entry != null) {
			Object[] arr = entry.getValue();
			if (arr == null) {
				ctx.report(Kind.ERROR, "dotGet.incompatible.plural", name, importStaticClass);
				return null;
			}

			if (ctx.checkAccessible((ClassNode) arr[0], (RawNode) arr[1], true, false)) {
				//out2.add((FieldNode) arr[1]);
				return (ClassNode) arr[0];
			}
		}
		return null;
	}

	/**
	 * @return [ConstantData owner]
	 */
	public ClassNode resolveMethod(LocalContext ctx, String name) {
		ClassNode imported = (ClassNode) importStatic.get(name);
		if (imported != null && imported.getMethod(name) >= 0) return imported;

		var methodCache = ctx.importCacheMethod;
		if (methodCache.isEmpty()) {
			MyHashSet<String> oneClass = new MyHashSet<>(), allClass = new MyHashSet<>();
			for (String klassName : importStaticClass) {
				ClassNode info = ctx.classes.getClassInfo(klassName);
				for (RawNode mn : info.methods()) {
					if ((mn.modifier()&Opcodes.ACC_STATIC) == 0) continue;

					name = mn.name();
					if (oneClass.add(name)) {
						methodCache.put(name, allClass.add(name) ? info : null);
					}
				}
				oneClass.clear();
			}
		}

		var entry = methodCache.getEntry(name);
		if (entry != null) {
			if (entry.getValue() == null) {
				ctx.report(Kind.ERROR, "invoke.incompatible.plural", name, importStaticClass);
			}

			return entry.getValue();
		}
		return null;
	}
}