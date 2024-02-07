package roj.compiler.api_rt;

import roj.asm.tree.Attributed;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.visitor.CodeWriter;
import roj.compiler.JavaLexer;
import roj.compiler.asm.Variable;
import roj.compiler.ast.block.BlockParser;

import java.lang.annotation.ElementType;

/**
 * @author Roj234
 * @since 2024/2/20 0020 22:28
 */
public interface AnnotationApi {
	// 未实现的： RECORD_COMPONENT

	/**
	 * 包括下列类别 - TYPE ANNOTATION_TYPE PACKAGE MODULE
	 * @param annotation
	 * @param stage
	 * @param processor
	 */
	void addTypeAnnotationProcessor(String annotation, Stage stage, AP<IClass> processor);
	void addFieldAnnotationProcessor(String annotation, Stage stage, AP<FieldNode> processor);
	void addMethodAnnotationProcessor(String annotation, Stage stage, AP<MethodNode> processor);
	void addParameterAnnotationProcessor(String annotation, int elementTypeBits, boolean exclusion, MAP<IType, Void> processor);
	void addTypeUseAnnotationProcessor(String annotation, int elementTypeBits, boolean exclusion, MAP<IType, Void> processor);
	void addLocalVariableAnnotationProcessor(String annotation, int elementTypeBits, boolean exclusion, MAP<Variable, Void> processor);
	void addTypeParamAnnotationProcessor(String annotation, MAP<Signature, Void> processor);

	void addMethodIntrustedWord0AnnotationProcessor(String annotation, XAP<JavaLexer, JavaLexer> processor);
	void addMethodIntrustedOverride0AnnotationProcessor(String annotation, XAP<BlockParser, BlockParser> processor);
	void addMethodIntrustedCode0AnnotationProcessor(String annotation, XAP<CodeWriter, CodeWriter> processor);

	enum Stage { PARSE, RESOLVE, WRITE }

	interface AP<ELEMENT extends Attributed> { void accept(ElementType position, Annotation annotation, IClass type, ELEMENT element); }
	interface MAP<SUBJECT, CONTEXT> { void accept(ElementType position, Annotation annotation, IClass type, MethodNode element, SUBJECT subject, CONTEXT context); }
	interface XAP<SUBJECT, RESPONSE> { RESPONSE accept(ElementType position, Annotation annotation, IClass type, MethodNode element, SUBJECT subject); }
}