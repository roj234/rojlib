package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.Opcodes;
import roj.asm.attr.BootstrapMethods;
import roj.asm.cp.Constant;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.api.Compiler;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.node.ConfigValue;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/1/27 2:57
 */
final class StringConcat extends Expr {
	private ArrayList<Expr> nodes = new ArrayList<>();
	private boolean hasAnyConstant;

	StringConcat() {}
	StringConcat(Expr left, Expr right) {
		nodes.add(left);
		nodes.add(right);
	}

	@Override
	public String toString() { return "/* StringConcat */ "+TextUtil.join(nodes, " + "); }

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

			hasAnyConstant = true;
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
	private static String safeToString(Object o) {return o instanceof ConfigValue entry ? entry.asString() : String.valueOf(o);}

	public Expr prepend(Expr left) {nodes.add(0, left);return this;}
	public Expr append(Expr right) {nodes.add(right);return this;}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);

		// this trick worse if and only if node.get(1).type() instanceof CharSequence && using SharedStringConcat
		if (nodes.size() == 2 && nodes.get(0).isConstant()) {
			cw.ldc(((String) nodes.get(0).constVal()));
			nodes.get(1).write(cw);
			int type = nodes.get(1).type().getActualType();
			if (type == 'B' || type == 'S') type = 'I';
			String specType = type == 'L' ? "Ljava/lang/Object;" : String.valueOf((char)type);
			cw.invokeS("java/lang/String", "valueOf", "("+specType+")Ljava/lang/String;");
			cw.invokeV("java/lang/String", "concat", "(Ljava/lang/String;)Ljava/lang/String;");
			return;
		}

		var ctx = CompileContext.get();
		if (ctx.compiler.hasFeature(Compiler.SHARED_STRING_CONCAT)) {
			myConcat(cw, ctx);
		} else {
			if (ctx.compiler.getMaximumBinaryCompatibility() > Compiler.JAVA_8 && nodes.size() < 20) {
				newConcat(cw, ctx);
			} else {
				legacyConcat(cw, ctx);
			}
		}
	}

	private void newConcat(MethodWriter cw, CompileContext ctx) {
		if (hasAnyConstant) {
			var recipeList = new ArrayList<Constant>(1);
			int tableIdx = ctx.file.addNewLambdaRef(new BootstrapMethods.Item(
					BootstrapMethods.Kind.INVOKESTATIC,
					cw.cpw.getRefByType("java/lang/invoke/StringConcatFactory",
							"makeConcatWithConstants",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;",
							Constant.METHOD
					),
					recipeList
			));

			var recipe = new CharList();
			var desc = new CharList();
			for (int i = 0; i < nodes.size(); i++) {
				Expr node = nodes.get(i);
				if (node.isConstant()) {
					recipe.append(((ConfigValue) node.constVal()).asString());
				} else {
					recipe.append('\u0001');
				}

				var type = node.type();
				if (type.getClass() == Type.ADT.class) {
					node = callPseudoToString(ctx, node);
					type = node.type();
				}
				node.write(cw);
				type.rawType().toDesc(desc);
			}

			recipeList.add(cw.cpw.getUtf(recipe.toStringAndFree()));
			cw.invokeDyn(tableIdx, "toString", desc.toStringAndFree());
		} else {
			int tableIdx = ctx.file.addNewLambdaRef(new BootstrapMethods.Item(
					BootstrapMethods.Kind.INVOKESTATIC,
					cw.cpw.getRefByType("java/lang/invoke/StringConcatFactory",
							"makeConcat",
							"(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;",
							Constant.METHOD
					),
					Collections.emptyList()
			));

			var desc = new CharList();
			for (int i = 0; i < nodes.size(); i++) {
				Expr node = nodes.get(i);
				var type = node.type();
				if (type.getClass() == Type.ADT.class) {
					node = callPseudoToString(ctx, node);
					type = node.type();
				}
				node.write(cw);
				type.rawType().toDesc(desc);
			}
			cw.invokeDyn(tableIdx, "toString", desc.toStringAndFree());
		}
	}
	private void legacyConcat(MethodWriter cw, CompileContext lc) {
		cw.newObject("java/lang/StringBuilder");
		for (int i = 0; i < nodes.size(); i++) {
			Expr node = nodes.get(i);
			var type = node.type();
			if (type.getClass() == Type.ADT.class) {
				node = callPseudoToString(lc, node);
				type = node.type();
			}
			node.write(cw);

			int cap = Type.getSort(type.getActualType());
			if (cap != Type.SORT_OBJECT) {
				String desc = switch (cap) {
					case Type.SORT_BOOLEAN -> "(Z)Ljava/lang/StringBuilder;";
					case Type.SORT_CHAR -> "(C)Ljava/lang/StringBuilder;";
					case Type.SORT_LONG -> "(J)Ljava/lang/StringBuilder;";
					case Type.SORT_FLOAT -> "(F)Ljava/lang/StringBuilder;";
					case Type.SORT_DOUBLE -> "(D)Ljava/lang/StringBuilder;";
					default -> "(I)Ljava/lang/StringBuilder;";
				};

				cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", desc);
			} else if (type.equals(Types.STRING_TYPE)) {
				cw.invoke(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;");
			} else {
				TypeCast.Cast cast = lc.castTo(type, Types.CHARSEQUENCE_TYPE, TypeCast.IMPOSSIBLE);
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
	private void myConcat(MethodWriter cw, CompileContext lc) {
		cw.newObject("roj/text/CharList");
		for (int i = 0; i < nodes.size(); i++) {
			Expr node = nodes.get(i);
			var type = node.type();
			if (type.getClass() == Type.ADT.class) {
				node = callPseudoToString(lc, node);
				type = node.type();
			}
			node.write(cw);

			int cap = Type.getSort(type.getActualType());
			if (cap != Type.SORT_OBJECT) {
				String desc = switch (cap) {
					case Type.SORT_BOOLEAN -> "(Z)Lroj/text/CharList;";
					case Type.SORT_CHAR -> "(C)Lroj/text/CharList;";
					case Type.SORT_LONG -> "(J)Lroj/text/CharList;";
					case Type.SORT_FLOAT -> "(F)Lroj/text/CharList;";
					case Type.SORT_DOUBLE -> "(D)Lroj/text/CharList;";
					default -> "(I)Lroj/text/CharList;";
				};

				cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "append", desc);
			} else if (type.equals(Types.STRING_TYPE)) {
				cw.invoke(Opcodes.INVOKEVIRTUAL, "roj/text/CharList", "append", "(Ljava/lang/String;)Lroj/text/CharList;");
			} else {
				TypeCast.Cast cast = lc.castTo(type, Types.CHARSEQUENCE_TYPE, TypeCast.IMPOSSIBLE);
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

	private static Expr callPseudoToString(CompileContext ctx, Expr node) {
		MemberAccess fn = new MemberAccess(node, "toString", 0);
		return new Invoke(fn, Collections.emptyList()).resolve(ctx);
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