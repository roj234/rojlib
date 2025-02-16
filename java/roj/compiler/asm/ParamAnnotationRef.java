package roj.compiler.asm;

import roj.asm.Attributed;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.ParameterAnnotations;

import java.util.Collections;
import java.util.List;

public final class ParamAnnotationRef implements Attributed {
	public final MethodNode method;
	public final int parameterId;
	public int pos;

	public ParamAnnotationRef(MethodNode method, int pos, int parameterId) {
		this.method = method;
		this.parameterId = parameterId;
		this.pos = pos;
	}

	@Override
	public void putAttr(Attribute attr) {
		boolean vis = ((Annotations) attr).vis;

		ParameterAnnotations p = (ParameterAnnotations) method.attrByName((vis ? Attribute.RtParameterAnnotations : Attribute.ClParameterAnnotations).name);
		if (p == null) method.putAttr(p = new ParameterAnnotations(vis));

		List<List<Annotation>> list = p.annotations;
		while (list.size() <= parameterId) list.add(Collections.emptyList());
		list.set(parameterId, ((Annotations) attr).annotations);
	}

	@Override
	public char modifier() {return 0;}
}