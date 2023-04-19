package roj.asm.nixim;

import roj.RequireTest;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.TransformException;
import roj.asm.cst.*;
import roj.asm.tree.*;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.AnnValString;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.AttrLineNumber.LineNumber;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.*;
import roj.asm.visitor.CodeVisitor;
import roj.collect.Int2IntMap;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.io.IOUtil;
import roj.mapper.MapUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static roj.asm.Opcodes.*;
import static roj.asm.tree.insn.InsnNode.T_LOAD_STORE;

/**
 * NiximTransformerV2
 *
 * @author Roj234
 * @version 2.3
 * @since 2021/10/3 13:49
 */
public class NiximSystem implements NiximHelper {
	public static void main(String[] args) throws Exception {
		if (args.length < 3) {
			System.out.println("NiximSystem <jar-to-nixim> <flag|0> <nixim-class>");
			System.out.println("把Nixim的class放在classpath里面");
			return;
		}
		NiximSystem nx = new NiximSystem();
		for (int i = 2; i < args.length; i++) {
			nx.load(NiximSystem.class.getClassLoader().getResourceAsStream(args[i].replace('.', '/') + ".class"));
		}
		int flag = Integer.parseInt(args[1]);

		int index = args[0].lastIndexOf('.');
		File target = new File(index < 0 ? args[0] + "-结果.jar" : args[0].substring(0, index) + "-结果" + args[0].substring(index));
		IOUtil.copyFile(new File(args[0]), target);

		try (ZipArchive toNixim = new ZipArchive(target)) {
			for (Map.Entry<String, NiximSystem.NiximData> entry : nx.getRegistry().entrySet()) {
				String file = entry.getKey().replace('.', '/') + ".class";
				InputStream in = toNixim.getStream(file);
				if (in == null) {
					System.err.println("Unable to find " + file);
					continue;
				}

				try {
					Context ctx = new Context(file, in);
					NiximSystem.nixim(ctx, entry.getValue(), flag);
					toNixim.put(file, ctx::getCompressedShared, true);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					in.close();
				}
			}
			toNixim.store();
		}

		System.out.println("OK");
	}

	private static class AsTransformer extends AbstractMap<String, String> {
		private final Map<String, NiximData> parentNx;

		public AsTransformer(Map<String, NiximData> parentNx) {
			this.parentNx = parentNx;
		}

		@Override
		public String get(Object key) {
			NiximData nx = parentNx.get(key);
			return nx == null ? null : nx.dest;
		}

		@Nonnull
		@Override
		public Set<Entry<String, String>> entrySet() {
			return Collections.emptySet();
		}
	}

	protected final Map<String, NiximData> registry = new MyHashMap<>();
	protected final Map<String, NiximData> byParent = new MyHashMap<>();

	private static ZipFileWriter debugZFW;
	public static boolean debug = false;

	public static final int NO_FIELD_MODIFIER_CHECK = 1, NO_METHOD_MODIFIER_CHECK = 2, SHADOW_OPTIONAL = 4, INJECT_OPTIONAL = 8;
	private static final byte DEFINAL = 0x20;

	public Map<String, NiximData> getRegistry() {
		return registry;
	}

	public Map<String, NiximData> getParentMap() {
		return byParent;
	}

	public final boolean remove(String target, String source) {
		NiximData nx = registry.get(target);
		NiximData prev = null;
		while (nx != null) {
			if (nx.self.equals(source)) {
				if (prev == null) registry.remove(target);
				else prev.next = nx.next;
				return true;
			}
			prev = nx;
			nx = nx.next;
		}
		return false;
	}

	public final boolean remove(String target) {
		return registry.remove(target) != null;
	}

	// region 应用

	private static final String
		SPEC_M_ANYVAR = "$$$VALUE",
		SPEC_M_RETVAL = "$$$VALUE",
		SPEC_M_CONTINUE = "$$$VALUE",
		SPEC_M_CONSTRUCTOR = "$$$CONSTRUCTOR",
		SPEC_M_CONSTRUCTOR_THIS = "$$$CONSTRUCTOR_THIS";

	public static final class SpecMethods {
		/**
		 * matcher find region begin
		 */
		public static void $$$MATCH_BEGIN() {}
		/**
		 * matcher find region end
		 */
		public static void $$$MATCH_END() {}
		/**
		 * matcher replace region begin
		 */
		public static void $$$MATCH_TARGET_BEGIN() {}
		/**
		 * matcher replace region end
		 */
		public static void $$$MATCH_TARGET_END() {}

		/**
		 *   <table border="1" cellspacing="2" cellpadding="2">
		 *     <tr>
		 *       <th colspan="9"><span style="font-weight:normal">
		 *        这些以{@link NiximSystem#SPEC_M_RETVAL}开头的方法()在Inject注解的函数中拥有特殊意义 <br>
		 *        如果返回值为某个具体的类，你也可以仿照开头在任何类中编写前缀为此的<i>静态</i>方法
		 *     </tr>
		 *     <tr>
		 *       <th>{@link Inject.At}</th>
		 *       <th><center>意义</center></th>
		 *       <th><center>示例</center></th>
		 *     </tr>
		 *     <tr>
		 *       <td>{@link Inject.At#HEAD}</td>
		 *       <td>立即(而不是结束)执行其后代码。相当于把注入函数作为循环体时的break语句。void返回值仅适用于该模式</td>
		 *       <td>return $$$VALUE_I()</td>
		 *     </tr>
		 *     <tr>
		 *       <td>{@link Inject.At#MIDDLE}</td>
		 *       <td>在匹配函数中指代任意同类型变量(当变量定义太早)。param第三项为true时,不同方法名称可以指代不同变量</td>
		 *       <td>new ArrayList<>($$$VALUE_I())</td>
		 *     </tr>
		 *     <tr>
		 *       <td>{@link Inject.At#TAIL}</td>
		 *       <td>指代函数返回值</td>
		 *       <td>int prevRet = $$$VALUE_I()</td>
		 *     </tr>
		 * <table>
		 */
		public static int $$$VALUE_I() {return 0;}
		public static long $$$VALUE_J() {return 0;}
		public static double $$$VALUE_D() {return 0;}
		public static float $$$VALUE_F() {return 0;}
		public static byte $$$VALUE_B() {return 0;}
		public static char $$$VALUE_C() {return 0;}
		public static short $$$VALUE_S() {return 0;}
		public static boolean $$$VALUE_Z() {return false;}
		public static void $$$VALUE_V() {}
	}

	public ByteList nixim(String className, ByteList in) throws TransformException {
		NiximData data = registry.remove(className);
		if (data != null) {
			Context ctx = new Context(className, in);
			nixim(ctx, data, 0);
			return ctx.get(false);
		}
		return in;
	}

