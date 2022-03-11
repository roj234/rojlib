package roj.mod.util;

import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.mapper.Mapping;
import roj.mapper.util.Desc;
import roj.text.SimpleLineReader;
import roj.text.TextUtil;
import roj.text.UTFCoder;
import roj.ui.CmdUtil;
import roj.util.Helpers;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Roj233
 * @since 2022/3/3 12:39
 */
public final class YarnMapping extends Mapping {
    public static void main(String[] args) throws IOException {
        YarnMapping test = new YarnMapping();
        test.load(new File(args[0]), new File(args[1]), args[2]);
        test.saveMap(new File(args[3]));
    }

    public void load(File intermediary, File mapping, String version) throws IOException {
        UTFCoder uc = IOUtil.SharedCoder.get();
        SimpleList<String> tmp = new SimpleList<>();
        readIntermediaryMap(new SimpleLineReader(new FileInputStream(intermediary)), tmp);

        final Map<String, Map<String, String>> elementMap = new MyHashMap<>(2000);

        ZipFile zf = new ZipFile(mapping);
        Enumeration<? extends ZipEntry> e = zf.entries();
        String fp = "yarn-" + version + "/mappings/";

        YarnMapping map1 = new YarnMapping();
        while (e.hasMoreElements()) {
            ZipEntry ze = e.nextElement();
            final String s = ze.getName();
            if(s.startsWith(fp) && s.endsWith(".mapping")) {
                uc.decodeFrom(zf.getInputStream(ze));
                map1.readYarnMap(new SimpleLineReader(uc.charBuf, false), tmp, null);
            }
        }

        extend(map1);
    }

    private void readYarnMap(SimpleLineReader slr, SimpleList<String> tmp, Map<String, Map<String, List<String>>> paramMap) {
        int i = 1;

        String cls = null;
        Desc method = null;

        for (String line : slr) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) continue;

            tmp.clear();
            TextUtil.split(tmp, line, ' ', 5);
            switch (tmp.get(0)) {
                case "CLASS":
                    cls = tmp.get(1);
                    classMap.put(tmp.get(1), tmp.get(2));
                    break;
                case "METHOD":
                    if (tmp.size() > 3) {
                        // METHOD intermediary_name new_name type
                        methodMap.put(method = new Desc(cls, tmp.get(1), tmp.get(3)), tmp.get(2));
                    //} else {
                    //     METHOD name type
                    }
                    break;
                case "ARG":
                    if (method == null)
                        throw new IllegalArgumentException("ARG出现在METHOD前");
                    // ARG index name
                    if (paramMap != null) {
                        List<String> list = paramMap.computeIfAbsent(cls, Helpers.fnMyHashMap())
                                                    .computeIfAbsent(method.name + '|' + method.param,
                                                                     Helpers.fnArrayList());
                        int index = Integer.parseInt(tmp.get(1)) - 1;
                        while (list.size() <= index) list.add(null);
                        list.set(index, tmp.get(2));
                    }
                    break;
                case "FIELD":
                    // FIELD intermediary_name new_name type
                    fieldMap.put(new Desc(cls, tmp.get(1), tmp.get(3)), tmp.get(2));
                    break;
                default:
                    CmdUtil.error(":" + i + ": 未知标记类型.");
            }
            i++;
        }
    }

    private void readIntermediaryMap(SimpleLineReader slr, SimpleList<String> tmp) {
        int i = 2;

        slr.skipLines(1);
        for (String line : slr) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) continue;

            tmp.clear();
            TextUtil.split(tmp, line, '\t', 5);
            switch (tmp.get(0)) {
                case "CLASS":
                    classMap.put(tmp.get(1), tmp.get(2));
                    break;
                case "METHOD":
                    // METHOD owner original_name param intermediary_name
                    methodMap.put(new Desc(tmp.get(1), tmp.get(3), tmp.get(2)), tmp.get(4));
                    break;
                case "FIELD":
                    // FIELD owner original_name type intermediary_name
                    fieldMap.put(new Desc(tmp.get(1), tmp.get(3), tmp.get(2)), tmp.get(4));
                    break;
                default:
                    CmdUtil.error(":" + i + ": 未知标记类型.");
            }
            i++;
        }
    }
}
