package roj.kscript.func;

import roj.kscript.api.IArguments;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 13:01
 */
public abstract class KFuncNative extends KFunction {
    public KFuncNative() {
        clazz = getClass().getName();
    }

    @Override
    public abstract KType invoke(@Nonnull IObject $this, IArguments param);

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [Native code] }");
    }
}
