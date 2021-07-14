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
package roj.asm.mapper;

import roj.asm.mapper.util.FlDesc;
import roj.asm.mapper.util.MtDesc;
import roj.collect.FindMap;
import roj.collect.Flippable;
import roj.collect.HashBiMap;
import roj.collect.MyHashMap;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/28 19:18
 */
public class Mapping {
    Flippable<String, String> classMap = new HashBiMap<>(1000);
    MyHashMap<FlDesc, String> fieldMap = new MyHashMap<>(1000);
    MyHashMap<MtDesc, String> methodMap = new MyHashMap<>(1000);

    /**
     * Data parse
     */
    public final void loadFromSrg(File path, boolean reverse) {
        try {
            loadFromSrg(new FileInputStream(path), reverse);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read srg file", e);
        }
    }

    public final void loadFromSrg(InputStream is, boolean reverse) {
        try(SimpleLineReader slr = new SimpleLineReader(is)) {
            CharList cl = new CharList(100);
            ArrayList<String> q = new ArrayList<>();
            String last0 = null, last1 = null;

            for(String s : slr) {
                q.clear();
                String dlm0 = TextUtil.splitStringF(q, cl, s, ':', 2).get(0);
                String dlm1 = q.get(1);

                q.clear();
                TextUtil.splitStringF(q, cl, dlm1, ' ');

                int id, id2;
                switch(dlm0) {
                    case "PK": // package
                        break;
                    case "CL": // class
                        if(reverse) {
                            classMap.put(q.get(1), q.get(0));
                        } else {
                            classMap.put(q.get(0), q.get(1));
                        }
                        last0 = q.get(0);
                        last1 = q.get(1);
                        break;
                    case "FD":
                        id = q.get(0).lastIndexOf("/");
                        id2 = q.get(1).lastIndexOf("/");

                        if(reverse) {
                            fieldMap.put(new FlDesc(q.get(1).substring(0, id2), q.get(1).substring(id2 + 1)), q.get(0).substring(id + 1));
                        } else {
                            fieldMap.put(new FlDesc(q.get(0).substring(0, id), q.get(0).substring(id + 1)), q.get(1).substring(id2 + 1));
                        }
                        break;
                    case "MD":
                        id = q.get(0).lastIndexOf("/");
                        id2 = q.get(2).lastIndexOf("/");

                        if(reverse) {
                            methodMap.put(new MtDesc(q.get(2).substring(0, id2), q.get(2).substring(id2 + 1), q.get(3)), q.get(0).substring(id + 1));
                        } else {
                            methodMap.put(new MtDesc(q.get(0).substring(0, id), q.get(0).substring(id + 1), q.get(1)), q.get(2).substring(id2 + 1));
                        }
                        break;
                    case "FL":
                        // CL aaa bbb
                        // FL b c
                        if(last0 == null)
                            throw new IllegalArgumentException("last[0] == null at line " + slr.index());

                        if(reverse) {
                            fieldMap.put(new FlDesc(last1, q.get(1)), q.get(0));
                        } else {
                            fieldMap.put(new FlDesc(last0, q.get(0)), q.get(1));
                        }
                        break;
                    case "ML":
                        if(last0 == null)
                            throw new IllegalArgumentException("last[0] == null at line " + slr.index());

                        if(reverse) {
                            // net/minecraft/client/renderer/entity/layers/LayerHeldItem[1] func_177141_a (Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V => doRenderLayer
                            methodMap.put(new MtDesc(last1, q.get(2), q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
                        } else {
                            // net/minecraft/client/renderer/entity/layers/LayerHeldItem[0] doRenderLayer (Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V => func_177141_a
                            methodMap.put(new MtDesc(last0, q.get(0), q.get(1)), q.get(2));
                        }
                        break;
                    default:
                        System.err.println("Unsupported type: " + s);
                }
            }
        } catch(Exception e) {
            throw new RuntimeException("Unable to read srg file", e);
        }
    }

    /**
     * SrgMap data
     */
    public final Flippable<String, String> getClassMap() {
        return classMap;
    }

    public final Map<FlDesc, String> getFieldMap() {
        return fieldMap;
    }

    public final FindMap<MtDesc, String> getMethodMap() {
        return methodMap;
    }
}
