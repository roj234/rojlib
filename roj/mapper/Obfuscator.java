package roj.mapper;

import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.*;
import roj.asm.misc.ReflectClass;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.insn.*;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.util.Context;
import roj.asm.util.InsnHelper;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.concurrent.OperationDone;
import roj.io.IOUtil;
import roj.mapper.obf.nodename.NameObfuscator;
import roj.mapper.util.Desc;
import roj.reflect.FastInit;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.ui.Profiler;
import roj.util.ArrayUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.*;

/**
 * @author Roj233
 * @since 2021/7/18 19:06
 */
public class Obfuscator {
	public static final Logger LOGGER = Logger.getLogger("Obfuscator");

	public static final int
		ADD_SYNTHETIC = 1,
		REMOVE_SYNTHETIC = 2,

		REMOVE_ATTR = 4,
		REMOVE_CODE_ATTR = 8,

		FAKE_SIGN = 16,
		FAKE_VAR_SIGN = 32,

		MANGLE_VAR = 64,
		MANGLE_VAR_EX = 128,

		MANGLE_LINE = 256,
		KILL_FRAME = 512,

		KEEP_LINES = 1024,
		OBF_STRING = 2048;

	public static final int EX_CLASS = 1, EX_FIELD = 2, EX_METHOD = 4;

	private final MyHashSet<String> tempF = new MyHashSet<>(), tempM = new MyHashSet<>();

	public int flags;
	public Random rand;
	public NameObfuscator clazz, method, field, param;
	public InheritableRuleset exclusions = new InheritableRuleset("/");

	public CharList lineLog;
	public boolean eval;

	public final Mapper m;
	private Map<String, IClass> named;

	public Obfuscator() {
		rand = new Random();
		m = new Mapper(true);
		m.flag = Mapper.FLAG_FIX_INHERIT;
	}

	public void obfuscate(List<Context> arr) {
		if (rand != null) ArrayUtil.shuffle(arr, rand);

		Mapper m = this.m; m.clear();

		if (named == null) named = new MyHashMap<>(arr.size());
		else named.clear();

		for (int i = 0; i < arr.size(); i++) {
			ConstantData data = arr.get(i).getData();
			named.put(data.name, data);
		}

		Context cur = null;
		try {
			Profiler.startSection("initSelf");
			m.initSelf(arr.size());
			Profiler.endStartSection("S1_parse");
			for (int i = 0; i < arr.size(); i++) m.S1_parse(cur = arr.get(i));

			Profiler.endStartSection("initSelfSuperMap");
			m.initSelfSuperMap();
			Profiler.endStartSection("generateObfuscationMap");
			for (int i = 0; i < arr.size(); i++) generateObfuscationMap(cur = arr.get(i));

			// 删去冲突项
			Profiler.endStartSection("loadLibraries");
			m.loadLibraries(Collections.singletonList(arr));
			Profiler.endStartSection("packup");
			m.packup();

			m.S2_begin(arr);
			Mapper.LOGGER.setLevel(Level.DEBUG);

			Profiler.endStartSection("S2_1_FixAccess");
			m.S2_1_FixAccess(arr, false);

			Profiler.endStartSection("S2_2_FixInheritConflict");
			m.S2_2_FixInheritConflict(arr);

			Profiler.endStartSection("S2_3_FixSubImpl");
			List<Desc> dup2 = m.S2_3_FixSubImpl(arr, true);
			if (!dup2.isEmpty()) LOGGER.log(Level.WARN, "[Obf-MapGen-Fix]: {} SubImpl entry added", null, dup2.size());

			Mapper.LOGGER.setLevel(Level.ERROR);
			m.S2_end();

			Profiler.endStartSection("S3_mapSelf");
			for (int i = 0; i < arr.size(); i++) m.S3_mapSelf(cur = arr.get(i), false);
			Profiler.endStartSection("S4_mapConstant");
			for (int i = 0; i < arr.size(); i++) m.S4_mapConstant(cur = arr.get(i));

			if ((flags & REMOVE_ATTR) != 0) {
				Profiler.endStartSection("removeOptionalAttribute");
				for (int i = 0; i < arr.size(); i++) {
					removeOptionalAttribute(arr.get(i).getData());
				}
			}

			// todo clear & add
			if ((flags & REMOVE_CODE_ATTR) != 0 && (flags & (MANGLE_VAR | FAKE_VAR_SIGN)) == 0) {
				Profiler.endStartSection("codeSign");
				for (int i = 0; i < arr.size(); i++) {
					codeSign(arr.get(i).getData());
				}
			}

			Profiler.endStartSection("S5_mapClassName");
			for (int i = 0; i < arr.size(); i++) m.S5_mapClassName(cur = arr.get(i));
			Profiler.endStartSection("compress");
			for (int i = 0; i < arr.size(); i++) (cur = arr.get(i)).compress();

			if ((flags & FAKE_SIGN) != 0) {
				Profiler.endStartSection("mangleSignature");
				for (int i = 0; i < arr.size(); i++) {
					mangleSignature(arr.get(i).getData());
				}
			}

			if ((flags & REMOVE_CODE_ATTR) != 0 && (flags & (MANGLE_VAR | FAKE_VAR_SIGN)) == 0) {
				Profiler.endStartSection("codeSign");
				for (int i = 0; i < arr.size(); i++) {
					codeSign(arr.get(i).getData());
				}
			}

			Profiler.endStartSection("afterMapCode");
			decryptString(arr);
			Profiler.endSection();
		} catch (Throwable e) {
			m.debugRelative(cur.getData().name, null);
			LOGGER.log(Level.FATAL, "exception parsing {}", e, cur.getFileName());
		}
	}

