package roj.lavac.block;

import roj.asm.Opcodes;
import roj.asm.tree.Field;
import roj.asm.tree.Method;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrCode;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.tree.insn.InvokeInsnNode;
import roj.asm.util.AccessFlag;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.lavac.expr.ASTNode;
import roj.lavac.expr.ExprParser;
import roj.lavac.parser.CompileLocalCache;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.JavaLexer;
import roj.lavac.parser.MethodPoetL;

import java.util.List;

import static roj.lavac.parser.JavaLexer.*;

public interface ParseTask {
	static ParseTask FieldVal(int start, int end) {
		return (u, p) -> {
			JavaLexer wr = u.getLexer();
			wr.index = start;

			ExprParser ep = CompileLocalCache.get().ep;

			Method m = u.getClinit();
			MethodPoetL poet = u.ctx().createMethodPoet(u, m);
			Field owner = (Field) p;

			ASTNode expr = ep.read(u, 0, null);
			expr.write(poet, false);

			Word w = wr.next();
			switch (w.type()) {
				case semicolon:
				case comma:
					break;
				default:
					throw wr.err("unexpected:" + w.val());
			}

			// todo if is not static?
			poet.node(new FieldInsnNode(Opcodes.PUTSTATIC, u.name, owner.name, owner.type));
		};
	}

	static ParseTask EnumVal(int enumId, int start, int end) {
		return (u, p) -> {
			JavaLexer wr = u.getLexer();
			wr.index = start;

			ExprParser ep = CompileLocalCache.get().ep;

			Method m = u.getClinit();
			MethodPoetL poet = u.ctx().createMethodPoet(u, m);
			Field owner = (Field) p;

			poet.new1(u.name).dup();
			while (true) {
				ASTNode expr = ep.read(u, 16, null);
				expr.write(poet, false);

				Word w = wr.next();
                if (w.type() == right_s_bracket) {
					break;
				} else if (w.type() != comma || wr.index > end)
					throw wr.err("unexpected:" + w.val());
			}
			poet.node(new InvokeInsnNode(Opcodes.INVOKESPECIAL, u.name, "<init>", u.ctx().findSuitableMethod(poet, u, "<init>", p)))
				.node(new FieldInsnNode(Opcodes.PUTSTATIC, u.name, owner.name, owner.type));
		};
	}

	// 对于格式良好的类文件，end用不到
	static ParseTask Method(List<String> names, int start, int end) {
		return (u, p) -> {
			BlockParser bp = CompileLocalCache.get().bp;
			MethodNode mn = ((AttrCode) p).getOwner();
			bp.init(u, start, mn);
			bp.type = 0;

			int off = (mn.accessFlag() & AccessFlag.STATIC) == 0 ? 1 : 0;
			for (int i = 0; i < names.size(); i++) {
				bp.variables.put(names.get(i), bp.mw.arg(i+off));
			}

			bp.parse0();
		};
	}

	static ParseTask StaticInit(int start, int end) {
		return (u, p) -> {
			BlockParser bp = CompileLocalCache.get().bp;
			bp.init(u, start, ((AttrCode)p).getOwner());
			bp.type = 1;
			bp.parse0();
		};
	}

	static ParseTask GlobalInit(int start, int end) {
		return (u, p) -> {
			BlockParser bp = CompileLocalCache.get().bp;
			bp.init(u, start, ((AttrCode)p).getOwner());
			bp.type = 2;
			bp.parse0();
		};
	}

	// 注解的default
	static ParseTask AnnotationConst(int start, int end) {
		return (u, p) -> {
			JavaLexer wr = u.getLexer();
			wr.index = start;

			ExprParser ep = CompileLocalCache.get().ep;
			ASTNode expr = ep.read(u, 0, null);
		};
	}

	void parse(CompileUnit unit, Object param) throws ParseException;
}
