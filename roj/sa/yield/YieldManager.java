package roj.sa.yield;

import roj.asm.OpcodeUtil;
import roj.asm.Parser;
import roj.asm.frame.Var2;
import roj.asm.frame.VarType;
import roj.asm.tree.ConstantData;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.attr.AttrLineNumber;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.insn.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.InsnHelper;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeWriter;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.ui.EasyProgressBar;
import roj.util.ByteList;
import roj.util.VarMapper;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2023/4/20 0020 16:35
 */
public class YieldManager {
	public static void main(String[] args) throws IOException {
		ByteList o = ByteList.wrap(IOUtil.readRes("roj/mod/FMDMain.class"));
		if (true) {
			EasyProgressBar b = new EasyProgressBar("Speed");
			b.setUnit("B");
			while (true) {
				Parser.parse(o);
				b.update(0.5d, o.length());
			}
		}

		ConstantData data = Parser.parseConstants(IOUtil.readRes("roj/sa/yield/YieldTest.class"));
		MethodNode mn = data.methods.get(data.getMethod("looper"));
		ConstantData register = createRegister(data, mn);
		data.dump();
		register.dump();
		ClassDefiner.INSTANCE.defineClassC(register);
		ClassDefiner.INSTANCE.defineClassC(data);
		runTest();
	}

	private static void runTest() {
		Generator<String> looper = YieldTest.looper(0, 0, "");
		int j = 0;
		while (looper.hasNext()) {
			String value = looper.next();
			System.out.println(value);
			if (j++ == 42) {
				break;
			}
		}
		looper.__yield_exit();
	}

