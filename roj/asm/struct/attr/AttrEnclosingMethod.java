/**
 * This file is a part of more items mod (MoreId)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: AttrEnclosingMethod.java
 */
package roj.asm.struct.attr;

import roj.asm.cst.CstClass;
import roj.asm.cst.CstNameAndType;
import roj.asm.util.ConstantWriter;
import roj.asm.util.type.ParamHelper;
import roj.asm.util.type.Type;
import roj.util.ByteWriter;

import java.util.List;

public final class AttrEnclosingMethod extends Attribute {
    public static final String PREDEFINED = new String("[]");

    public AttrEnclosingMethod(CstClass clazz, CstNameAndType method) {
        super("EnclosingMethod");
        // In particular, method_index must be zero if the current class was immediately enclosed in source code by an instance initializer, static initializer, instance variable initializer, or class variable initializer. 
        this.owner = clazz.getValue().getString();
        if (method == null) {
            this.name = PREDEFINED;
        } else {
            this.name = method.getName().getString();
            this.parameters = ParamHelper.parseMethod(method.getType().getString());
            this.returnType = this.parameters.remove(this.parameters.size() - 1);
        }
    }

    public String owner, name;
    public List<Type> parameters;
    public Type returnType;

    @Override
    protected void toByteArray1(ConstantWriter pool, ByteWriter w) {
        w.writeShort(pool.getClassId(this.owner));
        if (PREDEFINED == this.name) {
            w.writeShort(0);
        } else {
            this.parameters.add(this.returnType);
            w.writeShort(pool.getDescId(this.name, ParamHelper.getMethod(this.parameters)));
            this.parameters.remove(this.parameters.size() - 1);
        }
    }

    public String toString() {
        final StringBuilder sb = new StringBuilder().append("EnclosingMethod: ").append(returnType).append(' ').append(this.owner).append('.').append(name).append('(');
        for (Type par : parameters) {
            sb.append(par).append(", ");
        }
        if (!parameters.isEmpty())
            sb.delete(sb.length() - 2, sb.length());
        return sb.append(')').toString();
    }
}