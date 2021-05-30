/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantClass.java
 */
package roj.asm.cst;

public final class CstPackage extends CstRefUTF {
    public CstPackage(int valueIndex) {
        super(CstType.PACKAGE, valueIndex);
    }

    public CstPackage() {
        super(CstType.PACKAGE);
    }

    public CstPackage(String name) {
        super(CstType.PACKAGE, 0);
        setValue(new CstUTF(name));
    }
}