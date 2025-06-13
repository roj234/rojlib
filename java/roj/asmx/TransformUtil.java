package roj.asmx;

import org.jetbrains.annotations.Nullable;
import roj.asm.*;
import roj.asm.attr.Attribute;
import roj.asm.attr.AttributeList;
import roj.asm.attr.BootstrapMethods;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.Constant;
import roj.asm.cp.CstDynamic;
import roj.asm.cp.CstNameAndType;
import roj.asm.insn.*;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2024/1/6 21:40
 */
public class TransformUtil {
	// region apiOnly | runOnly
	/**
	 * 删除方法的代码，可用于生成api-only package
	 */
	public static boolean apiOnly(ClassNode data) {
		boolean flag = false;
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode ms = methods.get(i);

			if (ms.getRawAttribute("Code") != null) {
				apiOnly(data, ms);
				flag = true;
			}
		}
		return flag;
	}
	public static void apiOnly(ClassNode data, MethodNode ms) {
		CodeWriter cw = new CodeWriter();
		cw.init(new ByteList(16), data.cp, ms);

		Type t = ms.returnType();

		cw.visitSize(t.length(), TypeHelper.paramSize(ms.rawDesc()) + ((ACC_STATIC & ms.modifier()) == 0 ? 1 : 0));

		if (ms.name().equals("<init>")) {
			cw.visitSizeMax(1, 0);
			cw.insn(ALOAD_0);
			cw.invokeD(data.parent(), "<init>", "()V");
		}

		switch (t.type) {
			case CLASS: cw.insn(ACONST_NULL); break;
			case VOID: break;
			case BOOLEAN: case BYTE: case CHAR: case SHORT:
			case INT: cw.insn(ICONST_0); break;
			case FLOAT: cw.insn(FCONST_0); break;
			case DOUBLE: cw.insn(DCONST_0); break;
			case LONG: cw.insn(LCONST_0); break;
		}
		cw.insn(t.getOpcode(IRETURN));
		cw.finish();

		ms.addAttribute(new UnparsedAttribute("Code", cw.bw));
	}

	private static final HashSet<String>
		class_allow = new HashSet<>("RuntimeVisibleAnnotations", "BootstrapMethods", "NestMembers", "NestHost"),
		field_allow = new HashSet<>("ConstantValue", "RuntimeVisibleAnnotations"),
		method_allow = new HashSet<>("Code", "RuntimeVisibleAnnotations", "AnnotationDefault");

	/**
	 * 删除多余的属性，目前仅支持Java8
	 */
	public static void runOnly(ClassNode data) {
		filter(data, class_allow);

		int minVersion = 6;
		if (data.getRawAttribute("BootstrapMethods") != null) minVersion = 8;
		if (data.getRawAttribute("NestMembers") != null || data.getRawAttribute("NestHost") != null) minVersion = 11;

		int minVer = ClassNode.JavaVersion(minVersion);
		if (data.version > minVer) {
			data.version = minVer;
		}

		ArrayList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			filter(fields.get(i), field_allow);
		}

		ArrayList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			filter(mn, method_allow);

			Code code = mn.getAttribute(data.cp, Attribute.Code);
			if (code == null) continue;

			if (minVer == 6) code.frames = null;

			AttributeList list = code.attributesNullable();
			if (list != null) list.clear();
		}
	}
	private static void filter(Attributed a, HashSet<String> permitted) {
		AttributeList list = a.attributesNullable();
		if (list == null) return;
		for (int i = list.size() - 1; i >= 0; i--) {
			if (!permitted.contains(list.get(i).name())) list.remove(i);
		}
	}
	// endregion
	// region Access Transformer
	/**
	 * 修改data中toOpen所包含的访问权限至public
	 */
	public static void makeAccessible(ClassDefinition data, Collection<String> toOpen) {
		if (toOpen.contains("<$extend>")) {
			data.modifier(toPublic(data.modifier(), true));
		}

		if (data instanceof ClassNode cdata) {
			var classes = cdata.getInnerClasses();
			for (int i = 0; i < classes.size(); i++) {
				var clz = classes.get(i);
				if (toOpen.contains(clz.self)) {
					clz.modifier = (char) toPublic(clz.modifier, true);
				}
			}
		}

		boolean starP = true;
		if (toOpen.contains("*")) {
			toOpen = null;
			starP = false;
		} else if (toOpen.contains("*P")) {
			toOpen = null;
		}

		toPublic(toOpen, starP, data.fields());
		toPublic(toOpen, starP, data.methods());
	}
	private static void toPublic(Collection<String> toOpen, boolean starP, List<? extends Member> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			Member node = nodes.get(i);
			if (toOpen != null && !toOpen.contains(node.name()) && !toOpen.contains(node.name()+node.rawDesc())) continue;

			int flag = toPublic(node.modifier(), starP || toOpen != null);
			node.modifier(flag);
		}
	}
	private static int toPublic(int flag, boolean starP) {
		flag &= ~(ACC_PRIVATE|ACC_FINAL);
		if (starP || (flag& ACC_PROTECTED) == 0)
			return (flag & ~ACC_PROTECTED) | ACC_PUBLIC;
		return flag;
	}
	// endregion
	@Nullable
	@Deprecated(forRemoval = true)
	public static ClassNode noStackFrameTableEver(ClassNode data, @Nullable String lambdaClassName) {
		BootstrapMethods lambda = data.getAttribute(data.cp, Attribute.BootstrapMethods);
		if (lambda == null) return null;

		ByteList tmp = new ByteList();
		boolean[] frame = {false};
		List<CstDynamic> dynList = new ArrayList<>();

		CodeVisitor cv = new CodeVisitor() {
			protected void invokeDyn(CstDynamic dyn, int type) {dynList.add(dyn);}
			protected void jump(byte code, int offset) {frame[0] = true;}
			protected void lookupSwitch(DynByteBuf r) {frame[0] = true;}
			protected void tableSwitch(DynByteBuf r) {frame[0] = true;}
		};

		for (MethodNode mn : data.methods)
			mn.transform(data.cp, tmp, cv);

		if (!frame[0]) return null;

		ClassNode newClass = new ClassNode();
		newClass.name(lambdaClassName != null ? lambdaClassName : data.name() +"$Lambda");
		newClass.addAttribute(lambda);
		data.attributes().removeByName(Attribute.BootstrapMethods.name);

		data.version = 49;
		newClass.version = 52;

		List<BootstrapMethods.Item> methods = lambda.methods;
		for (int i = 0; i < methods.size(); i++) {
			CstNameAndType nat = null;
			for (int j = 0; j < dynList.size(); j++) {
				if (dynList.get(j).tableIdx == i) {
					nat = dynList.get(i).desc();
					break;
				}
			}
			assert nat != null;

			String desc = nat.rawDesc().str();
			int size = 0;
			List<Type> types = methodDesc(desc);
			Type ret = types.remove(types.size() - 1);

			CodeWriter mycw = newClass.newMethod(ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC, "lambdaBridge"+i, desc);

			for (int j = 0; j < types.size(); j++) {
				Type type = types.get(j);
				mycw.varLoad(type, size);
				size += type.length();
			}
			mycw.visitSize(Math.max(1,size), size);
			mycw.invokeDyn(i, nat.name().str(), desc, 0);
			mycw.return_(ret);
		}

		CodeWriter cw = new CodeWriter() {
			public void invokeDyn(int idx, String name, String desc, int type) { super.invoke(INVOKESTATIC, newClass, idx); }
			public void visitExceptions() { super.visitExceptions(); frames = null; }
		};

		for (MethodNode mn : data.methods) {
			Attribute code = mn.getRawAttribute("Code");
			if (code != null) {
				tmp.clear();
				cw.init(tmp, data.cp, mn);
				cw.visit(data.cp, code.getRawData());
				cw.finish();
				mn.addAttribute(new UnparsedAttribute("Code", new ByteList(tmp.toByteArray())));
			}
		}

		return newClass;
	}

	public static void compress(ClassNode data) {
		var lazyLDC = new HashSet<Constant>();
		var cpw = AsmCache.getInstance().constPool();
		CodeVisitor smallerLdc = new CodeVisitor() {
			protected void ldc(byte code, Constant c) {if (code != LDC2_W) lazyLDC.add(c);}
		};
		ByteList bw = new ByteList();

		var methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			methods.get(i).transform(data.cp, bw, smallerLdc);
		}
		for (var constant : lazyLDC) cpw.reset(constant);

		CodeWriter cw = new CodeWriter();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.getRawAttribute("Code");
			if (code != null) {
				bw.clear();
				cw.init(bw, cpw, mn);
				cw.visit(data.cp, code.getRawData());
				cw.finish();

				byte[] array = bw.toByteArray();
				// will not parse this
				mn.addAttribute(new Attribute() {
					@Override public String name() {return "Code";}
					@Override public DynByteBuf getRawData() {return ByteList.wrap(array);}
					@Override public String toString() {return "Tmpcw";}
				});
			}
		}

		data.parsed();
		AsmCache.getInstance().constPool(data.cp);
		data.cp = cpw;

		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.getRawAttribute("Code");
			if (code != null) mn.addAttribute(new UnparsedAttribute("Code", code.getRawData()));
		}
	}

	public static void prependStaticConstructor(ClassNode data, Consumer<AbstractCodeWriter> writer) {
		MethodNode clinit = data.getMethodObj("<clinit>");
		AbstractCodeWriter c;
		if (clinit == null) {
			CodeWriter c1 = data.newMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "<clinit>", "()V");
			c1.visitSize(1, 0);
			c1.computeFrames(Code.COMPUTE_FRAMES| Code.COMPUTE_SIZES|CodeWriter.ADD_RETURN);
			c = c1;
		} else {
			if (clinit.getRawAttribute("Code") instanceof AttrCodeWriter cw) {
				c = cw.cw;
				clinit = null;
			} else {
				c = new InsnList();
			}
		}

		writer.accept(c);

		if (clinit != null) {
			Code code = clinit.getAttribute(data.cp, Attribute.Code);
			if (code.stackSize == 0) code.stackSize = 1;
			code.computeFrames(Code.COMPUTE_FRAMES| Code.COMPUTE_SIZES);
			code.instructions.replaceRange(0,0, (InsnList) c, false);
		}
	}
}