	public static void nixim(Context ctx, NiximData nx, int flag) throws TransformException {
		if (debug) zipClass(ctx, "in");

		System.out.println("NiximClass " + ctx.getFileName());

		while (nx != null) {
			ConstantData data = ctx.getData();

			// 添加接口
			List<String> itfs = nx.addItfs;
			for (int i = 0; i < itfs.size(); i++) {
				data.interfaces.add(data.cp.getClazz(itfs.get(i)));
			}

			DescEntry tester = new DescEntry();
			List<? extends MoFNode> fields = data.fields;
			List<? extends MethodNode> methods = data.methods;
			// region 检查 Shadow 兼容性
			if (!nx.shadowChecks.isEmpty()) {
				for (int i = 0; i < fields.size(); i++) {
					MoFNode fs = fields.get(i);
					tester.name = fs.name();
					tester.desc = fs.rawDesc();
					DescEntry t = nx.shadowChecks.find(Helpers.cast(tester));
					// noinspection all
					if (t instanceof ShadowCheck) {
						nx.shadowChecks.remove(t);
						// noinspection all
						ShadowCheck sc = (ShadowCheck) t;
						if ((sc.flag & DEFINAL) != 0) fs.modifier(fs.modifier() & ~AccessFlag.FINAL);

						if ((flag & NO_FIELD_MODIFIER_CHECK) != 0) continue;
						if ((sc.flag & ~AccessFlag.PRIVATE) != (fs.modifier() & (AccessFlag.STATIC | AccessFlag.FINAL))) {
							// 排除我有final你没有的情况
							if ((sc.flag & AccessFlag.FINAL) == 0) throw new TransformException(data.name + '.' + fs.name() + "  Nixim字段 static/final存在与否不匹配");
						}
					}
				}
				for (int i = 0; i < methods.size(); i++) {
					MethodNode fs = methods.get(i);
					tester.name = fs.name();
					tester.desc = fs.rawDesc();
					DescEntry t = nx.shadowChecks.find(Helpers.cast(tester));
					// noinspection all
					if (t instanceof ShadowCheck) {
						nx.shadowChecks.remove(t);
						if ((flag & NO_METHOD_MODIFIER_CHECK) != 0) continue;
						// noinspection all
						ShadowCheck sc = (ShadowCheck) t;
						if ((sc.flag & ~AccessFlag.FINAL) != (fs.modifier() & (AccessFlag.STATIC | AccessFlag.PRIVATE))) {
							// 排除我没有private你有的情况
							if ((sc.flag & AccessFlag.PRIVATE) != 0) throw new TransformException(data.name + '.' + fs.name() + "  Nixim方法 private/static存在与否不匹配");
						}
					}
				}
				// region 检查存在性
				if (!nx.shadowChecks.isEmpty() && (flag & SHADOW_OPTIONAL) == 0) {
					throw new TransformException("以下Shadow对象没有在目标找到, 源: " + nx.self + ": " + nx.shadowChecks + ", 目标的方法: " + data.methods + ", 目标的字段: " + data.fields);
				}
				// endregion
			}
			// endregion
			// region 创建BSM并更新ID
			if (nx.bsm != null && !nx.bsm.isEmpty()) {
				BootstrapMethods selfBSM = data.parsedAttr(data.cp,Attribute.BootstrapMethods);
				if (selfBSM == null) {
					data.putAttr(selfBSM = new BootstrapMethods());
				}

				for (IntMap.Entry<LambdaInfo> entry : nx.bsm.selfEntrySet()) {
					int newId = selfBSM.methods.size();
					LambdaInfo info = entry.getValue();
					selfBSM.methods.add(info.bootstrapMethod);

					List<InvokeDynInsnNode> nodes = info.nodes;
					for (int i = 0; i < nodes.size(); i++) {
						nodes.get(i).tableIdx = (char) newId;
					}
				}
			}
			// endregion
			// region 实施 Inject (3/3)
			if (!nx.injectMethod.isEmpty()) {
				for (int i = methods.size() - 1; i >= 0; i--) {
					MethodNode ms = methods.get(i);

					tester.name = ms.name();
					tester.desc = ms.rawDesc();
					InjectState state = nx.injectMethod.remove(tester);
					while (state != null) {
						doInject(state, data, methods, i);
						state = state.next;
					}
				}
				// region 检查存在性
				if (!nx.injectMethod.isEmpty() && (flag & INJECT_OPTIONAL) == 0) {
					outer:
					for (Iterator<Map.Entry<DescEntry, InjectState>> itr = nx.injectMethod.entrySet().iterator(); itr.hasNext(); ) {
						Map.Entry<DescEntry, InjectState> entry = itr.next();
						InjectState state = entry.getValue();
						while (state != null) {
							// FLAG_OPTIONAL
							if ((state.flags & Inject.FLAG_OPTIONAL) == 0) {
								continue outer;
							}
							state = state.next;
						}
						itr.remove();
					}
					if (!nx.injectMethod.isEmpty()) {
						throw new TransformException("以下Inject方法没有在目标找到, 源: " + nx.self + ": " + nx.injectMethod.keySet() + ", 目标的方法: " + data.methods);
					}
				}
				// endregion
			}
			// endregion
			// region 复制方法, lambda, 字段和初始化器
			if (!nx.copyMethod.isEmpty()) methods.addAll(Helpers.cast(nx.copyMethod));
			if (!nx.copyField.isEmpty()) {
				fields.addAll(Helpers.cast(nx.copyField.keySet()));
				for (Iterator<Method> itr = nx.copyField.values().iterator(); itr.hasNext(); ) {
					Method val = itr.next();
					if (val == null) continue;

					Method clinit;
					int m_id = data.getMethod("<clinit>");
					if (m_id >= 0) {
						clinit = data.getUpgradedMethod(m_id);
					} else {
						clinit = new Method(AccessFlag.PUBLIC | AccessFlag.STATIC, data, "<clinit>", "()V");
						clinit.setCode(new AttrCode(clinit));
						clinit.getCode().instructions.one(RETURN);
						methods.add(Helpers.cast(clinit));
					}
					AttrCode code = clinit.getCode();

					NPInsnNode _return = (NPInsnNode) code.instructions.remove(code.instructions.size() - 1);

					o:
					do {
						String n = "^" + Integer.toString(data.methods.size(), 36);
						val.name = n;
						data.methods.add(Helpers.cast(val));
						code.instructions.invokeS(data.name, n, "()V");
						do {
							if (!itr.hasNext()) break o;
							val = itr.next();
						} while (val == null);
					} while (true);

					code.instructions.add(_return);
				}
			}
			// endregion

			for (int i = 0; i < data.methods.size(); i++) {
				if (data.methods.get(i) instanceof Method) {
					Method m = (Method) data.methods.get(i);
					if (m.getCode() != null) {
						AttributeList list = m.getCode().attributesNullable();
						if (list != null) {
							list.removeByName("LocalVariableTable");
							list.removeByName("LocalVariableTypeTable");
						}
					}
				}
			}

			try {
				data.verify();
			} catch (IllegalArgumentException e) {
				throw new TransformException("验证失败 " + data.name, e);
			}

			data.normalize();
			nx = nx.next;
		}
		killMappingIfJ7(ctx);

		//ctx.set(new ByteList(ctx.get().toByteArray()));

		if (debug) zipClass(ctx, "out");
	}

	private static void killMappingIfJ7(Context ctx) {
		ConstantData data = ctx.getData();
		//if (data.attrByName("BootstrapMethods") == null) {
			//if (data.version == 52 << 16) {
			//	data.version = 50 << 16;
			//	System.err.println("Kill STF for " + data.name);
				for (int i = 0; i < data.methods.size(); i++) {
					AttrCode code = data.getUpgradedMethod(i).getCode();
					if (code == null) continue;
					code.interpretFlags = 0;

					AttributeList list = code.attributesNullable();
					if (list == null) continue;
					list.removeByName("StackMapTable");
				}
			//}
		//}
	}

