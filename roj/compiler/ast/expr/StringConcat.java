package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.word.NotStatementException;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

/**
 * @author Roj234
 * @since 2024/1/27 0027 2:57
 */
final class StringConcat implements ExprNode {
	SimpleList<ExprNode> nodes = new SimpleList<>();

	StringConcat(ExprNode left, ExprNode right) {
		nodes.add(left);
		append(right);
	}

	@Override
	public String toString() { return "<concat> "+TextUtil.join(nodes, " + "); }

	@Override
	public IType type() { return Constant.STRING; }

	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		for (int i = 0; i < nodes.size(); i++) nodes.set(i, nodes.get(i).resolve(ctx));

		CharList sb = IOUtil.getSharedCharBuf();
		for (int i = 0; i < nodes.size()-1;) {
			ExprNode node = nodes.get(i), next = nodes.get(++i);
			if (!node.isConstant() || !next.isConstant()) continue;

			sb.clear();
			sb.append(safeToString(node.constVal()));
			do {
				sb.append(safeToString(next.constVal()));

				nodes.remove(i);
				if (nodes.size() == i) break;

				next = nodes.get(i);
			} while (next.isConstant());

			nodes.set(i-1, Constant.valueOf(sb.toString()));
		}
		return nodes.size() == 1 ? nodes.get(0) : this;
	}
	private static String safeToString(Object o) { return o instanceof AnnVal ? ((AnnVal) o).toRawString() : o.toString(); }

	public ExprNode prepend(ExprNode left) {
		nodes.add(0, left);
		return this;
	}

	public ExprNode append(ExprNode right) {
		while (right.getClass() == Binary.class) {
			Binary binary = (Binary) right;
			if (binary.operator != JavaLexer.add) break;

			nodes.add(binary.left);
			right = binary.right;
		}
		nodes.add(right);
		return this;
	}

	private static final Type CHARSEQUENCE_TYPE = new Type("java/lang/CharSequence");
	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		cw.newObject("java/lang/StringBuilder");
		for (int i = 0; i < nodes.size(); i++) {
			ExprNode node = nodes.get(i);
			node.write(cw, false);

			Type rawType = node.type().rawType();
			if (rawType.isPrimitive()) {
				String desc = switch (TypeCast.getDataCap(rawType.type)) {
					case 0 -> "(Z)Ljava/lang/StringBuilder;";
					case 2 -> "(C)Ljava/lang/StringBuilder;";
					case 5 -> "(J)Ljava/lang/StringBuilder;";
					case 6 -> "(F)Ljava/lang/StringBuilder;";
					case 7 -> "(D)Ljava/lang/StringBuilder;";
					default -> "(I)Ljava/lang/StringBuilder;";
				};

				cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", desc);
			} else if (rawType.equals(Constant.STRING)) {
				cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
			} else {
				TypeCast.Cast cast = cw.ctx1.castTo(rawType, CHARSEQUENCE_TYPE, TypeCast.E_NEVER);
				if (cast.type >= 0) {
					cast.write(cw);
					cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/CharSequence;)Ljava/lang/StringBuilder;");
				} else {
					cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;");
				}
			}
		}
		cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;");
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof StringConcat concat)) return false;
		return nodes.equals(concat.nodes);
	}
}