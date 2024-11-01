package roj.compiler.plugin;

import roj.asm.tree.Attributed;
import roj.asm.tree.IClass;
import roj.asm.tree.anno.Annotation;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;

import java.util.List;
import java.util.Set;

/**
 * 注解处理器
 *
 * @author Roj234
 * @since 2024/6/10 0010 3:29
 */
public interface Processor {
	Set<String> acceptedAnnotations();

	default boolean acceptClasspath() {return false;}

	void handle(LocalContext ctx, IClass file, Attributed node, Annotation annotation);

	default void handle(LocalContext ctx, CompileUnit file, Attributed node, List<? extends Annotation> annotations) {
		Set<String> set = acceptedAnnotations();
		for (Annotation annotation : annotations) {
			if (set.contains(annotation.type())) handle(ctx, file, node, annotation);
		}
	}
}