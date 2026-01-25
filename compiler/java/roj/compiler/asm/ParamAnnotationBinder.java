package roj.compiler.asm;

import roj.asm.Attributed;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Annotations;
import roj.asm.attr.Attribute;
import roj.asm.attr.ParameterAnnotations;

import java.util.Collections;
import java.util.List;

public final class ParamAnnotationBinder implements Attributed {
	public final MethodNode method;
	public final int parameterId;
	public int pos;

	public ParamAnnotationBinder(MethodNode method, int pos, int parameterId) {
		this.method = method;
		this.parameterId = parameterId;
		this.pos = pos;
	}

	@Override
	public void addAttribute(Attribute attr) {
		boolean vis = ((Annotations) attr).vis;

		ParameterAnnotations p = (ParameterAnnotations) method.getAttribute((vis ? Attribute.VisibleParameterAnnotations : Attribute.InvisibleParameterAnnotations).name);
		if (p == null) method.addAttribute(p = new ParameterAnnotations(vis));

		List<List<Annotation>> list = p.annotations;
		while (list.size() <= parameterId) list.add(Collections.emptyList());
		list.set(parameterId, ((Annotations) attr).annotations);
	}

	@Override
	public char modifier() {return 0;}
}