/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: MethodSimple.java
 */
package roj.asm.struct.simple;

import roj.annotation.Internal;
import roj.asm.cst.CstUTF;
import roj.asm.struct.IMethod;
import roj.asm.struct.attr.Attribute;
import roj.asm.util.FlagList;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;

import java.util.List;

/**
 * {@link roj.asm.struct.ConstantData}中的简单方法, 不解析{@link Attribute}
 * 与{@link FieldSimple}的代码完全一样，{@link SimpleComponent#type}是参数
 */
public final class MethodSimple extends SimpleComponent implements IMethod {
    public MethodSimple(int accesses, CstUTF name, CstUTF param) {
        super(accesses, name, param);
    }

    private String owner, parent;
    private List<Type> types;

    @Override
    public String parentClass() {
        return parent;
    }

    @Override
    public String ownerClass() {
        return owner;
    }

    @Override
    public List<Type> parameters() {
        if (types == null) {
            types = ParamHelper.parseMethod(this.type.getString());
            types.remove(types.size() - 1);
        }
        return types;
    }

    @Override
    public FlagList access() {
        return this.accesses;
    }

    @Internal
    public void cn(String owner, String parent) {
        this.owner = owner;
        this.parent = parent;
    }

    @Override
    public int type() {
        return 3;
    }
}