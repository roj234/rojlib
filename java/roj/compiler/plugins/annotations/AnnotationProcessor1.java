package roj.compiler.plugins.annotations;

import roj.asm.cp.CstInt;
import roj.asm.tree.Attributed;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.ConstantValue;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.api.Processor;
import roj.config.data.CInt;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * @author Roj234
 * @since 2024/6/10 0010 4:27
 */
public class AnnotationProcessor1 implements Processor {
	private static final Set<String> ACCEPTS = Collections.singleton("roj/compiler/plugins/annotations/AutoIncrement");
	@Override
	public Set<String> acceptedAnnotations() {return ACCEPTS;}

	private final WeakHashMap<Annotation, CInt> increment_count = new WeakHashMap<>();

	@Override
	public void handle(LocalContext ctx, IClass file, Attributed node, Annotation annotation) {
		CInt start = increment_count.computeIfAbsent(annotation, x -> new CInt(annotation.getInt("start")));

		((CompileUnit) file).cancelTask(node);
		((FieldNode) node).putAttr(new ConstantValue(new CstInt(start.value)));

		start.value += annotation.getInt("step");
	}
}