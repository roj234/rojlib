package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/6/9 0009 1:42
 */
public final class MultiReturn extends ExprNode {
	private final List<ExprNode> values;
	public MultiReturn(List<ExprNode> values) {
		this.values = values;
		if (values.size() == 0 || values.size() > 255) throw new UnsupportedOperationException("Illegal return size "+values.size());
	}

	@Override
	public String toString() {return values.toString();}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		for (int i = 0; i < values.size(); i++) {
			values.set(i, values.get(i).resolve(ctx));
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
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		cw.invokeS("roj/compiler/runtime/ReturnStack", "get", "()Lroj/compiler/runtime/ReturnStack;");
		for (ExprNode v : values) {
			v.write(cw, false);

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