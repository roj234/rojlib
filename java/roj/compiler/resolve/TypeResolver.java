package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.RawNode;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/5/12 0012 14:43
 */
public final class TypeResolver {
	private boolean importAny, restricted, inited;
	private final MyHashMap<String, Object> importClass = new MyHashMap<>(), importStatic = new MyHashMap<>();
	private final SimpleList<String> importPackage = new SimpleList<>(), importStaticClass = new SimpleList<>();

	public TypeResolver() {}

	public MyHashMap<String, String> getImportClass() {return Helpers.cast(importClass);}
	public MyHashMap<String, String> getImportStatic() {return Helpers.cast(importStatic);}
	public List<String> getImportPackage() {return importPackage;}
	public List<String> getImportStaticClass() {return importStaticClass;}
	public void setImportAny(boolean importAny) {this.importAny = importAny;}
	public void setRestricted(boolean restricted) {this.restricted = restricted;}

	public void clear() {
		importClass.clear();
		importStatic.clear();
		importPackage.clear();
		importStaticClass.clear();
		importAny = false;
		restricted = false;
		inited = false;
	}

	public void init(LocalContext ctx) {
		if (inited) return;
		inited = true;

		GlobalContext gc = ctx.classes;

		for (Map.Entry<String, Object> entry : importClass.entrySet()) {
			String name = (String) entry.getValue();
			if (name == null) continue;

			IClass info = gc.getClassInfo(name);
			if (info == null) info = fixStaticImport(ctx, name);
			if (info == null) ctx.report(Kind.ERROR, "symbol.error.noSuchClass", name);
			else entry.setValue(info);
		}

		for (Map.Entry<String, Object> entry : importStatic.entrySet()) {
			String name = (String) entry.getValue();
			if (name == null) continue;

			IClass info = gc.getClassInfo(name);
			if (info == null) info = fixStaticImport(ctx, name);
			if (info == null) ctx.report(Kind.ERROR, "symbol.error.noSuchClass", name);
			else entry.setValue(info);
		}
	}
	private IClass fixStaticImport(LocalContext ctx, String name) {
		// import java.util.Map.Entry
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			CharList sb = ctx.tmpList;
			sb.clear();
			sb.append(name);

			do {
				sb.set(slash, '$');

				IClass info = ctx.classes.getClassInfo(sb);
				if (info != null) return info;

				slash = sb.lastIndexOf("/", slash-1);
			} while (slash >= 0);
		}

		return null;
	}

	/**
	 * 将短名称解析为全限定名称
	 */
	public IClass resolve(LocalContext ctx, String name) {
		var entry = ctx.importCache.getEntry(name);
		if (entry != null) return entry.getValue();

		IClass info = resolve1(ctx, name);
		block:
		if (info == null) {
			// import java.util.Map
			// then Map.Entry
			int slash = name.indexOf('/');
			if (slash >= 0) {
				var entry1 = importClass.getEntry(name);
				if (entry1 == null || entry1.getValue() == null) break block;

				String _name = ((IClass) entry1.getValue()).name();
				info = ctx.classes.getClassInfo(_name + name.substring(slash).replace('/', '$'));
			}
		}

		ctx.importCache.put(name, info);
		return info;
	}
	private IClass resolve1(LocalContext ctx, String name) {
		// 具名和unimported
		var entry = importClass.getEntry(name);
		if (entry != null) return (IClass) entry.getValue();

		if (restricted) return null;

		GlobalContext gc = ctx.classes;
		IClass c = gc.getClassInfo(name);
		// 已经是全限定名
		if (c != null) return c;

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

		if (qualifiedName == null) {
			// 在当前包搜索 (此处包括自身)
			String myName = ctx.file.name;
			c = gc.getClassInfo(IOUtil.getSharedCharBuf().append(myName, 0, myName.lastIndexOf('/')+1).append(name));
			if (c != null) return c;

			if (packages.contains("java/lang")) qualifiedName = "java/lang/"+name;
			else if (importAny && packages.size() == 1) qualifiedName = packages.get(0)+"/"+name;
		}

		if (qualifiedName != null) c = gc.getClassInfo(qualifiedName);
		return c;
	}

	/**
	 * @return [IClass owner, FieldNode node]
	 */
	public IClass resolveField(LocalContext ctx, String name, List<FieldNode> out2) {
		IClass imported = (IClass) importStatic.get(name);
		if (imported != null) {
			int fid = imported.getField(name);
			if (fid < 0) return null;
			RawNode node = imported.fields().get(fid);

			if ((node.modifier() & Opcodes.ACC_STATIC) != 0 && ctx.checkAccessible(imported, node, true, false)) {
				out2.add((FieldNode) node);
				return imported;
			}
			return null;
		}

		var fieldCache = ctx.importCacheField;
		if (fieldCache.isEmpty()) {
			fieldCache = new MyHashMap<>();

			MyHashSet<String> oneClass = new MyHashSet<>(), allClass = new MyHashSet<>();
			for (String klassName : importStaticClass) {
				IClass info = ctx.classes.getClassInfo(klassName);
				if (info == null) {
					ctx.report(Kind.ERROR, "symbol.error.noSuchClass", klassName);
					continue;
				}

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

			if (ctx.checkAccessible((IClass) arr[0], (RawNode) arr[1], true, false)) {
				out2.add((FieldNode) arr[1]);
				return (IClass) arr[0];
			}
		}
		return null;
	}

	/**
	 * @return [IClass owner]
	 */
	public IClass resolveMethod(LocalContext ctx, String name) {
		IClass imported = (IClass) importStatic.get(name);
		if (imported != null) return imported;

		var methodCache = ctx.importCacheMethod;
		if (methodCache.isEmpty()) {
			MyHashSet<String> oneClass = new MyHashSet<>(), allClass = new MyHashSet<>();
			for (String klassName : importStaticClass) {
				IClass info = ctx.classes.getClassInfo(klassName);
				if (info == null) {
					ctx.report(Kind.ERROR, "symbol.error.noSuchClass", klassName);
					continue;
				}

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