package roj.asm.cst;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstRefMethod extends CstRef {
	public CstRefMethod(CstClass c, CstNameAndType d) {
		super(c, d);
	}

	public CstRefMethod() {}

	@Override
	public byte type() {
		return Constant.METHOD;
	}
}