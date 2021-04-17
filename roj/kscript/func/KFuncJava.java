package roj.kscript.func;

import roj.kscript.Arguments;
import roj.kscript.KConstants;
import roj.kscript.api.IGettable;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 12:28
 */
public abstract class KFuncJava extends KFunction {
    public KFuncJava() {
        super(KConstants.FUNCTION);
    }

    public KFuncJava(KObject prototype) {
        super(prototype);
    }

    public abstract KType invoke(@Nonnull IGettable $this, Arguments param);

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public String getClassName() {
        return getClass().getName();
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [Compiled java code] }");
    }
}
