package roj.plugins.obfuscator.flow;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.MethodNode;
import roj.asm.attr.AttrUnknown;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.asm.insn.CodeVisitor;
import roj.asm.type.Desc;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.io.IOUtil;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * 静态分析方法调用, 无法处理反射
 *
 * @author Roj233
 * @since 2022/2/20 1:34
 */
public class StaticAnalyzer {
	public static void main(String[] args) throws Exception {
		IOUtil.copyFile(new File(args[0]), new File(args[1]));
		Map<String, Context> map = new MyHashMap<>();

		ZipArchive zf = new ZipArchive(new File(args[1]));
		for (ZEntry entry : zf.entries()) {
			if (entry.getName().endsWith(".class")) {
				Context ctx = new Context(entry.getName(), zf.get(entry));
				map.put(ctx.getData().name(), ctx);
			}
		}

		List<String> list = SimpleList.asModifiableList(args);
		list.remove(0);
		list.remove(0);
		for (int i = 0; i < list.size(); i++) {
			list.set(i, list.get(i).replace('.', '/'));
		}

		MyHashSet<Desc> used = new MyHashSet<>();
		if (System.getProperty("analyze_method") == null) {
			analyzeClass(list, map, used);
		} else {
			analyzeMethod(list, map, used);
		}
		System.out.println(used.size() + " used");

		MyHashSet<String> removed = new MyHashSet<>(map.keySet());
		for (Desc desc : used) removed.remove(desc.owner);
		System.out.println(removed.size() + " removed");

		for (String name : removed) zf.put(name + ".class", null);
		zf.save();
		zf.close();
	}

	public static void analyzeClass(List<String> entryPoint, Map<String, Context> data, MyHashSet<Desc> used) {
		List<String> pending = new SimpleList<>(entryPoint), next = new SimpleList<>();
		do {
			for (int i = 0; i < pending.size(); i++) {
				Context ctx = data.get(pending.get(i));
				if (ctx != null) analyzeClass0(ctx, next);
			}

			pending.clear();
			for (int i = 0; i < next.size(); i++) {
				if (used.add(new Desc(next.get(i), "")))
					pending.add(next.get(i));
			}
		} while (!pending.isEmpty());
	}

	private static void analyzeClass0(Context ctx, List<String> next) {
		List<CstClass> classes = ctx.getClassConstants();
		for (int i = 0; i < classes.size(); i++) {
			next.add(classes.get(i).name().str());
		}
	}

	public static void analyzeMethod(List<String> entryPoint, Map<String, Context> data, MyHashSet<Desc> used) {
		List<Desc> pending = new SimpleList<>();
		for (int i = 0; i < entryPoint.size(); i++) {
			pending.add(new Desc(entryPoint.get(i), null));
		}

		MyHashSet<Desc> next = new MyHashSet<>();
		do {
			for (int i = 0; i < pending.size(); i++) {
				Desc desc = pending.get(i);
				Context ctx = data.get(desc.owner);
				if (ctx != null) analyzeMethod0(ctx, desc, next);
			}

			pending.clear();
			for (Desc desc : next) {
				if (used.add(desc))
					pending.add(desc);
			}
		} while (!pending.isEmpty());
	}

	private static void analyzeMethod0(Context ctx, Desc desc, MyHashSet<Desc> next) {
		List<? extends MethodNode> methods = ctx.getData().methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			if (desc.name == null || (mn.name().equals(desc.name) && mn.rawDesc().equals(desc.param))) {
				AttrUnknown attr = (AttrUnknown) mn.attrByName("Code");
				if (attr == null) {
					if (desc.name != null) break;
					continue;
				}
				System.out.println(mn);

				MyVisitor v = new MyVisitor(desc.owner, next);
				v.visitCopied(ctx.getData().cp, attr.getRawData());

				if (desc.name != null) break;
			}
		}
	}

	static class MyVisitor extends CodeVisitor {
		MyVisitor(String owner, MyHashSet<Desc> next) {
			self = owner;
			user = next;
		}

		String self;
		MyHashSet<Desc> user;

		@Override
		public void invoke(byte code, CstRef method) {
			if (!method.className().equals(self)) {
				user.add(new Desc().read(method));
			}
		}
	}
}