package roj.asm.mapper;

import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.MtDesc;
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

import static roj.asm.mapper.Obfuscator.TREMINATE_THIS_CLASS;

/**
 * 反混淆
 *
 * @author Roj233
 * @since 2021/8/9 23:01
 */
public final class SimpleDeobfuscator extends Deobfuscator {
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
                case "noPkg":
                    obf.pkg = false;
                    break;
                case "cType":
                    obf.clazz = 0;
                    break;
                case "fType":
                    obf.field = 0;
                    break;
                case "mType":
                    obf.method = 0;
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

    public boolean pkg;
    public int clazz, method, field;

    public SimpleDeobfuscator() {
        pkg = true;
        clazz = method = field = -1;
    }

    CharList buf = new CharList();

    @Override
    public String obfClass(String origin) {
        if(packageExclusions.startsWith(origin) || classExclusions.contains(origin))
            return TREMINATE_THIS_CLASS;
        if(clazz == -1)
            return null;
        buf.clear();
        if (pkg) {
            int i = origin.lastIndexOf('/');
            if(i != -1) {
                buf.append(origin, 0, i + 1);
            }
        }

        return buf.append("c_" + (clazz++)).toString();
    }

    @Override
    public String obfMethodName(MtDesc desc) {
        if(method == -1)
            return null;

        return "m_" + (method++);
    }

    @Override
    public String obfFieldName(MtDesc desc) {
        if(field == -1)
            return null;

        return "f_" + (field++);
    }
}