	public static ConstantData createRegister(ConstantData data, MethodNode method) {
		int mid = data.methods.indexOfAddress(method);

		ConstantData reg = new ConstantData();
		reg.name(data.name+"$Register$"+mid);
		reg.parent("roj/sa/yield/Generator");
		reg.access |= AccessFlag.FINAL;
		reg.npConstructor();

		// generate hook method
		Method _invoke = new Method(AccessFlag.STATIC, data, "^yield$"+mid+System.nanoTime(), method.rawDesc());
		List<Type> _invoke_par = _invoke.parameters();
		if ((method.modifier() & AccessFlag.STATIC) == 0) {
			_invoke_par.add(0, new Type(data.name));
		}
		_invoke_par.add(0, new Type(reg.name));
		_invoke.setReturnType(Type.std(Type.VOID));
		data.methods.add(_invoke);

		Type _fieldType = null;
		int _fieldId = -1;

		AttrCode code = method.parsedAttr(data.cp, Attribute.Code);
		code.localSize++;
		code.frames = null;
		_invoke.putAttr(code);
		//

		// jump via bci
		InsnList list = code.instructions;
		InsnList add = new InsnList();
		SwitchInsnNode sin = new SwitchInsnNode(LOOKUPSWITCH);

		add.one(ALOAD_0);
		add.field(GETFIELD, "roj/sa/yield/Generator", "yield_pos", "I");
		add.switches(sin);
		sin.def = list.get(0);
		list.addAll(0, add);
		//

		VarMapper vmx = new VarMapper();
		vmx.getVars().withContent(true);

		// spawn generator / invoke
		AttrCodeWriter inv = new AttrCodeWriter(data.cp, method);
		method.putAttr(inv);

		CodeWriter cwi = reg.newMethod(AccessFlag.PROTECTED|AccessFlag.FINAL, "invoke", "()V");
		int paramSize = TypeHelper.paramSize(method.rawDesc())+1;
		cwi.visitSize(paramSize, 1);
		cwi.one(ALOAD_0);

		CodeWriter cw = inv.cw;
		// double or long
		cw.visitSize(paramSize+((method.modifier() & AccessFlag.STATIC) == 0 ? 1 : 0) > _invoke_par.size() ? 4 : 3, paramSize+1);
		cw.newObject(reg.name);

		int len = 0;
		for (int i = 1; i < _invoke_par.size(); i++) {
			Type type = _invoke_par.get(i);
			int fid = reg.newField(0, "p"+len, type);

			cw.one(DUP);
			cw.var(type.shiftedOpcode(ILOAD, false), len);
			cw.field(PUTFIELD, reg, fid);

			cwi.one(ALOAD_0);
			cwi.field(GETFIELD, reg, fid);

			vmx.addIfAbsentEx(len).set(0);
			len += type.length();
		}
		cwi.invoke(INVOKESTATIC, _invoke);
		cwi.one(RETURN);
		cw.one(ARETURN);
		//

		for (int i = 3; i < list.size(); i++) {
			InsnNode node = list.get(i);

			int vid = InsnHelper.getVarId(node);
			if (vid >= 0) {
				String name = OpcodeUtil.toString0(node.code);
				if (name.startsWith("LOAD", 1)) vmx.addIfAbsentEx(vid).get(i);
				else vmx.addIfAbsentEx(vid).set(i);

				if (node instanceof IIndexInsnNode) {
					((IIndexInsnNode) node).setIndex(((IIndexInsnNode) node).getIndex()+1);
				} else {
					add.clear();
					add.var((byte) OpcodeUtil.getByName().getInt(name.substring(0,name.length()-2)), vid+1);
					list.setr(i, add.remove(0));
				}
			} else if (node.code == IINC) {
				IncrInsnNode ii = ((IncrInsnNode) node);
				vmx.addIfAbsentEx(ii.variableId++).getset(i);
			} else if (node.nodeType() == InsnNode.T_GOTO_IF) {
				vmx.jump(i, list.indexOf(InsnNode.validate(((JumpInsnNode) node).target)));
			}
		}

		Collection<VarMapper.VarX> values = vmx.vars__.values();
		vmx.mapEx(values);
		for (VarMapper.VarX value : values) {
			int myId = (int) value.att+1;

			for (VarMapper.Var var : value.subVars) {
				Var2 myExactType = Var2.any();
				var.att = myExactType;
				var.slot = myId;

				for (int i = var.start; i <= var.end; i++) {
					InsnNode node = list.get(i);

					int vid = InsnHelper.getVarId(node);
					if (vid == myId) {
						String name = OpcodeUtil.toString0(node.code);

						switch (name.charAt(0)) {
							case 'I': myExactType.merge(Var2.INT); break;
							case 'L': myExactType.merge(Var2.LONG); break;
							case 'F': myExactType.merge(Var2.FLOAT); break;
							case 'D': myExactType.merge(Var2.DOUBLE); break;
							case 'A': myExactType.merge(new Var2(VarType.REFERENCE, "gfdgfd")); break;
						}
					} else if (node.code == IINC) {
						IncrInsnNode ii = ((IncrInsnNode) node);
						if (ii.variableId == myId) myExactType.merge(Var2.INT);
					}
				}
				System.out.println("====");
				System.out.println(var);
				System.out.println(myExactType);
			}
		}

		for (int i = 3; i < list.size(); i++) {
			InsnNode node = list.get(i);

			String name = OpcodeUtil.toString0(node.code);

			// var slot 0 is generator
			if (node.code == INVOKESTATIC) {
				InvokeInsnNode iin = ((InvokeInsnNode) node);
				if (!iin.name.startsWith("$$$YIELD")) continue;

				List<Type> types = TypeHelper.parseMethod(iin.desc);
				if (types.size() != 2) throw new IllegalArgumentException("fake func signature error");
				if (types.remove(1).type != Type.VOID) throw new IllegalArgumentException("fake func signature error");

				Type type = types.remove(0), fieldType;
				switch (type.getActualType()) {
					case Type.VOID: throw new IllegalArgumentException();
					case Type.LONG: case Type.DOUBLE: fieldType = Type.std(Type.LONG); break;
					case Type.CLASS: fieldType = new Type("java/lang/Object"); break;
					case Type.FLOAT: default: fieldType = Type.std(Type.INT); break;
				}

				if (_fieldType == null) {
					_fieldType = fieldType;
					_fieldId = reg.newField(0, "$val", _fieldType);
					CodeWriter c = reg.newMethod(AccessFlag.PROTECTED | AccessFlag.FINAL, _fieldType.nativeName(), "()".concat(_fieldType.toDesc()));
					c.visitSize(1, 1);
					c.one(ALOAD_0);
					c.field(GETFIELD, reg, _fieldId);
					c.one(_fieldType.shiftedOpcode(IRETURN, false));
					c.finish();
				} else if (!_fieldType.equals(fieldType)) {
					throw new IllegalArgumentException("yield type must be same");
				}

				add.clear();
				list.set(i, NPInsnNode.of(ALOAD_0));
				add.one(DUP_X1);

				add.ldc(sin.targets.size()+1);
				add.field(PUTFIELD, "roj/sa/yield/Generator", "yield_pos", "I");

				if (type.getActualType() == Type.FLOAT) {
					add.invokeS("java/lang/Float", "floatToRawIntBits", "(F)I");
				} else if (type.getActualType() == Type.DOUBLE) {
					add.invokeS("java/lang/Double", "doubleToRawLongBits", "(D)J");
				}

				// dup_x1
				add.field(PUTFIELD, reg, _fieldId);

				List<VarMapper.Var> used = vmx.getVars().collect(i);
				System.out.println(used);
				for (VarMapper.Var var : used) {
					Var2 ttt = (Var2) var.att;
					add.one(ALOAD_0);
					Type type1 = ttt.type();
					add.var(type1.shiftedOpcode(ILOAD, false), var.slot);
					add.field(PUTFIELD, reg, findField(reg, var, type1));
				}

				add.one(RETURN);

				int len1 = add.size();
				for (VarMapper.Var var : used) {
					// no need to reload parameter
					if (var instanceof VarMapper.VarX && reg.getField("p"+(var.slot-1)) >= 0) continue;

					Var2 ttt = (Var2) var.att;
					add.one(ALOAD_0);
					Type type1 = ttt.type();
					add.field(GETFIELD, reg, findField(reg, var, type1));
					add.var(type1.shiftedOpcode(ISTORE, false), var.slot);
				}

				InsnNode label = len1 == add.size() ? list.get(i+1) : add.get(len1);
				sin.branch(sin.targets.size()+1, label);

				list.addAll(i+1, add);
				i += add.size();
			} else if (node.getOpcode() == ARETURN) {
				assert list.get(i-1).getOpcode() == ACONST_NULL;
				list.setr(i-1, NPInsnNode.of(ALOAD_0));
				list.setr(i, NPInsnNode.of(ICONST_3));
				list.add(i+1, new FieldInsnNode(PUTFIELD, "roj/sa/yield/Generator", "stage", "B"));
				list.add(i+2, NPInsnNode.of(RETURN));
			}
		}
		AttrLineNumber.debugBci(code);

		return reg;
	}

	private static int findField(ConstantData reg, VarMapper.Var var, Type type1) {
		// FIRST
		if (var instanceof VarMapper.VarX) {
			int field = reg.getField("p"+(var.slot-1));
			if (field >= 0) return field;
		}

		int field = reg.getField("v"+var.hashCode());
		if (field >= 0) return field;

		return reg.newField(0, "v"+var.hashCode(), type1.toDesc());
	}
}
