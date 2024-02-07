package roj.asm.cp;

/**
 * Cst Module
 *
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstModule extends CstRefUTF {
	public CstModule(CstUTF v) {
		super(v);
	}

	public CstModule() {}

	public CstModule(String name) {
		setValue(new CstUTF(name));
	}

	@Override
	public byte type() {
		return Constant.MODULE;
	}
}