	private static void zipClass(Context ctx, String id) {
		try {
			if (debugZFW == null) debugZFW = new ZipFileWriter(new File("nixim_debug." + System.currentTimeMillis() + ".zip"), false);
			debugZFW.writeNamed(id + '/' + ctx.getFileName(), ctx.get());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static void doInject(InjectState s, ConstantData data, List<? extends MethodNode> methods, int index) throws TransformException {
		if (!methods.get(index).rawDesc().equals(s.method.rawDesc())) throw new TransformException("目标与Nixim方法返回值不匹配 " + methods.get(index));
		AttrCode CODE = s.method.getCode();
		switch (s.at) {
			case "REMOVE":
				methods.remove(index);
				break;
			case "REPLACE": {
				st:
				if (s.method.name.equals("<init>")) {
					InvokeInsnNode iin = (InvokeInsnNode) CODE.instructions.get(s.superCallEnd);
					if (iin.name.startsWith(SPEC_M_CONSTRUCTOR_THIS)) {
						iin.name = "<init>";
						if (iin.desc.equals(s.method.rawDesc())) throw new TransformException("this循环调用自身!");
						iin.owner = data.name;
						for (int i = 0; i < methods.size(); i++) {
							MethodNode mn = methods.get(i);
							if (mn.rawDesc().equals(iin.desc)) break st;
						}
						throw new TransformException("覆盖的构造器不存在:S");
					} else {
						// 无法检查上级构造器是否存在, 但是Object还是可以查一下
						iin.owner = data.parent;
						if (data.parent.equals("java/lang/Object") && !"()V".equals(iin.desc)) {
							throw new TransformException("覆盖的构造器中的上级调用不存在(Object parent)\n" +
								"如果您使用[构造器覆盖],不建议[用构造器]覆盖\n" +
								"或者你也许忘了: $$CONSTRUCTOR是对Nixim目标的父类的调用");
						}
					}
					CODE.interpretFlags = AttrCode.COMPUTE_FRAMES;
					s.method.owner = data.name;
					iin.name = "<init>";
				}
				methods.set(index, Helpers.cast(s.method));
			}
			break;
			case "HEAD": {
				Method tm = data.getUpgradedMethod(index);
				InsnList insn = tm.getCode().instructions;
				int superBegin = 0;
				if (methods.get(index).name().equals("<init>")) {
					while (superBegin < insn.size()) {
						InsnNode node = insn.get(superBegin++);
						if (node.nodeType() == InsnNode.T_INVOKE) {
							InvokeInsnNode iin = (InvokeInsnNode) node;
							if (iin.name.equals("<init>") && (iin.owner.equals(data.parent) || iin.owner.equals(data.name))) {
								break;
							}
						}
					}
					if (superBegin == insn.size()) throw new TransformException(data.name + " 存在错误: 无法找到上级/自身初始化的调用");
				}

				int pl = computeParamLength(tm, null);
				InsnList insn2 = CODE.instructions;
				InsnNode entryPoint;
				if (s.assignId != null) {
					int size = insn2.size();
					for (Int2IntMap.Entry entry : s.assignId.selfEntrySet()) {
						int tKey = entry.getIntKey() - 1 + pl;
						insn2.var((byte) (entry.v - (ISTORE - ILOAD)), tKey);
						insn2.var((byte) entry.v, entry.getIntKey());
					}
					entryPoint = insn2.get(size);
				} else {
					entryPoint = insn.get(superBegin);
				}

				List<InsnNode> gotos = s.gotos();
				for (int i = 0; i < gotos.size(); i++) {
					gotos.get(i)._i_replace(entryPoint);
				}

				insn.addAll(superBegin, insn2);
				tm.getCode().interpretFlags = AttrCode.COMPUTE_FRAMES;
				tm.getCode().stackSize = (char) Math.max(tm.getCode().stackSize, CODE.stackSize);
				tm.getCode().localSize = (char) Math.max(tm.getCode().localSize, CODE.localSize);
				if (debug) copyLine(CODE, tm.getCode());
			}
			break;
			case "INVOKE": {
				List<AnnVal> list = s.param;

				String name = list.get(0).asString();
				int ordinal = list.size() > 2 ? Integer.parseInt(list.get(2).asString()) : -1;
				int withClass = name.lastIndexOf('.');

				String clazz;
				if (withClass > 0) {
					clazz = name.substring(0, withClass).replace('.', '/');
					name = name.substring(withClass + 1);
				} else clazz = null;

				int isStatic = (s.method2.access & AccessFlag.STATIC);
				Method tm = data.getUpgradedMethod(index);
				InsnList insn = tm.getCode().instructions;
				int used = 0;
				for (int i = 0; i < insn.size(); i++) {
					InsnNode node = insn.get(i);
					if (node.nodeType() == InsnNode.T_INVOKE) {
						InvokeInsnNode node1 = (InvokeInsnNode) node;
						if ((clazz == null || node1.owner.equals(clazz)) && node1.name.equals(name) && (ordinal < 0 || ordinal-- == 0)) {
							String desc;
							if ((node1.code == INVOKESTATIC) == isStatic > 0) {
								// 相同
								desc = node1.desc;
							} else if (node1.code != INVOKESTATIC) {
								// virtual -> static
								desc = "(L" + node1.owner + ';' + node1.desc.substring(1);
							} else {
								desc = node1.desc;
								if (clazz != null) {
									isStatic = -1;
									insn.add(i++, NPInsnNode.of(ALOAD_0));
								} else {
									// 定义了class的Inject(INVOKE)才能适配static转virtual，防止误判
									// 这个分支也只可能是同名不同参数的情况
									continue;
								}
							}
							if (!s.method2.rawDesc().equals(desc)) {
								if (ordinal == -1) {
									throw new TransformException("类型为INVOKE的Inject注解 " + node1.owner + "." + node1.name + " 参数不匹配: except " + desc + " got " + s.method2.rawDesc());
								} else continue;
							}

							node1.code = isStatic > 0 ? INVOKESTATIC : INVOKESPECIAL;
							node1.owner = data.name;
							node1.name = s.method2.name;
							node1.desc = s.method2.rawDesc();
							used++;
						}
					}
				}
				if (used == 0 && (s.flags & Inject.FLAG_OPTIONAL) == 0) throw new TransformException("类型为INVOKE的Inject注解 " + name + " 没有匹配项");
			}
			break;
			case "LDC": {
				throw new TransformException("LDC Inject is WIP");
			}
			case "TAIL": {
				Method tm = data.getUpgradedMethod(index);
				Int2IntMap assignedLV = new Int2IntMap();
				int retValId = 0 != (tm.access & AccessFlag.STATIC) ? -1 : 0;
				// 和tail用到的变量取交集
				if (s.assignId != null) {
					computeParamLength(tm, assignedLV);
					for (Iterator<Int2IntMap.Entry> itr = assignedLV.selfEntrySet().iterator(); itr.hasNext(); ) {
						Int2IntMap.Entry entry = itr.next();
						if (!s.assignId.containsKey(entry.getIntKey())) {itr.remove();} else retValId = Math.max(entry.getIntKey(), retValId);
					}
				}
				retValId++;

				Type ret = TypeHelper.parseReturn(tm.rawDesc());
				byte base = ret.shiftedOpcode(ILOAD, true);
				InsnList injectInsn = CODE.instructions;
				if (s.superCallEnd > 0) injectInsn.removeRange(0, s.superCallEnd + 1);
				byte type;
				// ILOAD+5 = VOID
				if (base != ILOAD+5) {
					List<Integer> retVal = s.retVal();
					if (retVal.size() == 0) {
						type = 0;
					} else if (retVal.size() == 1 && retVal.get(0) == 0) {
						injectInsn.remove(0);
						type = 1;
					} else {
						for (int i : retVal) {
							_compress(retValId, base, injectInsn, i);
						}
						// 后面用到_compress就是store了
						base = ret.shiftedOpcode(ISTORE, false);
						type = 2;
					}
					retVal.clear();
				} else { // 返回VOID，正好可以复用type=1: 非空是带栈跳,空就直接跳
					type = 1;
				}

				InsnNode FIRST = injectInsn.get(0);
				int accessedMax = 0;

				InsnList targetInsn = tm.getCode().instructions;
				for (int i = 0; i < targetInsn.size(); i++) {
					InsnNode node = targetInsn.get(i);
					// 检测参数的assign然后做备份
					int i2 = InsnHelper.getVarId(node);
					if (i2 >= 0) {
						int code = node.getOpcodeInt();
						if (code >= ISTORE && code <= ASTORE_3) {
							Int2IntMap.Entry entry = assignedLV.getEntry(i2);
							if (entry != null) {
								if (code >= ISTORE_0) code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
								entry.v = code;
							}
							// needn't check assign: 他们是正常的class
						}
						accessedMax = Math.max(accessedMax, i2);
					} else if (i > 0 && InsnHelper.isReturn(node.code)) {
						JumpInsnNode Goto = new JumpInsnNode(FIRST);
						// 将返回值（如果存在）存放到指定的变量
						// 将目标方法中return替换成goto开头
						switch (type & 3) {
							case 0: // 没有使用,就放弃返回值并跳到目标
								targetInsn.set(i, NPInsnNode.of(POP));
								targetInsn.add(i, Goto);
								break;
							case 1: // 类似int v=$$VALUE_I();的语句
								if (i == targetInsn.size() - 1) {
									// 如果正好又是方法最后面的return
									targetInsn.remove(i)._i_replace(FIRST);
								} else {
									targetInsn.set(i, Goto);
								}
								break;
							case 2: // fallback
								if (i == targetInsn.size() - 1) {
									_compress(retValId, base, targetInsn, i);
								} else {
									targetInsn.add(_compress(retValId, base, targetInsn, i), Goto);
								}
								break;
						}
					}
				}
				// 计算tail用到的参数id，若目标方法修改过value则暂存到新的变量id中然后再恢复
				if (!assignedLV.isEmpty()) {
					InsnList prepend = new InsnList();
					for (Iterator<Int2IntMap.Entry> itr = assignedLV.selfEntrySet().iterator(); itr.hasNext(); ) {
						Int2IntMap.Entry entry = itr.next();
						if (entry.v != 0) {
							byte tCode = (byte) (entry.v - (ISTORE - ILOAD));
							prepend.var(tCode, entry.getIntKey());
							int tKey = ++accessedMax;
							prepend.var((byte) entry.v, tKey);
							// re-load 加上恢复用的指令
							targetInsn.var(tCode, tKey);
							targetInsn.var((byte) entry.v, entry.getIntKey());
						} else {
							itr.remove();
						}
					}
					if (!assignedLV.isEmpty()) {
						targetInsn.addAll(0, prepend);
					}
				}
				targetInsn.addAll(injectInsn);
				tm.getCode().interpretFlags = AttrCode.COMPUTE_FRAMES;
				tm.getCode().stackSize = (char) Math.max(tm.getCode().stackSize, CODE.stackSize);
				tm.getCode().localSize = (char) Math.max(tm.getCode().localSize, CODE.localSize);
				if (debug) copyLine(CODE, tm.getCode());
			}
			break;
			case "OLD_SUPER_INJECT":
				methods.get(index).name(data.cp, s.name);
				methods.add(Helpers.cast(s.method));
				break;
			case "MIDDLE":
				Method tm = data.getUpgradedMethod(index);
				InsnList toFind = tm.getCode().instructions;
				InsnList check = s.method2.getCode().instructions;
				InsnHelper ctx = new InsnHelper() {
					int iPos = -2;
					@Override
					public boolean isNodeSimilar(InsnNode haystack, InsnNode needle) {
						if (needle.code == INVOKESTATIC) {
							InvokeInsnNode iin = (InvokeInsnNode) needle;
							if (iin.name.startsWith(SPEC_M_ANYVAR)) {
								if (haystack.nodeType() != T_LOAD_STORE) {
									failed = true;
									return false;
								}

								return checkId(iPos--, getVarId(haystack));
							}
						}
						return super.isNodeSimilar(haystack, needle);
					}
				};
				for (int i = 0; i < toFind.size(); i++) {
					ctx.reset();

					findFirst:
					if (ctx.isNodeSimilar(toFind.get(i), check.get(0))) {
						System.out.println("find similar at bci " + (int)toFind.get(i).bci);
						for (int j = 1; j < check.size(); j++) {
							if (!ctx.isNodeSimilar(toFind.get(i + j), check.get(j))) {
								System.out.println("not similar at " + j + check.get(j));
								break findFirst;
							}
						}
						// found

						InsnList tmp = new InsnList();
						ctx.mapId(CODE.instructions, tmp);

						toFind.get(i)._i_replace(tmp.get(0));
						InsnNode finalNode = toFind.get(i + check.size());

						if (s.superCallEnd < 0) {
							toFind.removeRange(i, i + check.size());
							toFind.addAll(i, tmp);
						} else {
							System.err.println("测试中！测试完了记得删掉");
							toFind.removeRange(i + s.superCallEnd, i + s.regionEnd);
							toFind.addAll(i + s.superCallEnd, tmp);
						}

						List<InsnNode> gotos = s.gotos();
						for (i = 0; i < gotos.size(); i++) {
							gotos.get(i)._i_replace(finalNode);
						}

						tm.getCode().interpretFlags = AttrCode.COMPUTE_FRAMES;
						tm.getCode().stackSize = (char) Math.max(tm.getCode().stackSize, CODE.stackSize);
						tm.getCode().localSize = (char) Math.max(tm.getCode().localSize, CODE.localSize);
						if (debug) copyLine(CODE, tm.getCode());
						return;
					}
				}
				throw new TransformException("没有匹配成功: 目标方法: " + tm.getCode() + "\n" + "匹配方法: " + s.method2.getCode());
		}
	}

	private static void copyLine(AttrCode from, AttrCode to) {
		AttrLineNumber lnFr = (AttrLineNumber) from.attrByName("LineNumberTable");
		if (lnFr == null) return;
		AttrLineNumber lnTo = (AttrLineNumber) to.attrByName("LineNumberTable");
		if (lnTo == null) {
			to.attributes().add(lnFr);
			return;
		}

		List<LineNumber> target = lnTo.list;
		InsnList insn = to.instructions;
		for (LineNumber entry : lnFr.list) {
			if (insn.contains(entry.node)) {
				target.add(entry);
			}
		}
		target.sort((o1, o2) -> Integer.compare(insn.indexOf(o1.node), insn.indexOf(o2.node)));
	}

	private static void strip(Method method) {
		AttributeList list = method.attributesNullable();
		if (list != null) {
			list.removeByName("LocalVariableTable");
			list.removeByName("LocalVariableTypeTable");
		}
	}

	private static int _compress(int varId, byte base, InsnList targetInsn, int i) {
		if (varId <= 3) {
			targetInsn.set(i, InsnHelper.i_loadStoreSmall(base, varId));
		} else if (varId <= 255) {
			targetInsn.set(i, new U1InsnNode(base, varId));
		} else if (varId <= 65535) {
			targetInsn.set(i, NPInsnNode.of(WIDE));
			targetInsn.add(i + 1, new U2InsnNode(base, varId));
			return i + 2;
		}
		return i + 1;
	}

	// endregion
	// region 读取

	public void load(Object bytes) throws TransformException {
		Context ctx = new Context("", bytes);

		System.out.println("NiximRead " + ctx.getData().name);

		loadCtx(ctx);
	}

	public void loadCtx(Context ctx) throws TransformException {
		NiximData nx = read0(ctx, this);
		if (nx != null) {
			if (nx.dest == null) return;
			nx.next = registry.put(nx.dest, nx);
		} else {
			throw new TransformException("对象没有使用Nixim");
		}
	}

	public static final String A_NIXIM_CLASS_FLAG = Nixim.class.getName().replace('.', '/');
	public static final String A_INJECT = Inject.class.getName().replace('.', '/');
	public static final String A_IMPL_INTERFACE = Implements.class.getName().replace('.', '/');
	public static final String A_SHADOW = Shadow.class.getName().replace('.', '/');
	public static final String A_COPY = Copy.class.getName().replace('.', '/');
	public static final String A_DYNAMIC = Dynamic.class.getName().replace('.', '/');

	public static final String A_BASE = "RuntimeInvisibleAnnotations";

	/**
	 * 读取一个Nixim类
	 *
	 * @throws TransformException 出现错误
	 */
	@Nullable
	@SuppressWarnings("fallthrough")
	public static NiximData read0(Context ctx, NiximHelper h) throws TransformException {
		ConstantData data = ctx.getData();
		data.normalize();

		NiximData nx = new NiximData(data.name);

		Integer nxFlag = checkNiximFlag(data, data.attrByName(A_BASE), nx, h);
		if (nxFlag == null) {
			throw new TransformException(data.name + " 不是有效的Nixim class （没有找到注解）");
		}
		if (nxFlag == -1) return nx;

		List<RawMethod> methods = Helpers.cast(data.methods);
		// region 检测特殊方法, 删除桥接方法
		for (int i = methods.size() - 1; i >= 0; i--) {
			RawMethod method = methods.get(i);
			String name = method.name();
			if (name.startsWith("$$$")) {
				if (method.attrByName(A_BASE) != null) {
					throw new TransformException("特殊方法(" + name + ")不能包含注解");
				}
				if (!name.startsWith(SPEC_M_CONSTRUCTOR)) {
					if (0 == (method.access & AccessFlag.STATIC)) throw new TransformException("特殊方法(" + name + ")必须是static的");
					if (!method.rawDesc().startsWith("()")) {
						throw new TransformException("特殊方法(" + name + ")不能有参数");
					}
				} else if (!method.rawDesc().endsWith(")V")) {
					throw new TransformException("构造器调用标记(" + name + ")必须为void返回");
				} else if (0 != (method.access & AccessFlag.STATIC)) throw new TransformException("构造器调用标记(" + name + ")不能是static的");
			}
			if ((nxFlag & Nixim.KEEP_BRIDGE) == 0 && 0 != (method.access & AccessFlag.BRIDGE)) {
				methods.remove(i);
			}
		}
		// endregion
		String destClass = nx.dest;
		// region 检测并处理 Shadow 注解
		processShadow(ctx, true, destClass, nx.shadowChecks, h);
		processShadow(ctx, false, destClass, nx.shadowChecks, h);
		// endregion
		MyHashSet<RemapEntry> entries = new MyHashSet<>(nx.shadowChecks);
		// region 检测并处理(一半) Copy 注解
		for (int i = methods.size() - 1; i >= 0; i--) {
			RawMethod method = methods.get(i);
			Annotation copy = getAnnotation(h, data.cp, method, A_COPY);
			if (copy != null) {
				if (copy.containsKey("staticInitializer") || copy.containsKey("targetIsFinal")) {
					throw new TransformException("staticInitializer/targetIsFinal属性只能用在字段上！位置: " + data.name + '.' + method.name + " " + method.type.getString());
				}
				String name = copy.getString("value");
				if (copy.getInt("unique", 0) > 0) {
					name = "m^" + System.nanoTime() % 100000 + "_" + i;
				} else if (copy.getBoolean("map", false)) {
					name = h.map(destClass, method, name);
				}

				RemapEntry entry = new RemapEntry(method);
				entry.toClass = destClass;
				entry.toName = name;
				entries.add(entry);

				if (name != null) method.name = data.cp.getUtf(name);
				nx.copyMethod.add(Helpers.cast(method));

				methods.remove(i);
			} else if ((nxFlag & Nixim.COPY_ACCESSOR) != 0 && method.name().startsWith("access$") && (method.access & AccessFlag.SYNTHETIC) != 0) {
				RemapEntry entry = new RemapEntry(method);
				entry.toClass = destClass;
				entry.toName = "acc^" + System.nanoTime() % 100000 + "_" + i;
				entries.add(entry);

				method.access = (char) (method.access & ~(AccessFlag.PRIVATE | AccessFlag.PROTECTED) | AccessFlag.PUBLIC);
				method.name = data.cp.getUtf(entry.toName);
				nx.copyMethod.add(Helpers.cast(method));

				methods.remove(i);
			}
		}
		List<RawField> fields = Helpers.cast(data.fields);
		Map<RawField, RawMethod> tmpCopyFields = new MyHashMap<>();
		for (int i = fields.size() - 1; i >= 0; i--) {
			RawField field = fields.get(i);
			Annotation copy = getAnnotation(h, data.cp, field, A_COPY);
			if (copy != null) {
				RawMethod staticInitializer = null;

				String val = copy.getString("staticInitializer");
				if (val != null) {
					int id = data.getMethod(val);
					if (id == -1) throw new TransformException("字段的staticInitializer不存在: 名称 " + val + " 位置: " + data.name + '.' + field.name);
					staticInitializer = methods.get(id);
					if (!"()V".equals(staticInitializer.rawDesc()) || (staticInitializer.access & AccessFlag.STATIC) == 0) {
						throw new TransformException("字段的staticInitializer签名/权限不合法: 名称 " + val + " 位置: " + data.name + '.' + field.name);
					}
				}
				int boolFlag = copy.getInt("targetIsFinal", 0);
				if (boolFlag == 1) field.access |= AccessFlag.FINAL;

				String newName = copy.getString("newName");
				if (copy.getInt("unique", 0) > 0) {
					newName = "m^" + System.nanoTime() % 100000 + "_" + i;
				}

				RemapEntry entry = new RemapEntry(field);
				entry.toClass = destClass;
				entry.toName = newName;
				entries.add(entry);

				if (newName != null) field.name = data.cp.getUtf(newName);
				tmpCopyFields.put(field, staticInitializer);
				fields.remove(i);
			}
		}
		// endregion

		Map<RawMethod, Annotation> tmpInjects = new MyHashMap<>();
		// region 处理 Inject 注解 (1/3)
		for (int i = methods.size() - 1; i >= 0; i--) {
			RawMethod method = methods.get(i);
			Annotation map = getAnnotation(h, data.cp, method, A_INJECT);
			if (map != null) {
				String remapName = map.getString("value", "");
				if (h != null) remapName = h.map(destClass, method, remapName);
				if (remapName.equals("/")) remapName = method.name();

				String desc = map.getString("desc", "");
				if (!desc.isEmpty()) method.rawDesc(data.cp, desc);

				RemapEntry entry = new RemapEntry(method);
				entry.toClass = destClass;
				entry.toName = remapName;
				entries.add(entry);

				map.put("value", new AnnValString(method.name()));
				method.name = data.cp.getUtf(remapName);
				tmpInjects.put(method, map);
				methods.remove(i);
			}
		}
		// endregion
		nx.remaps = entries;
		ShadowCheck tmp = new ShadowCheck();
		// region 提前检测可能出现的 IllegalAccessError/NoSuchFieldError (应该可以全部检测出来，改过顺序了)
		MyHashSet<DescEntry> inaccessible = new MyHashSet<>();
		boolean isSamePackage = MapUtil.arePackagesSame(data.name, destClass);
		for (int i = 0; i < methods.size(); i++) {
			RawMethod remain = methods.get(i);
			if (remain.name().startsWith("$$$")) continue;
			int acc = remain.access;
			if (((acc & (AccessFlag.PRIVATE | AccessFlag.STATIC)) != AccessFlag.STATIC) || ((acc & AccessFlag.PUBLIC) == 0 && !isSamePackage)) {
				inaccessible.add(new DescEntry(remain));
			}
		}
		for (int i = 0; i < fields.size(); i++) {
			RawField remain = fields.get(i);
			int acc = remain.access;
			if (((acc & (AccessFlag.PRIVATE | AccessFlag.STATIC)) != AccessFlag.STATIC) || ((acc & AccessFlag.PUBLIC) == 0 && !isSamePackage)) {
				inaccessible.add(new DescEntry(remain));
			}
		}

		MyCodeVisitor cv = new MyCodeVisitor(data, inaccessible, tmp, nx);
		for (Map.Entry<RawMethod, Annotation> entry : tmpInjects.entrySet()) {
			RawMethod m = entry.getKey();
			cv.tmp2.name = m.name();
			cv.tmp2.desc = m.rawDesc();
			cv.isInit = entry.getValue().getString("value").equals("<init>");
			// Inject = remove 时
			Attribute code = m.attrByName("Code");
			if (code != null) cv.MyVisit(code.getRawData());
		}
		cv.tmp2 = null;
		List<RawMethod> copyMethod1 = Helpers.cast(nx.copyMethod);
		for (int i = 0; i < copyMethod1.size(); i++) {
			// 允许复制抽象方法
			Attribute code = copyMethod1.get(i).attrByName("Code");
			if (code != null) cv.MyVisit(code.getRawData());
		}
		// endregion
		// region 获取父类（如果也是Nixim），并继承Copy/Shadow
		Map<String, NiximData> parentNx;
		if (h != null && (parentNx = h.getParentMap()) != null) {
			parentNx.put(nx.self, nx);
		} else {
			parentNx = Collections.singletonMap(nx.self, nx);
		}
		// endregion
		// region 统一在常量模式做映射(Shadow/Copy)，降低在操作码模式的工作量

		MapUtil tr = MapUtil.getInstance();
		tr.checkSubClass = false;
		AbstractMap<String, String> fakeMap = new AsTransformer(parentNx);

		List<CstRef> refs = ctx.getFieldConstants();
		int k = 0;
		while (true) {
			if (k == refs.size()) {
				if (refs != ctx.getFieldConstants()) break;
				refs = ctx.getMethodConstants();
				k = 0;
			}
			CstRef ref = refs.get(k++);

			NiximData nx1 = parentNx.get(ref.getClassName());
			if (nx1 != null) {
				RemapEntry target = nx1.remaps.find(tmp.read(ref));
				if (target != tmp) {
					String name = target.toClass;
					if (name != null && !name.equals(ref.getClassName())) ref.setClazz(data.cp.getClazz(name));
					name = target.toName;
					if (name != null && !name.equals(tmp.name)) ref.desc(data.cp.getDesc(name, tmp.desc));
				}
			}

			CstUTF desc = ref.desc().getType();
			desc.setString(tr.mapMethodParam(fakeMap, desc.getString()));
		}

		List<CstClass> clzs = ctx.getClassConstants();
		for (int i = 0; i < clzs.size(); i++) {
			CstClass clz = clzs.get(i);
			NiximData nx1 = parentNx.get(clz.getValue().getString());
			if (nx1 != null) {
				clz.setValue(data.cp.getUtf(nx1.dest));
			}
		}

		for (Iterator<ShadowCheck> itr = nx.shadowChecks.iterator(); itr.hasNext(); ) {
			ShadowCheck sc = itr.next();
			itr.remove();
			// 不检查不是owner的shadow
			if (!sc.toClass.equals(nx.dest)) continue;
			sc.name = sc.toName;
			nx.shadowChecks.add(sc);
		}
		// endregion

		BootstrapMethods bsms = data.parsedAttr(data.cp,Attribute.BootstrapMethods);
		IntMap<LambdaInfo> lambdaBSM = nx.bsm = bsms == null ? null : new IntMap<>(bsms.methods.size());
		List<Method> copyMethod = nx.copyMethod;
		// region 后处理 Copy 注解
		for (int i = 0; i < copyMethod.size(); i++) {
			RawMethod ms = copyMethod1.get(i);
			Method method = new Method(data, ms);

			if (bsms != null) {
				InsnList insn = method.getCode().instructions;
				for (int j = 0; j < insn.size(); j++) {
					InsnNode node = insn.get(j);
					if (node.code == INVOKEDYNAMIC) {
						InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
						processInvokeDyn(idn, data.name, destClass);
						BootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.tableIdx);
						lambdaBSM.computeIfAbsentIntS(idn.tableIdx, () -> new LambdaInfo(bsm)).nodes.add(idn);
					}
				}
			}

			postProcNxMd(destClass, method);

			copyMethod.set(i, method);
		}
		Map<Field, Method> copyField = nx.copyField;
		for (Map.Entry<RawField, RawMethod> entry : tmpCopyFields.entrySet()) {
			Field field = new Field(data, entry.getKey());
			RawMethod ms = entry.getValue();

			Method m = ms == null ? null : new Method(data, ms);
			copyField.put(field, m);

			if (m == null) continue;

			InsnList insn = m.getCode().instructions;
			for (int i = 0; i < insn.size(); i++) {
				InsnNode node = insn.get(i);
				if (node.code == PUTSTATIC) {
					FieldInsnNode fin = (FieldInsnNode) node;
					if (fin.owner.equals(data.name) && (!fin.name.equals(field.name) || !fin.rawType.equals(field.rawDesc()))) {
						System.out.println("NiximWarn: 在static{}修改了不属于自己的字段 " + fin);
					}
				} else if (node.code == INVOKEDYNAMIC) {
					InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
					processInvokeDyn(idn, data.name, destClass);
					BootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.tableIdx);
					lambdaBSM.computeIfAbsentIntS(idn.tableIdx, () -> new LambdaInfo(bsm)).nodes.add(idn);
				}
			}
		}
		tmpCopyFields.clear();
		// endregion
		// region 处理 Inject 注解 (2/3)
		for (Map.Entry<RawMethod, Annotation> entry : tmpInjects.entrySet()) {
			RawMethod ms = entry.getKey();
			Annotation map = entry.getValue();

			Method remap = new Method(data, ms);

			postProcNxMd(destClass, remap);

			InjectState state = new InjectState(remap, map);

			if (remap.getCode() == null) {
				switch (state.at) {
					case "REMOVE":
					case "INVOKE":
					case "LDC":
						continue;
					default:
						throw new TransformException(state.at+"类型的注入不能是抽象方法");
				}
			}
			InsnList insn = remap.getCode().instructions;
			for (int i = 0; i < insn.size(); i++) {
				InsnNode node = insn.get(i);
				switch (node.code) {
					case INVOKESPECIAL: {
						InvokeInsnNode inv = (InvokeInsnNode) node;
						if (remap.name.equals("<init>") && (inv.owner.equals("//MARKER") || inv.owner.equals(data.parent))) {
							state.superCallEnd = i;
						}
					}
					// not check fallthrough
					case INVOKEVIRTUAL:
					case INVOKEINTERFACE:
					case INVOKESTATIC: {
						InvokeInsnNode inv = (InvokeInsnNode) node;
						if (inv.owner.equals("//MARKER")) {
							if (state.at.equals("OLD_SUPER_INJECT")) {
								inv.name = state.name;
								state.flags |= FLAG_HAS_INVOKE;
							}
							inv.owner = destClass;
						}
					}
					break;
					case INVOKEDYNAMIC:
						if (bsms == null) {
							throw new TransformException("在没有BootstrapMethods的类中找到了InvokeDynamic!");
						}
						InvokeDynInsnNode idn = (InvokeDynInsnNode) node;
						processInvokeDyn(idn, data.name, destClass);
						BootstrapMethods.BootstrapMethod bsm = bsms.methods.get(idn.tableIdx);
						lambdaBSM.computeIfAbsentIntS(idn.tableIdx, () -> new LambdaInfo(bsm)).nodes.add(idn);
						break;
				}
			}

			processInject(state, data, nx);

			if (state.superCallEnd == 0 && state.name.equals("<init>")) throw new TransformException("没有找到 superCallEnd");

			InjectState state1 = nx.injectMethod.putIfAbsent(new DescEntry(remap), state);
			if (state1 != null) state1.next = state;
		}
		// endregion
		// region 复制用到的 lambda 方法
		if (lambdaBSM != null) {
			for (LambdaInfo info : lambdaBSM.values()) {
				List<Constant> args = info.bootstrapMethod.arguments;
				find:
				for (int i = 0; i < args.size(); i++) {
					Constant c = args.get(i);
					if (c.type() != Constant.METHOD_HANDLE) continue;
					CstMethodHandle handle = (CstMethodHandle) c;
					CstRef ref = handle.getRef();

					if (!ref.getClassName().equals(destClass)) {
						// not self method
						break;
					}

					List<? extends MethodNode> nodes = data.methods;
					for (int j = 0; j < nodes.size(); j++) {
						RawMethod method = (RawMethod) nodes.get(j);
						if (method.name.equals(ref.desc().getName()) && method.type.equals(ref.desc().getType())) {
							Method lmd = new Method(data, method);
							lmd.name = "NLambda^" + copyMethod.size();
							ref.desc(data.cp.getDesc(lmd.name, lmd.rawDesc()));

							postProcNxMd(destClass, lmd);

							copyMethod.add(lmd);
							break find;
						}
					}

					throw new TransformException("无法找到符合条件的 lambda 方法: " + ref.desc());
				}
			}
		}
		// endregion

		return nx.isUsed() ? nx : null;
	}

