/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantClass.java
 */
package roj.asm.constant;

public final class CstClass extends CstRefUTF {
    public CstClass(int valueIndex) {
        super(CstType.CLASS, valueIndex);
    }

    public CstClass() {
        super(CstType.CLASS);
    }

    public CstClass(String name) {
        super(CstType.CLASS, 0);
        setValue(new CstUTF(name));
    }
}