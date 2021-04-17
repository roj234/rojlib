package roj.lavac.expr;

import roj.asm.tree.insn.LabelInsnNode;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.collect.Int2IntMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.JavaLexer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static roj.config.word.Word.*;
import static roj.lavac.parser.JavaLexer.*;

/**
 * 操作符优先级靠它实现
 *
 * @author Roj233
 * @since 2020/10/13 22:14
 */
public final class ExprParser {
	private final ArrayList<ASTNode> words = new ArrayList<>();
	private final Int2IntMap ordered = new Int2IntMap();
	private final ArrayList<Int2IntMap.Entry> sort = new ArrayList<>();
	private boolean busy;
	private ExprParser next;
	ExprParser next() {
		if (next != null) return next;
		ExprParser ep = new ExprParser(depth+1);
		if (depth < 10) next = ep;
		return ep;
	}

	private final int depth;

	static final Comparator<Int2IntMap.Entry> sorter = (a, b) -> Integer.compare(b.v, a.v);

	public ExprParser(int depth) {
		this.depth = depth;
	}

	/**
	 * @see #read(CompileUnit, short, LabelInsnNode)
	 */
	@Nullable
	public ASTNode read(CompileUnit ctx, int exprFlag) throws ParseException {
		try {
			return read0(ctx, exprFlag, null, null);
		} finally {
			busy = false;
		}
	}

	@SuppressWarnings("fallthrough")
	@Nullable
	public ASTNode read(CompileUnit ctx, int exprFlag, Label ifFalse) throws ParseException {
		try {
			return read0(ctx, exprFlag, ifFalse, null);
		} finally {
			busy = false;
		}
	}

	static final int OP_DOT = 1, OP_OBJECT = 2, OB_PRIMITIVE = 4;
	/**
	 * Expression parser(表达式解析器) <BR>
	 *
	 * @throws ParseException if error occurs.
	 */
	private ASTNode read0(CompileUnit ctx, int exprFlag, Label ifFalse, Type excepting) throws ParseException {
		ArrayList<ASTNode> tmp = words;
		if (busy) {
			try {
				return next().read0(ctx, exprFlag, ifFalse, excepting);
			} finally {
				if (next != null) next.busy = false;
			}
		}
		busy = true;

		JavaLexer wr = ctx.getLexer();

		ASTNode cur = null;
		// 这TM其实是个链表
		UnaryPrefix pf = null, pfTop = null;

		simpleNode(0);

		/**
		 *    1   : 有'.' <BR>
		 *    2   : 当前是对象类型 <BR>
		 *    4   : 当前是基本类型 <BR>
		 *    8   : new <BR>
		 *    16  : delete <BR>
		 *    32  : 逗号连接模式 <BR>
		 */
		int opFlag = 0;

		return null;
	}

	CompileUnit ctx;
	JavaLexer wr;
	SimpleList<ASTNode> plist = new SimpleList<>();

	private List<ASTNode> parameterList() throws ParseException {
		// recursion safe
		int i = plist.size();

		Word w;
		while (true) {
			plist.add(simpleNode(0));
			w = wr.next();
			if (w.type() != comma) {
				wr.retractWord();
				break;
			}
		}
		wr.except(right_s_bracket);

		if (i == plist.size()) return Collections.emptyList();

		List<ASTNode> ret = new SimpleList<>(plist.size()-i);
		for (; i < plist.size(); i++) ret.add(plist.get(i));
		plist.removeRange(i, plist.size());
		return ret;
	}

	private New _new() throws ParseException {
		IType type = ctx.resolveType(CompileUnit.TYPE_GENERIC | CompileUnit.TYPE_LEVEL2);
		New n = new New(type);
		Word w = wr.next();
		if (w.type() == semicolon) {
			// no-arg (not supported by javac)
			wr.retractWord();
			return n;
		} else if (w.type() == left_s_bracket) {
			n.param(parameterList());
		}
		return n;
	}

	private ASTNode _literal() throws ParseException {
		CharSequence seq = ctx.objectPath();
		Word w = wr.next();
		switch (w.type()) {
			case method_referent:
		}
		return new FieldChainPre(seq);
	}

	static final int NEW_TYPE = 1;
	private ASTNode simpleNode(int nodeType) throws ParseException {
		Word w = wr.next();
		switch (w.type()) {
			case NEW:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return _new();
			case LITERAL:
				wr.retractWord();
				return _literal();
			case THIS:
				w = wr.next();
				if (w.type() == dot) {
					wr.except(LITERAL);
					wr.retractWord();
					return ((FieldChainPre) simpleNode(114)).ThisFirst();
				} else {
					return new This();
				}
			case INTEGER:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.Int(w.asInt());
			case CHARACTER:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.Char(w.val().charAt(0));
			case STRING:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.String(w.val());
			case Word.DOUBLE:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.Double(w.asDouble());
			case Word.FLOAT:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.Float(w.asDouble());
			case TRUE:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.True();
			case NULL:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.Null();
			case FALSE:
				if ((nodeType & NEW_TYPE) == 0) wr.unexpected(w.val());
				return LDC.False();
			// assign

		}
		return null;
	}

	private static String description(short exprFlag) {
		if ((exprFlag & 8) != 0) return "delimiter.array_index";
		if ((exprFlag & 16) != 0) return "delimiter.func";
		if ((exprFlag & 32) != 0) return "delimiter.if";
		if ((exprFlag & 64) != 0) return "}";
		if ((exprFlag & 128) != 0) return "]";
		if ((exprFlag & 256) != 0) return ")";
		if ((exprFlag & 512) != 0) return "delimiter.triple";
		if ((exprFlag & 1024) != 0) return "delimiter.for";
		return "exprFlag." + exprFlag;
	}

	public void reset() {
		this.words.clear();
		this.sort.clear();
		this.ordered.clear();
	}

	private static short assign2op(short type) {
		switch (type) {
			case add_assign: return add;
			case div_assign: return div;
			case and_assign: return and;
			case lsh_assign: return lsh;
			case mod_assign: return mod;
			case mul_assign: return mul;
			case rsh_assign: return rsh;
			case rsh_unsigned_assign: return rsh_unsigned;
			case sub_assign: return sub;
			case xor_assign: return xor;
		}
		throw new IllegalStateException("Unknown assign type: " + type);
	}
}
