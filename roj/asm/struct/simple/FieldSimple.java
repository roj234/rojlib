/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: FieldSimple.java
 */
package roj.asm.struct.simple;

import roj.asm.constant.CstUTF;
import roj.asm.struct.attr.Attribute;

/**
 * {@link roj.asm.struct.ConstantData}中的简单字段, 不解析{@link Attribute}
 */
public final class FieldSimple extends SimpleComponent {
    public FieldSimple(int accesses, CstUTF name, CstUTF typeName) {
        super(accesses, name, typeName);
    }
}