	public static void transformUsers(Context ctx, Map<String, NiximData> parentNx) {
		ConstantData data = ctx.getData();
		RemapEntry tester = new RemapEntry();

		MapUtil tr = MapUtil.getInstance();
		tr.checkSubClass = false;
		AbstractMap<String, String> fakeMap = new AsTransformer(parentNx);

		List<CstRef> refs = ctx.getFieldConstants();
		int k = 0;
		while (true) {
			if (k == refs.size()) {
				if (refs != ctx.getFieldConstants()) break;
				refs = ctx.getMethodConstants();
				k = 0;
			}
			CstRef ref = refs.get(k++);

			NiximData nx1 = parentNx.get(ref.getClassName());
			if (nx1 != null) {
				RemapEntry target = nx1.remaps.find(tester.read(ref));
				if (target != tester) {
					String name = target.toClass;
					if (name != null && !name.equals(ref.getClassName())) ref.setClazz(data.cp.getClazz(name));
					name = target.toName;
					if (name != null && !name.equals(tester.name)) {
						ref.desc(data.cp.getDesc(name, tester.desc));
					}
				}
			}

			CstUTF desc = ref.desc().getType();
			desc.setString(tr.mapMethodParam(fakeMap, desc.getString()));
		}

		for (int i = 0; i < data.methods.size(); i++) {
			MethodNode mn = data.methods.get(i);
			mn.rawDesc(data.cp, tr.mapMethodParam(fakeMap, mn.rawDesc()));
		}

		List<CstClass> clzs = ctx.getClassConstants();
		for (int i = 0; i < clzs.size(); i++) {
			CstClass clz = clzs.get(i);
			NiximData nx1 = parentNx.get(clz.getValue().getString());
			if (nx1 != null) {
				clz.setValue(data.cp.getUtf(nx1.dest));
			}
		}
	}

