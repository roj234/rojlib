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
package roj.mod.compiler;

import roj.util.ByteList;

import javax.tools.SimpleJavaFileObject;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/2 14:00
 */
public class ByteListOutput extends SimpleJavaFileObject {
    private final String name;
    private final ByteList output;

    protected ByteListOutput(String className, String basePath) throws URISyntaxException {
        super(new URI("file://" + basePath + className.replace('.', '/') + ".class"), Kind.CLASS);
        this.output = new ByteList();
        this.name = className.replace('.', '/') + ".class";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public OutputStream openOutputStream() {
        return output.asOutputStream();
    }

    public ByteList getOutput() {
        return output;
    }
}
