package roj.mapper;

import roj.RequireUpgrade;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUTF;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.insn.*;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.Context;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.TrieTreeSet;
import roj.config.JSONParser;
import roj.config.data.CEntry;
import roj.config.data.CList;
import roj.config.data.CMapping;
import roj.io.IOUtil;
import roj.mapper.obf.MyExcluder;
import roj.mapper.obf.policy.*;
import roj.mapper.util.Desc;
import roj.reflect.FastInit;
import roj.text.CharList;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.ByteArrayInputStream;
import java.io.File;
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
		CLEAR_ATTR = 4096, FAKE_SIGN = 8, INVALID_VAR_NAME = 16,
		FAKE_VAR_SIGN = 32, RANDOMIZED_SIGN = 64, DESTROY_VAR = 128,
		DESTROY_LINE = 256, CLEAR_CODE_ATTR = 512, KEEP_LINES = 1024, OBF_STRING = 2048;
	public static final int MAX_LEN = Integer.parseInt(System.getProperty("roj.obf.sign_max_len", "63"));

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.out.println(
				"Roj234's Class Obfuscator 0.2\n" +
					"Usage: SimpleObfuscator <input> <output> [config] \n" +
					"    配置项:\n" +
					"      mapStore [path]    => 指定class混淆表保存位置\n" +
					"      libPath [path]     => 指定库位置\n" +
					"      lineLog [path]     => 指定行号混淆表保存位置\n" +
					"      flag [int]         => 混淆flag\n" +
					"            1 给方法和字段加上synthesis标记\n" +
					"            2 给方法和字段加上public标记\n" +
					"            4 给方法和字段删除synthesis标记\n" +
					"            8 添加错误的泛型\n" +
					"           16 添加无效的变量名 (慢)\n" +
					"           32 添加错误的变量泛型 (慢)\n" +
					"           64 泛型随机化\n" +
					"          128 炸了变量属性! (部分JVM不支持)\n" +
					"          256 炸了行号\n" +
					"          512 删除code可选属性 (慢)\n" +
					"         1024 保留常量池 (仅用于DEBUG)\n" +
					"         2048 加密字符串 (很慢) (还没做，还没做，还没做，重要的事情说三遍)\n" +
					"         4096 删除可选属性\n" +
					"\n" +
					"           把喜欢的数字加起来\n" +
					"\n" + "      seed [int]         => 随机数种子,同样的种子混淆出的内容相同\n" +
					"      [c,f,m]Type [type] => 指定class,field,method混淆类别\n" +
					"            w : windows保留字 [c]\n" +
					"            i : i L 1 I      [cfm]\n" +
					"            m [ch] : 字符混合 [cfm]\n" +
					"            a : abc          [cfm]\n" +
					"            f : 文件(一行一个) [cfm]\n" +
					"            k : java保留字    [cfm]\n" +
					"\n" + "      noPkg              => 不要保留包名\n" +
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

		for (int i = 2; i < args.length; i++) {
			switch (args[i]) {
				case "noPkg":
					if (obf.clazz == null) throw new IllegalArgumentException("noPkg选项要放在cType之后");
					obf.clazz.setKeepPackage(false);
					obf.flags |= 2;
					break;
				case "excludes":
					while (!args[++i].equals("---")) obf.packageExclusions.add(args[i]);
					break;
				case "keepClasses":
					while (!args[++i].equals("---")) obf.classExclusions.add(args[i]);
					break;
				case "flag":
					obf.setFlags(Integer.parseInt(args[++i]));
					break;
				case "seed":
					obf.rand.setSeed(Long.parseLong(args[++i]));
					break;
				case "cfg":
					CMapping map = JSONParser.parses(IOUtil.readUTF(new File(args[++i])), JSONParser.LITERAL_KEY).asMap();
					obf.packageExclusions.addAll(map.get("keepPackages").asList().asStringList());
					obf.classExclusions.addAll(map.get("keepClasses").asList().asStringList());
					for (CEntry entry : map.get("keep").asList()) {
						CList list = entry.asList();
						Desc key = new Desc(list.get(0).asString(), list.get(1).asString(), list.get(2).asString());
						(key.param.indexOf('(') > 0 ? obf.m1.getMethodMap() : obf.m1.getFieldMap()).put(key, key.name);
					}
					obf.flags = map.getInteger("flag");
					if (map.containsKey("seed")) {
						if (map.getLong("seed") == 0) obf.rand = null;
						else obf.rand.setSeed(map.getLong("seed"));
					}
					if (map.containsKey("mapStore")) mapStore = map.getString("mapStore");
					if (map.containsKey("charset")) charset = Charset.forName(map.getString("charset"));
					if (map.getBool("noPackage")) {
						if (obf.clazz == null) throw new IllegalArgumentException("noPkg选项要放在cType之后");
						obf.clazz.setKeepPackage(false);
						obf.flags |= 2;
					}
					if (map.containsKey("libPath")) {
						obf.loadLibraries(IOUtil.findAllFiles(new File(map.getString("libPath")), file -> file.getName().endsWith(".jar") || file.getName().endsWith(".zip")));
					}
					if (map.containsKey("mapping")) {
						obf.m1.loadMap(new ByteArrayInputStream(IOUtil.SharedCoder.get().encode(map.getString("mapping"))), false);
					}
					if (map.getBool("evaluate")) {
						obf.eval = true;
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
					obf.loadLibraries(IOUtil.findAllFiles(new File(args[++i]), file -> file.getName().endsWith(".jar") || file.getName().endsWith(".zip")));
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

		List<Context> arr = Context.fromZip(new File(args[0]), charset, data);

		ZipFileWriter zfw = new ZipFileWriter(new File(args[1]), false);

		Thread writer = MapUtil.writeResourceAsync(zfw, data);

		List<Desc> excludes = MyExcluder.checkAtomics(arr);
		for (int i = 0; i < excludes.size(); i++) {
			obf.classExclusions.add(excludes.get(i).owner);
		}
		obf.obfuscate(arr);

		if (mapStore != null) obf.writeObfuscationMap(new File(mapStore));

		if (lineLog != null) {
			try (FileOutputStream fos = new FileOutputStream(lineLog)) {
				IOUtil.SharedCoder.get().encodeTo(obf.lineLog, fos);
			}
		}

		writer.join();

		for (int i = 0; i < arr.size(); i++) {
			Context ctx = arr.get(i);
			zfw.writeNamed(ctx.getFileName(), ctx.get());
		}
		zfw.finish();

		System.out.println("Mem: " + (roj.manage.SystemInfo.getMemoryUsedO() >> 20) + " MB");
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
				throw new IllegalArgumentException("Unknown " + args[i - 1]);
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
	public TrieTreeSet packageExclusions = new TrieTreeSet();
	public MyHashSet<String> classExclusions = new MyHashSet<>();

	public Random rand;
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
		if (rand != null) ArrayUtil.shuffle(arr, rand);
		super.obfuscate(arr);
	}

	@Override
	protected void beforeMapCode(List<Context> arr) {
		if ((flags & CLEAR_ATTR) != 0) {
			for (int i = 0; i < arr.size(); i++) {
				clearSign(arr.get(i).getData());
			}
		}
		if ((flags & CLEAR_CODE_ATTR) != 0 && (flags & (INVALID_VAR_NAME | FAKE_VAR_SIGN)) == 0) {
			for (int i = 0; i < arr.size(); i++) {
				codeSign(arr.get(i).getData());
			}
		}
	}

	static StackTraceElement[] syncStackTrace = new StackTraceElement[2];

	public static StackTraceElement[] _syncGetStackTrace() {
		return syncStackTrace;
	}

	@Override
	@RequireUpgrade
	protected void afterMapCode(List<Context> arr) {
		eva:
		if (eval) {
			CryptFinder cf = new CryptFinder();

			for (int i = 0; i < arr.size(); i++) {
				ConstantData data = arr.get(i).getData();
				List<? extends MethodNode> methods = data.methods;

				for (int j = 0; j < methods.size(); j++) {
					AttrUnknown code0 = (AttrUnknown) methods.get(j).attrByName("Code");
					if (code0 == null) continue;
					cf.visit(data.cp, Parser.reader(code0));
				}
			}

			if (cf.decoders.isEmpty()) {
				System.out.println("没有找到疑似字符串解密方法");
				break eva;
			}

			Desc desc = new Desc();
			desc.param = "(Ljava/lang/String;)Ljava/lang/String;";
			for (int i = 0; i < arr.size(); i++) {
				ConstantData data = arr.get(i).getData();
				List<? extends MethodNode> methods = data.methods;

				desc.owner = m1.classMap.getOrDefault(data.name, data.name);
				for (int j = methods.size() - 1; j >= 0; j--) {
					RawMethod m = (RawMethod) methods.get(j);
					if (!m.type.getString().equals("(Ljava/lang/String;)Ljava/lang/String;")) continue;

					desc.name = m.name.getString();
					if (cf.decoders.containsKey(desc)) {
						ConstantData dg = new ConstantData();
						dg.version = data.version;
						dg.name("roj/reflect/gen" + Math.abs(System.nanoTime() % 9999999));
						dg.addInterface("roj/mapper/SimpleObfuscator$Decoder");

						Method method = new Method(data, m);
						method.name = "decode0";
						dg.methods.add(method);

						InsnList ins = method.getCode().instructions;
						for (int k = 0; k < ins.size(); k++) {
							InsnNode node = ins.get(k);
							if (node.nodeType() == InsnNode.T_INVOKE) {
								InvokeInsnNode cin = (InvokeInsnNode) node;
								if (cin.name.equals("getStackTrace") && cin.awslDesc().equals("[Ljava/lang/StackTraceElement;")) {
									cin.code = Opcodes.INVOKESTATIC;
									cin.owner = SimpleObfuscator.class.getName().replace('.', '/');
									cin.name = "_syncGetStackTrace";
									ins.add(k, NPInsnNode.of(Opcodes.POP));
									System.out.println("找到stack trace 调用！");
								}
							}
						}

						CodeWriter cw = dg.newMethod(AccessFlag.PUBLIC, "decode", "(Ljava/lang/String;)Ljava/lang/String;");

						cw.visitSize(1, 2);

						cw.one(Opcodes.ALOAD_1);
						cw.invoke(Opcodes.INVOKESTATIC, dg, 0);
						cw.one(Opcodes.ARETURN);

						FastInit.prepare(dg);
						try {
							Decoder dar = (Decoder) FastInit.make(dg);
							cf.decoders.put(desc, dar);
						} catch (Throwable e) {
							e.printStackTrace();
							continue;
						}

						// 在这里删除了解密方法，也许可以不用删？
						// methods.remove(j);
					}
				}
			}

			//MyInterpreter intr = new MyInterpreter(cf);
			boolean ch;
			for (int i = 0; i < arr.size(); i++) {
				ConstantData data = arr.get(i).getData();
				List<? extends MethodNode> methods = data.methods;

				//intr.setClass(data);
				for (int j = 0; j < methods.size(); j++) {
					MethodNode m = methods.get(j);
					AttrUnknown code0 = (AttrUnknown) m.attrByName("Code");
					if (code0 == null) continue;

					AttrCode code = new AttrCode(m, Parser.reader(code0), data.cp);
					try {
						//if (intr.interpret(code)) {
						//	code.instructions.removeAll(intr.toDelete);
						//	m.attributes().putByName(code);
						//}
					} catch (Exception e) {
						e.printStackTrace();
						System.out.println(code);
					}


					InsnList insn = code.instructions;
					for (int k = 0; k < insn.size()-1; k++) {
						InsnNode node = insn.get(k);
						if (node.nodeType() == InsnNode.T_LDC) {
							LdcInsnNode ldc = (LdcInsnNode) node;
							if (ldc.c.type() == Constant.STRING) {
								InsnNode next = insn.get(k + 1);
								if (next.nodeType() == InsnNode.T_INVOKE) {
									InvokeInsnNode iin = (InvokeInsnNode) next;
									if (iin.code == Opcodes.INVOKESTATIC && iin.awslDesc().equals("(Ljava/lang/String;)Ljava/lang/String;")) {
										CstUTF utf = ((CstString) ldc.c).getValue();
										//if (!intr.done.contains(utf.getString())) {
											String value = cf.tryDecode(iin, null, utf);
											if (value != null) {
												//intr.done.add(value);
												data.cp.setUTFValue(utf, value);
											} else {
												continue;
											}
										//}
										m.putAttr(code);
										insn.remove(++k)._i_replace(ldc);
									}
								}
							}
						}
					}
				}
			}
		}
	}

	public interface Decoder {
		String decode(String str);
	}

	static class CryptFinder extends CodeVisitor {
		CryptFinder() {
			this.decoders = new MyHashMap<>();
			this.tmp = new Desc();
		}

		@Override
		public void visit(ConstantPool cp, DynByteBuf r) {
			user = false;
			super.visit(cp, r);
		}

		boolean user;
		int ldcPos;
		Desc tmp;
		MyHashMap<Desc, Decoder> decoders;

		@Override
		protected void ldc(byte code, Constant c) {
			if (c.type() == Constant.STRING) {
				ldcPos = bci;
			}
		}

		@Override
		public void invoke(byte code, CstRef method) {
			if (code == Opcodes.INVOKESTATIC && method.desc().getType().getString().equals("(Ljava/lang/String;)Ljava/lang/String;")) {
				if (bci - ldcPos > 3) return;
				decoders.putIfAbsent(tmp.read(method).copy(), null);
				user = true;
			}
		}

		public String tryDecode(InvokeInsnNode iin, MethodNode caller, CstUTF utf) {
			if (caller != null) {
				// 如果用到了line的话那只好自己再弄啦，也就是麻烦一点，多读取一些属性的事
				syncStackTrace[0] = new StackTraceElement(iin.owner.replace('/', '.'), iin.name, "SourceFile", -1);
				syncStackTrace[1] = new StackTraceElement(caller.ownerClass().replace('/', '.'), caller.name(), "SourceFile", -1);
			}
			tmp.owner = iin.owner;
			tmp.name = iin.name;
			tmp.param = iin.awslDesc();
			Decoder dec = decoders.get(tmp);
			if (dec != null) {
				try {
					return dec.decode(utf.getString());
				} catch (Throwable e) {
					System.err.println("解密失败: ");
					e.printStackTrace();
				}
			}
			return null;
		}
	}

	static final List<IType> fake = Collections.singletonList(Type.std(Type.INT));
	static final Map<String, List<IType>> fake2 = new MyHashMap<>();

	static {
		fake2.put("\u0000", Collections.singletonList(new Generic("int", 23, Generic.EX_SUPERS)));
		fake2.put("\u0001", Collections.singletonList(new Generic("long", 0, Generic.EX_EXTENDS)));
		fake2.put("\u0002", Collections.singletonList(new Generic("double", 0, Generic.EX_SUPERS)));
		fake2.put("\u0003", Collections.singletonList(new Generic("short", 46, Generic.EX_NONE)));
		fake2.put("\u0004", Collections.singletonList(new Generic("char", 0, Generic.EX_SUPERS)));
	}

	static void clearSign(ConstantData data) {
		AttributeList al = data.attributesNullable();
		if (al == null) return;

		al.removeByName("InnerClasses");
		al.removeByName("Signature");
		al.removeByName("SourceFile");
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			AttributeList al1 = methods.get(i).attributesNullable();
			if (al1 != null) al1.removeByName("Signature");
		}
		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			AttributeList al1 = fields.get(i).attributesNullable();
			if (al1 != null) al1.removeByName("Signature");
		}
	}

	void fakeSign(ConstantData data) {
		Signature sign1 = getSign(Signature.CLASS);

		if (data.methods.isEmpty() || data.fields.isEmpty() || rand.nextFloat() > 0.11f) data.attributes().add(new AttrUTF("Signature", sign1.toDesc()));

		sign1 = getSign(Signature.METHOD);

		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			if (rand.nextFloat() > 0.66f) methods.get(i).attributes().add(new AttrUTF("Signature", sign1.toDesc()));
		}

		sign1 = getSign(Signature.FIELD);

		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			if (rand.nextFloat() > 0.66f) fields.get(i).attributes().add(new AttrUTF("Signature", sign1.toDesc()));
		}
	}

	@SuppressWarnings("fallthrough")
	void codeSign(ConstantData data) {
		Signature sign2 = getSign(Signature.METHOD);

		List<? extends MethodNode> methods = data.methods;

		if (lineLog != null && (flags & DESTROY_LINE) != 0) {
			lineLog.append(data.name).append('\n');
		}

		for (int i = 0; i < methods.size(); i++) {
			MethodNode m = methods.get(i);
			AttrUnknown au = (AttrUnknown) m.attrByName("Code");
			if (au == null) continue;

			DynByteBuf r = Parser.reader(au);
			ByteList w = new ByteList(r.readableBytes());
			r.rIndex += 4; // stack size
			int codeLen = r.readInt();
			r.rIndex += codeLen; // code

			int len1 = r.readUnsignedShort(); // exception
			r.rIndex += len1 << 3;
			w.put(r.slice(0, r.rIndex));

			ConstantPool pool = data.cp;
			len1 = r.readUnsignedShort();

			int count = 0;
			int countIdx = w.wIndex();
			w.putShort(0);

			/*if ((flags & (CLEAR_CODE_ATTR | KEEP_LINES)) == CLEAR_CODE_ATTR) {
				len = 0;
			}*/

			while (len1-- > 0) {
				String name = ((CstUTF) pool.get(r)).getString();
				int len = r.readInt();
				int end = len + r.rIndex;
				switch (name) {
					case "LocalVariableTable":
						if ((flags & (INVALID_VAR_NAME | DESTROY_VAR)) != 0) {
							count++;
							List<ParamNameMapper.V> list = ParamNameMapper.readVar(pool, r);
							w.putShort(data.cp.getUtfId(name)).putInt(len).putShort(list.size());
							for (int k = 0; k < list.size(); k++) {
								ParamNameMapper.V v = list.get(k);
								v.name = data.cp.getUtf("");

								if ((flags & DESTROY_VAR) != 0 && rand.nextFloat() > 0.5f) {
									v.type = data.cp.getUtf(TypeHelper.getField(randType()));
									int e = v.end;
									v.end = v.start;
									v.start = e;
									v.slot = rand.nextInt();
								}
								v.write(w);
							}
						} else if ((flags & CLEAR_CODE_ATTR) != 0) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex-6,len+6));
						}
						break;
					case "LocalVariableTypeTable":
						if ((flags & (DESTROY_VAR | FAKE_VAR_SIGN)) != 0) {
							count++;
							List<ParamNameMapper.V> list = ParamNameMapper.readVar(pool, r);
							int pos = w.wIndex();
							w.putShort(data.cp.getUtfId(name)).putInt(len).putShort(list.size());
							for (int k = 0; k < list.size(); k++) {
								ParamNameMapper.V v = list.get(k);
								v.name = data.cp.getUtf("");
								if ((flags & FAKE_VAR_SIGN) != 0 && rand.nextFloat() > 0.5f) data.cp.setUTFValue(v.type, getSign(Signature.CLASS).toDesc());

								if ((flags & DESTROY_VAR) != 0 && rand.nextFloat() > 0.5f) {
									v.type = data.cp.getUtf(TypeHelper.getField(randType()));
									int e = v.end;
									v.end = v.start;
									v.start = e;
									v.slot = rand.nextInt();
								}
								v.write(w);
							}
						} else if ((flags & CLEAR_CODE_ATTR) != 0) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex-6,len+6));
						}
						break;
					case "LineNumberTable":
						if ((flags & DESTROY_LINE) != 0) {
							count++;
							int tableLen = r.readUnsignedShort();
							w.putShort(data.cp.getUtfId(name)).putInt(len).putShort(tableLen);

							if (lineLog != null) lineLog.append(' ').append(m.name()).append(' ').append(m.rawDesc()).append('\n');
							for (int k = 0; k < tableLen; k++) {
								int index = r.readUnsignedShort();
								int line = r.readUnsignedShort();

								int v = rand.nextInt(65536);
								if (lineLog != null) lineLog.append("  ").append(line).append(' ').append(v).append('\n');

								w.putShort(index).putShort(v);
							}
						} else if ((flags & (CLEAR_CODE_ATTR | KEEP_LINES)) == CLEAR_CODE_ATTR) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex-6,len+6));
						}
						break;
					default:
						if ((flags & CLEAR_CODE_ATTR) != 0) break;
					case "StackMapTable":
						count++;
						w.put(r.slice(r.rIndex-6,len+6));
						break;
				}
				r.rIndex = end;
			}
			w.putShort(countIdx, count);

			au.setRawData(w);
		}
	}

	private Signature getSign(byte type) {
		Signature sign1 = new Signature(type);
		if ((flags & RANDOMIZED_SIGN) == 0) {
			sign1.Throws = fake;
			sign1.values = fake;
			sign1.typeParams = fake2;
		} else {
			int len = rand.nextInt(4);
			ArrayList<IType> gens = new ArrayList<>(len);
			for (int i = 0; i < len; i++) {
				gens.add(randAny());
			}
			sign1.Throws = gens;

			len = rand.nextInt(7);
			gens = new ArrayList<>(len);
			for (int i = 0; i < len; i++) {
				gens.add(randType());
			}
			sign1.values = gens;

			len = rand.nextInt(6);
			Map<String, List<IType>> map = new MyHashMap<>(len);
			for (int i = 0; i < len; i++) {
				int len2 = rand.nextInt(4) + 1;
				ArrayList<IType> gens1 = new ArrayList<>(len2);
				for (int j = 0; j < len2; j++) {
					gens1.add(randGen());
				}
				map.put(Integer.toString(rand.nextInt(1000000), 36), gens1);
			}
			sign1.typeParams = map;
		}
		return sign1;
	}

	private IType randGen() {
		switch (rand.nextInt(10)) {
			default:
				return new Generic(
					rand.nextFloat() > 0.233 ? Type.toString(randType().type) : Integer.toString(rand.nextInt(1000000), 36),
					rand.nextInt(MAX_LEN), (byte) rand.nextInt(2));
			case 1: return Signature.any();
			case 2: return Signature.placeholder();
		}
	}

	private Type randType() {
		switch (rand.nextInt(8)) {
			case 0: return Type.std(Type.LONG);
			case 1: return Type.std(Type.DOUBLE);
			case 2: return Type.std(Type.VOID);
			case 3: return Type.std(Type.BOOLEAN);
			case 4: return Type.std(Type.BYTE);
			case 5: return Type.std(Type.CHAR);
			case 6: return Type.std(Type.FLOAT);
			case 7:
			default: return Type.std(Type.SHORT);
		}
	}

	private IType randAny() {
		return rand.nextBoolean() ? randType() : randGen();
	}

	@Override
	public String obfClass(IClass cls) {
		String origin = cls.name();
		if (packageExclusions.strStartsWithThis(origin) || classExclusions.contains(origin) || cls.modifier() == AccessFlag.MODULE) return TREMINATE_THIS_CLASS;

		tempF.clear();
		tempM.clear();

		if (clazz == null) return origin;

		return clazz.obfClass(origin, classes, rand);
	}

	@Override
	public String obfMethodName(IClass cls, Desc entry) {
		switch (cls.name()) {
			case "roj/reflect/Instantiator":
				if (entry.name.equals("syncCallback")) return null;
				break;
			case "roj/io/NIOUtil$NUT":
			case "roj/io/misc/SCNative":
			case "roj/reflect/ClassDefiner$FastDef":
			case "roj/net/ch/MySelector$H":
			case "roj/math/MutableBigInteger$Opr":
			case "roj/reflect/UFA$UNSAFE":
			case "roj/util/NativeMemory$H":
				return null;
		}
		if (MyExcluder.isClassExclusive(cls, entry)) return null;
		return method == null ? null : method.obfName(tempM, entry, rand);
	}

	@Override
	public String obfFieldName(IClass cls, Desc entry) {
		if (MyExcluder.isClassExclusive(cls, entry)) return null;
		return field == null ? null : field.obfName(tempF, entry, rand);
	}
}