	// 核心之一 (2/4)
	private static void processInject(InjectState s, ConstantData data, NiximData nx) throws TransformException {
		Method method = s.method;
		sw:
		switch (s.at) {
			case "REMOVE":
				break;
			case "REPLACE":
				if (method.name.equals("<init>")) {
					boolean selfInit = s.name.equals("<init>");
					InsnList insn = method.getCode().instructions;
					for (int i = 0; i < insn.size(); i++) {
						InsnNode node = insn.get(i);
						if (node.nodeType() == InsnNode.T_INVOKE) {
							InvokeInsnNode iin = (InvokeInsnNode) node;
							// 使用 $$$CONSTRUCT来假装使用了初始化器
							if (selfInit ? (iin.name.equals("<init>") && (iin.owner.equals(data.parent))) : iin.name.startsWith(SPEC_M_CONSTRUCTOR)) {
								iin.setOpcode(INVOKESPECIAL);
								s.superCallEnd = i;
								AttributeList attributes = s.method.attributes();
								AttrLineNumber ln = (AttrLineNumber) attributes.getByName("LineNumberTable");
								if (ln != null) {
									st:
									for (int j = 0; j < ln.list.size(); j++) {
										LineNumber ln1 = ln.list.get(j);
										for (int k = 0; k < i; k++) {
											if (insn.get(k) == ln1.node) {
												ln.list.remove(j);
												break st;
											}
										}
									}
								}
								break sw;
							}
						}
					}
					throw new TransformException("替换构造器 " + method.name + ' ' + method.rawDesc() + " 未发现使用 " + SPEC_M_CONSTRUCTOR + " 作为初始化器的标记");
				}
				break;
			case "HEAD": {
				boolean usedContinue = false;
				Int2IntMap assignedLV = new Int2IntMap();
				int paramLength = computeParamLength(method, assignedLV);
				InsnList insn = method.getCode().instructions;
				if (s.superCallEnd > 0) insn.removeRange(0, s.superCallEnd);

				for (int i = 0; i < insn.size(); i++) {
					InsnNode node = insn.get(i);
					// 检测参数的assign然后做备份
					int index = InsnHelper.getVarId(node);
					if (index >= 0) {
						int code = node.getOpcodeInt();
						if (code >= ISTORE && code <= ASTORE_3) {
							Int2IntMap.Entry entry = assignedLV.getEntry(index);
							if (entry != null) {
								if (code >= ISTORE_0) code = (byte) (((code - ISTORE_0) / 4) + ISTORE);
								entry.v = code;
							} else if (index < paramLength) throw new TransformException("无效的assign " + method + "# " + insn.get(i) + " i " + index + " " + assignedLV);
						}
					} else if (i > 0 && InsnHelper.isReturn(node.code)) {
						node = insn.get(i-1);
						if (node.code == INVOKESTATIC) {
							InvokeInsnNode iin = (InvokeInsnNode) node;
							// 用特殊的字段名(startWith: $$$CONTINUE)指定【我还要继续执行】
							if (iin.name.startsWith(SPEC_M_CONTINUE)) {
								usedContinue = true;
								//删除调用和其后的return
								s.gotos().add(insn.remove(--i));

								if (i > 0 && insn.get(i-1) instanceof JumpInsnNode) {
									// 【否】则走进return
									// 此时需要保留
									LabelInsnNode target = new LabelInsnNode();
									s.gotos().add(target);
									insn.set(i, new JumpInsnNode(target));
								} else {
									insn.remove(i);
								}
							}
						}
					}
				}
				if (!usedContinue) throw new TransformException("Head注入未用到CONTINUE, 你应该使用REPLACE模式");
				// 备份参数
				if (!assignedLV.isEmpty()) {
					InsnList prepend = new InsnList();
					for (Iterator<Int2IntMap.Entry> itr = assignedLV.selfEntrySet().iterator(); itr.hasNext(); ) {
						Int2IntMap.Entry entry = itr.next();
						if (entry.v != 0) {
							byte tCode = (byte) (entry.v - (ISTORE - ILOAD));
							prepend.var(tCode, entry.getIntKey());
							int tKey = entry.getIntKey() - 1 + paramLength;
							prepend.var((byte) entry.v, tKey);
						} else {
							itr.remove();
						}
					}
					if (!assignedLV.isEmpty()) {
						insn.addAll(0, prepend);
						s.assignId = assignedLV;
					}
				}
			}
			break;
			case "INVOKE":
			case "LDC":
				String name = s.param.get(1).asString();
				for (RemapEntry entry : nx.remaps) {
					if (entry.name.equals(name)) {
						if (entry.toName != null) name = entry.toName;
						break;
					}
				}
				List<Method> copyMethod = nx.copyMethod;
				for (int i = 0; i < copyMethod.size(); i++) {
					Method copy = copyMethod.get(i);
					if (copy.name.equals(name)) {
						s.method2 = copy;
						//if ((copy.access & AccessFlag.STATIC) == 0) throw new TransformException(s.at + "类型的注入源必须为静态的");
						return;
					}
				}
				throw new TransformException("未找到" + s.at + "的目标方法" + name + ",你有打Copy吗");
			case "TAIL": {
				Int2IntMap assignedLV = new Int2IntMap();
				int paramLength = computeParamLength(method, assignedLV);
				InsnList insn = method.getCode().instructions;

				for (int i = s.superCallEnd; i < insn.size(); i++) {
					InsnNode node = insn.get(i);
					int index = InsnHelper.getVarId(node);
					if (index >= 0) {
						int code = node.getOpcodeInt();
						if (code >= ILOAD && code <= ALOAD_3) {
							// 计算tail用到的参数id，若目标方法修改过value则暂存到新的变量id中然后再恢复
							Int2IntMap.Entry entry = assignedLV.getEntry(index);
							if (entry != null) {
								if (code >= ILOAD_0) code = (byte) (((code - ILOAD_0) / 4) + ILOAD);
								entry.v = code;
							}/* else if (index < paramLength)
                                throw new TransformException("无效的assign " + method + "# " + insn.get(i));*/
						} else if (i > 0 && code >= ISTORE && code <= ASTORE_3) {
							node = insn.get(i - 1);
							if (node.code == INVOKESTATIC) {
								InvokeInsnNode iin = (InvokeInsnNode) node;
								// 将返回值（如果存在）存放到指定的变量，用特殊的字段名(startWith: $$$RETURN_VAL)指定
								if (iin.name.startsWith(SPEC_M_RETVAL)) {
									if (TypeHelper.parseReturn(iin.desc).type != method.returnType().type)
										throw new TransformException("返回值指代#"+s.retVal().size()+"(CodeIndex: "+ i +")的返回值不适用于目的方法的" + method.returnType());
									s.retVal().add(i - 1);
								}
							}
						}
					}
				}
				// 备份参数
				if (!assignedLV.isEmpty()) {
					// noinspection all
					for (Iterator<Int2IntMap.Entry> itr = assignedLV.selfEntrySet().iterator(); itr.hasNext(); ) {
						Int2IntMap.Entry entry = itr.next();
						if (entry.v == 0) {
							itr.remove();
						}
					}
					if (!assignedLV.isEmpty()) s.assignId = assignedLV;
				}
			}
			break;
			// 注入super方法
			case "OLD_SUPER_INJECT":
				if ((s.flags & FLAG_HAS_INVOKE) == 0) {
					s.at = "REPLACE";
				}
				break;
			case "MIDDLE":
				name = s.param.get(0).asString();
				s.method2 = data.getUpgradedMethod(name);
				if (s.method2 == null) throw new TransformException("未找到" + s.at + "的目标方法" + name);
				// replace
				if (checkAndTrimMatcher(s, s.method.getCode()) != -1L) throw new TransformException("替换【目标】为什么需要Match_Target标志呢, 打注解的是目标，param填入的才是matcher！");
				// matcher
				long l = checkAndTrimMatcher(null, s.method2.getCode());
				s.superCallEnd = (int) (l >>> 32);
				s.regionEnd = (int) l;
				if (s.superCallEnd < 0 != s.regionEnd < 0)
					throw new TransformException("MATCH_TARGET不成对出现");
				break;
		}
	}

