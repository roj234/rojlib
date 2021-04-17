package roj.kscript.func;

import roj.kscript.Arguments;
import roj.kscript.api.ArgumentList;
import roj.kscript.api.IGettable;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 13:01
 */
public class KInitializer extends KFunction {
    final Function<ArgumentList, KType> caller;

    public KInitializer(Function<ArgumentList, KType> caller) {
        super();
        this.caller = caller;
        this.name = "call";
    }

    public KInitializer(KObject prototype, Function<ArgumentList, KType> caller) {
        super(prototype);
        this.caller = caller;
        this.name = "call";
    }

    @Override
    public KType invoke(@Nonnull IGettable $this, Arguments param) {
        return KUndefined.UNDEFINED;
    }

    @Override
    public String getClassName() {
        return caller.getClass().getName();
    }

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [Native code] }");
    }

    @Override
    public KType createInstance(Arguments args) {
        return caller.apply(args);
    }
}
