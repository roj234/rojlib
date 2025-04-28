package roj.plugins.obfuscator.string;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.attr.Attribute;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.*;
import roj.asm.insn.*;
import roj.asm.type.Desc;
import roj.asm.type.Type;
import roj.asm.util.Context;
import roj.collect.MyHashMap;
import roj.concurrent.OperationDone;
import roj.reflect.ClassDefiner;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj233
 * @since 2021/7/18 19:06
 */
@Deprecated
public class StringDeobfusactor {
	public static final Logger LOGGER = Logger.getLogger("Obfuscator");

	public boolean eval;

	// region string decrypt (WIP)
	public interface Decoder { String decode(Object... par); }
	private static final StackTraceElement[] syncStackTrace = new StackTraceElement[2];
	public static StackTraceElement[] _syncGetStackTrace() { return syncStackTrace; }
	public static class DecoderCandidate {
		ClassNode data;
		MethodNode mn;
		UnparsedAttribute code;
		Decoder impl;
		int refCount;

		public DecoderCandidate(ClassNode data, MethodNode mn, UnparsedAttribute code0) {
			this.data = data;
			this.mn = mn;
			this.code = code0;
		}

		public Decoder createDecoder() {
			ClassNode cls = new ClassNode();
			cls.version = data.version;
			cls.name("roj/reflect/gen" + Math.abs(System.nanoTime() % 9999999));
			cls.addInterface("roj/mapper/Obfuscator$Decoder");

			MethodNode method = mn.copy().parsed(data.cp);
			method.name("_decode_");
			cls.methods.add(method);

			InsnList ins = method.getAttribute(data.cp, Attribute.Code).instructions;
			for (InsnNode node : ins) {
				if (node.opcode() == Opcodes.INVOKEVIRTUAL) {
					Desc desc = node.desc();
					if (desc.name.equals("getStackTrace") && desc.param.equals("[Ljava/lang/StackTraceElement;")) {
						var replaceList = new InsnList();
						replaceList.insn(Opcodes.POP);
						replaceList.invoke(Opcodes.INVOKESTATIC, "_syncGetStackTrace", "roj/mapper/Obfuscator", desc.param);
						node.replace(replaceList, false);
						System.out.println("找到stack trace 调用！");
					}
				}
			}

			CodeWriter c = cls.newMethod(Opcodes.ACC_PUBLIC, "decode", "([Ljava/lang/Object;)Ljava/lang/String;");

			List<Type> par = mn.parameters();
			c.visitSize(Math.max(2, par.size()), 2);
			c.unpackArray(1, 0, par);
			c.invoke(Opcodes.INVOKESTATIC, method);
			c.insn(Opcodes.ARETURN);

			ClassDefiner.premake(cls);
			return impl = (Decoder) ClassDefiner.make(cls);
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

		public String tryDecode(Desc iin, MethodNode caller, CstUTF utf) {
			if (caller != null) {
				// 如果用到了line的话那只好自己再弄啦，也就是麻烦一点，多读取一些属性的事
				syncStackTrace[0] = new StackTraceElement(iin.owner.replace('/', '.'), iin.name, "SourceFile", -1);
				syncStackTrace[1] = new StackTraceElement(caller.ownerClass().replace('/', '.'), caller.name(), "SourceFile", -1);
			}
			tmp.owner = iin.owner;
			tmp.name = iin.name;
			tmp.param = iin.param;
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
			protected void invokeItf(CstRef method, short argc) { invoke((byte) 0, method); }
			protected void invokeDyn(CstDynamic dyn, int type) { throw OperationDone.INSTANCE; }
		};

		MyHashMap<Desc, DecoderCandidate> decoderCandidate = new MyHashMap<>();

		for (int i = 0; i < arr.size(); i++) {
			ClassNode data = arr.get(i).getData();
			List<MethodNode> methods = data.methods;

			next:
			for (int j = 0; j < methods.size(); j++) {
				MethodNode mn = methods.get(j);
				if ((mn.modifier()&(Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE)) == Opcodes.ACC_STATIC &&
					"java/lang/String".equals(mn.returnType().owner)) {
					List<Type> params = mn.parameters();
					if (params.size() > 4) continue;
					for (int k = 0; k < params.size(); k++) {
						Type param = params.get(k);
						if (!param.isPrimitive() && !"java/lang/String".equals(param.owner)) continue next;
					}

					mn.unparsed(data.cp);
					UnparsedAttribute code0 = (UnparsedAttribute) mn.getRawAttribute("Code");
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
			ClassNode data = arr.get(i).getData();
			List<? extends MethodNode> methods = data.methods;

			for (int j = 0; j < methods.size(); j++) {
				MethodNode mn = methods.get(j);
				UnparsedAttribute code0 = (UnparsedAttribute) mn.getRawAttribute("Code");
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
			ClassNode data = arr.get(i).getData();
			List<? extends MethodNode> methods = data.methods;

			//intr.setClass(data);
			for (int j = 0; j < methods.size(); j++) {
				MethodNode m = methods.get(j);
				UnparsedAttribute code0 = (UnparsedAttribute) m.getRawAttribute("Code");
				if (code0 == null) continue;

				AttrCode code = new AttrCode(Parser.reader(code0), data.cp, m);
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
				for (InsnNode node : insn) {
					Constant cst = node.constantOrNull();
					if (cst != null) {
						if (cst.type() == Constant.STRING) {
							InsnNode next = node.next();
							Desc desc1 = next.descOrNull();
							if (desc1 != null) {
								if (next.opcode() == Opcodes.INVOKESTATIC && desc1.param.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
									CstUTF utf = ((CstString) cst).name();
									//if (!intr.done.contains(utf.getString())) {
									String value = null;
									if (value != null) {
										//intr.done.add(value);
										data.cp.setUTFValue(utf, value);
									} else {
										continue;
									}
									//}
									m.addAttribute(code);
									next.remove();
								}
							}
						}
					}
				}
			}
		}
	}
	// endregion
}