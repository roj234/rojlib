package roj.asm.cst;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstPackage extends CstRefUTF {
	public CstPackage(CstUTF v) {
		super(v);
	}

	public CstPackage() {}

	public CstPackage(String name) {
		setValue(new CstUTF(name));
	}

	@Override
	public byte type() {
		return Constant.PACKAGE;
	}
}