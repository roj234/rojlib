package roj.compiler.api_rt;

import roj.asm.tree.Attributed;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.type.IType;
import roj.compiler.asm.Variable;

import java.lang.annotation.ElementType;

/**
 * @author Roj234
 * @since 2024/2/20 0020 22:28
 */
public interface AnnotationApi {
	// 未实现的： RECORD_COMPONENT
	// Stage should be 2 Resolve

	/**
	 * 包括下列类别 - TYPE ANNOTATION_TYPE PACKAGE MODULE
	 * @param annotation
	 * @param processor
	 */
	void addTypeAnnotationProcessor(String annotation, AP<IClass> processor);
	void addFieldAnnotationProcessor(String annotation, AP<FieldNode> processor);
	void addMethodAnnotationProcessor(String annotation, AP<MethodNode> processor);
	void addParameterAnnotationProcessor(String annotation, MAP<IType, MethodNode> processor);
	void addTypeUseAnnotationProcessor(String annotation, MAP<IType, MethodNode> processor);
	void addLocalVariableAnnotationProcessor(String annotation,MAP<Variable, MethodNode> processor);

	interface AP<ELEMENT extends Attributed> { void accept(ElementType position, Annotation annotation, IClass type, ELEMENT element); }
	interface MAP<SUBJECT, CONTEXT> { void accept(ElementType position, Annotation annotation, IClass type, MethodNode element, SUBJECT subject, CONTEXT context); }
}