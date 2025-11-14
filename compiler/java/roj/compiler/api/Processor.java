package roj.compiler.api;

import roj.asm.Attributed;
import roj.asm.ClassDefinition;
import roj.asm.annotation.Annotation;
import roj.compiler.CompileContext;

import java.util.Set;

/**
 * 注解处理器
 *
 * @author Roj234
 * @since 2024/6/10 3:29
 */
public interface Processor {
	Set<String> acceptedAnnotations();

	default boolean readClasspath() {return false;}

	void handle(CompileContext ctx, ClassDefinition file, Attributed node, Annotation annotation);
}