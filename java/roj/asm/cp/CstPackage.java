package roj.asm.cp;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstPackage extends CstRefUTF {
	public CstPackage() {}
	public CstPackage(CstUTF v) { super(v); }
	public CstPackage(String name) { super(name); }

	@Override
	public byte type() {return Constant.PACKAGE;}
}