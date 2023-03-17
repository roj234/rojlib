package roj.asm.frame;

import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;

/**
 * @author Roj234
 * @since 2022/2/5 15:36
 */
public class ClassPoet {
	public ConstantData poem;
	public MethodPoet mp = new MethodPoet(this);

	public ClassPoet(ConstantData clazz) {
		this.poem = clazz;
	}

	// check abstract...
	public MethodPoet method(String name, String desc, int flag) {
		Method m = new Method(flag, poem, name, desc);
		poem.methods.add(m);
		mp.method = m;
		mp.init(m);
		return mp;
	}
}
