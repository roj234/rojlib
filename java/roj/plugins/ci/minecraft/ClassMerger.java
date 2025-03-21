package roj.plugins.ci.minecraft;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.attr.InnerClasses;
import roj.asm.util.Context;
import roj.collect.MyHashMap;

import java.util.Collection;
import java.util.List;

/**
 * Merge client and server classes
 *
 * @author Roj234
 * @since 2020/8/30 13:23
 */
final class ClassMerger {
	public int serverOnly, clientOnly, both;
	public int mergedField, mergedMethod, replaceMethod;

	public Collection<Context> process(List<Context> main, List<Context> sub) {
		MyHashMap<String, Context> byName = new MyHashMap<>();
		for (int i = 0; i < main.size(); i++) {
			Context ctx = main.get(i);
			byName.put(ctx.getFileName(), ctx);
		}

		clientOnly = main.size();
		serverOnly = sub.size();

		for (int i = 0; i < sub.size(); i++) {
			Context sc = sub.get(i);

			Context mc = byName.putIfAbsent(sc.getFileName(), sc);
			if (mc != null) {
				processOne(mc, sc);
				clientOnly--;
				serverOnly--;
				both++;
			}
		}

		return byName.values();
	}

	private void processOne(Context main, Context sub) {
		ClassNode subData = sub.getData();
		ClassNode mainData = main.getData();

		var subMs = subData.methods;
		var mainMs = mainData.methods;
		outer:
		for (int i = 0; i < subMs.size(); i++) {
			var sm = subMs.get(i);
			for (int j = 0; j < mainMs.size(); j++) {
				var mm = mainMs.get(j);
				if (sm.name().equals(mm.name()) && sm.rawDesc().equals(mm.rawDesc())) {
					mainMs.set(j, detectPriority(mainData, mm, subData, sm));
					continue outer;
				}
			}
			mergedMethod++;
			mainMs.add(sm.parsed(subData.cp));
		}

		var subFs = subData.fields;
		var mainFs = mainData.fields;
		outer:
		for (int i = 0; i < subFs.size(); i++) {
			var fs = subFs.get(i);
			for (int j = 0; j < mainFs.size(); j++) {
				var fs2 = mainFs.get(j);
				if (fs.name().equals(fs2.name()) && fs.rawDesc().equals(fs2.rawDesc())) {
					continue outer;
				}
			}
			mergedField++;
			mainData.fields.add(fs.parsed(subData.cp));
		}

		processInnerClasses(mainData, subData);
		processItf(mainData, subData);
	}

	private MethodNode detectPriority(ClassNode main, MethodNode mainMethod, ClassNode sub, MethodNode subMethod) {
		Attribute subCode = subMethod.getRawAttribute("Code");
		if (subCode == null) return mainMethod;
		Attribute mainCode = mainMethod.getRawAttribute("Code");
		if (mainCode == null) return subMethod;

		var mainRealCode = mainMethod.getAttribute(main.cp, Attribute.Code);
		var subRealCode = mainMethod.getAttribute(sub.cp, Attribute.Code);

		if (mainRealCode.instructions.bci() != subRealCode.instructions.bci()) {
			replaceMethod++;
		}

		return mainRealCode.instructions.bci() >= subRealCode.instructions.bci() ? mainMethod : subMethod;
	}

	private void processInnerClasses(ClassNode main, ClassNode sub) {
		List<InnerClasses.Item> scs = sub.getInnerClasses();
		if (scs == null) return;
		List<InnerClasses.Item> mcs = main.getInnerClasses();
		if (mcs == null) {
			main.addAttribute(sub.getRawAttribute("InnerClasses"));
			return;
		}

		for (int i = 0; i < scs.size(); i++) {
			var sc = scs.get(i);
			if (!mcs.contains(sc)) mcs.add(sc);
		}
	}

	private void processItf(ClassNode main, ClassNode sub) {
		var mainItf = main.itfList();
		var subItf = sub.itfList();

		for (int i = 0; i < subItf.size(); i++) {
			var n = subItf.get(i);
			if (!mainItf.contains(n)) {
				mainItf.add(n);
			}
		}
	}
}