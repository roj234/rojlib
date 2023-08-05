package roj.mod.mapping;

import roj.asm.cst.CstClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
import roj.asm.visitor.XAttrCode;
import roj.collect.MyHashMap;
import roj.util.Helpers;

import java.util.Collection;
import java.util.List;

/**
 * Merge client and server classes
 *
 * @author Roj234
 * @since 2020/8/30 13:23
 */
public class ClassMerger {
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
		ConstantData subData = sub.getData();
		ConstantData mainData = main.getData();

		List<MethodNode> subMs = subData.methods;
		List<MethodNode> mainMs = mainData.methods;
		outer:
		for (int i = 0; i < subMs.size(); i++) {
			MethodNode sm = subMs.get(i);
			for (int j = 0; j < mainMs.size(); j++) {
				MethodNode mm = mainMs.get(j);
				if (sm.name().equals(mm.name()) && sm.rawDesc().equals(mm.rawDesc())) {
					MethodNode Mm = mm.parsed(mainData.cp);

					MethodNode v = detectPriority(mainData, Mm, subData, sm);
					if (v != Mm) {
						mainMs.set(j, Helpers.cast(v));
					}
					continue outer;
				}
			}
			mergedMethod++;
			mainMs.add(sm.parsed(subData.cp));
		}

		List<FieldNode> subFs = subData.fields;
		List<FieldNode> mainFs = mainData.fields;
		outer:
		for (int i = 0; i < subFs.size(); i++) {
			FieldNode fs = subFs.get(i);
			for (int j = 0; j < mainFs.size(); j++) {
				FieldNode fs2 = mainFs.get(j);
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

	private MethodNode detectPriority(ConstantData cstM, MethodNode major, ConstantData cstS, MethodNode minor) {
		XAttrCode cMinor = minor.parsedAttr(cstS.cp, Attribute.Code);
		if (cMinor == null) return major;
		XAttrCode cMajor = major.parsedAttr(cstM.cp, Attribute.Code);
		if (cMajor == null) return minor;

		if (cMajor.instructions.byteLength() != cMinor.instructions.byteLength()) {
			replaceMethod++;
			//CmdUtil.warning("R/" + cstM.name + '.' + mainMethod.name() + mainMethod.rawDesc());
		}

		// 指令合并太草了
		return cMajor.instructions.byteLength() >= cMinor.instructions.byteLength() ? major : minor.parsed(cstS.cp);
	}

	private void processInnerClasses(ConstantData main, ConstantData sub) {
		List<InnerClasses.InnerClass> scs = AttrHelper.getInnerClasses(sub.cp, sub);
		if (scs == null) return;
		List<InnerClasses.InnerClass> mcs = AttrHelper.getInnerClasses(main.cp, main);
		if (mcs == null) {
			main.putAttr(sub.attrByName("InnerClasses"));
			return;
		}

		for (int i = 0; i < scs.size(); i++) {
			InnerClasses.InnerClass sc = scs.get(i);
			if (!mcs.contains(sc)) mcs.add(sc);
		}
	}

	private void processItf(ConstantData main, ConstantData sub) {
		List<CstClass> mainItf = main.interfaces;
		List<CstClass> subItf = sub.interfaces;

		for (int i = 0; i < subItf.size(); i++) {
			CstClass n = subItf.get(i);
			if (!mainItf.contains(n)) {
				mainItf.add(n);
			}
		}
	}
}