	private static long checkAndTrimMatcher(InjectState s, AttrCode m) throws TransformException {
		int begin = -1, end = -1, tbegin = -1, tend = -1;
		InsnList insn = m.instructions;
		for (int i = 0; i < insn.size(); i++) {
			InsnNode node = insn.get(i);
			if (node.code == INVOKESTATIC) {
				InvokeInsnNode iin = (InvokeInsnNode) node;
				switch (iin.name) {
					case "$$$MATCH_BEGIN":
						if (begin >= 0) throw new TransformException("重复的Matcher_Begin标志");
						begin = i + 1;
						break;
					case "$$$MATCH_END":
						if (end >= 0) throw new TransformException("重复的Matcher_End标志");
						if (s != null) {
							s.gotos().add(iin);
						}
						end = i;
						break;
					case "$$$MATCH_TARGET_BEGIN":
						if (tbegin >= 0) throw new TransformException("重复的Matcher_Insert标志");
						tbegin = i;
						break;
					case "$$$MATCH_TARGET_END":
						if (tend >= 0) throw new TransformException("重复的Matcher_Insert_End标志");
						tend = i;
						break;
				}
				if (iin.name.startsWith(SPEC_M_CONTINUE)) {
					if (s != null) {
						s.gotos().add(iin);
					}
				}
			}
		}
		if (begin != end) {
			if (begin == end - 1) {
				throw new TransformException("空的Matcher区段");
			}
			if (end >= 0) insn.removeRange(end, insn.size());
			if (begin >= 0) insn.removeRange(0, begin);
		}
		return ((long)tbegin << 32L) | tend;
	}

