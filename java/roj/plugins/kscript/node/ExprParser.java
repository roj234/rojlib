package roj.plugins.kscript.node;

import org.jetbrains.annotations.Nullable;
import roj.collect.Int2IntMap;
import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.Word;
import roj.config.data.*;
import roj.plugins.kscript.KParser;
import roj.plugins.kscript.LabelNode;
import roj.plugins.kscript.func.KSFunction;
import roj.plugins.kscript.func.KSObject;
import roj.plugins.kscript.token.KSLexer;
import roj.util.ArrayRef;
import roj.util.Helpers;
import roj.util.TimSortForEveryone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

import static roj.plugins.kscript.token.KSLexer.*;
import static roj.reflect.ReflectionUtils.u;

/**
 * 操作符优先级靠它实现
 *
 * @author Roj233
 * @since 2020/10/13 22:14
 */
public final class ExprParser {
    public static final int
        STOP_SEMICOLON = 1, SKIP_SEMICOLON = 2,
        STOP_RSB = 4, SKIP_RSB = 8,
        STOP_RMB = 16, SKIP_RMB = 32,
        STOP_COMMA = 64, SKIP_COMMA = 128,
        STOP_RLB = 256,
        STOP_COLON = 512,
        STOP_LAMBDA = 1024,
		_ENV_SPREAD_ARRAY = 2048,
		_ENV_SPREAD_OBJECT = 4096;

	public static final int SM_UnaryPre = 1 << 29, SM_ExprStart = 2 << 29, SM_ExprNext = 3 << 29, SM_ExprTerm = 4 << 29;
	public static Int2IntMap getStateMap() { return SM; }

	private static final Int2IntMap SM = new Int2IntMap(64);
	static {
		SM.putInt(SM_UnaryPre | inc, -1);
		SM.putInt(SM_UnaryPre | dec, -1);
		SM.putInt(SM_UnaryPre | add, -1);
		SM.putInt(SM_UnaryPre | sub, -1);
		SM.putInt(SM_UnaryPre | logic_not, -1);
		SM.putInt(SM_UnaryPre | rev, -1);

		SM.putInt(SM_UnaryPre | spread, -2);
		SM.putInt(SM_UnaryPre | NEW, -3);
		SM.putInt(SM_UnaryPre | DELETE, -4);
		SM.putInt(SM_UnaryPre | lParen, -5);

		SM.putInt(SM_ExprStart | lambda, -1);
		SM.putInt(SM_ExprStart | FUNCTION, -2);
		SM.putInt(SM_ExprStart | NAN, -3);
		SM.putInt(SM_ExprStart | INFINITY, -3);
		SM.putInt(SM_ExprStart | Word.DOUBLE, -3);
		SM.putInt(SM_ExprStart | Word.LONG, -3);
		SM.putInt(SM_ExprStart | Word.INTEGER, -4);
		SM.putInt(SM_ExprStart | TRUE, -5);
		SM.putInt(SM_ExprStart | FALSE, -5);
		SM.putInt(SM_ExprStart | Word.STRING, -6);
		SM.putInt(SM_ExprStart | NULL, -7);
		SM.putInt(SM_ExprStart | lBracket, -8);
		SM.putInt(SM_ExprStart | lBrace, -9);
		SM.putInt(SM_ExprStart | THIS, -10);
		SM.putInt(SM_ExprStart | ARGUMENTS, -11);
		SM.putInt(SM_ExprStart | Word.LITERAL, -12);

		SM.putInt(SM_ExprNext | lBracket, -1);
		SM.putInt(SM_ExprNext | dot, -2);
		SM.putInt(SM_ExprNext | Word.LITERAL, -3);
		SM.putInt(SM_ExprNext | lParen, -4);
		SM.putInt(SM_ExprNext | assign, -5);
		for (int i = binary_assign_base_offset; i < binary_assign_base_offset + binary_assign_count; i++) {
			SM.putInt(SM_ExprNext | i, -5);
		}
		SM.putInt(SM_ExprNext | inc, -6);
		SM.putInt(SM_ExprNext | dec, -6);
		SM.putInt(SM_ExprNext | 999, -7);

		// Spec
		//SM.putInt(SM_ExprTerm | INSTANCEOF, -1);
		//SM.putInt(SM_ExprTerm | KSLexer.ask, -1);

		// 终结
		// 0x400 = FLAG_TERMINATOR
		// 0x040 = FLAG_ALWAYS_STOP
		// 0x020 = FLAG_NEVER_SKIP
		SM.putInt(SM_ExprTerm | semicolon, 0x400);
		SM.putInt(SM_ExprTerm | rParen, 0x402);
		SM.putInt(SM_ExprTerm | rBracket, 0x404);
		SM.putInt(SM_ExprTerm | comma, 0x446);
		SM.putInt(SM_ExprTerm | rBrace, 0x428);
		SM.putInt(SM_ExprTerm | colon, 0x429);
		SM.putInt(SM_ExprTerm | lambda, 0x42A);

		// 优先级
		SM.putInt(SM_ExprTerm | and, 10);
		SM.putInt(SM_ExprTerm | or, 10);
		SM.putInt(SM_ExprTerm | xor, 10);

		SM.putInt(SM_ExprTerm | lsh, 9);
		SM.putInt(SM_ExprTerm | rsh, 9);
		SM.putInt(SM_ExprTerm | rsh_unsigned, 9);

		SM.putInt(SM_ExprTerm | pow, 8);

		SM.putInt(SM_ExprTerm | mul, 7);
		SM.putInt(SM_ExprTerm | div, 7);
		SM.putInt(SM_ExprTerm | mod, 7);

		SM.putInt(SM_ExprTerm | add, 6);
		SM.putInt(SM_ExprTerm | sub, 6);

		SM.putInt(SM_ExprTerm | lss, 5);
		SM.putInt(SM_ExprTerm | gtr, 5);
		SM.putInt(SM_ExprTerm | geq, 5);
		SM.putInt(SM_ExprTerm | leq, 5);
		SM.putInt(SM_ExprTerm | equ, 5);
		SM.putInt(SM_ExprTerm | neq, 5);
		SM.putInt(SM_ExprTerm | feq, 5);
		SM.putInt(SM_ExprTerm | fne, 5);

		SM.putInt(SM_ExprTerm | nullish_consolidating, 4);
		SM.putInt(SM_ExprTerm | logic_and, 4);
		SM.putInt(SM_ExprTerm | logic_or, 4);
	}
	private static void batch(int mask, int val, short... tokens) {
		for (short token : tokens) SM.putInt(token|mask, val);
	}

