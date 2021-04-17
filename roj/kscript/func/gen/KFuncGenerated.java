package roj.kscript.func.gen;

import roj.kscript.Arguments;
import roj.kscript.api.IGettable;
import roj.kscript.func.KFunction;
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
public class KFuncGenerated extends KFunction {
    final GeneratedFunction caller;
    final int index;

    public KFuncGenerated(GeneratedFunction caller, int index) {
        super();
        this.caller = caller;
        this.index = index;
    }

    public KFuncGenerated(KObject prototype, GeneratedFunction caller, int index) {
        super(prototype);
        this.caller = caller;
        this.index = index;
    }

    @Override
    public KType invoke(@Nonnull IGettable $this, Arguments param) {
        return caller.call(index, $this, param);
    }

    @Override
    public String getSource() {
        return null;
    }

    @Override
    public String getClassName() {
        return caller.getClass().getName();
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [Compiled java code(Reflection)] }");
    }
}
