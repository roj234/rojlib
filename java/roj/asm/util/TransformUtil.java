package roj.asm.util;

import org.jetbrains.annotations.Nullable;
import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cp.Constant;
import roj.asm.cp.CstDynamic;
import roj.asm.cp.CstNameAndType;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.AttributeList;
import roj.asm.tree.attr.BootstrapMethods;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.XAttrCode;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.Collection;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.type.Type.*;

/**
 * @author Roj234
 * @since 2024/1/6 0006 21:40
 */
public class TransformUtil {
	// region apiOnly | runOnly
	/**
	 * 删除方法的代码，可用于生成api-only package
	 */
	public static boolean apiOnly(ConstantData data) {
		boolean flag = false;
		List<? extends MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode ms = methods.get(i);

			if (ms.attrByName("Code") != null) {
				apiOnly(data, ms);
				flag = true;
			}
		}
		return flag;
	}
	public static void apiOnly(ConstantData data, MethodNode ms) {
		CodeWriter cw = new CodeWriter();
		cw.init(new ByteList(16), data.cp);

		Type t = ms.returnType();

		cw.visitSize(t.length(), TypeHelper.paramSize(ms.rawDesc()) + ((ACC_STATIC & ms.modifier()) == 0 ? 1 : 0));

		if (ms.name().equals("<init>")) {
			cw.visitSizeMax(1, 0);
			cw.one(ALOAD_0);
			cw.invokeD(data.parent, "<init>", "()V");
		}

		switch (t.type) {
			case CLASS: cw.one(ACONST_NULL); break;
			case VOID: break;
			case BOOLEAN: case BYTE: case CHAR: case SHORT:
			case INT: cw.one(ICONST_0); break;
			case FLOAT: cw.one(FCONST_0); break;
			case DOUBLE: cw.one(DCONST_0); break;
			case LONG: cw.one(LCONST_0); break;
		}
		cw.one(t.shiftedOpcode(IRETURN));
		cw.finish();

		ms.putAttr(new AttrUnknown("Code", cw.bw));
	}

	private static final MyHashSet<String>
		class_allow = new MyHashSet<>("RuntimeVisibleAnnotations", "BootstrapMethods", "NestMembers", "NestHost"),
		field_allow = new MyHashSet<>("ConstantValue", "RuntimeVisibleAnnotations"),
		method_allow = new MyHashSet<>("Code", "RuntimeVisibleAnnotations", "AnnotationDefault");

	/**
	 * 删除多余的属性，目前仅支持Java8
	 */
	public static void runOnly(ConstantData data) {
		filter(data, class_allow);

		int minVersion = 6;
		if (data.attrByName("BootstrapMethods") != null) minVersion = 8;
		if (data.attrByName("NestMembers") != null || data.attrByName("NestHost") != null) minVersion = 11;

		int minVer = ConstantData.JavaVersion(minVersion);
		if (data.version > minVer) {
			data.version = minVer;
		}

		SimpleList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			filter(fields.get(i), field_allow);
		}

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			filter(mn, method_allow);

			XAttrCode code = mn.parsedAttr(data.cp, Attribute.Code);
			if (code == null) continue;

			if (minVer == 6) code.frames = null;

			AttributeList list = code.attributesNullable();
			if (list != null) list.clear();
		}
	}
	private static void filter(Attributed a, MyHashSet<String> permitted) {
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
	public static void makeAccessible(IClass data, Collection<String> toOpen) {
		if (toOpen.contains("<$extend>")) {
			data.modifier(toPublic(data.modifier(), true));
		}

		if (data instanceof ConstantData cdata) {
			var classes = cdata.getInnerClasses();
			for (int i = 0; i < classes.size(); i++) {
				var clz = classes.get(i);
				if (toOpen.contains(clz.self)) {
					clz.flags = (char) toPublic(clz.flags, true);
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
	private static void toPublic(Collection<String> toOpen, boolean starP, List<? extends RawNode> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);
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
	public static ConstantData noStackFrameTableEver(ConstantData data, @Nullable String lambdaClassName) {
		BootstrapMethods lambda = data.parsedAttr(data.cp, Attribute.BootstrapMethods);
		if (lambda == null) return null;

		ByteList tmp = new ByteList();
		boolean[] frame = {false};
		List<CstDynamic> dynList = new SimpleList<>();

		CodeVisitor cv = new CodeVisitor() {
			protected void invokeDyn(CstDynamic dyn, int type) {dynList.add(dyn);}
			protected void jump(byte code, int offset) {frame[0] = true;}
			protected void lookupSwitch(DynByteBuf r) {frame[0] = true;}
			protected void tableSwitch(DynByteBuf r) {frame[0] = true;}
		};

		for (MethodNode mn : data.methods)
			visitCode(data, mn, tmp, cv);

		if (!frame[0]) return null;

		ConstantData newClass = new ConstantData();
		newClass.name(lambdaClassName != null ? lambdaClassName : data.name+"$Lambda");
		newClass.putAttr(lambda);
		data.attributes().removeByName(Attribute.BootstrapMethods.name);

		data.version = 49 << 16;
		newClass.version = 52 << 16;

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

			String desc = nat.getType().str();
			int size = 0;
			List<Type> types = TypeHelper.parseMethod(desc);
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
			Attribute code = mn.attrByName("Code");
			if (code != null) {
				tmp.clear();
				cw.init(tmp, data.cp);
				cw.mn = mn;
				cw.visit(data.cp, code.getRawData());
				cw.finish();
				mn.putAttr(new AttrUnknown("Code", new ByteList(tmp.toByteArray())));
			}
		}

		return newClass;
	}

	public static void compress(ConstantData data) {
		var lazyLDC = new MyHashSet<Constant>();
		var cpw = AsmShared.local().constPool();
		CodeVisitor smallerLdc = new CodeVisitor() {
			protected void ldc(byte code, Constant c) {if (code != LDC2_W) lazyLDC.add(c);}
		};
		ByteList bw = new ByteList();

		var methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			visitCode(data, methods.get(i), bw, smallerLdc);
		}
		for (var constant : lazyLDC) cpw.reset(constant);

		CodeWriter cw = new CodeWriter();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.attrByName("Code");
			if (code != null) {
				bw.clear();
				cw.init(bw, cpw);
				cw.mn = mn; // for UNINITIAL_THIS
				cw.visit(data.cp, code.getRawData());
				cw.finish();

				byte[] array = bw.toByteArray();
				// will not parse this
				mn.putAttr(new Attribute() {
					@Override
					public String name() { return "Code"; }
					@Override
					public DynByteBuf getRawData() { return ByteList.wrap(array); }
					@Override
					public String toString() { return "Tmpcw"; }
				});
			}
		}

		data.parsed();
		AsmShared.local().constPool(data.cp);
		data.cp = cpw;

		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.attrByName("Code");
			if (code != null) mn.putAttr(new AttrUnknown("Code", code.getRawData()));
		}
	}

	public static void visitCode(ConstantData data, MethodNode mn, ByteList tmp, CodeVisitor cv) {
		Attribute code = mn.attrByName("Code");
		if (code != null) {
			if (code instanceof XAttrCode x) {
				tmp.clear();
				code.toByteArrayNoHeader(tmp, data.cp);
				code = new AttrUnknown("Code", new ByteList(tmp.toByteArray()));
				mn.putAttr(code);
			}
			cv.visit(data.cp, Parser.reader(code));
		}
	}

	public static boolean exceptionCheck(byte code) {
		return switch (code) {
			// ClassCastException
			case CHECKCAST,
			// NullPointerException
			ATHROW, ARRAYLENGTH, MONITORENTER, MONITOREXIT,
			// NullPointerException, ArrayStoreException
			IALOAD, LALOAD, FALOAD, DALOAD, AALOAD, BALOAD, CALOAD, SALOAD,
			IASTORE, LASTORE, FASTORE, DASTORE, AASTORE, BASTORE, CASTORE, SASTORE,
			// LinkageError for Load_Dynamic
			LDC, LDC_W, LDC2_W,
			// NullPointerException | LinkageError
			GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC,
			// NullPointerException | ExceptionInExecution | LinkageError
			INVOKEVIRTUAL, INVOKEINTERFACE, INVOKESPECIAL,
			// ExceptionInExecution | LinkageError
			INVOKESTATIC, INVOKEDYNAMIC,
			// OutOfMemoryError | LinkageError
			NEW, INSTANCEOF,
			// OutOfMemoryError | NegativeArraySizeException | LinkageError
			NEWARRAY, ANEWARRAY, MULTIANEWARRAY -> true;
			default -> false;
		};
	}

}