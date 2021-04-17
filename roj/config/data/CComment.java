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

import roj.text.TextUtil;

import javax.annotation.Nonnull;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/31 21:17
 */
public final class CComment extends CEntry {
    public CharSequence comment;
    public boolean multiLine;

    public CComment(CharSequence comment) {
        this.comment = comment;
        this.multiLine = TextUtil.limitedIndexOf(comment, '\n', comment.length()) != -1;
    }

    @Nonnull
    @Override
    public Type getType() {
        return Type.COMMENT;
    }

    @Override
    public StringBuilder toYAML(StringBuilder sb, int depth) {
        return multiLine ? sb.append("'''").append(comment).append("'''") : sb.append("#").append(comment);
    }

    @Override
    public StringBuilder toJSON(StringBuilder sb, int depth) {
        return multiLine ? sb.append("/*").append(comment).append("*/") : sb.append("//").append(comment);
    }

    @Override
    public Object toNudeObject() {
        throw new UnsupportedOperationException("Comment couldn't cast!");
    }
}
