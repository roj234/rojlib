package roj.kscript.func.gen;

import roj.kscript.func.KFunction;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 13:01
 */
abstract class KFuncJava extends KFunction {
    KFuncJava() {}

    abstract KFuncJava copyAs(int id);

    abstract Object get_set_Object(Object object);

    @Override
    public StringBuilder toString0(StringBuilder sb, int depth) {
        return sb.append("function ").append(name).append("(){ [ Compiled java code ] }");
    }
}
