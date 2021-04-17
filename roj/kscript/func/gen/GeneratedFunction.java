package roj.kscript.func.gen;

import roj.kscript.api.ArgumentList;
import roj.kscript.api.IGettable;
import roj.kscript.type.KType;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/17 18:54
 */
@FunctionalInterface
public interface GeneratedFunction {
    KType call(int index, IGettable $this, ArgumentList param);
}
