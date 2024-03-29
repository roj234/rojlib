package roj.asm.cst;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstString extends CstRefUTF {
	public CstString(CstUTF v) {
		super(v);
	}

	public CstString(String s) {
		setValue(new CstUTF(s));
	}

	@Override
	public byte type() {
		return Constant.STRING;
	}
}