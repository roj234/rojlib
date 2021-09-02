package roj.asm.mapper;

import roj.asm.mapper.obf.*;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.FlDesc;
import roj.asm.mapper.util.MtDesc;
import roj.asm.tree.ConstantData;
import roj.asm.tree.attr.*;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.simple.FieldSimple;
import roj.asm.tree.simple.MethodSimple;
import roj.asm.type.*;
import roj.asm.util.AttributeList;
import roj.asm.util.IGeneric;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.ToIntMap;
import roj.collect.TrieTreeSet;
import roj.config.JSONParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipOutputStream;

/**
 * 混淆名与原名完全无关时使用我
 *
 * @author Roj233
 * @since 2021/7/18 19:06
 */
public final class SimpleObfuscator extends Obfuscator {
    public static final int
            CLEAR_ATTR = 4,
            FAKE_SIGN = 8,
            INVALID_VAR_NAME = 16,
            FAKE_VAR_SIGN = 32,
            RANDOMIZED_SIGN = 64,
            DESTROY_VAR = 128,
            DESTROY_LINE = 256,
            CLEAR_VAR_NAME = 512,
            KEEP_CP = 1024,
            OBF_STRING = 2048;
    public static final int MAX_LEN = Integer.parseInt(System.getProperty("roj.obf.sign_max_len", "63"));

