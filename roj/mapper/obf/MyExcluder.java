package roj.mapper.obf;

import roj.asm.Opcodes;
import roj.asm.cst.Constant;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstRef;
import roj.asm.cst.CstString;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.util.AccessFlag;
import roj.asm.util.Context;
import roj.asm.visitor.CodeVisitor;
import roj.collect.SimpleList;
import roj.mapper.util.Desc;

import java.util.List;

/**
 * 排除常见的不能混淆的情况
 *
 * @author Roj233
 * @since 2022/2/20 22:18
 */
public class MyExcluder extends CodeVisitor {
	public static boolean isClassExclusive(IClass clz, Desc desc) {
		if (0 != (clz.modifier() & AccessFlag.ENUM)) {
			if (desc.name.equals("values")) return desc.param.startsWith("()");
			if (desc.name.equals("valueOf")) return desc.param.startsWith("(Ljava/lang/String;)");
			if (desc.name.equals("VALUES")) return desc.param.startsWith("L");
		}
		return false;
	}

	public static List<Desc> checkAtomics(List<Context> arr) {
		MyExcluder cv = new MyExcluder();
		for (int i = 0; i < arr.size(); i++) {
			ConstantData data = arr.get(i).getData();
			List<? extends MethodNode> methods = data.methods;

			for (int j = 0; j < methods.size(); j++) {
				AttrUnknown code = (AttrUnknown) methods.get(j).attrByName("Code");
				if (code == null) continue;
				cv.visitCopied(data.cp, code.getRawData());
			}
		}
		return cv.excludes;
	}

	List<Desc> excludes = new SimpleList<>();
	String ldcVal1, ldcVal2;
	int ldcPos;
	int stack;

	@Override
	public void ldc(byte code, Constant c) {
		switch (c.type()) {
			case Constant.STRING:
				ldcPos = bci;
				ldcVal1 = ((CstString) c).name().str();
				stack = 2;
				break;
			case Constant.CLASS:
				if (stack-- > 0) return;
				ldcVal2 = ((CstClass) c).name().str();
				break;
		}
	}

	@Override
	public void invoke(byte code, CstRef method) {
		String name = method.className();
		if (code == Opcodes.INVOKESTATIC && name.startsWith("java/util/concurrent/atomic/Atomic") && name.endsWith("FieldUpdater") && bci == ldcPos + 3) {
			excludes.add(new Desc(ldcVal2, ldcVal1));
		}
	}
}
