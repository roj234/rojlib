/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantString.java
 */
package roj.asm.cst;

public final class CstString extends CstRefUTF {
    public CstString(int valueIndex) {
        super(CstType.STRING, valueIndex);
    }

    public CstString(String s) {
        super(CstType.STRING, -1);
        setValue(new CstUTF(s));
    }
}