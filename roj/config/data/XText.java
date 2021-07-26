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
package roj.config.data;

import java.util.Collections;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/11/22 15:53
 */
public final class XText extends AbstXML {
    public String value;

    public XText(String value) {
        super(Collections.emptyMap(), Collections.emptyList());
        this.value = value;
    }

    @Override
    public String asText() {
        return value;
    }

    public CString toJSON() {
        return new CString(value);
    }

    @Override
    protected StringBuilder toXML(StringBuilder sb, int depth) {
        return toCompatXML(sb);
    }

    @Override
    protected StringBuilder toCompatXML(StringBuilder sb) {
        return !value.startsWith("<![CDATA[") && value.indexOf('<') >= 0 ? sb.append("<![CDATA[").append(value).append("]]>") : sb.append(value);
    }
}
