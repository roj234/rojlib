package roj.plugins.obfuscator.flow;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.asm.ClassNode;
import roj.asm.MemberDescriptor;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.asm.insn.CodeVisitor;
import roj.asmx.Context;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
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
		Map<String, Context> map = new HashMap<>();

		ZipArchive zf = new ZipArchive(new File(args[1]));
		for (ZEntry entry : zf.entries()) {
			if (entry.getName().endsWith(".class")) {
				Context ctx = new Context(entry.getName(), zf.get(entry));
				map.put(ctx.getData().name(), ctx);
			}
		}

		List<String> list = ArrayList.asModifiableList(args);
		list.remove(0);
		list.remove(0);
		for (int i = 0; i < list.size(); i++) {
			list.set(i, list.get(i).replace('.', '/'));
		}

		HashSet<MemberDescriptor> used = new HashSet<>();
		if (System.getProperty("analyze_method") == null) {
			analyzeClass(list, map, used);
		} else {
			analyzeMethod(list, map, used);
		}
		System.out.println(used.size() + " used");

		HashSet<String> removed = new HashSet<>(map.keySet());
		for (MemberDescriptor desc : used) removed.remove(desc.owner);
		System.out.println(removed.size() + " removed");

		for (String name : removed) zf.put(name + ".class", null);
		zf.save();
		zf.close();
	}

	public static void analyzeClass(List<String> entryPoint, Map<String, Context> data, HashSet<MemberDescriptor> used) {
		List<String> pending = new ArrayList<>(entryPoint), next = new ArrayList<>();
		do {
			for (int i = 0; i < pending.size(); i++) {
				Context ctx = data.get(pending.get(i));
				if (ctx != null) analyzeClass0(ctx, next);
			}

			pending.clear();
			for (int i = 0; i < next.size(); i++) {
				if (used.add(new MemberDescriptor(next.get(i), "")))
					pending.add(next.get(i));
			}
		} while (!pending.isEmpty());
	}

	private static void analyzeClass0(Context ctx, List<String> next) {
		List<CstClass> classes = ctx.getClassConstants();
		for (int i = 0; i < classes.size(); i++) {
			next.add(classes.get(i).value().str());
		}
	}

	public static void analyzeMethod(List<String> entryPoint, Map<String, Context> data, HashSet<MemberDescriptor> used) {
		List<MemberDescriptor> pending = new ArrayList<>();
		for (int i = 0; i < entryPoint.size(); i++) {
			pending.add(new MemberDescriptor(entryPoint.get(i), null));
		}

		HashSet<MemberDescriptor> next = new HashSet<>();
		do {
			for (int i = 0; i < pending.size(); i++) {
				MemberDescriptor desc = pending.get(i);
				Context ctx = data.get(desc.owner);
				if (ctx != null) analyzeMethod0(ctx, desc, next);
			}

			pending.clear();
			for (MemberDescriptor desc : next) {
				if (used.add(desc))
					pending.add(desc);
			}
		} while (!pending.isEmpty());
	}

	private static void analyzeMethod0(Context ctx, MemberDescriptor desc, HashSet<MemberDescriptor> next) {
		ClassNode data = ctx.getData();
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			if (desc.name == null || (mn.name().equals(desc.name) && mn.rawDesc().equals(desc.rawDesc))) {
				var attr = mn.getAttribute(data.cp, "Code");
				if (attr == null) {
					if (desc.name != null) break;
					continue;
				}
				System.out.println(mn);

				MyVisitor v = new MyVisitor(desc.owner, next);
				v.visitCopied(data.cp, attr.getRawData());

				if (desc.name != null) break;
			}
		}
	}

	static class MyVisitor extends CodeVisitor {
		MyVisitor(String owner, HashSet<MemberDescriptor> next) {
			self = owner;
			user = next;
		}

		String self;
		HashSet<MemberDescriptor> user;

		@Override
		public void invoke(byte code, CstRef method) {
			if (!method.owner().equals(self)) {
				user.add(new MemberDescriptor().read(method));
			}
		}
	}
}