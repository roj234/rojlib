package roj.asm.cp;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstRefItf extends CstRef {
	public CstRefItf(CstClass c, CstNameAndType d) {
		super(c, d);
	}

	public CstRefItf() {}

	@Override
	public byte type() {
		return Constant.INTERFACE;
	}
}