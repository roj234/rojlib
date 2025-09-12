package roj.compiler.asm;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.annotation.AList;
import roj.asm.annotation.AnnVal;
import roj.asm.annotation.Annotation;
import roj.asm.type.IType;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.compiler.CompileContext;
import roj.compiler.CompileUnit;
import roj.compiler.JavaTokenizer;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.NewArray;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.TypeCast;
import roj.text.ParseException;
import roj.text.Token;
import roj.config.node.ConfigValue;
import roj.util.Helpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

public final class AnnotationPrimer extends Annotation {
	public int pos;
	public boolean valueOnly;

	public AnnotationPrimer(String type, int pos) {
		this.setType(type);
		this.pos = pos - 1;
	}

	public void newEntry(CompileUnit file, String key) throws ParseException {
		Object expr;

		var wr = file.lc().lexer;
		Token w = wr.next();
		wr.retractWord();

		checkExpr:{
		if (w.type() == JavaTokenizer.lBrace) {
			wr.mark();
			System.out.println(wr.next());
			if (wr.next().type() == JavaTokenizer.at) {
				wr.skip();

				var list = new ArrayList<AnnotationPrimer>();
				while (true) {
					file.readAnnotations(list);
					if (!wr.nextIf(JavaTokenizer.comma)) break;
					wr.except(JavaTokenizer.at);
				}
				wr.except(JavaTokenizer.rBrace);

				expr = new AList(Helpers.cast(list));
				break checkExpr;
			} else {
				wr.retract();
			}
		}

		if (wr.nextIf(JavaTokenizer.at)) {
			var list = file.lc().tmpAnnotations;
			int size = list.size();

			file.readAnnotations(list);
			if (list.size() != size+1) file.lc().report(Kind.ERROR, "cu.annotation.multiAnnotation");
			expr = list.pop();
		} else {
			int state = wr.setState(JavaTokenizer.STATE_EXPR);
			// _ENV_TYPED_ARRAY允许直接使用数组生成式而不用给定类型
			expr = file.lc().ep.parse(ExprParser.STOP_COMMA|ExprParser.STOP_RSB|ExprParser._ENV_TYPED_ARRAY);
			wr.state = state;

			if (expr == null) return;
		}
		}

		((HashMap<String,?>) properties).put(key, Helpers.cast(expr));
	}

	public void setValues(Map<String, ConfigValue> values) {this.properties = values;}

	@Nullable
	public static ConfigValue toAnnVal(CompileContext ctx, Expr node, IType type) {
		if (node instanceof NewArray def) def.setType(type);

		// begin 20250120 可以用 @ABC (DEF)这种方式直接引用枚举常量DEF
		var prevDfi = ctx.dynamicFieldImport;
		var typeRef = type.isPrimitive() ? null : ctx.resolve(type);
		if (typeRef != null && (typeRef.modifier & Opcodes.ACC_ENUM) != 0) {
			ctx.dynamicFieldImport = ctx.getFieldDFI(typeRef, null, prevDfi);
		}
		// end
		node = node.resolve(ctx);
		ctx.dynamicFieldImport = prevDfi;

		if (!node.isConstant() && !node.hasFeature(Expr.Feature.ENUM_REFERENCE)) {
			ctx.report(Kind.ERROR, "cu.annotation.noConstant");
			return null;
		}

		IType ftype = node.type();
		boolean tryAutoArray = type.array() - ftype.array() == 1;
		if (tryAutoArray) type = TypeHelper.componentType(type);

		var r = ctx.castTo(ftype, type, 0);
		if (r.type < 0) return null;

		var val = toAnnVal(node.constVal(), type);
		return tryAutoArray ? new AList(Collections.singletonList(val)) : val;
	}
	private static ConfigValue toAnnVal(Object o, IType type) {
		if (o instanceof Object[] arr) {
			type = type.clone();
			type.setArrayDim(type.array()-1);
			for (int i = 0; i < arr.length; i++) {
				arr[i] = toAnnVal(arr[i], type);
			}
			return new AList(Helpers.cast(Arrays.asList(arr)));
		}
		if (o instanceof AnnVal x) return x; // AnnValEnum | AnnValClass
		if (o instanceof ConfigValue a) return castPrimitive(a, type);
		if (o instanceof String) return ConfigValue.valueOf(o.toString());
		if (o instanceof IType type1) return AnnVal.valueOf(type1.rawType());
		if (o instanceof Boolean b) return ConfigValue.valueOf(b);
		throw new UnsupportedOperationException("未预料的常量类型:"+o);
	}

	public static ConfigValue castPrimitive(ConfigValue entry, IType type) {
		int sourceType = TypeCast.getDataCap(entry.dataType());
		int targetType = TypeCast.getDataCap(type.getActualType());
		if (sourceType == targetType) return entry;
		return switch (targetType) {
			default -> entry;
			case 1 -> ConfigValue.valueOf((byte) entry.asInt());
			case 2 -> ConfigValue.valueOf((char) entry.asInt());
			case 3 -> ConfigValue.valueOf((short) entry.asInt());
			case 4 -> ConfigValue.valueOf(entry.asInt());
			case 5 -> ConfigValue.valueOf(entry.asLong());
			case 6 -> ConfigValue.valueOf(entry.asFloat());
			case 7 -> ConfigValue.valueOf(entry.asDouble());
		};
	}
}