package roj.compiler.api_rt;

/**
 * @author Roj234
 * @since 2024/2/20 0020 16:57
 */
public interface LavaApi {
	IClassContext getClassContext();
	AnnotationApi getAnnotationApi();
	ExprApi getExprApi();
	LexApi getLexApi();
	ResolveApi getResolveApi();
}