package roj.asm.mapper;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.mapper.CodeMapper.SimpleVar;
import roj.asm.mapper.obf.policy.*;
import roj.asm.mapper.util.Context;
import roj.asm.mapper.util.Desc;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUTF;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.insn.InsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.insn.LdcInsnNode;
import roj.asm.tree.insn.NPInsnNode;
import roj.asm.type.*;
import roj.asm.util.*;
import roj.asm.visitor.CodeVisitor;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.TrieTreeSet;
import roj.config.JSONParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.io.ZipFileWriter;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 混淆名与原名完全无关时使用我
 *
 * @author Roj233
 * @since 2021/7/18 19:06
 */
public final class SimpleObfuscator extends Obfuscator {
    public static final int
            CLEAR_ATTR       = 4,
            FAKE_SIGN        = 8,
            INVALID_VAR_NAME = 16,
            FAKE_VAR_SIGN    = 32,
            RANDOMIZED_SIGN  = 64,
            DESTROY_VAR      = 128,
            DESTROY_LINE     = 256,
            CLEAR_CODE_ATTR  = 512,
            KEEP_LINES       = 1024,
            OBF_STRING       = 2048;
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
                    "      cfg [file]         => 加载配置文件\n");
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
                    obf.clazz.setKeepPackage(false);
                    obf.flags |= 2;
                    break;
                case "excludes":
                    while (!args[++i].equals("---"))
                        obf.packageExclusions.add(args[i]);
                    break;
                case "keepClasses":
                    while (!args[++i].equals("---"))
                        obf.classExclusions.add(args[i]);
                    break;
                case "flag":
                    obf.setFlags(Integer.parseInt(args[++i]));
                    break;
                case "seed":
                    obf.rand.setSeed(Long.parseLong(args[++i]));
                    break;
                case "cfg":
                    CMapping map = JSONParser.parse(IOUtil.readUTF(new FileInputStream(args[++i]))).asMap();
                    obf.packageExclusions.addAll(map.get("keepPackages").asList().asStringList());
                    obf.classExclusions.addAll(map.get("keepClasses").asList().asStringList());
                    for (CEntry entry : map.get("keep").asList()) {
                        CList list = entry.asList();
                        Desc key = new Desc(list.get(0).asString(), list.get(1).asString(), list.get(2).asString());
                        (key.param.indexOf('(') > 0 ? obf.m1.getMethodMap() : obf.m1.getFieldMap()).put(key, key.name);
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
                        obf.clazz.setKeepPackage(false);
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
                case "evaluate":
                    obf.eval = true;
                    break;
                default:
                    throw new IllegalArgumentException("未知 " + args[i]);
            }
        }

        Map<String, byte[]> data = new MyHashMap<>();

        List<Context> arr = Util.ctxFromZip(new File(args[0]), charset, data);

        ZipFileWriter zfw = new ZipFileWriter(new File(args[1]));

        Thread writer = Util.writeResourceAsync(zfw, data);

        obf.obfuscate(arr);

        if(mapStore != null)
            obf.writeObfuscationMap(new File(mapStore));

        if (lineLog != null) {
            try(FileOutputStream fos = new FileOutputStream(new File(lineLog))) {
                ByteWriter.encodeUTF(obf.lineLog).writeToStream(fos);
            }
        }

        writer.join();

        for (int i = 0; i < arr.size(); i++) {
            Context ctx = arr.get(i);
            zfw.writeNamed(ctx.getFileName(), ctx.getCompressedShared());
        }
        zfw.finish();

        System.out.println("Mem: " + (Runtime.getRuntime().totalMemory() >> 20) + " MB");
        System.out.println("Time: " + (System.currentTimeMillis() - time) + "ms");

        obf.dumpMissingClasses();
    }

    private static int resolveType(SimpleObfuscator o, String[] args, int i, int t) {
        NamingFunction fn;
        switch (args[++i]) {
            case "deobf":
                fn = new Deobfuscate();
                break;
            case "e":
                fn = Equal.INSTANCE;
                break;
            case "w":
                fn = new WindowsReserved();
                break;
            case "i":
                fn = CharMixture.newIII(10, 10);
                break;
            case "m":
                fn = new CharMixture(args[++i], 1, 7);
                break;
            case "a":
                fn = new ClassicABC();
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

        fn.setKeepPackage(true);

        switch (t) {
            case 0:
                o.clazz = fn;
                break;
            case 1:
                o.field = fn;
                break;
            case 2:
                o.method = fn;
                break;
        }
        return i;
    }

    /**
     * 忽略这些package
     */
    public TrieTreeSet       packageExclusions = new TrieTreeSet();
    public MyHashSet<String> classExclusions   = new MyHashSet<>();

    public final Random rand;
    public NamingFunction clazz, method, field, param;
    final MyHashSet<String> tempF = new MyHashSet<>(), tempM = new MyHashSet<>(), classes = new MyHashSet<>();
    public CharList lineLog;
    public boolean eval;

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

    static StackTraceElement[] syncStackTrace = new StackTraceElement[2];
    public static StackTraceElement[] _syncGetStackTrace() {
        return syncStackTrace;
    }

    @Override
    protected void afterMapCode(List<Context> arr) {
        eva:
        if (eval) {
            MyCodeVisitor mcv = new MyCodeVisitor();
            for (int i = 0; i < arr.size(); i++) {
                ConstantData data = arr.get(i).getData();
                List<MethodSimple> methods = data.methods;

                for (int j = 0; j < methods.size(); j++) {
                    MethodSimple m = methods.get(j);
                    AttrUnknown code0 = (AttrUnknown) m.attributes.getByName("Code");
                    if(code0 == null) continue;
                    mcv.initNil(code0.getRawData());
                    mcv.visit(data.cp);
                }
            }
            if (mcv.methods.isEmpty()) {
                System.out.println("没有找到疑似字符串解密方法");
                break eva;
            }
            Desc desc = new Desc("", "");
            desc.param = "(Ljava/lang/String;)Ljava/lang/String;";
            for (int i = 0; i < arr.size(); i++) {
                ConstantData data = arr.get(i).getData();
                List<MethodSimple> methods = data.methods;

                desc.owner = m1.classMap.getOrDefault(data.name, data.name);
                for (int j = methods.size() - 1; j >= 0; j--) {
                    MethodSimple m = methods.get(j);
                    if (!m.type.getString().equals("(Ljava/lang/String;)Ljava/lang/String;")) continue;
                    desc.name = m.name.getString();
                    if (mcv.methods.containsKey(desc)) {
                        Clazz dg = new Clazz(data.version, AccessFlag.PUBLIC | AccessFlag.SUPER_OR_SYNC,
                                             "roj/reflect/gen" + Math.abs(System.nanoTime() % 9999999),
                                             "java/lang/Object");
                        dg.methods.add(new Method(data, m));
                        InsnList ins = dg.methods.get(0).code.instructions;
                        for (int k = 0; k < ins.size(); k++) {
                            InsnNode node = ins.get(k);
                            if(node.nodeType() == InsnNode.T_INVOKE) {
                                InvokeInsnNode cin = (InvokeInsnNode) node;
                                if (cin.owner.equals("java/lang/Throwable") || (
                                        cin.owner.startsWith("java/lang/") &&
                                        cin.owner.endsWith("Exception"))) {
                                    if (cin.name.equals("getStackTrace")) {
                                        cin.code = Opcodes.INVOKESTATIC;
                                        cin.owner = SimpleObfuscator.class.getName().replace('.', '/');
                                        cin.name = "_syncGetStackTrace";
                                        ins.add(k, NPInsnNode.of(Opcodes.POP));
                                        System.out.println("找到stack trace 调用！");
                                    }
                                }
                            }
                        }
                        ByteList sh = Parser.toByteArrayShared(dg);
                        Class<?> df = ClassDefiner.INSTANCE.defineClassC(
                                dg.name.replace('/', '.'),
                                sh.list, 0, sh.pos());
                        Decoder dar = DirectAccessor.builder(Decoder.class)
                                .delegate(df, desc.name, "decode").build();
                        mcv.methods.put(desc, dar);
                        // 在这里删除了解密方法，也许可以不用删？
                        methods.remove(j);
                    }
                }
            }

            MyHashSet<String> done = new MyHashSet<>();
            boolean ch;
            for (int i = 0; i < arr.size(); i++) {
                ConstantData data = arr.get(i).getData();
                List<MethodSimple> methods = data.methods;

                ch = false;
                for (int j = 0; j < methods.size(); j++) {
                    MethodSimple m = methods.get(j);
                    AttrUnknown code0 = (AttrUnknown) m.attributes.getByName("Code");
                    if(code0 == null) continue;
                    AttrCode code = new AttrCode(m, code0.getRawData(), data.cp);
                    InsnList insn = code.instructions;
                    for (int k = 0; k < insn.size(); k++) {
                        InsnNode node = insn.get(k);
                        if (node.nodeType() == InsnNode.T_LDC) {
                            LdcInsnNode ldc = (LdcInsnNode) node;
                            if (ldc.c.type() == CstType.STRING) {
                                InsnNode next = insn.get(k + 1);
                                if (next.nodeType() == InsnNode.T_INVOKE) {
                                    InvokeInsnNode iin = (InvokeInsnNode) next;
                                    if (iin.code == Opcodes.INVOKESTATIC && iin.rawDesc().equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                                        CstUTF utf = ((CstString) ldc.c).getValue();
                                        if (!done.contains(utf.getString())) {
                                            String value = mcv.tryDecode(iin, null, utf);
                                            if (value != null) {
                                                done.add(value);
                                                data.cp.setUTFValue(utf, value);
                                            } else {
                                                continue;
                                            }
                                        }
                                        ch = true;
                                        insn.remove(++k)._i_replace(ldc);
                                    }
                                }
                            }
                        }
                    }
                    if (ch) {
                        m.attributes.putByName(code);
                    }
                }
                done.clear();
            }
        }
        if((flags & FAKE_SIGN) != 0)
            for (int i = 0; i < arr.size(); i++) {
                fakeSign(arr.get(i).getData());
            }
        if((flags & (INVALID_VAR_NAME | FAKE_VAR_SIGN | DESTROY_LINE | CLEAR_CODE_ATTR)) != 0)
            for (int i = 0; i < arr.size(); i++) {
                if (arr.get(i).getFileName().equals("cbk.class"))
                    arr.get(i).getData().dump();
                codeSign(arr.get(i).getData());
            }
    }

    interface Decoder {
        String decode(String str);
    }

    static class MyCodeVisitor extends CodeVisitor {
        MyCodeVisitor() {
            this.bw = new ByteWriter(null);
            this.br = new ByteReader();
            this.methods = new MyHashMap<>();
            this.tmp = new Desc("", "");
        }

        public void initNil(ByteList data) {
            this.br.refresh(data);
            this.cw = new ConstantPoolEmpty();
            this.bw.list = new ByteList.EmptyByteList();
        }

        int ldcPos;
        Desc tmp;
        MyHashMap<Desc, Decoder> methods;

        @Override
        public void ldc(byte code, Constant c) {
            if (c.type() == CstType.STRING) {
                ldcPos = br.index;
            }
        }

        @Override
        public void invoke(byte code, CstRef method) {
            if (code == Opcodes.INVOKESTATIC && method.desc().getType().getString().equals("(Ljava/lang/String;)Ljava/lang/String;") && br.index == ldcPos + 3) {
                methods.putIfAbsent(tmp.read(method).copy(), null);
            }
        }

        public String tryDecode(InvokeInsnNode iin, MethodSimple caller, CstUTF utf) {
            if (caller != null) {
                syncStackTrace[syncStackTrace.length - 1] = new StackTraceElement(iin.owner.replace('/', '.'), iin.name, "SourceFile", -1);
                // 如果用到了line的话那只好自己再弄啦，也就是麻烦一点，多读取一些属性的事
                syncStackTrace[syncStackTrace.length - 2] = new StackTraceElement(caller.ownerClass().replace('/', '.'), caller.name(), "SourceFile", -1);
            }
            tmp.owner = iin.owner;
            tmp.name = iin.name;
            tmp.param = iin.rawDesc();
            Decoder dec = methods.get(tmp);
            if (dec != null)
                try {
                    return dec.decode(utf.getString());
                } catch (Throwable e) {
                    System.err.println("解密失败: ");
                    e.printStackTrace();
                }
            return null;
        }
    }

    static final List<IGeneric>                   fake  = Collections.singletonList(Type.std(NativeType.INT));
    static final Map<String, List<Generic>> fake2 = new MyHashMap<>();
    static {
        fake2.put("\u0000", Collections.singletonList(new Generic(Generic.TYPE_TYPE_PARAM, "int", 23, Generic.EX_SUPERS)));
        fake2.put("\u0001", Collections.singletonList(new Generic(Generic.TYPE_INHERIT_CLASS, "long", 0, Generic.EX_EXTENDS)));
        fake2.put("\u0002", Collections.singletonList(new Generic(Generic.TYPE_SUB_CLASS, "double", 0, Generic.EX_SUPERS)));
        fake2.put("\u0003", Collections.singletonList(new Generic(Generic.TYPE_INHERIT_CLASS, "short", 46, Generic.EX_NONE)));
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

        if (data.methods.isEmpty() || data.fields.isEmpty() || rand.nextFloat() > 0.11f)
            al.putByName(new AttrUTF("Signature", sign1.toGeneric()));

        sign1 = getSign(Signature.METHOD);

        List<MethodSimple> methods = data.methods;
        for (int i = 0; i < methods.size(); i++) {
            MethodSimple m = methods.get(i);
            AttributeList al1 = m.attributes;
            if (rand.nextFloat() > 0.66f)
                al1.putByName(new AttrUTF("Signature", sign1.toGeneric()));
        }

        sign1 = getSign(Signature.FIELD_OR_CLASS);

        List<FieldSimple> fields = data.fields;
        for (int i = 0; i < fields.size(); i++) {
            FieldSimple m = fields.get(i);
            AttributeList al1 = m.attributes;
            if (rand.nextFloat() > 0.66f)
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
            AttrUnknown au = (AttrUnknown) m.attributes.getByName("Code");
            if(au == null)
                continue;

            ByteReader r = Parser.reader(au);
            ByteWriter w = new ByteWriter(r.remain());
            r.index += 4; // stack size
            int codeLen = r.readInt();
            r.index += codeLen; // code

            int len = r.readUnsignedShort(); // exception
            r.index += len << 3;
            w.writeBytes(r.getBytes().subList(0, r.index));

            ConstantPool pool = data.cp;
            len = r.readUnsignedShort();

            int count = 0;
            int countIdx = w.list.pos();
            w.writeShort(0);

            System.out.println(m);
            for (int j = 0; j < len; j++) {
                String name = ((CstUTF) pool.get(r)).getString();
                int end = r.readInt() + r.index;
                switch (name) {
                    case "LocalVariableTable":
                        if ((flags & (INVALID_VAR_NAME | DESTROY_VAR)) != 0) {
                            count++;
                            List<SimpleVar> list = CodeMapper.readVar(pool, r);
                            w.writeShort(data.cp.getUtfId(name)).writeInt(len)
                             .writeShort(list.size());
                            for (int k = 0; k < list.size(); k++) {
                                SimpleVar v = list.get(k);
                                v.name = data.cp.getUtf("");

                                if ((flags & DESTROY_VAR) != 0 && rand.nextFloat() > 0.5f) {
                                    v.refType = data.cp.getUtf(ParamHelper.getField(randType()));
                                    int e = v.end;
                                    v.end = v.start;
                                    v.start = e;
                                    v.slot = rand.nextInt();
                                }
                                v.write(w);
                            }
                        } else if((flags & CLEAR_CODE_ATTR) != 0) {
                            continue;
                        } else {
                            count++;
                            w.writeBytes(r.getBytes().subList(w.list.pos(), len + 6));
                        }
                        break;
                    case "LocalVariableTypeTable":
                        if ((flags & (DESTROY_VAR | FAKE_VAR_SIGN)) != 0) {
                            count++;
                            List<SimpleVar> list = CodeMapper.readVar(pool, r);
                            int pos = w.list.pos();
                            w.writeShort(data.cp.getUtfId(name)).writeInt(len)
                             .writeShort(list.size());
                            for (int k = 0; k < list.size(); k++) {
                                SimpleVar v = list.get(k);
                                v.name = data.cp.getUtf("");
                                if ((flags & FAKE_VAR_SIGN) != 0 && rand.nextFloat() > 0.5f)
                                    data.cp.setUTFValue(v.refType, getSign(Signature.CLASS).toGeneric());

                                if ((flags & DESTROY_VAR) != 0 && rand.nextFloat() > 0.5f) {
                                    v.refType = data.cp.getUtf(ParamHelper.getField(randType()));
                                    int e = v.end;
                                    v.end = v.start;
                                    v.start = e;
                                    v.slot = rand.nextInt();
                                }
                                v.write(w);
                            }
                        } else if((flags & CLEAR_CODE_ATTR) != 0) {
                            continue;
                        } else {
                            count++;
                            w.writeBytes(r.getBytes().subList(w.list.pos(), len + 6));
                        }
                        break;
                    case "LineNumberTable":
                        if ((flags & DESTROY_LINE) != 0) {
                            count++;
                            int tableLen = r.readUnsignedShort();
                            w.writeShort(data.cp.getUtfId(name)).writeInt(len)
                             .writeShort(tableLen);

                            if(lineLog != null)
                                lineLog.append(' ').append(m.name.getString())
                                       .append(' ').append(m.type.getString()).append('\n');
                            for (int k = 0; k < tableLen; k++) {
                                int index = r.readUnsignedShort();
                                int line = r.readUnsignedShort();

                                int v = rand.nextInt(65536);
                                if(lineLog != null)
                                    lineLog.append("  ").append(line).append(' ').append(v).append('\n');

                                w.writeShort(index).writeShort(v);
                            }
                        } else if((flags & (CLEAR_CODE_ATTR | KEEP_LINES)) == CLEAR_CODE_ATTR) {
                            continue;
                        } else {
                            count++;
                            w.writeBytes(r.getBytes().subList(w.list.pos(), len + 6));
                        }
                        break;
                    default:
                        count++;
                        w.writeBytes(r.getBytes().subList(w.list.pos(), len + 6));
                    break;
                }
                r.index = end;
            }
            int pos = w.list.pos();
            w.list.pos(countIdx);
            w.writeShort(count);
            w.list.pos(pos);

            au.setRawData(new ByteList(w.toByteArray()));
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
            Map<String, List<Generic>> map = new MyHashMap<>(len);
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
            return origin;

        tempF.clear();
        tempM.clear();

        return clazz.obfClass(origin, classes, rand);
    }

    @Override
    public String obfMethodName(Desc desc) {
        return method == null ? null : method.obfName(tempM, desc, rand);
    }

    @Override
    public String obfFieldName(Desc desc) {
        return field == null ? null : field.obfName(tempF, desc, rand);
    }
}
