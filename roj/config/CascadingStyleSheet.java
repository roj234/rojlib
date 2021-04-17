package roj.config;

import roj.collect.MyHashMap;
import roj.kscript.parser.expr.Expression;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/2/21 23:59
 */
// todo
public class CascadingStyleSheet {
    MyHashMap<String, Style> byId, byClassName, byTagName;
    MyHashMap<Selector, Style> bySelector;

    public static Style computeStyle() {
        return null;
    }

    public static class Style {
        int priority;
        MyHashMap<String, String> kvMap;
    }

    public static class Selector {
        // subClasses:
        //   nthChildSelector, lastChildSelector 第几个元素
        //   pseudoElementSelector, before/after ElementSelector 伪元素选择器
        //   stateSelector 状态选择器: hover, focus, etc
        // :nth-child(id % 2 == 0) // a single line of js code... with only a few parameter(s) and is an instance of Expression
        Expression expr;
    }
}
