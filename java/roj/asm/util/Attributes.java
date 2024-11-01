package roj.asm.util;

import roj.asm.cp.ConstantPool;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.ParameterAnnotations;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/4/23 14:18
 */
@Deprecated
public abstract class Attributes extends Attribute {
	private Attributes() {}

	public static List<List<Annotation>> getParameterAnnotation(ConstantPool cp, MethodNode m, boolean vis) {
		ParameterAnnotations pa = m.parsedAttr(cp, vis ? RtParameterAnnotations : ClParameterAnnotations);
		return pa == null ? null : pa.annotations;
	}
}