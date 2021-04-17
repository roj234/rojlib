/*
 * This file is a part of MI
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
package roj.config;

import roj.collect.MyHashMap;
import roj.kscript.parser.expr.Expression;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2021/2/21 23:59
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
