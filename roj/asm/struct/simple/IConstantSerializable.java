package roj.asm.struct.simple;

import roj.asm.util.ConstantWriter;
import roj.util.ByteWriter;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: IConstantSerializable.java
 */
public interface IConstantSerializable {
    void toByteArray(ConstantWriter pool, ByteWriter w);

    String name();

    String rawDesc();
}
