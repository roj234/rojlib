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
package roj.mapper;

import roj.collect.*;
import roj.mapper.util.Desc;
import roj.mapper.util.MapperList;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.text.UTFCoder;

import java.io.*;
import java.util.*;

/**
 * Class Mapping
 *
 * @author Roj234
 * @since  2020/8/28 19:18
 */
public class Mapping {
    protected Flippable<String, String> classMap;
    protected MyHashMap<Desc, String> fieldMap, methodMap;
    protected final TrieTree<String> packageMap;
    protected final boolean checkFieldType;

    public Mapping() {
        this(false);
    }
    public Mapping(boolean checkFieldType) {
        this.checkFieldType = checkFieldType;
        this.classMap = new HashBiMap<>(1000);
        this.fieldMap = new MyHashMap<>(1000);
        this.methodMap = new MyHashMap<>(1000);
        this.packageMap = new TrieTree<>();
    }
    public Mapping(Mapping o) {
        this.classMap = o.classMap;
        this.fieldMap = o.fieldMap;
        this.methodMap = o.methodMap;
        this.packageMap = o.packageMap;
        this.checkFieldType = o.checkFieldType;
    }

    /**
     * Data parse
     */
    public final void loadMap(File path, boolean reverse) {
        try {
            loadMap(new FileInputStream(path), reverse);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read mapping file", e);
        }
    }

