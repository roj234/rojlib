package roj.mod.mapping;

import roj.asm.cst.CstClass;
import roj.asm.tree.*;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.util.AttrHelper;
import roj.asm.util.Context;
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

		List<? extends MoFNode> subMs = subData.methods;
		List<? extends MoFNode> mainMs = mainData.methods;
		outer:
		for (int i = 0; i < subMs.size(); i++) {
			MoFNode sm = subMs.get(i);
			for (int j = 0; j < mainMs.size(); j++) {
				MoFNode mm = mainMs.get(j);
				if (sm.name().equals(mm.name()) && sm.rawDesc().equals(mm.rawDesc())) {
					Method Mm = mm instanceof Method ? (Method) mm : new Method(mainData, (RawMethod) mm);

					Method v = detectPriority(mainData, Mm, subData, sm);
					if (v != Mm) {
						mainMs.set(j, Helpers.cast(v));
					}
					continue outer;
				}
			}
			mergedMethod++;
			mainMs.add(Helpers.cast(sm instanceof Method ? sm : new Method(subData, (RawMethod) sm)));
		}

		List<? extends MoFNode> subFs = subData.fields;
		List<? extends MoFNode> mainFs = mainData.fields;
		outer:
		for (int i = 0; i < subFs.size(); i++) {
			MoFNode fs = subFs.get(i);
			for (int j = 0; j < mainFs.size(); j++) {
				MoFNode fs2 = mainFs.get(j);
				if (fs.name().equals(fs2.name()) && fs.rawDesc().equals(fs2.rawDesc())) {
					continue outer;
				}
			}
			mergedField++;
			mainData.fields.add(Helpers.cast(fs instanceof Field ? fs : new Field(subData, (RawField) fs)));
		}

		processInnerClasses(mainData, subData);
		processItf(mainData, subData);
	}

	private Method detectPriority(ConstantData cstM, Method mainMethod, ConstantData cstS, MoFNode sub) {
		Method subMethod = sub instanceof Method ? (Method) sub : new Method(cstS, (RawMethod) sub);

		if (subMethod.getCode() == null) return mainMethod;
		if (mainMethod.getCode() == null) return subMethod;

		if (mainMethod.getCode().instructions.size() != subMethod.getCode().instructions.size()) {
			replaceMethod++;

			//CmdUtil.warning("R/" + cstM.name + '.' + mainMethod.name() + mainMethod.rawDesc());
		}

		// 指令合并太草了
		if (mainMethod.getCode().instructions.size() >= subMethod.getCode().instructions.size()) {
			return mainMethod;
		}
		return subMethod;
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