package roj.kscript.func;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 13:01
 */
public abstract class KInitializer extends KFunction {
    public KInitializer() {
        super();
        clazz = getClass().getName();
    }

    @Override
    public KType invoke(@Nonnull IObject $this, ArgList param) {
        return KUndefined.UNDEFINED;
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [Native code] }");
    }

    @Override
    public abstract KType createInstance(ArgList args);
}