	private static int computeParamLength(Method method, Int2IntMap assigned) {
		int paramLength = 0 != (method.access & AccessFlag.STATIC) ? 0 : 1;
		List<Type> params = method.parameters();
		for (int i = 0; i < params.size(); i++) {
			Type t = params.get(i);
			if (assigned != null) assigned.putInt(paramLength, 0);
			paramLength += t.length();
		}
		return paramLength;
	}

	private static void processInvokeDyn(InvokeDynInsnNode ind, String name, String dest) {
		ind.desc = MapUtil.getInstance().mapMethodParam(Collections.singletonMap(name, dest), ind.desc);
	}

	private static void processShadow(Context ctx, boolean b, String targetDef, Set<ShadowCheck> shadowChecks, NiximHelper h) {
		ConstantData data = ctx.getData();
		List<? extends MoFNode> target = b ? data.fields : data.methods;
		for (int j = target.size() - 1; j >= 0; j--) {
			MoFNode obj = target.get(j);
			Annotation shadow = getAnnotation(h, data.cp, obj, A_SHADOW);
			if (shadow != null) {
				ShadowCheck check = new ShadowCheck(obj);

				String owner = shadow.getString("owner");
				check.toClass = owner == null ? targetDef : unifyClassName(owner);
				check.toName = shadow.getString("value", "");
				if (h != null) check.toName = h.map(check.toClass, obj, check.toName);
				if (check.toName.equals("/")) {
					check.toName = obj.name();
				}

				check.flag = (byte) (obj.modifier() & (AccessFlag.FINAL | AccessFlag.STATIC | AccessFlag.PRIVATE));
				switch (shadow.getEnumValue("finalType", "NORMAL")) {
					case "NORMAL": break;
					case "FINAL": check.flag |= AccessFlag.FINAL; break;
					case "DEFINAL": check.flag |= DEFINAL; break;
				}
				shadowChecks.add(check);

				target.remove(j);
			}
		}
	}

