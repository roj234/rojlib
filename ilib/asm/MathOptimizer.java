package ilib.asm;

import roj.asm.cst.*;
import roj.asm.tree.insn.InsnList;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.asm.util.Context;

import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Set;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/10/19 20:38
 */
public final class MathOptimizer {
	static void optimizeMathHelper(Context ctx) {
		List<Constant> csts = ctx.getData().cp.array();
		for (int i = 0; i < csts.size(); i++) {
			Constant cst = csts.get(i);
			switch (cst.type()) {
				case Constant.DOUBLE:
					CstDouble db = (CstDouble) cst;
					if (db.value == 65536.0) db.value = 32768.0;
					break;
				case Constant.FLOAT:
					CstFloat fl = (CstFloat) cst;
					if (fl.value == 65536F) {
						fl.value = 32768F;
					} else if (fl.value == 10430.378F) {
						fl.value = 5215.189F;
					} else if (fl.value == 16384.0F) {
						fl.value = 8192.0F;
					}
					break;
				case Constant.INT:
					CstInt in = (CstInt) cst;
					if (in.value == 65536) in.value = 32768;
					else if (in.value == 65535) in.value = 32767;
					break;
			}
		}
	}

	public static double sin(double sin) {
		return MathHelper.sin((float) sin);
	}

	public static double cos(double cos) {
		return MathHelper.cos((float) cos);
	}

	public static float sin(float val) {
		return MathHelper.sin(val);
	}

	public static float cos(float val) {
		return MathHelper.cos(val);
	}

	private static void optimizeSlowJavaCall(Context ctx) {
		List<CstRef> methodRefs = ctx.getMethodConstants();
		for (int i = 0; i < methodRefs.size(); i++) {
			CstRef ref = methodRefs.get(i);
			if (ref.className().equals("java/lang/Math")) {
				switch (ref.desc().name().str()) {
					case "sin":
					case "cos":
						ref.clazz(ctx.getData().cp.getClazz("ilib/asm/transformers/MathOptimizer"));
						break;
				}
			}
		}
	}

	static Set<String> ENUM_VALUES_CALL;

	static void optimizeEnumValues(Context ctx) {
		List<CstRef> methodRefs = ctx.getMethodConstants();
		for (int i = 0; i < methodRefs.size(); i++) {
			CstRef ref = methodRefs.get(i);
			if (ENUM_VALUES_CALL.contains(ref.className())) {
				// 这个一般都会放在for循环里面，如果不是，就崩溃吧 233
				// 开个线程每秒检测似乎没毛病，但是很丑，不是么
				// 也许可以放在必须解析code的地方
				if ("values".equals(ref.desc().name().str())) {
					ref.clazz(ctx.getData().cp.getClazz("ilib/asm/transformers/MathOptimizer"));
					ref.desc(ctx.getData().cp.getDesc("sharedValues", ref.desc().getType().str()));
				}
			}
		}
	}

