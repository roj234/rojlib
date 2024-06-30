package roj.asm.util;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.Attributed;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.tree.attr.ParameterAnnotations;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/23 14:18
 */
public abstract class Attributes extends Attribute {
	private Attributes() {}

	public static List<Annotation> getAnnotations(ConstantPool cp, Attributed node, boolean vis) {
		Annotations a = node.parsedAttr(cp, vis?RtAnnotations:ClAnnotations);
		return a == null ? null : a.annotations;
	}

	public static Annotation getAnnotation(List<Annotation> list, String name) {
		if (list == null) return null;
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).type().equals(name)) return list.get(i);
		}
		return null;
	}

	public static List<InnerClasses.Item> getInnerClasses(ConstantPool cp, IClass node) {
		InnerClasses ic = node.parsedAttr(cp, InnerClasses);
		return ic == null ? null : ic.classes;
	}

	public static List<List<Annotation>> getParameterAnnotation(ConstantPool cp, MethodNode m, boolean vis) {
		ParameterAnnotations pa = m.parsedAttr(cp, vis ? RtParameterAnnotations : ClParameterAnnotations);
		return pa == null ? null : pa.annotations;
	}
}