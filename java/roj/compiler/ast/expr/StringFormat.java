package roj.compiler.ast.expr;

import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.text.TextUtil;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/6/14 0014 8:05
 */
class StringFormat extends ExprNode {
	private final String template;
	private VarNode prev;

	public StringFormat(VarNode prev, String template) {
		this.prev = prev;
		this.template = template;
	}

	@Override
	public String toString() {return prev+".\""+Tokenizer.addSlashes(template)+'"';}
	@Override
	public IType type() {return Types.STRING_TYPE;}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		String type = ((DotGet) prev).names.get(0);
		if (type.equals("f")) {
			var concat = new StringConcat();

			var tag = template;
			var prevIndex = ctx.lexer.prevIndex;

			int prevI = 0;
			while (true) {
				int i = tag.indexOf("${", prevI);
				if (i < 0) break;

				// TODO fix escape / offset
				if (i > 0 && tag.charAt(i-1) == '\\') {
					concat.append(valueOf("$"));
					prevI = i+1;
					continue;
				}

				int end = tag.indexOf('}', i);
				if (end < 0) {
					ctx.report(this, Kind.ERROR, "stringFormat.f.unclosed");
					break;
				}

				if (prevI < i) concat.append(valueOf(tag.substring(prevI, i)));
				try {
					ctx.lexer.setText(tag, i+2);
					var parse = ctx.ep.parse(ExprParser.STOP_RLB|ExprParser.NAE);
					concat.append(parse.resolve(ctx));
				} catch (Exception e) {
					e.printStackTrace();
				}

				prevI = end+1;
			}
			if (prevI < tag.length()) concat.append(valueOf(tag.substring(prevI)));

			ctx.lexer.setText(ctx.file.getCode(), prevIndex);
			return concat;
		} else if (type.equals("b")) {
			return new PrimitiveArray(Type.BYTE, DynByteBuf.wrap(TextUtil.hex2bytes(template)));
		} else {
			// cast to StringProcessor ...
			ExprNode override = ctx.getOperatorOverride(prev, type, Word.STRING);
			if (override != null) return override;
		}

		ctx.report(this, Kind.ERROR, "stringFormat.unknown", type);
		return this;
	}

	@Override public void write(MethodWriter cw, boolean noRet) {}
}