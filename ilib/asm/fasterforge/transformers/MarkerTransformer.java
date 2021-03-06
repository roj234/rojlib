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
package ilib.asm.fasterforge.transformers;

import ilib.api.ContextClassTransformer;
import roj.asm.tree.ConstantData;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MarkerTransformer implements ContextClassTransformer {
    private final MyHashMap<String, List<String>> markers;

    public MarkerTransformer() throws IOException {
        this("fml_marker.cfg");
    }

    protected MarkerTransformer(String rulesFile) throws IOException {
        this.markers = new MyHashMap<>();
        readMapFile(rulesFile);
    }

    private void readMapFile(String rulesFile) throws IOException {
        String rulesResource;
        File file = new File(rulesFile);
        if (!file.exists()) {
            rulesResource = IOUtil.readUTF(MarkerTransformer.class, rulesFile);
        } else {
            rulesResource = IOUtil.readUTF(new FileInputStream(file));
        }
        ArrayList<String> tmp = new ArrayList<>();
        for (String input : new SimpleLineReader(rulesResource)) {
            TextUtil.split(tmp, input, '#', 2);
            if (tmp.size() == 0)
                continue;
            String str = tmp.get(0);
            tmp.clear();
            TextUtil.split(tmp, str, ' ');

            if (tmp.size() != 2)
                throw new RuntimeException("Invalid config file line " + input);
            String name = tmp.get(0).trim();
            String val = tmp.get(1);
            tmp.clear();
            TextUtil.split(tmp, val, ',');
            for (int i = 0; i < tmp.size(); i++) {
                tmp.set(i, tmp.get(i).trim());
            }
            markers.put(name, new ArrayList<>(tmp));
        }
    }

    @Override
    public void transform(String transformedName, Context context) {
        List<String> itfs = this.markers.remove(transformedName);
        if (itfs == null) return;
        ConstantData data = context.getData();
        for (int i = 0; i < itfs.size(); i++) {
            data.interfaces.add(data.cp.getClazz(itfs.get(i)));
        }
    }
}
