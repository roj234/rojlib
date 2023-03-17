package roj.kscript.asm;

import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.util.InsnList;

/**
 * @author solo6975
 * @since 2021/6/27 13:19
 */
public class CompileContext {
	public ConstantData clazz;
	public Method method;
	public InsnList list;

	public void loadVar(String name, byte spec_op_type) {

	}

	public void loadThis() {

	}

	public int createTmpVar(String name) {
		return 0;
	}

	public void endTmpVar(int id) {

	}
}
