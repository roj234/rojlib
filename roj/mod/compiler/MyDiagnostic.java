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

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.util.Locale;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/10/2 14:15
 */
public class MyDiagnostic implements Diagnostic<JavaFileObject> {
    private final String msg;
    private final Kind   kind;

    MyDiagnostic(String msg, Kind kind) {
        this.msg = msg;
        this.kind = kind;
    }

    @Override
    public Kind getKind() {
        return kind;
    }

    @Override
    public JavaFileObject getSource() {
        return null;
    }

    @Override
    public long getPosition() {
        return -1;
    }

    @Override
    public long getStartPosition() {
        return 0;
    }

    @Override
    public long getEndPosition() {
        return 0;
    }

    @Override
    public long getLineNumber() {
        return -1;
    }

    @Override
    public long getColumnNumber() {
        return 0;
    }

    @Override
    public String getCode() {
        return "";
    }

    @Override
    public String getMessage(Locale locale) {
        return msg;
    }
}