    public final void loadMap(InputStream is, boolean reverse) {
        try(SimpleLineReader slr = new SimpleLineReader(is)) {
            ArrayList<String> q = new ArrayList<>();
            String last0 = null, last1 = null;

            for(String s : slr) {
                q.clear();
                String dlm0 = TextUtil.split(q, s, ':', 2).get(0);
                String dlm1 = q.get(1);

                q.clear();
                TextUtil.split(q, dlm1, ' ');

                int id, id2;
                switch(dlm0) {
                    case "PK": // package
                        if(!q.get(0).equals(q.get(1))) {
                            if (reverse) {
                                packageMap.put(q.get(1), q.get(0));
                            } else {
                                packageMap.put(q.get(0), q.get(1));
                            }
                        }
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
                            fieldMap.put(new Desc(q.get(1).substring(0, id2), q.get(1).substring(id2 + 1)), q.get(0).substring(id + 1));
                        } else {
                            fieldMap.put(new Desc(q.get(0).substring(0, id), q.get(0).substring(id + 1)), q.get(1).substring(id2 + 1));
                        }
                        break;
                    case "MD":
                        id = q.get(0).lastIndexOf("/");
                        id2 = q.get(2).lastIndexOf("/");

                        if(reverse) {
                            methodMap.put(new Desc(q.get(2).substring(0, id2), q.get(2).substring(id2 + 1), q.get(3)), q.get(0).substring(id + 1));
                        } else {
                            methodMap.put(new Desc(q.get(0).substring(0, id), q.get(0).substring(id + 1), q.get(1)), q.get(2).substring(id2 + 1));
                        }
                        break;
                    case "FL":
                        // CL aaa bbb
                        // FL b c
                        if(last0 == null)
                            throw new IllegalArgumentException("last[0] == null at line " + slr.lineNumber());

                        if(reverse) {
                            fieldMap.put(new Desc(last1, q.get(1)), q.get(0));
                        } else {
                            fieldMap.put(new Desc(last0, q.get(0)), q.get(1));
                        }
                        break;
                    case "ML":
                        if(last0 == null)
                            throw new IllegalArgumentException("last[0] == null at line " + slr.lineNumber());

                        if(reverse) {
                            // net/minecraft/client/renderer/entity/layers/LayerHeldItem[1] func_177141_a (Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V => doRenderLayer
                            methodMap.put(new Desc(last1, q.get(2), q.get(3).equals("~") ? q.get(1) : q.get(3)), q.get(0));
                        } else {
                            // net/minecraft/client/renderer/entity/layers/LayerHeldItem[0] doRenderLayer (Lnet/minecraft/entity/EntityLivingBase;FFFFFFF)V => func_177141_a
                            methodMap.put(new Desc(last0, q.get(0), q.get(1)), q.get(2));
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

    public void saveMap(File target) throws IOException {
        CharList ob = new CharList(1024 * 1024);

        MyHashMap<String, CharList> classFos = new MyHashMap<>(classMap.size());

        for(Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
            String cn = entry.getKey().owner;

            CharList cl = classFos.get(cn);
            if(cl == null) {
                classFos.put(cn, cl = new CharList(100));
            }

            cl.append("FL: ").append(entry.getKey().name).append(' ').append(entry.getValue()).append('\n');
        }

        Util U = Util.getInstance();
        for(Map.Entry<Desc, String> entry : methodMap.entrySet()) {
            Desc desc = entry.getKey();

            String cn = desc.owner;
            CharList cl = classFos.get(cn);
            if(cl == null) {
                classFos.put(cn, cl = new CharList(100));
            }
            String param = U.transformMethodParam(classMap, desc.param);

            cl.append("ML: ").append(desc.name).append(' ').append(desc.param).append(' ').append(entry.getValue()).append(' ').append(param.equals(desc.param) ? "~" : param).append('\n');
        }

        for(Map.Entry<String, String> entry : classMap.entrySet()) {
            ob.append("CL: ").append(entry.getKey()).append(' ').append(entry.getValue()).append('\n');
            CharList list = classFos.get(entry.getKey());
            if(list != null)
                ob.append(list);
        }

        try (FileOutputStream fos = new FileOutputStream(target)) {
            new UTFCoder().encodeTo(ob, fos);
        }
    }

    public static void makeInheritMap(Map<String, List<String>> superMap, Map<String, String> filter) {
        MapperList parents = new MapperList();

        SimpleList<String> self = new SimpleList<>();
        SimpleList<String> next = new SimpleList<>();

        // ?????????????????????????????????, note: ???????????????
        for (Iterator<Map.Entry<String, List<String>>> itr = superMap.entrySet().iterator(); itr.hasNext(); ) {
            Map.Entry<String, List<String>> entry = itr.next();
            if (entry.getValue().getClass() == MapperList.class) continue; // done

            String name = entry.getKey();

            self.addAll(entry.getValue());

            int cycle = 0;
            /**
             * excepted order:
             *     fatherclass fatheritf grandclass granditf, etc...
             */
            do {
                if (cycle++ > 30)
                    throw new IllegalStateException("Probably circular reference for " + name + " " + parents);
                parents.addAll(self);
                if ((cycle & 3) == 0)
                    parents.preClean();
                for (int i = 0; i < self.size(); i++) {
                    String s = self.get(i);
                    Collection<String> tmp;
                    if ((tmp = superMap.get(s)) != null) {
                        if (tmp.getClass() != MapperList.class) {
                            next.addAll(tmp);
                            if (cycle > 15 && tmp.contains(s))
                                throw new IllegalStateException("Circular reference in " + s);
                        } else {
                            parents.addAll(tmp);
                        }
                    }
                    if (cycle > 15 && next.contains(s))
                        throw new IllegalStateException("Circular reference in " + s);
                }
                SimpleList<String> tmp1 = self;
                self = next;
                next = tmp1;
                next.clear();
            } while (!self.isEmpty());

            if (filter != null) {
                for (int i = parents.size() - 1; i >= 0; i--) {
                    if (!filter.containsKey(parents.get(i))) {
                        parents.remove(i); // ???????????????????????????
                    }
                }
            }

            if (!parents.isEmpty()) { // ?????????????????????????????????
                parents._init_();
                entry.setValue(parents);
                parents = new MapperList();
            } else {
                itr.remove();
            }
        }
    }

    /**
     * SrgMap data
     */
    public final Flippable<String, String> getClassMap() {
        return classMap;
    }

    public final Map<Desc, String> getFieldMap() {
        return fieldMap;
    }

    public final FindMap<Desc, String> getMethodMap() {
        return methodMap;
    }

    /**
     * Mapper{A->B} .reverse()   =>>  Mapper{B->A}
     */
    public void reverse() {
        Util U = Util.getInstance();

        this.classMap = classMap.flip();
        MyHashMap<Desc, String> fieldMap1 = new MyHashMap<>(this.fieldMap.size());
        for (Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
            Desc desc = entry.getKey();
            Desc target = new Desc(desc.owner, entry.getValue(), desc.param, desc.flags);
            String param = U.transformFieldType(classMap, desc.param);
            if(param != null)
                target.param = param;
            fieldMap1.put(target, desc.name);
        }
        this.fieldMap = fieldMap1;

        MyHashMap<Desc, String> methodMap1 = new MyHashMap<>(this.methodMap.size());
        for (Map.Entry<Desc, String> entry : methodMap.entrySet()) {
            Desc desc = entry.getKey();
            Desc target = new Desc(desc.owner, entry.getValue(), U.transformMethodParam(classMap, desc.param), desc.flags);
            methodMap1.put(target, desc.name);
        }
        this.methodMap = methodMap1;
    }

    /**
     * Mapper{B->C} .extend ( Mapper{A->B} )   =>>  Mapper{A->C}
     */
    public void extend(Mapping from) {
        Util U = Util.getInstance();

        for(Map.Entry<String, String> entry : classMap.entrySet()) {
            classMap.put(entry.getKey(), from.classMap.getOrDefault(entry.getValue(), entry.getValue()));
        }
        Desc md = new Desc("", "", "");
        for(Map.Entry<Desc, String> entry : fieldMap.entrySet()) {
            Desc descriptor = entry.getKey();
            md.owner = U.mapClassName(classMap, descriptor.owner);
            md.name = entry.getValue();
            String param = U.transformFieldType(classMap, md.param = descriptor.param);
            if(param != null)
                md.param = param;
            entry.setValue(from.fieldMap.getOrDefault(md, entry.getValue()));
        }
        for(Map.Entry<Desc, String> entry : methodMap.entrySet()) {
            Desc descriptor = entry.getKey();
            md.owner = U.mapClassName(classMap, descriptor.owner);
            md.name = entry.getValue();
            md.param = U.transformMethodParam(classMap, descriptor.param);
            entry.setValue(from.methodMap.getOrDefault(md, entry.getValue()));
        }
    }
}
