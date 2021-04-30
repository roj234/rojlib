package roj.asm.struct;

import roj.asm.util.FlagList;
import roj.asm.util.type.Type;

import java.util.List;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: IMethodData.java.java
 */
public interface IMethodData {
    String parentClass();

    String ownerClass();

    String name();

    List<Type> parameters();

    FlagList access();

    String rawDesc();
}
