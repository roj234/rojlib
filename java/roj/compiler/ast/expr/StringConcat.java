package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.api.Compiler;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/1/27 2:57
 */
final class StringConcat extends Expr {
	ArrayList<Expr> nodes = new ArrayList<>();

	StringConcat() {}
	StringConcat(Expr left, Expr right) {
		nodes.add(left);
		nodes.add(right);
	}

	@Override
	public String toString() { return "<concat> "+TextUtil.join(nodes, " + "); }

	@Override
	public IType type() { return Types.STRING_TYPE; }

	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException {
		for (int i = 0; i < nodes.size();) {
			Expr node = nodes.get(i).resolve(ctx);
			if (node instanceof StringConcat sc) {
				nodes.remove(i);
				nodes.addAll(i, sc.nodes);
				i += sc.nodes.size();
			} else {
				nodes.set(i, node);
				i++;
			}
		}

		CharList sb = IOUtil.getSharedCharBuf();
		for (int i = 0; i < nodes.size()-1;) {
			Expr node = nodes.get(i), next = nodes.get(++i);
			if (!node.isConstant() || !next.isConstant()) continue;

			sb.clear();
			sb.append(safeToString(node.constVal()));
			do {
				sb.append(safeToString(next.constVal()));

				nodes.remove(i);
				if (nodes.size() == i) break;

				next = nodes.get(i);
			} while (next.isConstant());

			nodes.set(i-1, valueOf(sb.toString()));
		}
		return nodes.size() == 1 ? nodes.get(0) : this;
	}
	private static String safeToString(Object o) {return o instanceof CEntry entry ? entry.asString() : String.valueOf(o);}

	public Expr prepend(Expr left) {nodes.add(0, left);return this;}
	public Expr append(Expr right) {nodes.add(right);return this;}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		// this trick worse if and only if node.get(1).type() instanceof CharSequence && using SharedStringConcat
		if (nodes.size() == 2 && nodes.get(0).isConstant()) {
			cw.ldc(((String) nodes.get(0).constVal()));
			nodes.get(1).write(cw, false);
			int type = nodes.get(1).type().getActualType();
			if (type == 'B' || type == 'S') type = 'I';
			String specType = type == 'L' ? "Ljava/lang/Object;" : String.valueOf((char)type);
			cw.invokeS("java/lang/String", "valueOf", "("+specType+")Ljava/lang/String;");
			cw.invokeV("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;");
			return;
		}

		var lc = CompileContext.get();
		if (lc.compiler.hasFeature(Compiler.SHARED_STRING_CONCAT)) {
			viaCharList(cw, lc);
		} else {
			viaStringBuilder(cw, lc);
		}
	}
	private void viaStringBuilder(MethodWriter cw, CompileContext lc) {
		cw.newObject("java/lang/StringBuilder");
		for (int i = 0; i < nodes.size(); i++) {
			Expr node = nodes.get(i);
			var type = node.type();
			if (type.getClass() == Type.DirtyHacker.class) {
				node = callPseudoToString(lc, node);
				type = node.type();
			}
			node.write(cw);

			int cap = TypeCast.getDataCap(type.getActualType());
			if (cap != 8) {
				String desc = switch (cap) {
					case 0 -> "(Z)Ljava/lang/StringBuilder;";
					case 2 -> "(C)Ljava/lang/StringBuilder;";
					case 5 -> "(J)Ljava/lang/StringBuilder;";
					case 6 -> "(F)Ljava/lang/StringBuilder;";
					case 7 -> "(D)Ljava/lang/StringBuilder;";
					default -> "(I)Ljava/lang/StringBuilder;";
				};

				cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", desc);
			} else if (type.equals(Types.STRING_TYPE)) {
				cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
			} else {
				TypeCast.Cast cast = lc.castTo(type, Types.CHARSEQUENCE_TYPE, TypeCast.E_NEVER);
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

	private static Expr callPseudoToString(CompileContext ctx, Expr node) {
		MemberAccess fn = new MemberAccess(node, "toString", 0);
		return new Invoke(fn, Collections.emptyList()).resolve(ctx);
	}

	private void viaCharList(MethodWriter cw, CompileContext lc) {
		cw.newObject("roj/text/CharList");
		for (int i = 0; i < nodes.size(); i++) {
			Expr node = nodes.get(i);
			var type = node.type();
			if (type.getClass() == Type.DirtyHacker.class) {
				node = callPseudoToString(lc, node);
				type = node.type();
			}
			node.write(cw);

			int cap = TypeCast.getDataCap(type.getActualType());
			if (cap != 8) {
				String desc = switch (cap) {
					case 0 -> "(Z)Lroj/text/CharList;";
					case 2 -> "(C)Lroj/text/CharList;";
					case 5 -> "(J)Lroj/text/CharList;";
					case 6 -> "(F)Lroj/text/CharList;";
					case 7 -> "(D)Lroj/text/CharList;";
					default -> "(I)Lroj/text/CharList;";
				};

				cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "append", desc);
			} else if (type.equals(Types.STRING_TYPE)) {
				cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "append", "(Ljava/lang/String;)Lroj/text/CharList;");
			} else {
				TypeCast.Cast cast = lc.castTo(type, Types.CHARSEQUENCE_TYPE, TypeCast.E_NEVER);
				if (cast.type >= 0) {
					cast.write(cw);
					cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "append", "(Ljava/lang/CharSequence;)Lroj/text/CharList;");
				} else {
					cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "append", "(Ljava/lang/Object;)Lroj/text/CharList;");
				}
			}
		}
		cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "toStringAndFree", "()Ljava/lang/String;");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof StringConcat concat)) return false;
		return nodes.equals(concat.nodes);
	}

	@Override
	public int hashCode() { return nodes.hashCode(); }
}