package roj.kscript.func;

import roj.kscript.KConstants;
import roj.kscript.api.IArguments;
import roj.kscript.api.IObject;
import roj.kscript.type.KInstance;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.type.Type;
import roj.kscript.util.opm.ObjectPropMap;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 */
public abstract class KFunction extends KObject {
    protected String name, source, clazz;

    public KFunction() {
        super(Type.FUNCTION, new ObjectPropMap(), KConstants.FUNCTION);
        this.put("prototype", new KObject(KConstants.OBJECT));
        this.chmod("prototype", false, true, null, null);
    }

    @Override
    public KFunction asFunction() {
        return this;
    }

    public abstract KType invoke(@Nonnull IObject $this, IArguments param);

    /**
     * 函数名称 <BR>
     *     eg: func
     */
    public String getName() {
        return name == null ? "<anonymous>" : name;
    }

    /**
     * 函数全称 - name <BR>
     *     eg: <global>.funcA.funcB.funcC.
     */
    public String getClassName() {
        return clazz;
    }

    public KFunction set(String source, String name, String clazz) {
        this.source = source;
        this.name = name;
        this.clazz = clazz;
        return this;
    }

    @Override
    public KType copy() {
        return this;
    }

    /**
     * 文件名 <BR>
     *     eg: test.js
     */
    public String getSource() {
        return source == null ? getClass().getSimpleName() + ".java" : source;
    }

    public KType createInstance(IArguments args) {
        return new KInstance(this, get("prototype").asKObject());
    }
}
