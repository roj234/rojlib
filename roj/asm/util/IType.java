package roj.asm.util;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: KType.java
 */
public interface IType {
    boolean isGeneric();

    String toGeneric();

    void appendGeneric(StringBuilder sb);

    void appendString(StringBuilder sb);
}
