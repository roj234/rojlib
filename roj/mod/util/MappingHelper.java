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
package roj.mod.util;

import roj.asm.type.ParamHelper;
import roj.collect.*;
import roj.io.IOUtil;
import roj.mapper.Mapping;
import roj.mapper.Util;
import roj.mapper.util.Desc;
import roj.math.MathUtils;
import roj.math.MutableBoolean;
import roj.text.CharList;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Mapping helper
 *
 * @author Roj234
 * @since  2020/8/29 22:25
 */
public class MappingHelper {
    public static PrintStream OUT = System.out;
    // 4mb
    private static final int MINIMUM_CAPACITY = 512 * 1024 * 4;

    private static final int NONNULL = 0;

    public final Map<String, String> classes;

    public final Map<String, List<Desc>> methods = new MyHashMap<>(2000);
    public final Map<String, List<Desc>> fields  = new MyHashMap<>(2000);

    public Map<String, List<Object[]>> revAll;

    /**
     * No TRansform
     */
    private final Set<String> NTR = new MyHashSet<>();

    private final CharList ob = new CharList();

    private int flag = 0;

    public MappingHelper(boolean reverse) {
        if(reverse) {
            revAll = new MyHashMap<>(2000);
            flag = 16384;
        }
        this.classes = new MyHashMap<>(2000);
    }

    public MappingHelper(Mapping mapping) {
        this.classes = mapping.getClassMap();

        for(Map.Entry<Desc, String> entry : mapping.getFieldMap().entrySet()) {
            Desc descriptor = entry.getKey().copy();
            descriptor.flags = Desc.NULL_FLAG;
            fields.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(descriptor);
        }

        for(Map.Entry<Desc, String> entry : mapping.getMethodMap().entrySet()) {
            Desc descriptor = entry.getKey().copy();
            descriptor.flags = Desc.NULL_FLAG;
            methods.computeIfAbsent(entry.getValue(), Helpers.fnArrayList()).add(descriptor);
        }

        this.flag = 32768;
    }

    // region MCP ??????

    private void parseCSVMethods(String csv) {
        SimpleLineReader slr = new SimpleLineReader(csv);

        slr.skipLines(1);
        List<String> tmp = new ArrayList<>(4);
        int i = 2;
        while (slr.hasNext()) {
            String line = slr.next();
            if (line.length() == 0 || line.startsWith("#")) continue;

            tmp.clear();
            if(TextUtil.split(tmp, line, ',').size() < 2) {
                throw new IllegalArgumentException("methods.csv:" + i + ": ????????????: " + line);
            }
            List<Desc> descriptors = methods.get(tmp.get(0));
            if(descriptors == null)
                OUT.println("methods.csv:" + i + ": ????????????SRG: " + tmp.get(0));
            else {
                for (int j = 0; j < descriptors.size(); j++) {
                    Desc descriptor = descriptors.get(j);
                    descriptor.name = tmp.get(1);
                    descriptor.flags = NONNULL;
                }
            }
            i++;
        }
    }

    private void parseCSVFields(String csv) {
        SimpleLineReader slr = new SimpleLineReader(csv);

        slr.skipLines(1);
        List<String> tmp = new ArrayList<>(4);
        int i = 2;
        while (slr.hasNext()) {
            String line = slr.next();
            if (line.length() == 0 || line.startsWith("#")) continue;

            tmp.clear();
            if(TextUtil.split(tmp, line, ',').size() < 2) {
                throw new IllegalArgumentException("fields.csv:" + i + ": ????????????: " + line);
            }
            List<Desc> descriptors = fields.get(tmp.get(0));
            if (descriptors == null)
                OUT.println("fields.csv:" + i + ": ????????????SRG: " + tmp.get(0));
            else {
                for (int j = 0; j < descriptors.size(); j++) {
                    Desc descriptor = descriptors.get(j);
                    descriptor.name = tmp.get(1);
                    descriptor.flags = NONNULL;
                }
            }
            i++;
        }
    }

