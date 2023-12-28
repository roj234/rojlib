package roj.asm.cp;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstMethodType extends CstRefUTF {
	public CstMethodType(CstUTF v) {
		super(v);
	}

	@Override
	public byte type() {
		return Constant.METHOD_TYPE;
	}
}