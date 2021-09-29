package roj.asm.mapper;

import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.Desc;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.TrieTreeSet;
import roj.text.CharList;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipOutputStream;

/**
 * 反混淆
 *
 * @author Roj233
 * @since 2021/8/9 23:01
 */
public final class SimpleDeobfuscator extends Obfuscator {
    public static void main(String[] args) throws Exception {
        if(args.length < 2) {
            System.out.println("Roj234's Class Deobfuscator 0.1\n" +
                    "Usage: SimpleDeobfuscator <input> <output> [config] \n" +
                    "    配置项:\n" +
                    "      mapStore [path]    => 指定class混淆表保存位置\n" +
                    "\n" +
                    "      [c,f,m]Type => 指定class,field,method是否deobf\n" +
                    "\n" +
                    "      excludes [pkg...]  => 忽略混淆的包\n" +
                    "            用'/', 比如java/lang/\n" +
                    "            用 ‘---’ 结束");
            return;
        }

        String mapStore = null;

        SimpleDeobfuscator obf = new SimpleDeobfuscator();
        obf.flags = 3;

        long time = System.currentTimeMillis();

        for(int i = 2; i < args.length; i++) {
            switch(args[i]) {
                case "excludes":
                    while (!args[++i].equals("---"))
                        obf.packageExclusions.add(args[i]);
                    break;
                case "mapStore":
                    mapStore = args[++i];
                    break;
                case "deobfs":
                    String v = args[++i];
                    obf.clazz = v.contains("c") ? 0 : -1;
                    obf.field = v.contains("f") ? 0 : -1;
                    obf.method = v.contains("m") ? 0 : -1;
                    break;
                case "names":
                    v = args[++i];
                    int o = 0;
                    o |= v.contains("p") ? 1 : 0;
                    o |= v.contains("c") ? 2 : 0;
                    o |= v.contains("f") ? 8 : 0;
                    o |= v.contains("m") ? 4 : 0;
                    obf.reFlags = o;
                    break;
                default:
                    throw new IllegalArgumentException("未知 " + args[i]);
            }
        }

        Map<String, byte[]> data = new MyHashMap<>();

        List<Context> arr = Util.ctxFromZip(new File(args[0]), StandardCharsets.ISO_8859_1, data);

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(args[1])));

        Thread writer = Util.writeResourceAsync(zos, data);

        obf.obfuscate(arr);

        if(mapStore != null)
            obf.writeObfuscationMap(new File(mapStore));

        writer.join();

        Util.write(arr, zos, true);

        System.out.println("Mem: " + (Runtime.getRuntime().totalMemory() >> 20) + " MB");
        System.out.println("Time: " + (System.currentTimeMillis() - time) + "ms");

        obf.dumpMissingClasses();
    }

    /**
     * 忽略这些package
     */
    public final TrieTreeSet packageExclusions = new TrieTreeSet();
    public final MyHashSet<String> classExclusions = new MyHashSet<>();

    public int reFlags;
    public int clazz, method, field;

    public SimpleDeobfuscator() {
        m1.checkFieldType = true;
        reFlags = 1;
    }

    CharList buf = new CharList();

    @Override
    public String obfClass(String origin) {
        if(packageExclusions.startsWith(origin) || classExclusions.contains(origin))
            return TREMINATE_THIS_CLASS;
        if(clazz == -1)
            return null;
        buf.clear();
        int i = origin.lastIndexOf('/');
        if ((reFlags & 1) != 0) {
            if(i != -1) {
                buf.append(origin, 0, i + 1);
            }
        }

        buf.append("class_").append(Integer.toString(clazz++));
        if((reFlags & 2) != 0) {
            buf.append('_').append(origin, i + 1, origin.length() - i - 1);
        }
        return buf.toString();
    }

    @Override
    public String obfMethodName(Desc desc) {
        if(method == -1)
            return null;

        if((reFlags & 4) != 0) {
            return "method_" + (method++) + '_' + desc.name;
        } else {
            return "method_" + (method++);
        }
    }

    @Override
    public String obfFieldName(Desc desc) {
        if(field == -1)
            return null;

        if((reFlags & 8) != 0) {
            return "field_" + (field++) + '_' + desc.name;
        } else {
            return "field_" + (field++);
        }
    }
}