    private static void parseCSVParams(String csv, Map<String, String> paramMap) {
        SimpleLineReader slr = new SimpleLineReader(csv);
        String[] currentClass = null;

        slr.skipLines(1);
        CharList cl = new CharList();
        List<String> tmp = new ArrayList<>(4);
        int i = 2;
        while (slr.hasNext()) {
            String line = slr.next();
            if (line.length() == 0 || line.startsWith("#")) continue;

            tmp.clear();
            if(TextUtil.split(tmp, line, ',').size() < 2) {
                throw new IllegalArgumentException("params.csv:" + i + ": ????????????: " + line);
            }
            paramMap.put(tmp.get(0), tmp.get(1));
            i++;
        }
    }

    public void parseMCP(File mcpFile, Map<String, String> paramMapping) throws IOException {
        if((flag & 32768) == 0 || (flag & 8192) != 0) {
            throw new IllegalStateException("Not done yet or MCP already parsed");
        }

        ZipFile zipFile = new ZipFile(mcpFile);
        Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
        while(enumeration.hasMoreElements()) {
            ZipEntry ze = enumeration.nextElement();
            String data = IOUtil.readUTF(zipFile.getInputStream(ze));
            switch (ze.getName()) {
                case "fields.csv": {
                    parseCSVFields(data);
                }
                break;
                case "methods.csv": {
                    parseCSVMethods(data);
                }
                break;
                case "params.csv": {
                    if(paramMapping != null)
                        parseCSVParams(data, paramMapping);
                }
                break;
                default:
                    System.err.println("Unexcepted file name " + ze.getName());
            }
        }

        flag |= 8192;
    }

    public void parseMCP(File mcpFile) throws IOException {
        parseMCP(mcpFile, null);
    }

    // endregion
    // region MCP ????????????

    public void applyOverride(File override) throws IOException {
        SimpleLineReader slr = new SimpleLineReader(IOUtil.readUTF(override), true);
        String src = null;
        while (slr.hasNext()) {
            String line = slr.next();
            if (line.startsWith("#")) continue;

            if (src == null) {
                src = line;
            } else {
                List<Desc> descs = fields.get(src);
                if (descs == null) descs = methods.get(src);
                if (descs == null) {
                    System.out.println("???????????? " + src);
                } else {
                    System.out.println("Override " + descs.get(0).name + "(" + src + ") => " + line);
                    for (int i = 0; i < descs.size(); i++) {
                        descs.get(i).name = line;
                    }
                }

                src = null;
            }
        }
    }

    public void extractMcp2Srg_MCP(File result) throws IOException {
        Util U = Util.getInstance();

        if((flag & 32768) == 0 || (flag & 8192) == 0) {
            throw new IllegalStateException("Not done yet or not read MCP");
        }

        ob.ensureCapacity(MINIMUM_CAPACITY);

        Map<String, CharList> classFos = new MyHashMap<>(classes.size());
        Set<String> failed = new MyHashSet<>();
        for(Map.Entry<String, List<Desc>> entry : fields.entrySet()) {
            List<Desc> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                Desc descriptor = value.get(i);
                String cn = classes.get(descriptor.owner);
                String k = entry.getKey();

                if (descriptor.flags == Desc.NULL_FLAG) {
                    if (!NTR.contains(descriptor.owner)) // forge ????????????
                        if (k.startsWith("field_") && failed.add(k)) OUT.println("?????? " + k + " ???MCP???");
                    continue;
                }

                CharList cl = classFos.get(cn);
                if (cl == null) {
                    classFos.put(cn, cl = new CharList(100));
                }

                cl.append("FL: ").append(descriptor.name).append(' ').append(k).append('\n');
            }
        }

