package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.asm.LPSignature;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/9 0009 1:42
 */
public final class MultiReturn extends ExprNode {
	private final List<ExprNode> values;
	private int hashCode;
	public MultiReturn(List<ExprNode> values) {
		this.values = values;
		if (values.size() == 0 || values.size() > 255) throw new UnsupportedOperationException("Illegal return size "+values.size());
	}

	@Override
	public String toString() {return values.toString();}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		for (int i = 0; i < values.size(); i++) {
			ExprNode node = values.get(i).resolve(ctx);
			values.set(i, node);
			if ("roj/compiler/runtime/ReturnStack".equals(node.type().owner())) {
				ctx.report(Kind.ERROR, "multiReturn.russianToy");
			}
		}

		LPSignature node = ctx.file.currentNode;
		ok: {
			if (node != null) {
				IType type1 = node.values.get(node.values.size() - 1);
				if (type1.owner().equals("roj/compiler/runtime/ReturnStack") && type1 instanceof Generic g) {
					for (IType child : g.children) {
						if ("roj/compiler/runtime/ReturnStack".equals(child.owner())) {
							ctx.report(Kind.ERROR, "multiReturn.russianToy");
						}
					}
					hashCode = g.children.hashCode();
					break ok;
				}
			}

			ctx.report(Kind.ERROR, "multiReturn.incompatible");
		}


		return this;
	}

	@Override
	public IType type() {
		Generic generic = new Generic("roj/compiler/runtime/ReturnStack");
		for (ExprNode value : values) generic.addChild(value.type());
		return generic;
	}

	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		cw.ldc(hashCode);
		cw.invokeS("roj/compiler/runtime/ReturnStack", "get", "(I)Lroj/compiler/runtime/ReturnStack;");
		for (ExprNode v : values) {
			v.write(cw);

			var type = v.type();
			cw.invokeV("roj/compiler/runtime/ReturnStack", "put", "("+(type.isPrimitive()?(char)type.rawType().type:"Ljava/lang/Object;")+")Lroj/compiler/runtime/ReturnStack;");
		}
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {throw new ResolveException("未预料的情况");}

	public int capacity() {
		int cap = 0;
		for (ExprNode value : values) {
			switch (TypeCast.getDataCap(value.type().getActualType())) {
				case 0, 1 -> cap += 1;
				case 2, 3 -> cap += 2;
				case 4, 6 -> cap += 4;
				case 5, 7 -> cap += 8;
			}
		}
		return cap;
	}
}