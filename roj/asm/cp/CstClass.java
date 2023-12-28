package roj.asm.cp;

import roj.asm.type.TypeHelper;
import roj.text.CharList;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstClass extends CstRefUTF {
	public CstClass() {}
	public CstClass(String name) { super(new CstUTF(name)); }
	public CstClass(CstUTF v) { super(v); }

	@Override
	public byte type() { return Constant.CLASS; }
	@Override
	public String getEasyReadValue() {
		CharList sb = new CharList();
		TypeHelper.toStringOptionalPackage(sb, name().str());
		return sb.replace('/', '.').append(".class").toStringAndFree();
	}
	@Override
	public String getEasyCompareValue() { return name().str(); }
}