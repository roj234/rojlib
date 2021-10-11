/*
 * This file is a part of MoreItems
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.asm.nixim;

import roj.mod.util.IntCallable;

/**
 * Nixim示例， 灰色已做好
 */
@Nixim(value = "roj.asm.nixim.TestTarget")
abstract class Example extends TestTarget {
    /*
     * 对特殊field/method检测，不能有Nixim注解存在
     */
    private static int $$$CONTINUE_I() {return 0;}
    private static int $$$RETURN_VAL_I() {return 0;}
    private static void $$$MATCHING_FLAG() {}
    private void $$$CONSTRUCTOR() {}

    /*
     * 使用shadow调用上级的方法或字段
     *  + 检测描述符是否相同:
     *     1. final【字段】不能改
     *     2. 是否static要统一
     *  + 可以换到别的类，不过这时无法检测了
     */
    @Shadow(value = "superField123123"/*, owner = "roj/asm/YYYY"*/)
    private int myField;

    /*
     * 对于private方法的shadow要检测方法在上级是否直接存在
     *    + 否则抛异常
     */
    @Shadow(value = "superMethod23333")
    private void myMethod() {}

    /*
     * 使用copy将这个方法或字段复制到目标类
     *    + 警告： 不支持【static final 【对象类型】 或者 【static 任何类型】】字段的默认值
     *       使用属性staticInitializer指定一个static方法
     *       它对其进行赋值并会被Nixim加入目标的clinit中
     *       若你想加final 你可以加上 targetIsFinal 属性
     *    + 检测，方法不能使用 staticInitializer 属性，来一个警告
     *    + 可以用newName改名字
     */
    //@Copy(newName = "superMethod23333")
    private void copyMethod() {}

    @Copy(staticInitializer = "IloveObjectInitializer", targetIsFinal = true)
    private static Object IloveObject;
    private static void IloveObjectInitializer() {
        IloveObject = IloveObject == null ? new Object() : IloveObject;
    }

    @Copy(newName = "ahaha", staticInitializer = "IloveObjectInitializer2", targetIsFinal = true)
    private static Object IloveObject2;
    private static void IloveObjectInitializer2() {
        IloveObject2 = IloveObject2 == null ? new Object() : IloveObject2;
    }

    /*
     * head: 将原方法中return替换成goto目标开头
     *    + 用特殊的字段名(startWith: $$$CONTINUE)指定【我还要继续执行】
     *    + 否则其它的返回就是真的结束了
     *    + hint: 建议给参数加上final
     *       否则要检测参数的assign然后做备份，同下
     */
    @Inject(value = "test", at = Inject.At.HEAD)
    public int testInjectAtHead(int myParam) {
        if (myParam == 0)
            return 0;
        myField = 18888;
        return $$$CONTINUE_I();
    }

    /*
     * javac会在构造器前面加上invokespecial上级
     * 所以特殊处理:
     *    + 检测invokespecial上级的<init>调用这个后视为方法开始
     */
    @Inject(value = "<init>", at = Inject.At.HEAD)
    public /* testInjectSuper */ Example() {
        super(); // 放些没用的参数
        myField = 9999;
    }

    /**
     * middle: 在方法中插入一段
     *    + 根据FC计算，这一段栈必须为空
     *    + 根据FC计算，参数要与方法变量表一一对应
     *    + 将所有不在结尾处的$$$_RETURN_VAL改成goto到结尾
     *
     * MIDDLE_MATCHING: 根据代码相似找到这一段 todo: PC换成IdentityHashMap 然后实现各种Insnnode.equals
     * MIDDLE_ORDINAL:  根据指定的pos找到这一段
     *
     */
    //@Inject(value = "test2", at = At.MIDDLE)
    public int testInjectAtMiddle(int superLocal1, int superLocal2) {
        if (superLocal1 == 1) {
            try {
                return ((IntCallable) () -> 0).call();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return $$$RETURN_VAL_I();
    }

    /*
     * tail: 将目标方法中return替换成goto开头
     *     + 将返回值（如果存在）存放到指定的变量，用特殊的字段名(startWith: $$$RETURN_VAL)指定, 类型不匹配抛异常
     *     + 计算tail用到的参数id，若目标方法修改过value则暂存到新的变量id中然后再恢复
     */
    @Inject(value = "test3", at = Inject.At.TAIL)
    public int testInjectAtTail(int myParam) {
        int returnValue = $$$RETURN_VAL_I();
        return returnValue + myParam;
    }

    /*
     * replace: 替换目标方法，没啥说的
     * $$$CONSTRUCTOR用于在非构造器中标记上级构造器的调用
     * 以及~_THIS标记自身构造器的调用
     */
    @Inject(value = "test4", at = Inject.At.REPLACE)
    public int testInjectReplace(int myParam) {
        myMethod();
        return 0;
    }
    //@Inject(value = "<init>", at = Inject.At.REPLACE)
    public  /*testInjectSuper*/  void xxx() {
        $$$CONSTRUCTOR();
        myField = 9999;
    }

    /*
     * old_super_inject: 只能差在头部或结尾，否则不行
     * 两者可以同时
     */
    @Inject(value = "test5", at = Inject.At.OLD_SUPER_INJECT)
    public Class<?> testInjectSIJ(int myTest233) {
        // 头部，测试
        if (myTest233 == 0) {
            return super.getClass();
        }

        // 结尾测试
        Class<?> toModify = super.test5(13);
        if (toModify == null)
            return Class.class;
        return super.test5(12);
    }

    /*
     * FLAG_OPTIONAL: 找不到时丢warning而不是异常
     */
}
