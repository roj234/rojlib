package roj.compiler.asm;

import roj.asm.tree.Attributed;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.ParameterAnnotations;

import java.util.Collections;
import java.util.List;

public final class ParamAnnotationRef implements Attributed {
	private final MethodNode m;
	private final int parId;
	public int pos;

	public ParamAnnotationRef(MethodNode method, int pos, int parId) {
		this.m = method;
		this.parId = parId;
		this.pos = pos;
	}

	@Override
	public void putAttr(Attribute attr) {
		boolean vis = ((Annotations) attr).vis;

		ParameterAnnotations p = (ParameterAnnotations) m.attrByName((vis ? Attribute.RtParameterAnnotations : Attribute.ClParameterAnnotations).name);
		if (p == null) m.putAttr(p = new ParameterAnnotations(vis));

		List<List<Annotation>> list = p.annotations;
		while (list.size() <= parId) list.add(Collections.emptyList());
		list.set(parId, ((Annotations) attr).annotations);
	}

	@Override
	public char modifier() {return 0;}
}