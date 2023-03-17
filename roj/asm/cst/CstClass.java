package roj.asm.cst;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstClass extends CstRefUTF {
	public CstClass(CstUTF v) {
		super(v);
	}

	public CstClass() {}

	public CstClass(String name) {
		setValue(new CstUTF(name));
	}

	@Override
	public byte type() {
		return Constant.CLASS;
	}
}