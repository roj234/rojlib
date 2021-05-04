package roj.kscript.type;

import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/25 18:34
 */
public class KObjectInst extends KObject {
    public KObjectInst(IObject proto) {
        super(proto);
    }

    @Override
    public boolean chmod(String key, boolean configurable, boolean enumerable, KFunction getter, KFunction setter) {
        return false;
    }
}
