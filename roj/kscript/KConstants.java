package roj.kscript;

import roj.kscript.func.KFuncLambda;
import roj.kscript.func.KFunction;
import roj.kscript.type.KArray;
import roj.kscript.type.KObject;
import roj.kscript.type.KString;
import roj.kscript.type.KUndefined;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 系统常量定义
 *
 * @author Roj233
 * @since 2020/9/21 22:45
 */
public final class KConstants {
    public static final KObject OBJECT = new KObject(null);

    public static final KObject FUNCTION_PROTOTYPE = new KObject(null);
    public static final KObject FUNCTION = new KObject(null);

    public static final KFunction ARRAY = new KFuncLambda(FUNCTION, ($this, param) -> new KArray(param.getOr(0, 0)));

    public static final KFunction TYPEOF = new KFuncLambda(FUNCTION, ($this, param) -> KString.valueOf((param.getOr(0, KUndefined.UNDEFINED).getType().typeof())));

    static {
        //OBJECT.put("toString", new KMethodAST(AST.builder().op(LOAD_OBJECT, 0).op(INVOKE_SPECIAL_METHOD, "toString").returnStack().build()));
        FUNCTION.put("prototype", FUNCTION_PROTOTYPE);
    }

}
