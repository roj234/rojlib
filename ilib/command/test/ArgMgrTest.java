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
package ilib.command.test;

import ilib.command.parser.Argument;
import ilib.command.parser.ArgumentHandler;
import net.minecraft.command.CommandException;
import roj.math.MutableInt;

import java.util.Collections;

/**
 * @author Roj234
 * @since  2021/1/9 14:41
 */
@Argument
public class ArgMgrTest implements ArgumentHandler<ArgMgrTest.Test> {
    @Override
    public Iterable<String> complete(String[] array, int index) {
        return Collections.singleton("TEST");
    }

    @Override
    public Test transform(String[] array, MutableInt index) throws CommandException {
        return array[index.getAndIncrement()].equals("TEST") ? new Test() : null;
    }

    @Override
    public Class<Test> getArgumentClass() {
        return Test.class;
    }

    public static class Test {

    }
}