	private void generateObfuscationMap(Context c) {
		ConstantData data = c.getData();

		String from = data.name();

		int exclusion = exclusions.get(from, 0);
		if (data.modifier() == AccessFlag.MODULE) return;

		if (from.endsWith("MapperUI")) {
			m.classMap.put("roj/mapper/MapperUI", "roj/FixedMapperUI");
			return;
		}

		tempF.clear(); tempM.clear();

		String to = clazz == null || (exclusion&EX_CLASS)!=0 ? from : clazz.obfClass(from, m.classMap.flip().keySet(), rand);

		Mapper m = this.m;
		if (to != null && m.classMap.putIfAbsent(from, to) != null) {
			System.out.println("重复的class name " + from);
		}

		Desc d = MapUtil.getInstance().sharedDC;
		d.name = from;
		CharList sb = IOUtil.getSharedCharBuf();

		prepareInheritCheck(from);
		List<? extends MethodNode> methods = (exclusion&EX_METHOD)!=0 ? Collections.emptyList() : data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode method = (MethodNode) methods.get(i);
			int acc = method.access;
			if ((flags & ADD_SYNTHETIC) != 0) {
				acc |= AccessFlag.SYNTHETIC;
			} else if ((flags & REMOVE_SYNTHETIC) != 0) {
				acc &= ~(AccessFlag.SYNTHETIC|AccessFlag.BRIDGE);
			}
			method.access = (char) acc;

			if ((d.name = method.name()).charAt(0) == '<') continue; // clinit, init
			d.param = method.rawDesc();
			if (0 == (acc & (AccessFlag.STATIC | AccessFlag.PRIVATE))) {
				if (isInherited(d)) continue;
			}
			d.flags = (char) acc;

			sb.clear();
			if (this.method != null && (exclusions.get(sb.append(d.owner).append("//").append(d.name), 0)&EX_METHOD) == 0) {
				String name = this.method.obfName(tempM, d, rand);
				if (name != null) m.methodMap.putIfAbsent(d.copy(), name);
			}
		}

		List<? extends FieldNode> fields = (exclusion&EX_FIELD)!=0 ? Collections.emptyList() : data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode field = (FieldNode) fields.get(i);
			int acc = field.access;
			if ((flags & ADD_SYNTHETIC) != 0) {
				acc |= AccessFlag.SYNTHETIC;
			} else if ((flags & REMOVE_SYNTHETIC) != 0) {
				acc &= ~(AccessFlag.SYNTHETIC|AccessFlag.BRIDGE);
			}
			field.access = (char) acc;

			d.name = field.name();
			d.param = field.rawDesc();
			d.flags = (char) acc;

