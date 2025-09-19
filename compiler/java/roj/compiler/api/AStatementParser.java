package roj.compiler.api;

import roj.asm.attr.Attribute;

/**
 * 以注解开始一个语句，例如 @Macro {
 *     code.append("return;");
 * }
 * 详细见插件API文档
 * @author Roj234
 * @since 2025/9/23 14:31
 */
public class AStatementParser extends Attribute {
	public static final String NAME = "LavaAnnotationStmt";

	public final Compiler.AnnotationStatement parser;

	public AStatementParser(Compiler.AnnotationStatement parser) {this.parser = parser;}

	@Override public final boolean writeIgnore() {return true;}
	@Override public final String name() {return NAME;}
	@Override public String toString() {return NAME+": "+parser.getClass().getSimpleName();}
}