package roj.kscript.func;

import roj.kscript.Arguments;
import roj.kscript.api.ArgumentList;
import roj.kscript.api.IGettable;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 13:01
 */
public class KFuncLambda extends KFunction {
    final JavaMethod caller;

    public KFuncLambda(JavaMethod caller) {
        super();
        this.caller = caller;
        this.name = "call";
    }

    public KFuncLambda(KObject prototype, JavaMethod caller) {
        super(prototype);
        this.caller = caller;
        this.name = "call";
    }

    @Override
    public KType invoke(@Nonnull IGettable $this, Arguments param) {
        return caller.call($this, param);
    }

    @Override
    public String getClassName() {
        return caller.getClass().getName();
    }

    @FunctionalInterface
    public interface JavaMethod {
        KType call(IGettable $this, ArgumentList param);
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [Native code] }");
    }
}
