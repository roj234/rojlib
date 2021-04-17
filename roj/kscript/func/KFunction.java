package roj.kscript.func;

import roj.collect.MyHashMap;
import roj.kscript.Arguments;
import roj.kscript.KConstants;
import roj.kscript.api.IGettable;
import roj.kscript.type.KInstance;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.Type;

import javax.annotation.Nonnull;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: null.java
 */
public abstract class KFunction extends KObject {
    protected String name, source;

    /**
     * Copy
     *
     * @param function brother
     */
    protected KFunction(KFunction function) {
        super(Type.FUNCTION, function.getInternalMap(), function);
        this.name = function.name;
        this.source = function.source;
    }

    public KFunction() {
        this(KConstants.FUNCTION);
    }

    /**
     * @param prototype 他爹
     */
    public KFunction(KObject prototype) {
        super(Type.FUNCTION, new MyHashMap<>(2), prototype);
    }

    @Override
    public KFunction asFunction() {
        return this;
    }

    public abstract KType invoke(@Nonnull IGettable $this, Arguments param);

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name == null ? "<anonymous>" : name;
    }

    public abstract String getClassName();

    public void setSource(String source) {
        this.source = source;
    }

    public String getSource() {
        return source == null ? getClass().getSimpleName() + ".java" : source;
    }

    @Override
    public KType copy() {
        return this;
    }

    public KType createInstance(Arguments args) {
        return new KInstance(this, getOr("prototype", KConstants.FUNCTION_PROTOTYPE).asObject());
    }
}
