package roj.asm.cp;

import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/29 17:16
 */
public final class CstMethodType extends CstRefUTF {
	public CstMethodType(CstUTF v) {super(v);}
	public CstMethodType(String v) {super(new CstUTF(Objects.requireNonNull(v)));}
	@Override
	public byte type() {return Constant.METHOD_TYPE;}
}