	static void optimize(Context ctx) {
		optimizeSlowJavaCall(ctx);
		//        List<MethodSimple> ms = ctx.getData().methods;
		//        for (int i = 0; i < ms.size(); i++) {
		//            if (ms.get(i).attributes.getByName("Code") == null) continue;
		//            Method method = new Method(ctx.getData(), ms.get(i));
		//            InsnList insn = method.code.instructions;
		//            InsnNode prev = null;
		//            for (int j = 0; j < insn.size(); j++) {
		//                InsnNode insnNode = insn.get(j);
		//                switch (insnNode.getOpcode()) {
		//                    case INVOKESTATIC:
		//                        InsnList inline = inline0((InvokeInsnNode) insnNode);
		//                        if (inline != null) {
		//                            insn.remove(i);
		//                            insn.addAll(i, inline);
		//                        }
		//                        break;
		//                    case DDIV:
		//                        if (prev.nodeType() == InsnNode.T_LDC) {
		//                            CstDouble c = (CstDouble) ((LdcInsnNode) prev).c;
		//                            if (c.value == 2.0D) {
		//                                insnNode.setOpcode(DMUL);
		//                                c.value = 0.5D;
		//                            }
		//                        }
		//                        break;
		//                    case FDIV:
		//                        if (prev.nodeType() == InsnNode.T_LDC) {
		//                            CstFloat c = (CstFloat) ((LdcInsnNode) prev).c;
		//                            if (c.value == 2.0F) {
		//                                insnNode.setOpcode(FMUL);
		//                                c.value = 0.5F;
		//                            } else if (prev.getOpcode() == FCONST_2) {
		//                                insn.set(i - 1, new LdcInsnNode(new CstFloat(0.5F)));
		//                            }
		//                        }
		//                        break;
		//                    case IDIV:
		//                        switch (prev.getOpcode()) {
		//                            case ICONST_2:
		//                                prev.setOpcode(ICONST_1);
		//                                insnNode.setOpcode(ISHR);
		//                                break;
		//                            case ICONST_4:
		//                                prev.setOpcode(ICONST_2);
		//                                insnNode.setOpcode(ISHR);
		//                                break;
		//                        }
		//                    break;
		//                }
		//                prev = insnNode;
		//            }
		//        }
		//        ctx.getData().normalize();
	}

	private static InsnList inline0(InvokeInsnNode node) {
		String owner = node.owner;
		if (!owner.equals("java/lang/Math") && !owner.equals("java/lang/StrictMath")) {
			return null;
		}
		String name = node.name;
		String descriptor = node.awslDesc();
		InsnList insns = new InsnList();
		if (descriptor.indexOf('I') != -1) {
			switch (name) {
				default:
					return null;
				case "func_76130_a":
				case "abs": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP);
					insns.jump(IFGE, label);
					insns.one(INEG);
					insns.add(label);
					break;
				}
				case "max": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP2);
					insns.jump(IF_icmpge, label);
					insns.one(SWAP);
					insns.add(label);
					insns.one(POP);
					break;
				}
				case "min": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP2);
					insns.jump(IF_icmple, label);
					insns.one(SWAP);
					insns.add(label);
					insns.one(POP);
					break;
				}
			}
		} else if (descriptor.indexOf('D') != -1) {
			switch (name) {
				default:
					return null;
				case "toRadians":
					insns.ldc(new CstDouble(180D));
					insns.one(DDIV);
					insns.ldc(new CstDouble(Math.PI));
					insns.one(DMUL);
					break;
				case "toDegrees":
					insns.ldc(new CstDouble(180D));
					insns.one(DMUL);
					insns.ldc(new CstDouble(Math.PI));
					insns.one(DDIV);
					break;
				case "abs": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP2);
					insns.one(DCONST_0);
					insns.one(DCMPG);
					insns.jump(IFGE, label);
					insns.one(DNEG);
					insns.add(label);
					break;
				}
			}
		} else if (descriptor.indexOf('F') != -1) {
			switch (name) {
				default:
					return null;
				case "func_76135_e":
				case "abs": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP);
					insns.one(FCONST_0);
					insns.one(FCMPG);
					insns.jump(IFGE, label);
					insns.one(FNEG);
					insns.add(label);
					break;
				}
				case "max": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP2);
					insns.one(FCMPL);
					insns.jump(IFGE, label);
					insns.one(SWAP);
					insns.add(label);
					insns.one(POP);
					break;
				}
				case "min": {
					LabelInsnNode label = new LabelInsnNode();
					insns.one(DUP2);
					insns.one(FCMPL);
					insns.jump(IFLE, label);
					insns.one(SWAP);
					insns.add(label);
					insns.one(POP);
					break;
				}
				//                case "square":
				//                    insns.one(DUP);
				//                    insns.one(FMUL);
				//                    break;
			}
		} else {
			return null;
		}
		return insns;
	}
}
