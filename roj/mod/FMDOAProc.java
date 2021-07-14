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

package roj.mod;

import roj.asm.annotation.AnnotationProcessor;
import roj.asm.annotation.OpenAny;
import roj.collect.MyHashSet;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * FMD @OpenAny Processor
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/30 19:59
 */
public final class FMDOAProc extends AnnotationProcessor {
    public FMDOAProc() {
        super();
        Shared.load_S2M_Map();
    }

    @Override
    public boolean hook() {
        FMDMain.annotationHook(this);
        return false;
    }

    @Override
    public void processAClass(List<OpenAny> list, Map<String, Set<String>> collectedAT) {
        for (int i = 0; i < list.size(); i++) {
            OpenAny oa = list.get(i);

            String classQualifiedName = oa.value().replace('.', '/').replace(':', '/');
            Set<String> data = collectedAT.computeIfAbsent(classQualifiedName, (key) -> new MyHashSet<>());

            for (String s : oa.names()) {
                data.add(Shared.srg2mcp.getOrDefault(s, s));
            }
        }
    }
}