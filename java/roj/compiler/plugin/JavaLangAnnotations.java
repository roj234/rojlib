package roj.compiler.plugin;

import roj.asm.Attributed;
import roj.asm.ClassDefinition;
import roj.asm.MethodNode;
import roj.asm.annotation.Annotation;
import roj.asm.attr.UnparsedAttribute;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.util.ByteList;

import java.util.Collections;
import java.util.Set;

/**
 * @author Roj234
 * @since 2025/4/3 23:03
 */
public class JavaLangAnnotations implements Processor {
	@Override
	public Set<String> acceptedAnnotations() {
		return Set.of("java/lang/Override", "java/lang/FunctionalInterface", "java/lang/SafeVarargs", "java/lang/SuppressWarnings", "java/lang/Deprecated");
	}

	@Override
	public void handle(LocalContext ctx, ClassDefinition file, Attributed node, Annotation a) {
		var annotation = (AnnotationPrimer) a;
		switch (annotation.type()) {
			case "java/lang/Override" -> {
				if (annotation.raw() != Collections.EMPTY_MAP) {
					ctx.classes.report(file, Kind.ERROR, annotation.pos, "annotation.override");
				}
			}
			case "java/lang/FunctionalInterface" -> {
				MethodNode lambdaMethod = ctx.classes.getResolveHelper(file).getLambdaMethod();
				if (lambdaMethod == null) {
					ctx.classes.report(file, Kind.ERROR, annotation.pos, "annotation.functionalInterface", file);
				}
			}
			case "java/lang/SafeVarargs" -> {
				ctx.report(Kind.WARNING, "not implemented yet");
			}
			case "java/lang/SuppressWarnings" -> {
				ctx.report(Kind.WARNING, "不支持该注解, SuppressWarnings, 手动处理DiagnosticReporter");
			}
			case "java/lang/Deprecated" -> node.addAttribute(new UnparsedAttribute("Deprecated", ByteList.EMPTY));
		}
	}
}