			sb.clear();
			if (this.field != null && (exclusions.get(sb.append(d.owner).append("//").append(d.name), 0)&EX_FIELD) == 0) {
				String name = this.field.obfName(tempF, d, rand);
				if (name != null) m.fieldMap.putIfAbsent(d.copy(), name);
			}
		}
	}

	private final List<String> iCheckTmp = new ArrayList<>();
	private void prepareInheritCheck(String owner) {
		List<String> tmp = iCheckTmp;
		tmp.clear();

		Map<String, List<String>> supers = m.selfSupers;
		List<String> parents = supers.get(owner);
		if (parents != null) {
			for (int i = 0; i < parents.size(); i++) {
				String parent = parents.get(i);
				if (!supers.containsKey(parent) && !named.containsKey(parent)) tmp.add(parent);
			}
		}
	}
	private boolean isInherited(Desc k) { return iCheckTmp.isEmpty() ? MapUtil.checkObjectInherit(k) : MapUtil.getInstance().isInherited(k, iCheckTmp, true); }

	public void dumpMissingClasses() {
		List<String> notFoundClasses = new SimpleList<>();
		for (Map.Entry<String, ReflectClass> s : MapUtil.getInstance().classInfo.entrySet()) {
			if (s.getValue() == MapUtil.FAILED) notFoundClasses.add(s.getKey());
		}

		if (!notFoundClasses.isEmpty()) {
			System.out.print(TextUtil.deepToString(notFoundClasses));
			System.out.println(notFoundClasses.size() + "个类没有找到");
			System.out.println("【如果】你没有在libPath中提供这些类, 可能会降低混淆水平");
		}
	}

	// region string decrypt (WIP)
	public interface Decoder { String decode(Object... par); }
	private static final StackTraceElement[] syncStackTrace = new StackTraceElement[2];
	public static StackTraceElement[] _syncGetStackTrace() { return syncStackTrace; }
	public static class DecoderCandidate {
		ConstantData data;
		MethodNode mn;
		AttrUnknown code;
		Decoder impl;
		int refCount;

		public DecoderCandidate(ConstantData data, MethodNode mn, AttrUnknown code0) {
			this.data = data;
			this.mn = mn;
			this.code = code0;
		}

		public Decoder createDecoder() {
			ConstantData cls = new ConstantData();
			cls.version = data.version;
			cls.name("roj/reflect/gen" + Math.abs(System.nanoTime() % 9999999));
			cls.addInterface("roj/mapper/Obfuscator$Decoder");

			MethodNode method = mn.copy().parsed(data.cp);
			method.name("_decode_");
			cls.methods.add(method);

			InsnList ins = method.getCode().instructions;
			for (int k = 0; k < ins.size(); k++) {
				InsnNode node = ins.get(k);
				if (node.nodeType() == InsnNode.T_INVOKE) {
					InvokeInsnNode cin = (InvokeInsnNode) node;
					if (cin.name.equals("getStackTrace") && cin.desc.equals("[Ljava/lang/StackTraceElement;")) {
						cin.code = Opcodes.INVOKESTATIC;
						cin.owner = "roj/mapper/Obfuscator";
						cin.name = "_syncGetStackTrace";
						ins.add(k, NPInsnNode.of(Opcodes.POP));
						System.out.println("找到stack trace 调用！");
					}
				}
			}

			CodeWriter c = cls.newMethod(AccessFlag.PUBLIC, "decode", "([Ljava/lang/Object;)Ljava/lang/String;");

			List<Type> par = mn.parameters();
			c.visitSize(Math.max(2, par.size()), 2);
			c.unpackArray(1, 0, par);
			c.invoke(Opcodes.INVOKESTATIC, method);
			c.one(Opcodes.ARETURN);

			FastInit.prepare(cls);
			return impl = (Decoder) FastInit.make(cls);
		}
	}

	static class CryptFinder extends CodeVisitor {
		boolean user;
		int ldcPos;
		Desc tmp;
		MyHashMap<Desc, Decoder> decoders;
		CryptFinder() {
			this.decoders = new MyHashMap<>();
			this.tmp = new Desc();
		}

		public CryptFinder(MyHashMap<Desc, DecoderCandidate> candidate) {

		}

		@Override
		public void visit(ConstantPool cp, DynByteBuf r) {
			user = false;
			super.visit(cp, r);
		}

		@Override
		protected void ldc(byte code, Constant c) {
			if (c.type() == Constant.STRING) {
				ldcPos = bci;
			}
		}

		@Override
		public void invoke(byte code, CstRef method) {
			if (code == Opcodes.INVOKESTATIC && method.desc().getType().str().equals("(Ljava/lang/String;)Ljava/lang/String;")) {
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
					return dec.decode(utf.str());
				} catch (Throwable e) {
					System.err.println("解密失败: ");
					e.printStackTrace();
				}
			}
			return null;
		}
	}
	protected void decryptString(List<Context> arr) {
		CodeVisitor filter = new CodeVisitor() {
			public void invoke(byte code, CstRef method) {
				String cn = method.className();
				if (!cn.startsWith("java/")) throw OperationDone.INSTANCE;
			}
			protected void invokeItf(CstRefItf itf, short argc) { invoke((byte) 0, itf); }
			protected void invokeDyn(CstDynamic dyn, int type) { throw OperationDone.INSTANCE; }
		};

		MyHashMap<Desc, DecoderCandidate> decoderCandidate = new MyHashMap<>();

		for (int i = 0; i < arr.size(); i++) {
			ConstantData data = arr.get(i).getData();
			List<MethodNode> methods = data.methods;

			next:
			for (int j = 0; j < methods.size(); j++) {
				MethodNode mn = methods.get(j);
				if ((mn.modifier()&(AccessFlag.STATIC|AccessFlag.PRIVATE)) == AccessFlag.STATIC &&
					"java/lang/String".equals(mn.returnType().owner)) {
					List<Type> params = mn.parameters();
					if (params.size() > 4) continue;
					for (int k = 0; k < params.size(); k++) {
						Type param = params.get(k);
						if (!param.isPrimitive() && !"java/lang/String".equals(param.owner)) continue next;
					}

					AttrUnknown code0 = (AttrUnknown) mn.attrByName("Code");
					try {
						filter.visit(data.cp, Parser.reader(code0));

						Desc d = new Desc();
						d.owner = mn.ownerClass();
						d.name = mn.name();
						d.param = mn.rawDesc();
						decoderCandidate.put(d, new DecoderCandidate(data, mn, code0));
					} catch (OperationDone ignored) {}
				}
			}
		}

		if (decoderCandidate.isEmpty()) {
			LOGGER.log(Level.ERROR, "没有找到疑似字符串解密方法", null);
			return;
		}

		CryptFinder finder = new CryptFinder(decoderCandidate);

		for (int i = 0; i < arr.size(); i++) {
			ConstantData data = arr.get(i).getData();
			List<? extends MethodNode> methods = data.methods;

			for (int j = 0; j < methods.size(); j++) {
				MethodNode mn = methods.get(j);
				AttrUnknown code0 = (AttrUnknown) mn.attrByName("Code");
				if (code0 != null) {
					//filter.visit(data.cp, Parser.reader(code0));
				}
			}
		}

		Desc desc = new Desc();
		desc.param = "(Ljava/lang/String;)Ljava/lang/String;";

		// 连续LDC+invoke static
		// todo stack analyze
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
				for (int k = 0; k < insn.size() - 1; k++) {
					InsnNode node = insn.get(k);
					if (node.nodeType() == InsnNode.T_LDC) {
						LdcInsnNode ldc = (LdcInsnNode) node;
						if (ldc.c.type() == Constant.STRING) {
							InsnNode next = insn.get(k + 1);
							if (next.nodeType() == InsnNode.T_INVOKE) {
								InvokeInsnNode iin = (InvokeInsnNode) next;
								if (iin.code == Opcodes.INVOKESTATIC && iin.awslDesc().equals("(Ljava/lang/String;)Ljava/lang/String;")) {
									CstUTF utf = ((CstString) ldc.c).name();
									//if (!intr.done.contains(utf.getString())) {
									String value = null;
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
	// endregion

	void mangleSignature(ConstantData data) {
		if (data.methods.isEmpty() || data.fields.isEmpty() || rand.nextFloat() > 0.11f) data.putAttr(getSign(Signature.CLASS));

		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			if (rand.nextFloat() > 0.66f) methods.get(i).putAttr(getSign(Signature.METHOD));
		}

		List<? extends FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			if (rand.nextFloat() > 0.66f) fields.get(i).putAttr(getSign(Signature.FIELD));
		}
	}

	static void removeOptionalAttribute(ConstantData data) {
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

	@SuppressWarnings("fallthrough")
	void codeSign(ConstantData data) {
		if (lineLog != null && (flags & MANGLE_LINE) != 0) {
			lineLog.append(data.name).append('\n');
		}

		List<? extends MethodNode> methods = data.methods;
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
				String name = ((CstUTF) pool.get(r)).str();
				int len = r.readInt();
				int end = len + r.rIndex;
				switch (name) {
					case "LocalVariableTable":
						if ((flags & (MANGLE_VAR | MANGLE_VAR_EX)) != 0) {
							count++;
							List<ParamNameMapper.V> list = ParamNameMapper.readVar(pool, r);
							w.putShort(data.cp.getUtfId(name)).putInt(len).putShort(list.size());
							for (int k = 0; k < list.size(); k++) {
								ParamNameMapper.V v = list.get(k);
								v.name = data.cp.getUtf("");

								if ((flags & MANGLE_VAR_EX) != 0 && rand.nextFloat() > 0.5f) {
									v.type = data.cp.getUtf(TypeHelper.getField(genRef()));
									int e = v.end;
									v.end = v.start;
									v.start = e;
									v.slot = rand.nextInt();
								}
								v.write(w);
							}
						} else if ((flags & REMOVE_CODE_ATTR) != 0) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex - 6, len + 6));
						}
						break;
					case "LocalVariableTypeTable":
						if ((flags & (MANGLE_VAR_EX | FAKE_VAR_SIGN)) != 0) {
							count++;
							List<ParamNameMapper.V> list = ParamNameMapper.readVar(pool, r);
							int pos = w.wIndex();
							w.putShort(data.cp.getUtfId(name)).putInt(len).putShort(list.size());
							for (int k = 0; k < list.size(); k++) {
								ParamNameMapper.V v = list.get(k);
								v.name = data.cp.getUtf("");
								if ((flags & FAKE_VAR_SIGN) != 0 && rand.nextFloat() > 0.5f) data.cp.setUTFValue(v.type, getSign(Signature.CLASS).toDesc());

								if ((flags & MANGLE_VAR_EX) != 0 && rand.nextFloat() > 0.5f) {
									v.type = data.cp.getUtf(TypeHelper.getField(genRef()));
									int e = v.end;
									v.end = v.start;
									v.start = e;
									v.slot = rand.nextInt();
								}
								v.write(w);
							}
						} else if ((flags & REMOVE_CODE_ATTR) != 0) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex - 6, len + 6));
						}
						break;
					case "LineNumberTable":
						if ((flags & MANGLE_LINE) != 0) {
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
						} else if ((flags & (REMOVE_CODE_ATTR | KEEP_LINES)) == REMOVE_CODE_ATTR) {
							r.rIndex = end;
							continue;
						} else {
							count++;
							w.put(r.slice(r.rIndex - 6, len + 6));
						}
						break;
					default:
						if ((flags & REMOVE_CODE_ATTR) != 0) break;
					case "StackMapTable":
						count++;
						w.put(r.slice(r.rIndex - 6, len + 6));
						break;
				}
				r.rIndex = end;
			}
			w.putShort(countIdx, count);

			au.setRawData(w);
		}
	}

	//region RANDOMIZE GENERIC/TYPE GENERATOR
	public Signature getSign(byte type) {
		Signature sign = new Signature(type);

		int len = rand.nextFloat() > 0.5 ? rand.nextInt(4) : 0;
		List<IType> gens = sign.Throws = new ArrayList<>(len);
		for (int i = 0; i < len; i++) gens.add(randomType());

		len = rand.nextInt(5);
		gens = new ArrayList<>(len);
		for (int i = 0; i < len; i++) gens.add(randomType());
		sign.values = gens;

		len = rand.nextFloat() > 0.5 ? rand.nextInt(5)+1 : 0;
		Map<String, List<IType>> map = new MyHashMap<>(len);
		for (int i = 0; i < len; i++) {
			int len2 = rand.nextInt(4) + 1;
			ArrayList<IType> gens1 = new ArrayList<>(len2);
			for (int j = 0; j < len2; j++) gens1.add(randomType());
			map.put(Long.toUnsignedString(rand.nextLong(), 36), gens1);
		}
		sign.typeParams = map;
		return sign;
	}

	private IType randomType() {
		float f = rand.nextFloat();
		if (f < 0.25f) return genPrim();
		if (f > 0.66f) return genSign(1);
		return genRef();
	}

	private Generic genSign(float factor) {
		float f = rand.nextFloat();
		Generic sign = new Generic(genClassName(1), f > 0.7f ? rand.nextInt(10) : 0, (byte) rand.nextInt(2));
		if (f > 0.3f*factor) return sign;

		int parts = rand.nextInt(4);
		for (int i = 0; i < parts; i++) sign.children.add(genSign(factor*1.5f));
		if (f > 0.15f*factor) return sign;

		parts = rand.nextInt(4);
		IGeneric current = sign;
		for (int i = 0; i < parts; i++) {
			Generic type = genSign(factor*2f);

			GenericSub type2 = new GenericSub();
			type2.owner = type.owner;
			type2.children = type.children;

			current = current.sub = type2;
		}

		return sign;
	}
	private Type genPrim() { return Type.std(InsnHelper.PrimitiveArray2Type(rand.nextInt(8)+4)); }
	private Type genRef() { return new Type(genClassName(1), rand.nextFloat() > 0.7f ? rand.nextInt(10) : 0); }
	private String genClassName(float factor) {
		float f = rand.nextFloat()*factor;
		if (f > 0.9) return Type.toString(InsnHelper.PrimitiveArray2Type(rand.nextInt(8)+4));
		if (f < 0.5) return Long.toUnsignedString(rand.nextLong(), 36);

		int parts = rand.nextInt(3);
		CharList sb = IOUtil.getSharedCharBuf();
		for (int i = 0; i < parts; i++) sb.append(genClassName(3)).append('/');
		return sb.append(genClassName(3)).toString();
	}
	//endregion
}