	private static String unifyClassName(String t) {
		return t.replace('.', '/');
	}

	private static boolean postProcNxMd(String dst, Method m) throws TransformException {
		if (m.getCode() == null) return true;
		//    throw new TransformException("方法不能是抽象的: " + m.owner + '.' + m.name + ' ' + m.rawDesc());
		if (m.name.equals("<init>") && !m.rawDesc().endsWith(")V")) throw new TransformException("构造器映射返回必须是void: " + m.owner + '.' + m.name + ' ' + m.rawDesc());

		if (m.rawDesc().contains(m.owner)) {
			List<Type> params = m.parameters();
			for (int i = 0; i < params.size(); i++) {
				Type type = params.get(i);
				if (m.owner.equals(type.owner)) {
					type.owner = dst;
				}
			}
			if (m.owner.equals(m.returnType().owner)) {
				m.returnType().owner = dst;
			}
		}

		Annotations anno = m.parsedAttr(null, Attribute.ClAnnotations);
		if (anno != null) {
			List<Annotation> annos = anno.annotations;
			for (int i = annos.size()-1; i >= 0; i--) {
				if (annos.get(i).clazz.startsWith("roj/asm/nixim")) annos.remove(i);
			}
		}

		return m.getCode().frames != null;
	}

	private static Integer checkNiximFlag(ConstantData data, Attribute attr, NiximData nx, NiximHelper h) {
		if (attr == null) return null;

		Integer flag = null;
		List<Annotation> anns = Annotations.parse(data.cp, Parser.reader(attr));
		for (int j = 0; j < anns.size(); j++) {
			Annotation ann = anns.get(j);
			if (ann.clazz.equals(A_NIXIM_CLASS_FLAG)) {
				nx.dest = unifyClassName(ann.getString("value"));
				if (nx.dest.equals("/")) nx.dest = data.parent;
				if (ann.getInt("copyItf", 0) != 0) {
					List<CstClass> itf = data.interfaces;
					for (int i = 0; i < itf.size(); i++) {
						nx.addItfs.add(itf.get(i).getValue().getString());
					}
				}
				flag = ann.getInt("flag", 0);
			} else if (ann.clazz.equals(A_IMPL_INTERFACE)) {
				List<AnnVal> annVals = ann.getArray("value");
				for (int i = 0; i < annVals.size(); i++) {
					nx.addItfs.add(annVals.get(i).asClass().owner);
				}
			} else if (ann.clazz.equals(A_DYNAMIC)) {
				List<AnnVal> annVals = ann.getArray("value");
				if (!h.shouldApply(A_NIXIM_CLASS_FLAG, null, Helpers.cast(annVals))) {
					return -1;
				}
			}
		}
		return flag;
	}

	public static Annotation getAnnotation(NiximHelper nh, ConstantPool pool, MoFNode cmp, String clazz) {
		List<Annotation> anns = AttrHelper.getAnnotations(pool, cmp, false);
		if (anns == null) return null;

		Annotation found = null;
		for (int i = anns.size() - 1; i >= 0; i--) {
			Annotation ann = anns.get(i);
			if (ann.clazz.equals(clazz)) {
				anns.remove(i);
				if (nh == null) return ann;
				found = ann;
			} else if (nh != null && ann.clazz.equals(A_DYNAMIC)) {
				if (!nh.shouldApply(clazz, cmp, Helpers.cast(ann.getArray("value")))) return null;
				if (found != null) break;
				nh = null;
			}
		}
		return found;
	}

	// endregion
	// region 工具人

	static final int FLAG_HAS_INVOKE = 262144;

	static final class InjectState {
		// 注解的信息
		String at;
		int flags;

		// replace模式: 方法原名, 检测 $$$CONSTRUCTOR调用
		// super inject模式: SIJ方法名
		String name;

		Method method;

		int superCallEnd;
		final List<?> nodeList;

		// Head
		Int2IntMap assignId;

		@SuppressWarnings("unchecked")
		public List<InsnNode> gotos() {
			return (List<InsnNode>) nodeList;
		}

		// Middle
		List<AnnVal> param;
		Method method2;
		int regionEnd;

		// Tail
		@SuppressWarnings("unchecked")
		public List<Integer> retVal() {
			return (List<Integer>) nodeList;
		}

		InjectState next;

		InjectState(Method method, Annotation map) {
			strip(method);
			this.method = method;

			this.at = map.getEnumValue("at", "OLD_SUPER_INJECT");
			this.flags = map.getInt("flags", 0);

			this.param = map.getArray("param");

			switch (this.at) {
				case "HEAD":
				case "TAIL":
				case "MIDDLE":
				case "OLD_SUPER_INJECT":
					this.nodeList = new ArrayList<>();
					break;
				default:
					this.nodeList = null;
					break;
			}
			this.name = this.at.equals("OLD_SUPER_INJECT") ? method.name + '_' + (System.nanoTime() % 10000) : map.getString("value");
		}
	}

	public static final class NiximData {
		// Nixim
		public final String self;
		public String dest;
		public final List<String> addItfs;

		// Inject
		public final Map<DescEntry, InjectState> injectMethod;

		// Copy
		public final MyHashMap<Field, Method> copyField;
		public final List<Method> copyMethod;

		// Shadow
		public final MyHashSet<ShadowCheck> shadowChecks;
		public MyHashSet<RemapEntry> remaps;

		// lambda
		public IntMap<LambdaInfo> bsm;

		NiximData next;

		public NiximData(String self) {
			this.self = self;
			this.addItfs = new ArrayList<>();
			this.injectMethod = new MyHashMap<>();
			this.copyField = new MyHashMap<>();
			this.copyMethod = new ArrayList<>();
			this.shadowChecks = new MyHashSet<>();
		}

		public boolean isUsed() {
			return !addItfs.isEmpty() || !injectMethod.isEmpty() || !copyField.isEmpty() || !copyMethod.isEmpty();
		}

		@Override
		public String toString() {
			return "Nixim{'" + self + "' => '" + dest + '\'' + '}';
		}
	}

	private static class MyCodeVisitor extends CodeVisitor {
		private final ConstantData data;
		private final NiximData nx;
		private final MyHashSet<DescEntry> inaccessible;

		private final RemapEntry tmp;
		DescEntry tmp2;
		boolean isInit;

		public MyCodeVisitor(ConstantData data, MyHashSet<DescEntry> inaccessible, RemapEntry tmp, NiximData nx) {
			this.data = data;
			this.inaccessible = inaccessible;
			this.tmp = tmp;
			this.tmp2 = new DescEntry();
			this.nx = nx;
		}

		ByteList.Slice I = new ByteList.Slice();
		public void MyVisit(DynByteBuf code) {
			visit(data.cp, I.copy(code));
		}

		@Override
		public void invokeItf(CstRefItf itf, short argc) {
			if (tmp2 != null) checkInvokeTarget(itf);
		}

		@Override
		@RequireTest
		public void invoke(byte code, CstRef method) {
			if (code == INVOKEVIRTUAL && method.desc().getName().getString().equals("<init>")) {
				for (int i = 0; i < 100; i++) {
					System.err.println("INVOKEINIT " + (I.get(I.rIndex - 3) == INVOKEVIRTUAL));
				}
				I.put(I.rIndex - 3, INVOKESPECIAL);
			}
			checkAccess(method);
			if (tmp2 != null) checkInvokeTarget(method);
		}

		@Override
		public void field(byte code, CstRefField field) {
			checkAccess(field);
			if (!isInit && (code == PUTFIELD || code == PUTSTATIC)) {
				if (field.getClassName().equals(data.name)) {
					ShadowCheck sc = nx.shadowChecks.find((ShadowCheck) tmp.read(field));
					if (sc != tmp && (sc.flag & AccessFlag.FINAL) != 0) {
						Helpers.athrow(new TransformException("不能修改final或为实际上final的字段 " + data.name + '.' + tmp));
					}
				}
			}
		}

		private void checkAccess(CstRef ref) {
			if (ref.getClassName().equals(data.name) && inaccessible.contains(tmp.read(ref))) {
				Helpers.athrow(new TransformException("无法访问" + data.name + '.' + tmp + ": 会出现 IllegalAccessError / NoSuchFieldError"));
			}
		}

		private void checkInvokeTarget(CstRef ref) {
			if (ref.getClassName().equals(nx.dest) && tmp.read(ref).equals(tmp2)) {
				ref.setClazz(data.cp.getClazz("//MARKER"));
			}
		}
	}

	// endregion
}
