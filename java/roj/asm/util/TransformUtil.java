package roj.asm.util;

import roj.asm.tree.*;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.AttributeList;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.XAttrCode;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.util.ByteList;

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
		class_allow = new MyHashSet<>("InnerClasses", "RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations", "BootstrapMethods"),
		field_allow = new MyHashSet<>("ConstantValue", "RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations"),
		method_allow = new MyHashSet<>("Code", "RuntimeInvisibleAnnotations", "RuntimeVisibleAnnotations", "AnnotationDefault");

	/**
	 * 删除多余的属性，目前仅支持Java8
	 */
	public static void runOnly(ConstantData data) {
		filter(data, class_allow);

		boolean low = false;
		if (data.version >= 52 << 16 && data.attrByName("BootstrapMethods") == null) {
			data.version = 51 << 16;
			low = true;
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

			if (low) code.frames = null;

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
	/**
	 * 修改InnerClasses属性中定义的内部类的访问权限
	 */
	public static void makeSubclassAccessible(IClass data, Collection<String> toOpen) {
		List<InnerClasses.InnerClass> classes = Attributes.getInnerClasses(data.cp(), data);
		if (classes == null) throw new IllegalStateException("no InnerClass in " + data.name());

		for (int i = 0; i < classes.size(); i++) {
			InnerClasses.InnerClass clz = classes.get(i);
			if (toOpen.contains(clz.self)) {
				clz.flags = (char) toPublic(clz.flags, true);
			}
		}
	}
	private static void toPublic(Collection<String> toOpen, boolean starP, List<? extends RawNode> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode node = nodes.get(i);
			if (toOpen != null && !toOpen.contains(node.name()) && !toOpen.contains(node.name()+'|'+node.rawDesc())) continue;

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
}