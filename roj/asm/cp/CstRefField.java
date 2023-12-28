package roj.asm.cp;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstRefField extends CstRef {
	public CstRefField(CstClass c, CstNameAndType d) {
		super(c, d);
	}

	public CstRefField() {}

	@Override
	public byte type() {
		return Constant.FIELD;
	}
}