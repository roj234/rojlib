/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantClass.java
 */
package roj.asm.cst;

public final class CstModule extends CstRefUTF {
    public CstModule(int valueIndex) {
        super(CstType.METHOD, valueIndex);
    }

    public CstModule() {
        super(CstType.METHOD);
    }

    public CstModule(String name) {
        super(CstType.METHOD, 0);
        setValue(new CstUTF(name));
    }
}