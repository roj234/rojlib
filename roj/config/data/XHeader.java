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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/6/1 1:22
 */
public final class XHeader extends AbstXML {
    public XHeader() {
        super(Collections.emptyMap(), new ArrayList<>());
    }

    public XHeader(Map<String, CEntry> attributes, List<AbstXML> children) {
        super(attributes, children);
    }

    @Override
    public String toString() {
        return toXML(new StringBuilder(128)).toString();
    }

    public StringBuilder toXML(StringBuilder sb) {
        sb.append("<?xml");
        if (!attributes.isEmpty()) {
            for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=');
                entry.getValue().toJSON(sb, 0);
            }
        }
        sb.append('?').append('>');
        if (!children.isEmpty()) {
            sb.append('\n');
            for (int i = 0; i < children.size(); i++) {
                children.get(i).toXML(sb, 0).append('\n');
            }
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb;
    }

    public StringBuilder toCompatXML(StringBuilder sb) {
        sb.append("<?xml");
        if (!attributes.isEmpty()) {
            for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
                sb.append(' ').append(entry.getKey()).append('=');
                entry.getValue().toJSON(sb, 0);
            }
        }
        sb.append('?').append('>');
        if (!children.isEmpty()) {
            for (int i = 0; i < children.size(); i++) {
                children.get(i).toCompatXML(sb);
            }
            sb.delete(sb.length() - 1, sb.length());
        }
        return sb;
    }

    public CMapping toJSON() {
        return super.toJSON().asMap();
    }

    @Override
    protected StringBuilder toXML(StringBuilder sb, int depth) {
        throw new UnsupportedOperationException("Should not call by underlevel");
    }

    public static XHeader fromJSON(CMapping map) {
        XHeader header = new XHeader();
        header.read(map);
        return header;
    }
}
