package roj.compiler.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.tree.anno.*;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.ast.expr.ArrayDef;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.config.ParseException;
import roj.config.Word;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.Collections;

public final class AnnotationPrimer extends Annotation {
	public int pos;
	public boolean valueOnly;

	public AnnotationPrimer(String type, int pos) {
		this.setType(type);
		this.values = new MyHashMap<>();
		this.pos = pos - 1;
	}

	public void newEntry(CompileUnit file, String key) throws ParseException {
		Object expr;

		var wr = file.lc().lexer;
		Word w = wr.next();
		wr.retractWord();

		checkExpr:{
		if (w.type() == JavaLexer.lBrace) {
			wr.mark();
			System.out.println(wr.next());
			if (wr.next().type() == JavaLexer.at) {
				wr.skip();

				var list = new SimpleList<>();
				while (true) {
					file._annotations(Helpers.cast(list));
					if (!wr.nextIf(JavaLexer.comma)) break;
					wr.except(JavaLexer.at);
				}
				wr.except(JavaLexer.rBrace);

				for (int i = 0; i < list.size(); i++) {
					list.set(i, new AnnValAnnotation((Annotation) list.get(i)));
				}

				expr = new AnnValArray(Helpers.cast(list));
				break checkExpr;
			} else {
				wr.retract();
			}
		}

		if (wr.nextIf(JavaLexer.at)) {
			var list = file.lc().tmpAnnotations;
			int size = list.size();

			file._annotations(list);
			if (list.size() != size+1) file.lc().report(Kind.ERROR, "cu.annotation.multiAnnotation");
			expr = new AnnValAnnotation(list.pop());
		} else {
			int state = wr.setState(JavaLexer.STATE_EXPR);
			// _ENV_TYPED_ARRAY允许直接使用数组生成式而不用给定类型
			expr = file.lc().ep.parse(ExprParser.STOP_COMMA|ExprParser.STOP_RSB|ExprParser._ENV_TYPED_ARRAY);
			wr.state = state;

			if (expr == null) return;
		}
		}

		((MyHashMap<String,?>)values).put(key, Helpers.cast(expr));
	}

	@Nullable
	public static AnnVal toAnnVal(LocalContext ctx, ExprNode node, Type type) {
		if (node instanceof ArrayDef def) def.setType(type);
		node = node.resolve(ctx);

		if (!node.isConstant() && !node.isKind(ExprNode.ExprKind.ENUM_REFERENCE)) {
			ctx.report(Kind.ERROR, "ap.annotation.noConstant");
			return null;
		}

		IType ftype = node.type();
		boolean tryAutoArray = ftype.array() == 0 && type.array() == 1;
		if (tryAutoArray) {
			type = type.clone();
			type.setArrayDim(0);
		}

		var r = ctx.castTo(ftype, type, 0);
		if (r.type < 0) return null;

		AnnVal val = toAnnVal(node.constVal(), type);
		return tryAutoArray ? new AnnValArray(Collections.singletonList(val)) : val;
	}
	private static AnnVal toAnnVal(Object o, Type type) {
		if (o instanceof Object[] arr) {
			type = type.clone();
			type.setArrayDim(type.array()-1);
			for (int i = 0; i < arr.length; i++) {
				arr[i] = toAnnVal(arr[i], type);
			}
			return new AnnValArray(Helpers.cast(Arrays.asList(arr)));
		}
		if (o instanceof AnnVal a) return switch (TypeCast.getDataCap(type.getActualType())) {
			default -> a;
			case 1 -> AnnVal.valueOf((byte)a.asInt());
			case 2 -> AnnVal.valueOf((char)a.asInt());
			case 3 -> AnnVal.valueOf((short)a.asInt());
			case 4 -> AnnVal.valueOf(a.asInt());
			case 5 -> AnnVal.valueOf(a.asLong());
			case 6 -> AnnVal.valueOf(a.asFloat());
			case 7 -> AnnVal.valueOf(a.asDouble());
		};
		if (o instanceof String) return AnnVal.valueOf(o.toString());
		if (o instanceof IType type1) return new AnnValClass(type1.rawType().toDesc());
		throw new UnsupportedOperationException("未预料的常量类型:"+o);
	}
}