    public static void main(String[] args) throws Exception {
        if(args.length < 2) {
            System.out.println("Roj234's Class Obfuscator 0.2\n" +
                    "Usage: SimpleObfuscator <input> <output> [config] \n" +
                    "    配置项:\n" +
                    "      mapStore [path]    => 指定class混淆表保存位置\n" +
                    "      libPath [path]     => 指定库位置\n" +
                    "      lineLog [path]     => 指定行号混淆表保存位置\n" +
                    "      flag [int]         => 混淆flag\n" +
                    "            1 给方法和字段加上synthesis标记\n" +
                    "            4 删除可选属性\n" +
                    "            8 添加错误的泛型\n" +
                    "           16 添加无效的变量名 (慢)\n" +
                    "           32 添加错误的变量泛型 (慢)\n" +
                    "           64 泛型随机化\n" +
                    "          128 炸了变量属性! (部分JVM不支持)\n" +
                    "          256 炸了行号\n" +
                    "          512 删除code可选属性 (慢)\n" +
                    "         1024 保留常量池 (仅用于DEBUG)\n" +
                    "         2048 加密字符串 (很慢) (还没做，还没做，还没做，重要的事情说三遍)\n" +
                    "\n" +
                    "           把喜欢的数字加起来\n" +
                    "\n" +
                    "      seed [int]         => 随机数种子,同样的种子混淆出的内容相同\n" +
                    "      [c,f,m]Type [type] => 指定class,field,method混淆类别\n" +
                    "            w : windows保留字 [c]\n" +
                    "            i : i L 1 I      [cfm]\n" +
                    "            m [ch] : 字符混合 [cfm]\n" +
                    "            a : abc          [cfm]\n" +
                    "            f : 文件(一行一个) [cfm]\n" +
                    "            k : java保留字    [cfm]\n" +
                    "\n" +
                    "      noPkg              => 不要保留包名\n" +
                    "      excludes [pkg...]  => 忽略混淆的包\n" +
                    "            用'/', 比如java/lang/\n" +
                    "            用 ‘---’ 结束\n" +
                    "      cs [xx]            => 文件编码\n" +
                    "      cfg [file]         => 加载配置文件\n" +
                    "\n" +
                    "cfmType不选不混淆, 目录不输不保存\n" +
                    "如果你觉得配置项不够多, 打开roj.asm.mapper包来和我一起play吧");
            return;
        }

        String mapStore = null, lineLog = null;

        Charset charset = File.separatorChar == '/' ? StandardCharsets.UTF_8 : StandardCharsets.US_ASCII;

        SimpleObfuscator obf = new SimpleObfuscator();

        long time = System.currentTimeMillis();

        for(int i = 2; i < args.length; i++) {
            switch(args[i]) {
                case "noPkg":
                    if(obf.clazz == null)
                        throw new IllegalArgumentException("noPkg选项要放在cType之后");
                    if(!(obf.clazz instanceof SimpleNamer))
                        throw new IllegalArgumentException("cType不支持");
                    ((SimpleNamer) obf.clazz).setKeepPackage(false);
                    obf.flags |= 2;
                    break;
                case "excludes":
                    while (!args[++i].equals("---"))
                        obf.packageExclusions.add(args[i]);
                    break;
                case "flag":
                    obf.setFlags(Integer.parseInt(args[++i]));
                    break;
                case "seed":
                    obf.rand.setSeed(Long.parseLong(args[++i]));
                    break;
                case "cfg":
                    CMapping map = JSONParser.parse(IOUtil.readAsUTF(new FileInputStream(args[++i]))).asMap();
                    obf.packageExclusions.addAll(map.get("keepPackages").asList().asStringList());
                    obf.classExclusions.addAll(map.get("keepClasses").asList().asStringList());
                    for (CEntry entry : map.get("keep").asList()) {
                        CList list = entry.asList();
                        if(list.size() == 3) {
                            obf.libMethods.add(new MtDesc(list.get(0).asString(), list.get(1).asString(), list.get(2).asString()));
                        } else {
                            obf.libFields.add(new FlDesc(list.get(0).asString(), list.get(1).asString()));
                        }
                    }
                    obf.flags = map.getInteger("flag");
                    if(map.containsKey("seed"))
                        obf.rand.setSeed(map.getLong("seed"));
                    if(map.containsKey("mapStore"))
                        mapStore = map.getString("mapStore");
                    if(map.containsKey("charset"))
                        charset = Charset.forName(map.getString("charset"));
                    if(map.getBool("noPackage")) {
                        if(obf.clazz == null)
                            throw new IllegalArgumentException("noPkg选项要放在cType之后");
                        if(!(obf.clazz instanceof SimpleNamer))
                            throw new IllegalArgumentException("cType不支持noPkg");
                        ((SimpleNamer) obf.clazz).setKeepPackage(false);
                        obf.flags |= 2;
                    }
                    break;
                case "mapStore":
                    mapStore = args[++i];
                    break;
                case "lineLog":
                    obf.lineLog = new CharList();
                    lineLog = args[++i];
                    break;
                case "libPath":
                    obf.reset(FileUtil.findAllFiles(new File(args[++i]), file -> file.getName().endsWith(".jar") || file.getName().endsWith(".zip")));
                    break;
                case "cs":
                    charset = Charset.forName(args[++i]);
                    break;
                case "cType":
                    i = resolveType(obf, args, i, 0);
                    break;
                case "fType":
                    i = resolveType(obf, args, i, 1);
                    break;
                case "mType":
                    i = resolveType(obf, args, i, 2);
                    break;
                default:
                    throw new IllegalArgumentException("未知 " + args[i]);
            }
        }

        Map<String, byte[]> data = new MyHashMap<>();

        List<Context> arr = Util.ctxFromZip(new File(args[0]), charset, data);

        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(args[1])));

        Thread writer = Util.writeResourceAsync(zos, data);

        obf.obfuscate(arr);

        if(mapStore != null)
            obf.writeObfuscationMap(new File(mapStore));

        if (lineLog != null) {
            try(FileOutputStream fos = new FileOutputStream(new File(lineLog))) {
                ByteWriter.encodeUTF(obf.lineLog).writeToStream(fos);
            }
        }

        writer.join();

        Util.write(arr, zos, true);

        System.out.println("Mem: " + (Runtime.getRuntime().totalMemory() >> 20) + " MB");
        System.out.println("Time: " + (System.currentTimeMillis() - time) + "ms");

        obf.dumpMissingClasses();
    }

    private static int resolveType(SimpleObfuscator o, String[] args, int i, int t) {
        SimpleNamer fn = null;
        NamingFunction fn2 = null;
        switch (args[++i]) {
            case "e":
                fn2 = Equal.INSTANCE;
                break;
            case "c":
                fn2 = new Compress();
                break;
            case "w":
                fn = new WindowsReserved();
                break;
            case "i":
                fn = CharMixture.newIII(7, 7);
                break;
            case "m":
                fn = new CharMixture(args[++i], 1, 7);
                break;
            case "a":
                fn = CharMixture.newABC(1, 7);
                break;
            case "f":
                try {
                    fn = new StringList(new File(args[++i]));
                } catch (IOException e) {
                    throw new IllegalArgumentException("Unable read file ", e);
                }
                break;
            case "k":
                fn = StringList.newJavaKeywordExtended();
                break;
            default:
                throw new IllegalArgumentException("Unknown " + args[i-1]);
        }

        if(fn != null) {
            fn.setKeepPackage(true).setFallback(CharMixture.newABC(1, 30).setKeepPackage(true));
            fn2 = fn;
        }

        switch (t) {
            case 0:
                o.clazz = fn2;
                break;
            case 1:
                o.field = fn2;
                break;
            case 2:
                o.method = fn2;
                break;
        }
        return i;
    }

    /**
     * 忽略这些package
     */
    public final TrieTreeSet packageExclusions = new TrieTreeSet();
    public final MyHashSet<String> classExclusions = new MyHashSet<>();

    public final Random rand;
    public NamingFunction clazz, method, field, param;
    final MyHashSet<String> tempF = new MyHashSet<>(), tempM = new MyHashSet<>(), classes = new MyHashSet<>();
    public CharList lineLog;

    public SimpleObfuscator() {
        this.rand = new Random();
    }

    public SimpleObfuscator(Random rand) {
        this.rand = rand;
    }

    public SimpleObfuscator(NamingFunction clazz, NamingFunction method, NamingFunction field) {
        this.rand = new Random();
        this.clazz = clazz;
        this.method = method;
        this.field = field;
    }

    public SimpleObfuscator(NamingFunction clazz, NamingFunction method, NamingFunction field, Random rand) {
        this.rand = rand;
        this.clazz = clazz;
        this.method = method;
        this.field = field;
    }

    public void obfuscate(List<Context> arr) {
        classes.clear();
        super.obfuscate(arr);
    }

    @Override
    protected void beforeMapCode(List<Context> arr) {
        if((flags & CLEAR_ATTR) != 0)
            for (int i = 0; i < arr.size(); i++) {
                clearSign(arr.get(i).getData());
            }
    }

    @Override
    protected void afterMapCode(List<Context> arr) {
        if((flags & FAKE_SIGN) != 0)
            for (int i = 0; i < arr.size(); i++) {
                fakeSign(arr.get(i).getData());
            }
        if((flags & (INVALID_VAR_NAME | FAKE_VAR_SIGN)) != 0)
            for (int i = 0; i < arr.size(); i++) {
                codeSign(arr.get(i).getData());
            }
    }

    static final List<IGeneric>                   fake  = Collections.singletonList(Type.std(NativeType.INT));
    static final Map<String, Collection<Generic>> fake2 = new MyHashMap<>();
    static {
        fake2.put("\u0000", Collections.singletonList(new Generic(Generic.TYPE_TYPE_PARAM, "int", 23, Generic.EX_SUPERS)));
        fake2.put("\u0001", Collections.singletonList(new Generic(Generic.TYPE_INTERFACE, "long", 0, Generic.EX_EXTENDS)));
        fake2.put("\u0002", Collections.singletonList(new Generic(Generic.TYPE_SUB_CLASS, "double", 0, Generic.EX_SUPERS)));
        fake2.put("\u0003", Collections.singletonList(new Generic(Generic.TYPE_INTERFACE, "short", 46, Generic.EX_NONE)));
        fake2.put("\u0004", Collections.singletonList(new Generic(Generic.TYPE_CLASS, "char", 0, Generic.EX_SUPERS)));
    }

    static void clearSign(ConstantData data) {
        AttributeList al = data.attributes;
        al.removeByName("InnerClasses");
        al.removeByName("Signature");
        al.removeByName("SourceFile");
        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple m = methods.get(i);
            AttributeList al1 = m.attributes;
            al1.removeByName("Signature");
        }
        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple m = fields.get(i);
            AttributeList al1 = m.attributes;
            al1.removeByName("Signature");
        }
    }

    void fakeSign(ConstantData data) {
        AttributeList al = data.attributes;

        Signature sign1 = getSign(Signature.CLASS);

        al.putByName(new AttrUTF("Signature", sign1.toGeneric()));

        sign1 = getSign(Signature.METHOD);

        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple m = methods.get(i);
            AttributeList al1 = m.attributes;
            al1.putByName(new AttrUTF("Signature", sign1.toGeneric()));
        }

        sign1 = getSign(Signature.FIELD_OR_CLASS);

        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple m = fields.get(i);
            AttributeList al1 = m.attributes;
            al1.putByName(new AttrUTF("Signature", sign1.toGeneric()));
        }
    }

    void codeSign(ConstantData data) {
        Signature sign2 = getSign(Signature.METHOD);

        List<MethodSimple> methods = data.methods;

        if (lineLog != null && (flags & DESTROY_LINE) != 0) {
            lineLog.append(data.name).append('\n');
        }

        for (int i = 0; i < methods.size(); i++) {
            MethodSimple m = methods.get(i);
            AttributeList al1 = m.attributes;
            AttrUnknown code0 = (AttrUnknown) al1.getByName("Code");
            if(code0 == null)
                continue;

            AttrCode code1 = new AttrCode(m, code0.getRawData(), data.cp);
            al1.putByName(code1);
            al1 = code1.attributes;

            if((flags & CLEAR_VAR_NAME) != 0) {
                al1.removeByName("LocalVariableTypeTable");
                al1.removeByName("LocalVariableTable");
                al1.removeByName("LineNumberTable");
            } else {
                if ((flags & DESTROY_LINE) != 0) {
                    AttrLineNumber lv = (AttrLineNumber) al1.getByName("LineNumberTable");
                    if (lv != null) {
                        if(lineLog != null)
                            lineLog.append(" ").append(m.name.getString()).append(' ').append(m.type.getString()).append('\n');
                        for (ToIntMap.Entry<InsnNode> e : lv.map.selfEntrySet()) {
                            int v = rand.nextInt(65536);
                            if(lineLog != null)
                                lineLog.append("  ").append(e.v).append(' ').append(v).append('\n');
                            e.v = v;
                        }
                    }
                }
                if ((flags & FAKE_VAR_SIGN) != 0) {
                    AttrLocalVars lv = (AttrLocalVars) al1.getByName("LocalVariableTypeTable");
                    if (lv != null) {
                        for (LocalVariable v : lv.list) {
                            v.name = "";
                            v.type = getSign(Signature.CLASS);
                        }
                    }
                }
                if ((flags & INVALID_VAR_NAME) != 0) {
                    AttrLocalVars lv = (AttrLocalVars) al1.getByName("LocalVariableTable");
                    if (lv != null) {
                        for (LocalVariable v : lv.list) {
                            v.name = "";
                            if ((flags & DESTROY_VAR) != 0) {
                                v.type = randType();
                                InsnNode e = v.end;
                                v.end = v.start;
                                v.start = e;
                                v.slot = rand.nextInt();
                            }
                        }
                    }
                }
            }
        }
    }

    private Signature getSign(byte type) {
        Signature sign1 = new Signature(type);
        if((flags & RANDOMIZED_SIGN) == 0) {
            sign1.throwsException = fake;
            sign1.values = fake;
            sign1.returns = Type.std(NativeType.LONG);
            sign1.genericTypeMap = fake2;
        } else {
            int len = rand.nextInt(4);
            ArrayList<IGeneric> gens = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                gens.add(randAny());
            }
            sign1.throwsException = gens;

            len = rand.nextInt(7);
            gens = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                gens.add(randType());
            }
            sign1.values = gens;

            sign1.returns = randType();

            len = rand.nextInt(6);
            Map<String, Collection<Generic>> map = new MyHashMap<>(len);
            for (int i = 0; i < len; i++) {
                int len2 = rand.nextInt(4);
                ArrayList<Generic> gens1 = new ArrayList<>(len2);
                for (int j = 0; j < len2; j++) {
                    gens1.add(randGen());
                }
                map.put(Integer.toString(rand.nextInt(1000000), 36), gens1);
            }
            sign1.genericTypeMap = map;
        }
        return sign1;
    }

    private Generic randGen() {
        return new Generic((byte) rand.nextInt(2), rand.nextFloat() > 0.233 ? NativeType.toString(randType().type) : Integer.toString(rand.nextInt(1000000), 36), rand.nextInt(MAX_LEN), (byte) (rand.nextInt(3) - 1));
    }

    private Type randType() {
        switch (rand.nextInt(9)) {
            case 0:
                return Type.std(NativeType.LONG);
            case 1:
                return Type.std(NativeType.DOUBLE);
            case 2:
                return Type.std(NativeType.VOID);
            case 3:
                return Type.std(NativeType.ARRAY);
            case 4:
                return Type.std(NativeType.BOOLEAN);
            case 5:
                return Type.std(NativeType.BYTE);
            case 6:
                return Type.std(NativeType.CHAR);
            case 7:
                return Type.std(NativeType.FLOAT);
            case 8:
            default:
                return Type.std(NativeType.SHORT);
        }
    }

    private IGeneric randAny() {
        return rand.nextBoolean() ? randType() : randGen();
    }

    @Override
    public String obfClass(String origin) {
        if(packageExclusions.startsWith(origin) || classExclusions.contains(origin))
            return TREMINATE_THIS_CLASS;
        if(clazz == null)
            return null;

        tempF.clear();
        tempM.clear();

        return clazz.obfClass(origin, classes, rand);
    }

    @Override
    public String obfMethodName(MtDesc desc) {
        return method == null ? null : method.obfName(tempM, desc.param, rand);
    }

    @Override
    public String obfFieldName(FlDesc desc) {
        return field == null ? null : field.obfName(tempF, null, rand);
    }
}