    private final SimpleList<?> tmp0 = new SimpleList<>();
    private <T> List<T> tmp() {
        if (depth > 0) return new SimpleList<>();
        var t = tmp0; t.clear(); return Helpers.cast(t);
    }

    private final SimpleList<ExprNode> nodes = new SimpleList<>();
    /**
     * 低10位放优先级
     * 高22位放节点序号
     */
    private final IntList binaryOps = new IntList();
    private static final TimSortForEveryone.MyComparator BOP_SORTER = (refLeft, offLeft, offRight) -> {
        var a = u.getInt(refLeft, offLeft) & 1023;
        var b = u.getInt(offRight) & 1023;
        return Integer.compare(b, a);
    };

	private KParser ctx;

    private int depth = -1;
    public Int2IntMap sm = SM;
    public List<Object> custom;

    public static void main(String[] args) throws ParseException, IOException {
        System.out.println("Roj234's KScript Interpreter 3.0\n");

        var wr = new KSLexer();
        var ep = new ExprParser();

        var r = new BufferedReader(new InputStreamReader(System.in));
        String line;
        var env = new KSObject();

        System.out.print(">");
        while ((line = r.readLine()) != null) {
            if(!line.endsWith(";")) line += ';';
            wr.init(line);

            try {
                var expr = ep.read(() -> wr, STOP_SEMICOLON, null);

                if (expr == null) {
                    System.out.println();
                } else {
                    System.out.println("Parse: "+expr);
					expr = expr.resolve();
					System.out.println("Resolve: "+expr.resolve());
                    System.out.println("Result: "+expr.eval(env));
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            System.out.print('>');
        }
    }

    public ExprParser() {}

	@Nullable
	public ExprNode read(KParser ctx, int flag, LabelNode ifFalse) throws ParseException {return read(ctx, flag);}

    /**
     * @see #parse1(int)
     */
    @Nullable
    public ExprNode read(KParser ctx, int flag) throws ParseException {
		this.ctx = ctx;
		try {
			return parse1(flag);
		} finally {
			this.ctx = null;
			depth = -1;
			nodes.clear();
			binaryOps.clear();
			tmp0.clear();
		}
	}

	@SuppressWarnings("fallthrough")
	private ExprNode parse1(int flag) throws ParseException {
        var wr = ctx.lexer();
        Word w;

		if (++depth > 127) throw wr.err("expr.stackOverflow");

        var nodes = this.nodes;
        int bopCount = binaryOps.size();
        int nodeCount = nodes.size();

        while (true) {
            // 这其实是个链表
			UnaryPreNode up = null;
            ExprNode cur = null;
            int _sid;

            int newOrDel = 0;
            var isPrimitive = false;

            w = wr.next();
            endValueConvNrt:{
            endValueConv:{
            endValueGen:{
            // region 前缀操作 (++ -- + - ! ~ 类型转换) 和 检测lambda 和 switch表达式
            loop:
            while (true) {
                UnaryPreNode pf;
                switch (_sid = sm.getOrDefaultInt(w.type()|SM_UnaryPre, 0)) {
					case -1: pf = new UnaryPre(w.type());break;
					case -2: pf = new Spread();break;
					case -3: newOrDel = 1; w = wr.next(); break loop;
                    case -4: newOrDel = 2; w = wr.next(); break loop;
                    case -5:
                        // TODO check for lambda
						cur = parse1(STOP_RSB|SKIP_RSB);
                        if(cur == null) ctx.report("empty.bracket");
                        w = wr.next();
                    break endValueGen;

                    default: break loop;
                }

                w = wr.next();

                if (up == null) nodes.add(pf);
                else {
					String error = up.setRight(pf);
					if (error != null) ctx.report(error);
				}
                up = pf;
            }
            // endregion
            // region 一次性"值生成"(自造词)操作 (加载常量 new this 花括号(direct)数组内容 int[].class String.class)
            switch (_sid = sm.getOrDefaultInt(w.type()|SM_ExprStart, 0)) {
                case -1, -2: {
                    KSFunction fn = ctx.parseFunction(w.type());
                    if (fn == null) throw wr.err("fn_error");
                    cur = Constant.valueOf(fn);
                }
                break endValueConvNrt;
				case -3:
					cur = Constant.valueOf(CDouble.valueOf(w.asDouble()));
					isPrimitive = true;
					break;
				case -4:
					cur = Constant.valueOf(CInt.valueOf(w.asInt()));
					isPrimitive = true;
					break;
                case -5:
					cur = Constant.valueOf(w.val().equals("true") ? CBoolean.TRUE : CBoolean.FALSE);
                    isPrimitive = true;
                break;
                case -6: cur = Constant.valueOf(CString.valueOf(w.val()));break;
				case -7: cur = Constant.valueOf(CNull.NULL);break;
                case -8: cur = newArray();break;
                case -9: cur = defineObject();break;
                case -10: cur = Special.THIS;break;
                case -11: cur = Special.ARGS;break;
                case -12: cur = new Variable(w.val());break;

				default: break endValueGen;
            }
            w = wr.next();
            // endregion
            }//END endValueGen
            // region 重复性"值生成" (变量|字段访问 数组获取 函数调用) 和 值终止 (赋值运算符 后缀自增/自减 方法引用lambda)
            var waitDot = cur != null;
            while (true) {
                switch (_sid = sm.getOrDefaultInt(w.type()|SM_ExprNext, 0)) {
                    case -1: {// array start
                        if (!waitDot || isPrimitive) ue(ctx, "[");

                        var index = parse1(STOP_RMB|SKIP_RMB);
                        if (index == null) ue(ctx, "empty.array_index");
                        cur = new ArrayGet(cur, index);
                    }
                    break;
                    case -2: {
                        if (!waitDot) ue(ctx, ".");
                        waitDot = false;
                    }
                    break;
                    // a.b.c.d
                    case -3: {
                        if (waitDot) ue(ctx, w.val(), ".");
                        waitDot = true;

                        cur = chain(cur, w.val());
                        isPrimitive = false;
                    }
                    break;
                    // ( x )
                    case -4:
                        // bracket
						if (isPrimitive) ue(ctx, "(");
						cur = invoke(cur);
					break;

                    // 我是无敌可爱的分隔线

                    default:
                        if (cur != null && !waitDot) ue(ctx, w.val(), "type.literal");
                    break endValueConvNrt;

                    case -5:
                        if (isPrimitive) ue(ctx, w.val());
						if(!(cur instanceof VarNode)) {
							ctx.report("invalid_left_value");
							break;
						}

						var opType = w.type() == assign ? 0 : w.type() + binary_assign_delta;

                        var right = parse1(flag);
                        if (right == null) ctx.report("empty.right_value");

                        if (opType != 0) right = new Binary((short) opType, cur, right);
                        cur = new Assign((VarNode) cur, right);
                    break endValueConv;
                    case -6:
                        if (!(cur instanceof VarNode vn)) ctx.report("expr.notVariable:".concat(w.val()));
						else cur = new UnaryPost(w.type(), vn);
                    break endValueConv;
                    case -7:
                        // method_Ref
                    break endValueConv;
                }
                w = wr.next();
            }
            // endregion
            }//END endValueConv
            w = wr.next();
            }//END endValueConvNtr

            // 应用前缀算符
            if (up != null) {
                String code = up.setRight(cur);
                if (code != null) ctx.report(code);

                cur = up;
            } else {
                // Nullable
                nodes.add(cur);
            }

            if (newOrDel == 2) {
                if(!(cur instanceof VarNode load)) ctx.report("delete.invalidType:"+cur);
                else if(!load.setDeletion()) ctx.report("delete.unableDelete");
            } else if (newOrDel == 1) {
                if (!(cur instanceof Invoke load)) ctx.report("new.invalidType:"+cur);
				else load.setNew();
            }

            // 二元运算符 | 三元运算符 | 终结符
            _sid = sm.getOrDefaultInt(SM_ExprTerm | w.type(), 0);
            if (_sid == 0) {
                ue(ctx, w.val());
            }

            if ((_sid&0x400) != 0) {
                // 除了逗号之外, 还有 instanceof 和 ?
                // 它们是-1, 所以这两个值都为真
                if ((_sid & 0x40) != 0) break;
                // 终结符
                // 0x400 = FLAG_TERMINATOR
                // 0x020 = FLAG_NEVER_SKIP
                int shl = _sid & 31;
                if ((flag & (1 << shl)) == 0) {
                    ue(ctx, w.val());
                }
                if ((_sid & 0x20) != 0 || (flag & (1 << (shl+1))) == 0) wr.retractWord();

                break;
            }

            // 二元运算符
            checkNullExpr(cur);

            // range 0 - 1023
            binaryOps.add((nodes.size() << 10) | _sid);
            nodes.add(new Binary(w.type()));
        }

		// region 表达式优先级
		ExprNode cur;
		var ops = binaryOps;
		if (ops.size() > bopCount) {
			TimSortForEveryone.sort(bopCount, ops.size(), BOP_SORTER, ArrayRef.primitiveArray(ops.getRawArray()));

			int i = bopCount;
			do {
				int ord = ops.get(i) >>> 10;

				Binary op = (Binary) nodes.get(ord);

				op.left = nodes.set(ord-1, op);
				op.right = nodes.set(ord+1, op);
				checkNullExpr(op.right);

				cur = op;
			} while (++i != ops.size());

			ops.removeRange(bopCount, ops.size());
			nodes.removeRange(nodeCount, nodes.size());
		} else {
			assert nodes.size() == nodeCount+1;
			// 肯定有这一项，但是可能是null
			cur = nodes.pop();
		}
		// endregion

		// 优先级也不低
		// if ("5"+3 instanceof String ? "5"+3 instanceof String : "5"+3 instanceof String);
		/*if (w.type() == INSTANCEOF) {
			checkNullExpr(cur);

			IType targetType = ctx.file.readType(CompileUnit.TYPE_GENERIC);
			String variable = wr.nextIf(LITERAL) ? w.val() : null;
			cur = newInstanceOf(targetType.rawType(), cur, variable);
		}*/

        if(w.type() == comma) {
			if ((flag & STOP_COMMA) == 0) {
				checkNullExpr(cur);

				List<ExprNode> args = tmp();
				args.add(cur);

				do {
					ExprNode expr = parse1(flag|STOP_COMMA|SKIP_COMMA);
					if (expr == null) ctx.report("noExpression");
					else args.add(expr);
				} while (w.type() == comma);
				if (w.type() != semicolon) ue(ctx, w.val(), ";");

				cur = new Chained(copyOf(args));
			} else if ((flag & SKIP_COMMA) == 0) {
				wr.retractWord();
			}
        }

		// 这也是终结. 但是优先级最高
		if (w.type() == ask) {
			checkNullExpr(cur);
			ExprNode middle = parse1(flag|STOP_COLON);
			checkNullExpr(middle);
			wr.except(colon);
			ExprNode right = parse1(flag);
			checkNullExpr(right);
			cur = new Trinary(cur, middle, right);
		}

		depth--;
        return cur;
    }

	private void checkNullExpr(ExprNode cur) throws ParseException {
		if (cur == null) ctx.report("noExpression");
	}

	private ExprNode chain(ExprNode expr, String name) {
		if (!(expr instanceof DotGet dg)) return new DotGet(expr, name);
		dg.add(name);
		return expr;
	}
	private Invoke invoke(ExprNode fn) throws ParseException {
		List<ExprNode> args = tmp();
		while (true) {
			ExprNode expr = parse1(STOP_RSB|STOP_COMMA|SKIP_COMMA);
			if (expr == null) {
				ctx.lexer().except(rParen);
				break;
			}
			args.add(expr);
		}

		return new Invoke(fn, copyOf(args));
	}

	/**
	 * 解析数组定义 <BR>
	 * [xxx, yyy, zzz] or []
	 */
	private ExprNode newArray() throws ParseException {
		var w = ctx.lexer().current();

		List<ExprNode> args = tmp();
		do {
			var node = parse1(STOP_RMB|STOP_COMMA|SKIP_COMMA|_ENV_SPREAD_ARRAY);
			if (node == null) break;
			args.add(node);
		} while (w.type() == comma);
		ctx.lexer().except(rBracket);

		return args.isEmpty() ? NewArray.EMPTY : new NewArray(copyOf(args));
	}

	/**
	 * 解析对象定义 <BR>
	 * {xxx: yyy, zzz: uuu}
	 */
	private ExprNode defineObject() throws ParseException {
		var wr = ctx.lexer();
		MyHashMap<String, ExprNode> map = new MyHashMap<>();

		boolean more = true;

		o:
		while (true) {
			Word name = wr.next();
			switch (name.type()) {
				case rBrace: break o;
				case comma:
					if (more) wr.unexpected(",");
					more = true;
					continue;
				case Word.STRING, Word.LITERAL: break;
				default: wr.unexpected(name.val(), more ? "字符串" : "逗号");
			}

			if (!more) wr.unexpected(name.val(), "逗号");
			more = false;

			String k = name.val();
			if (map.containsKey(k)) ctx.report("重复的key: "+k);

			wr.except(colon, ":");

			ExprNode result = parse1(STOP_RLB|STOP_COMMA|_ENV_SPREAD_ARRAY);
			if (result == null) ctx.report("empty_statement");

			map.put(k, result);
		}

		return map.isEmpty() ? NewMap.EMPTY : new NewMap(map);
	}

	private <T> List<T> copyOf(List<T> args) {
		if (args.isEmpty()) return Collections.emptyList();
		if (!(args instanceof SimpleList<T>)) return args;
		SimpleList<T> copy = new SimpleList<>(args);
		args.clear();
		return copy;
	}

    private static void ue(KParser context, String word, String expecting) throws ParseException {
        throw context.lexer().err("unexpected:" + word + ':' + expecting);
    }

    private static void ue(KParser context, String s) throws ParseException {
        throw context.lexer().err("unexpected:" + s + ": ");
    }
}
