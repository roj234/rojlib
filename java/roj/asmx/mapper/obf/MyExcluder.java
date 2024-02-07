package roj.asmx.mapper.obf;

import roj.asm.Opcodes;
import roj.asm.cp.Constant;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstRef;
import roj.asm.cp.CstString;
import roj.asm.type.Desc;
import roj.asm.visitor.CodeVisitor;
import roj.collect.SimpleList;

import java.util.List;

/**
 * 排除常见的不能混淆的情况
 * TODO: StackAnalyzer
 *
 * @author Roj233
 * @since 2022/2/20 22:18
 */
public class MyExcluder extends CodeVisitor {
	public List<Desc> excludes = new SimpleList<>();

	private final List<String> ldc = new SimpleList<>();
	private int ldcPos;

	@Override
	public void ldc(byte code, Constant c) {
		switch (c.type()) {
			case Constant.STRING:
				ldc.add(((CstString) c).name().str());
				ldcPos = bci;
			break;
			case Constant.CLASS:
				ldc.add(((CstClass) c).name().str());
				ldcPos = bci;
			break;
		}
	}

	@Override
	public void invoke(byte code, CstRef method) {
		String owner = method.className();
		String name = method.descName();
		String param = method.descType();

		if (bci - ldcPos <= 3 && ldc.size() > 0) {
			if (code == Opcodes.INVOKESTATIC) {
				if (owner.startsWith("java/util/concurrent/atomic/Atomic") && owner.endsWith("FieldUpdater") && ldc.size() >= 2) {
					excludes.add(new Desc(ldc.get(ldc.size()-(owner.endsWith("ReferenceFieldUpdater") ? 3 : 2)), ldc.get(ldc.size()-1), "", 1));
				} else if (owner.equals("java/lang/Class") && name.equals("forName")) {
					excludes.add(new Desc(ldc.get(ldc.size()-1).replace('.', '/'), "", "", 2));
				} else if (owner.equals("roj/reflect/DirectAccessor") && name.equals("builder")) {
					excludes.add(new Desc(ldc.get(ldc.size()-1), "", "", 3));
				}
			} else if (owner.equals("java/lang/Class") && name.startsWith("get") && (name.endsWith("Field") || name.endsWith("Method")) && ldc.size() >= 2) {
				excludes.add(new Desc(ldc.get(0), ldc.get(1), "", 4));
			}
		}

		ldc.clear();
	}
}