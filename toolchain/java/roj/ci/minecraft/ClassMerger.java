package roj.ci.minecraft;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.attr.Attribute;
import roj.asm.attr.InnerClasses;
import roj.asmx.Context;
import roj.collect.HashMap;

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
		HashMap<String, Context> byName = new HashMap<>();
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
		ClassNode base = main.getData();
		ClassNode extra = sub.getData();

		var extraMethods = extra.methods;
		var baseMethods = base.methods;

		for (int i = 0; i < extraMethods.size(); i++) {
			var sm = extraMethods.get(i);
			int j = base.getMethod(sm.name(), sm.rawDesc());
			if (j >= 0) {
				baseMethods.set(j, detectPriority(base, baseMethods.get(j), extra, sm));
			} else {
				mergedMethod++;
				baseMethods.add(sm.parsed(extra.cp));
			}
		}

		var extraFields = extra.fields;
		var baseFields = base.fields;
		outer:
		for (int i = 0; i < extraFields.size(); i++) {
			var fs = extraFields.get(i);
			for (int j = 0; j < baseFields.size(); j++) {
				var fs2 = baseFields.get(j);
				if (fs.name().equals(fs2.name()) && fs.rawDesc().equals(fs2.rawDesc())) {
					continue outer;
				}
			}
			mergedField++;
			base.fields.add(fs.parsed(extra.cp));
		}

		processInnerClasses(base, extra);
		processItf(base, extra);
	}

	private MethodNode detectPriority(ClassNode base, MethodNode baseMethod, ClassNode extra, MethodNode extraMethod) {
		if (extraMethod.getAttribute("Code") == null) return baseMethod;
		if (baseMethod.getAttribute("Code") == null) return extraMethod;

		var baseCode = baseMethod.getAttribute(base.cp, Attribute.Code);
		var extraCode = extraMethod.getAttribute(extra.cp, Attribute.Code);

		if (baseCode.instructions.length() != extraCode.instructions.length()) {
			replaceMethod++;
		}

		return baseCode.instructions.length() >= extraCode.instructions.length() ? baseMethod : extraMethod;
	}

	private void processInnerClasses(ClassNode main, ClassNode sub) {
		List<InnerClasses.Item> scs = sub.getInnerClasses();
		if (scs == null) return;
		List<InnerClasses.Item> mcs = main.getInnerClasses();
		if (mcs == null) {
			main.addAttribute(sub.getAttribute("InnerClasses"));
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