        for(Map.Entry<String, List<Desc>> entry : methods.entrySet()) {
            List<Desc> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                Desc descriptor = value.get(i);
                String cn = classes.get(descriptor.owner);
                String k = entry.getKey();

                if (descriptor.flags == Desc.NULL_FLAG) {
                    if (!NTR.contains(descriptor.owner)) // forge ????????????
                        if (k.startsWith("field_") && failed.add(k)) OUT.println("?????? " + k + " ???MCP???");
                    continue;
                }

                String param = U.transformMethodParam(classes, descriptor.param);
                CharList cl = classFos.get(cn);
                if (cl == null) {
                    classFos.put(cn, cl = new CharList(100));
                }

                cl.append("ML: ").append(descriptor.name).append(' ').append(param).append(' ').append(k).append(" " +
                        "~\n");
            }
        }

        for(String clazz : classes.values()) {
            ob.append("CL: ").append(clazz).append(' ').append(clazz).append('\n');
            final CharList list = classFos.get(clazz);
            if(list != null)
                ob.append(list);
        }

        writeOb(result);
    }

    private void writeOb(File result) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(result)) {
            new UTFCoder().encodeTo(ob, fos);
        } finally {
            ob.clear();
        }
    }

    // endregion
    // region McpConfig ??????

    public boolean readMcpConfig(File mapFile) throws IOException {
        if((flag & 32768) != 0)
            throw new IllegalStateException("Already done!");

        ZipFile zipFile = new ZipFile(mapFile);
        String path = mapFile.getAbsolutePath();

        ZipEntry entry = zipFile.getEntry("config/joined.tsrg");
        if (entry == null) {
            CmdUtil.error("??????: ?????????????????? " + path + "#!config/joined.tsrg");
            return false;
        }
        SimpleLineReader slr = new SimpleLineReader(IOUtil.readUTF(zipFile.getInputStream(entry)));

        String[] ctx = new String[2];

        int same = 0;
        boolean maybeSame = false;

        int i = 1, fuckedArgs = 0, fuckingTsrg2 = 0;

        final ArrayList<String> list = new ArrayList<>();

        for (String line : slr) {
            if (line.length() == 0 || line.startsWith("#")) continue;
            char c = line.charAt(0);
            if (c == '\t') {
                if (ctx[0] == null) {
                    CmdUtil.error(path + "#!config/joined.tsrg:" + i + ": ?????????????????????.");
                    return false;
                }
                if(line.charAt(1) == '\t') { // 1.17.sbformat
                    if(!line.equals("\t\tstatic"))
                        fuckedArgs++;
                    continue;
                }
                list.clear();
                List<String> arr = TextUtil.split(list, line.substring(1), ' ', 3 + fuckingTsrg2);
                if(fuckingTsrg2 == 1)
                    arr.remove(arr.size() - 1);

                if (arr.size() == 2) {
                    if(maybeSame) {
                        if(!arr.get(1).equals(arr.get(0))) {
                            maybeSame = false;
                            NTR.add(ctx[0]);
                        } else {
                            continue;
                        }
                    }

                    fields.computeIfAbsent(arr.get(1), Helpers.fnArrayList()).add(new Desc(ctx[0], arr.get(0)));
                    if(revAll != null)
                        revAll.computeIfAbsent(ctx[0], Helpers.fnArrayList()).add(arr.toArray());
                } else if (arr.size() == 3) {
                    if(maybeSame) {
                        if(!arr.get(2).equals(arr.get(0))) {
                            maybeSame = false;
                            NTR.add(ctx[0]);
                        } else {
                            continue;
                        }
                    }

                    methods.computeIfAbsent(arr.get(2), Helpers.fnArrayList()).add(new Desc(ctx[0], arr.get(0), arr.get(1)));
                    if(revAll != null)
                        revAll.computeIfAbsent(ctx[0], Helpers.fnArrayList()).add(arr.toArray());
                } else {
                    CmdUtil.error(path + "#!config/joined.tsrg:" + i + ": ??????????????????: " + line);
                    return false;
                }
            } else {
                if(line.startsWith("tsrg2")) {
                    CmdUtil.warning("FUCKING TSRG2 FORMAT !!!");
                    fuckingTsrg2 = 1;
                }
                if(maybeSame) {
                    same++;
                    classes.remove(ctx[0]);
                }

                list.clear();
                TextUtil.split(list, line, ' ', 2);
                ctx[0] = list.get(0);
                ctx[1] = list.get(1);

                maybeSame = ctx[0].equals(ctx[1]);
                classes.put(ctx[0], ctx[1]);
            }
            i++;
        }

        zipFile.close();

        if(same > 0)
            CmdUtil.info("????????? " + same + " ??????????????????");
        if(fuckedArgs > 0)
            CmdUtil.info("????????? " + fuckedArgs + " ???Fucked???????????????");

        flag |= 32768;

        return true;
    }

    // endregion
    // region McpConfig ????????????

    public void extractNotch2Srg_McpConf(File output) throws IOException {
        Util U = Util.getInstance();

        if((flag & 32768) == 0 || (flag & 16384) == 0) {
            throw new IllegalStateException("Not done yet or not reverse function");
        }

        ob.ensureCapacity(MINIMUM_CAPACITY);

        for(Map.Entry<String, String> e : classes.entrySet()) {
            ob.append("CL: ").append(e.getKey()).append(' ').append(e.getValue()).append('\n');
            List<Object[]> orDefault = revAll.getOrDefault(e.getKey(), Collections.emptyList());
            for (int i = 0; i < orDefault.size(); i++) {
                Object[] descriptor = orDefault.get(i);
                if (descriptor.length == 2) {
                    ob.append("FL: ").append(descriptor[0]).append(' ').append(descriptor[1]).append('\n');
                } else {
                    String param = U.transformMethodParam(classes, (String) descriptor[1]);

                    ob.append("ML: ").append(descriptor[0]).append(' ').append(descriptor[1]).append(' ').append(descriptor[2]).append(' ').append(param.equals(descriptor[1]) ? "~" : param).append('\n');

                }
            }
        }

        writeOb(output);
        //CmdUtil.success("??????: ?????????????????? " + output.getAbsolutePath());
    }

    // endregion
    // region ????????????

    void extractMcp2Srg(File result, int flagId, Map<String, String> forge2dest, Map<String, String> mcClz) throws IOException {
        Util U = Util.getInstance();

        if((flag & (32768 | 4096 | flagId)) != (32768 | 4096)) {
            throw new IllegalStateException("Done, not read MCP or not read YARN");
        }

        ob.ensureCapacity(MINIMUM_CAPACITY);

        Map<String, CharList> classFos = new MyHashMap<>(forge2dest.size());
        for(Map.Entry<String, List<Desc>> entry : fields.entrySet()) {
            List<Desc> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                Desc descriptor = value.get(i);
                final String key = forge2dest.get(descriptor.owner);
                CharList cl = classFos.get(key);
                if (cl == null) {
                    classFos.put(key, cl = new CharList(100));
                }
                cl.append("FL: ").append(descriptor.name).append(' ').append(entry.getKey()).append('\n');
            }
        }
        for(Map.Entry<String, List<Desc>> entry : methods.entrySet()) {
            List<Desc> value = entry.getValue();
            for (int i = 0; i < value.size(); i++) {
                Desc descriptor = value.get(i);
                final String key = forge2dest.get(descriptor.owner);
                CharList cl = classFos.get(key);
                if (cl == null) {
                    classFos.put(key, cl = new CharList(100));
                }

                String mcParam = U.transformMethodParam(mcClz, descriptor.param);
                String forgeParam = U.transformMethodParam(classes, descriptor.param);

                cl.append("ML: ").append(descriptor.name).append(' ').append(mcParam).append(' ').append(entry.getKey()).append(' ').append(forgeParam).append('\n');
            }
        }

        for(Map.Entry<String, String> entry : forge2dest.entrySet()) {
            ob.append("CL: ").append(entry.getValue()).append(' ').append(entry.getKey()).append('\n');
            final CharList list = classFos.get(entry.getValue());
            if(list != null)
                ob.append(list);
        }

        writeOb(result);
        //CmdUtil.success("??????: ?????????????????? " + result.getAbsolutePath());

        flag |= flagId;
    }

    public void MCP_optimizeParamMap(Map<String, String> paramNameMap, Map<String, Map<String, List<String>>> paramClassMap) {
        Util U = Util.getInstance();

        if((flag & 32768) == 0 || (flag & 8192) == 0) {
            throw new IllegalStateException("Not done yet or not read MCP");
        }

        SimpleList<String> tmp = new SimpleList<>(4);

        Map<String, String[]> ds = new MyHashMap<>(1000);

        // func_76663_a=[MD{axx.isEmpty ()Z, flags=Flag{}}]  func ??????????????????
        for(Map.Entry<String, List<Desc>> entry : methods.entrySet()) {
            final String key = entry.getKey();
            if(!key.startsWith("func_"))
                continue;
            tmp.clear();
            TextUtil.split(tmp, key, '_');

            if(tmp.size() < 3) {
                OUT.println("[Warn]Src??????????????? " + key);
                continue;
            }
            List<Desc> value = entry.getValue();
            // aqb.onHarvest (Lamu;Let;Lawt;Laed;)Z
            Desc descriptor = value.get(0);

            ds.put(tmp.get(1), new String[]{
                    descriptor.owner,
                    descriptor.name + '|' + U.transformMethodParam(classes, descriptor.param)
            });
        }

        final Function<String, Map<String, List<String>>> fnM = Helpers.fnMyHashMap();
        final Function<String, List<String>> fnL = Helpers.fnArrayList();
        for (Map.Entry<String, String> entry : paramNameMap.entrySet()) {
            tmp.clear();
            TextUtil.split(tmp, entry.getKey(), '_');
            String[] data = ds.get(tmp.get(1));
            if(data == null) {
                if(!tmp.get(1).startsWith("i"))
                    OUT.println("[Warn]Src??????????????? " + entry.getKey());
                continue;
            }

            int entryId = /*getRealId((byte[]) data[2], */MathUtils.parseInt(tmp.get(2))/*)*/;

            List<String> list = paramClassMap.computeIfAbsent(data[0], fnM).computeIfAbsent(data[1], fnL);
            while (list.size() <= entryId) {
                list.add(null);
            }
            list.set(entryId, entry.getValue());
        }
    }

    // endregion
    // section Mojang????????????, requires McpConfig
    public final class Mojang {
        final HashBiMap<String, String> mcClz = new HashBiMap<>(2000);
        final MyHashMap<String, String> forge2mojang = new MyHashMap<>();

        public Mojang() {}

        // section MojangMap ??????

        public boolean read(File clientMap, File serverMap) throws IOException {
            if((flag & (32768 | 4096)) != 32768) {
                throw new IllegalStateException("Not done yet or already read MOJANG");
            }

            final Map<String, Map<String, String>> elementMap = new MyHashMap<>(4000);

            if (!readMojangMap(clientMap, elementMap)) return false;
            if (serverMap != null && !readMojangMap(serverMap, elementMap)) return false;

            for(Map.Entry<String, List<Desc>> entry : methods.entrySet()) {
                List<Desc> value = entry.getValue();
                for (int i = 0; i < value.size(); i++) {
                    Desc desc = value.get(i);
                    if (NTR.contains(desc.owner)) continue;
                    Map<String, String> methodNameMap = elementMap.get(desc.owner);

                    if (methodNameMap == null) {
                        if (isMCFunction(desc)) CmdUtil.error("??????????????? " + desc);
                        desc.owner = classes.getOrDefault(desc.owner, desc.owner);
                        continue;
                    }
                    String tName = methodNameMap.get(desc.name + '|' + desc.param);

                    if (tName == null) {
                        if (isMCFunction(desc)) {
                            CmdUtil.error("MCP???MC???????????????! ?????????????????? " + desc.name + '|' + desc.param);
                            OUT.println(methodNameMap);
                        }
                    } else {
                        desc.name = tName;
                    }
                    desc.owner = classes.getOrDefault(desc.owner, desc.owner);
                }
            }

            for(Map.Entry<String, List<Desc>> entry : fields.entrySet()) {
                List<Desc> value = entry.getValue();
                for (int i = 0; i < value.size(); i++) {
                    Desc desc = value.get(i);
                    if (NTR.contains(desc.owner)) continue;
                    Map<String, String> fieldNameMap = elementMap.get(desc.owner);


                    if (fieldNameMap == null) {
                        CmdUtil.error("MCP???MC???????????????! ??????????????? " + desc);
                        desc.owner = classes.getOrDefault(desc.owner, desc.owner);
                        continue;
                    }
                    String tName = fieldNameMap.get(desc.name);

                    if (tName == null) {
                        CmdUtil.error("MCP???MC???????????????! ?????????????????? " + desc.name);
                        OUT.println(fieldNameMap);
                    } else {
                        desc.name = tName;
                    }
                    desc.owner = classes.getOrDefault(desc.owner, desc.owner);
                }
            }

            forge2mojang.ensureCapacity(classes.size());

            for(Map.Entry<String, String> entry : classes.entrySet()) {
                forge2mojang.put(entry.getValue(), mcClz.getOrDefault(entry.getKey(), entry.getKey()));
            }

            flag |= 4096;

            return true;
        }

        private boolean readMojangMap(File map, Map<String, Map<String, String>> elementMap) throws IOException {
            Util U = Util.getInstance();

            SimpleLineReader slr = new SimpleLineReader(IOUtil.readUTF(new FileInputStream(map)));

            String[] currentClass = null;

            int i = 1;

            for (String line : slr) {
                if (line.length() == 0 || line.startsWith("#")) continue;
                char c = line.charAt(0);
                if (c != ' ') {
                    int index = line.indexOf(" -> ");
                    line = line.replace('.', '/');
                    currentClass = new String[] {
                            line.substring(0, index),
                            line.substring(index + 4, line.length() - 1)

                    };

                    mcClz.put(currentClass[1], currentClass[0]);
                }
                i++;
            }

            i = 1;
            final ArrayList<String> list = new ArrayList<>(2);
            for (String line : slr) {
                if (line.length() == 0 || line.startsWith("#")) continue;
                char c = line.charAt(0);
                if (c == ' ') {
                    if (currentClass == null) {
                        CmdUtil.error(map.getAbsolutePath() + ':' + i + ": ?????????????????????.");
                        return false;
                    }

                    MutableBoolean mb = new MutableBoolean();
                    FilterList<String> arr1 = (FilterList<String>) TextUtil.split(new FilterList<>((s, t1) -> {
                        if(s != null)
                            mb.set(true);
                        return true;
                    }), line.trim(), ':');
                    String val = arr1.found;

                    if (!mb.get()) {
                        if (TextUtil.limitedLastIndexOf(val, ')', 4 + 2 + 5) != -1) {
                            // SBMJ, no line number
                            mb.set(true);
                        } else {
                            // field type: arr[0]
                            list.clear();
                            final String s = TextUtil.split(list, val, ' ', 2).get(1);
                            int index = s.indexOf(" -> ");

                            elementMap.computeIfAbsent(currentClass[1], Helpers.fnMyHashMap()).put(s.substring(index + 4), s.substring(0, index));

                            continue;
                        }
                    }

                    if (mb.get()) {
                        list.clear();
                        List<String> arr = TextUtil.split(list, val, ' ', 2);
                        final String s = arr.get(1);
                        int index = s.indexOf(" -> ");

                        String arr20 = s.substring(0, index);

                        //void <init>(int) -> <init>

                        int j = arr20.indexOf('(');

                        String param = ParamHelper.dehumanize(arr20.substring(j + 1, arr20.length() - 1), arr.get(0));
                        param = U.transformMethodParam(mcClz.flip(), param);
                        // ' -> '.length
                        String targetName = s.substring(index + 4);

                        String name = arr20.substring(0, j);
                        if (!name.equals(targetName)) {
                            elementMap.computeIfAbsent(currentClass[1], Helpers.fnMyHashMap()).put(targetName + '|' + param, name);
                        }
                    } else {
                        CmdUtil.error(map.getAbsolutePath() + ':' + i + ": ??????????????????: " + line);
                        return false;
                    }
                } else {
                    int index = line.indexOf(" -> ");
                    line = line.replace('.', '/');
                    currentClass = new String[]{
                            line.substring(0, index),
                            line.substring(index + 4, line.length() - 1)

                    };
                }
                i++;
            }
            return true;
        }

        // section MojangMap ????????????

        public void extract(File result) throws IOException {
            MappingHelper.this.extractMcp2Srg(result, 131072, forge2mojang, mcClz);
        }
    }

    private static boolean isMCFunction(Desc desc) {
        return desc.name.startsWith("func_");
    }
}
