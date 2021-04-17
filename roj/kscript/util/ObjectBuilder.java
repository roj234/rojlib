package roj.kscript.util;

import roj.kscript.KConstants;
import roj.kscript.api.ArgumentList;
import roj.kscript.func.KFuncLambda;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;

import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/31 0:06
 */
public final class ObjectBuilder {
    private final KObject object;
    private final ObjectBuilder parent;

    private ObjectBuilder(ObjectBuilder parent) {
        this.object = new KObject(parent == null ? null : KConstants.OBJECT);
        this.parent = parent;
    }

    public static ObjectBuilder builder() {
        return new ObjectBuilder(null);
    }

    public ObjectBuilder returnVoid(String name, Consumer<ArgumentList> consumer) {
        return returns(name, ($this, param) -> {
            consumer.accept(param);
            return KUndefined.UNDEFINED;
        });
    }

    public ObjectBuilder returns(String name, Function<ArgumentList, KType> func) {
        return returns(name, ($this, param) -> func.apply(param));
    }

    public ObjectBuilder returns(String name, KFuncLambda.JavaMethod func) {
        object.put(name, new KFuncLambda(KConstants.FUNCTION, func));
        return this;
    }

    public ObjectBuilder put(String name, KType some) {
        object.put(name, some);
        return this;
    }

    public ObjectBuilder sub() {
        return new ObjectBuilder(this);
    }

    public ObjectBuilder endSub(String name) {
        parent.object.put(name, object);
        return parent;
    }

    public KObject build() {
        return object;
    }
}
