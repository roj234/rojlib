/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: ConstantMethodReference.java
 */
package roj.asm.constant;

public final class CstRefMethod extends CstRef {
    public CstRefMethod(int classIndex, int nameAndTypeIndex) {
        super(CstType.METHOD, classIndex, nameAndTypeIndex);
    }

    public CstRefMethod() {
        super(CstType.METHOD);
    }

}