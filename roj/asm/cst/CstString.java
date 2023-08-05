package roj.asm.cst;

import roj.config.word.ITokenizer;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstString extends CstRefUTF {
	public CstString(CstUTF v) { super(v); }
	public CstString(String s) { setValue(new CstUTF(s)); }

	@Override
	public byte type() { return STRING; }
	@Override
	public String getEasyReadValue() { return '"'+ITokenizer.addSlashes(name().str())+'"'; }
	@Override
	public String getEasyCompareValue() { return name().str(); }
}