package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

/**
 * AST - 字符串模板处理器.
 * 例如 r`no \escape` b"aaccff" f"${name}" 语法
 * @author Roj234
 * @since 2024/6/14 8:05
 */
class TemplateStringLiteral extends Expr {
	private final String template;
	private LeftValue processor;

	public TemplateStringLiteral(LeftValue processor, String template) {
		this.processor = processor;
		this.template = template;
	}

	@Override
	public String toString() {return processor +".\""+Tokenizer.escape(template)+'"';}
	@Override
	public IType type() {return Types.STRING_TYPE;}

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		// 未来也许会支持基于变量的处理器？
		String type = ((MemberAccess) processor).nameChain.get(0);
		switch (type) {
			case "f" -> {
				var concat = new StringConcat();
				var tag = template;
				var prevIndex = ctx.lexer.prevIndex;
				int prevI = 0;
				while (true) {
					int i = tag.indexOf("${", prevI);
					if (i < 0) break;

					// TODO fix escape / offset
					if (i > 0 && tag.charAt(i - 1) == '\\') {
						concat.append(valueOf("$"));
						prevI = i + 1;
						continue;
					}

					int end = tag.indexOf('}', i);
					if (end < 0) {
						ctx.report(this, Kind.ERROR, "stringFormat.f.unclosed");
						break;
					}

					if (prevI < i) concat.append(valueOf(tag.substring(prevI, i)));
					try {
						ctx.lexer.setText(tag, i + 2);
						var parse = ctx.ep.parse(ExprParser.STOP_RLB | ExprParser.NAE);
						concat.append(parse.resolve(ctx));
					} catch (Exception e) {
						e.printStackTrace();
					}

					prevI = end + 1;
				}
				if (prevI < tag.length()) concat.append(valueOf(tag.substring(prevI)));
				ctx.lexer.setText(ctx.file.getCode(), prevIndex);
				return concat;
			}
			case "b" -> {
				return new NewPackedArray(Type.BYTE, DynByteBuf.wrap(TextUtil.hex2bytes(template)));
			}
			case "r" -> {
				return valueOf(template); // r`...`
			}
			default -> {
				Expr override = ctx.getOperatorOverride(processor, type, Word.STRING);
				if (override != null) return override;
			}
		}

		ctx.report(this, Kind.ERROR, "stringFormat.unknown", type);
		return this;
	}

	@Override public void write(MethodWriter cw, boolean noRet) {}
}