package roj.lavac.expr;

import roj.asm.type.IType;
import roj.asm.visitor.Label;
import roj.collect.Int2IntMap;
import roj.collect.SimpleList;
import roj.compiler.ast.expr.ExprNode;
import roj.concurrent.OperationDone;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.lavac.asm.GenericPrimer;
import roj.lavac.parser.CompileUnit;
import roj.lavac.parser.JavaLexer;
import roj.util.Helpers;

import javax.annotation.Nullable;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static roj.lavac.parser.JavaLexer.*;

/**
 * @author Roj233
 * @since 2023/09/18 7:56
 */
public final class ExprParser {
	private final ArrayList<ExprNode> words = new ArrayList<>();
	private final Int2IntMap ordered = new Int2IntMap();
	private final ArrayList<Int2IntMap.Entry> sort = new ArrayList<>();

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

	public static final int
		STOP_COMMA = 1, SKIP_COMMA = 2,
		STOP_SEMICOLON = 4,
		STOP_COLON = 8,
		STOP_RSB = 16, SKIP_RSB = 32,
		STOP_RLB = 64,
		STOP_RMB = 128, SKIP_RMB = 256,
		ALLOW_SPREAD = 512,
		_ENV_INVOKE_AFTER = 1024,
		_ENV_INVOKE = 2048;
	static final int OP_NEW = 1, OP_DEL = 2, OP_OPTIONAL = 4;

	@Nullable
	public ExprNode parse(CompileUnit ctx, int exprFlag) throws ParseException { return parse(ctx, exprFlag, null); }
	@SuppressWarnings("fallthrough")
	public ExprNode parse(CompileUnit ctx, int flag, Label ifFalse) throws ParseException {
		ArrayList<ExprNode> tmp = words;
		if (!tmp.isEmpty() || !ordered.isEmpty() || !sort.isEmpty()) // 使用中
			return next().parse(ctx, flag, null);

		JavaLexer wr = ctx.lex();

		Word w = wr.next();
		int prevId = -1;
		while (true) {
			if (prevId == wr.index) throw wr.err("死循环");
			prevId = wr.index;

			int opFlag = 0;
			UnaryPre up = null;
			ExprNode cur = null;

			// region 只能出现一次的"前缀操作" (++a, --a, ...a, delete a, new a)
			switch (w.type()) {
				//case spread: // ES6 扩展运算符
				//	if ((flag & ALLOW_SPREAD) == 0) ue(ctx, w.val());
				//	return new Spread(parse(ctx, flag, ifFalse));
				case inc: case dec: up = new UnaryPre(w.type()); tmp.add(up); break;
				//case DELETE: opFlag |= OP_DEL; break;
				case NEW:
					// double[]的部分不受支持
					// new <double[]>test<int[]>(new int[0], (Object) assign2op((short) 2));
					// test.<int[]>b();
					// String[] a = new test<int[]>(null, null).<String[]>a(new int[3]);
					IType newType = ctx.resolveType(CompileUnit.TYPE_GENERIC|CompileUnit.TYPE_LEVEL2);

					w = wr.next();

					// String[][] array = new String[][0] {};
					// 从零开始，有数字则继续，至[]结束，往后均为null
					// 若有容量，则不能手动指定内容
					if (w.type() == left_m_bracket) {
						List<ExprNode> args = Helpers.cast(sort); args.clear();
						int array = 1;
						arrayDef: {
							while (true) {
								w = wr.next();
								if (w.type() == right_m_bracket) break; // END OF COUNTED DEFINITION

								wr.retractWord();
								args.add(parse(ctx, STOP_RMB|SKIP_RMB, null));

								w = wr.next();
								if (w.type() == left_m_bracket) array++;
								else break arrayDef; // END OF ARRAY DEFINITION
							}

							while (true) {
								w = wr.next();
								if (w.type() != left_m_bracket) {
									wr.retractWord();
									break;
								}
								wr.except(right_m_bracket);
								array++;
							}
						}

						newType.setArrayDim(array);
						if (((GenericPrimer) newType).checkGenericArray()) {
							ctx.fireDiagnostic(Diagnostic.Kind.ERROR, "creation of generic array");
						}

						if (!args.isEmpty()) {
							cur = new ArrayDef(newType, new SimpleList<>(args), true);
						} else {
							wr.except(left_l_bracket);
							while (true) {
								w = wr.next();
								if (w.type() == right_l_bracket) break;

								wr.retractWord();
								args.add(parse(ctx, STOP_RLB|STOP_COMMA|SKIP_COMMA, null));
							}

							cur = new ArrayDef(newType, new SimpleList<>(args), false);
						}
						args.clear();
					} else if (w.type() == left_s_bracket) {
						Method m = _invoke(ctx, wr, null);
						m._type = newType;
						cur = m;
						// todo: NewAnonymousClass
					} else {
						if (w.type() == semicolon) {
							// 语法糖: new n => 无参数调用
							Method m = new Method(null, Collections.emptyList());
							m = _invoke(ctx, wr, null);
							m._type = newType;
							cur = m;
						} else {
							throw wr.err("unexpected state");
						}
						wr.retractWord();
					}
				break;
				default: wr.retractWord(); break;
			}
			// endregion
			// region 能出现多次的"前缀操作" (+a, -a, !a, ~a) 和 只能出现一次的"值加载" (string|int|double|'this'|'arguments'|array_define|object_define|'('|function)
			if (cur == null) while (true) {
				w = wr.next();
				switch (w.type()) {
					case add: case sub: case logic_not: case rev: {
						UnaryPre a = new UnaryPre(w.type());
						if (up == null) tmp.add(a);
						else up.setRight(a);
						up = a;
					}
					continue;
					// constant
					case Word.CHARACTER: case Word.STRING:
					case Word.INTEGER: case Word.LONG:
					case Word.FLOAT: case Word.DOUBLE:
					case TRUE: case FALSE: case NULL:
						cur = Constant.valueOf(w);
					break;
					// this
					case THIS: cur = This.INST; break;
					// define (unknown array)
					case left_l_bracket:
						if ((flag & _ENV_INVOKE_AFTER) != 0) {
							// invoke_after_env:
							// new Object(xxx) {}
						}
						if ((flag & _ENV_INVOKE) != 0) {
							// invoke_env:
							// a.b({xxx: yyy})
						}
						// todo direct map definition
						flag |= STOP_RLB;
						break;
					case left_s_bracket:
						int pos = wr.index;

						notLambda: {
							List<String> ids = Helpers.cast(sort); ids.clear();
							while (true) {
								w = wr.next();
								if (w.type() != Word.LITERAL) {
									if (w.type() == right_s_bracket && wr.next().type() == lambda) break;
									break notLambda;
								}
								ids.add(w.val());
							}

							handleLambda(ctx, wr, ids);
						}
						wr.index = pos;

						IType castTarget = ctx.resolveType(CompileUnit.TYPE_GENERIC);
						Cast a = new Cast(castTarget);
						if (up == null) tmp.add(a);
						else up.setRight(a);
						up = a;
					break;
					case Word.LITERAL:
						pos = wr.indexForRet();
						String id = w.val();
						if (wr.next().type() == lambda) {
							// raw_lambda_env:
							// x -> ...
							wr.index = pos;
							handleLambda(ctx, wr, Collections.singletonList(id));
						} else {
							wr.index = pos;
						}
						break;
					case lambda: throw OperationDone.INSTANCE;
					default: wr.retractWord(); break;
				}
				break;
			}
			// endregion
			// region 能出现多次的"值加载" (variable|field|array_get|invoke)

			GenericPrimer fnGeneric = null;
			boolean curIsObj = cur != null;
			while (true) {
				w = wr.next();
				switch (w.type()) {
					case lss:
						if (curIsObj || fnGeneric != null) throw wr.err("unexpected state");
						fnGeneric = (GenericPrimer) ctx.genericForExpr(false);

						// Helpers.<Helpers>nonnull().<int[]>nonnull();

						cur = _dot(cur, wr.except(Word.LITERAL).val(), (opFlag&OP_OPTIONAL));
						cur = _invoke(ctx, wr, cur);
					continue;
					case left_m_bracket: { // a[b]
						if (!curIsObj) ue(ctx, w.val(), "type.literal");
						ExprNode index = parse(ctx, STOP_RMB|SKIP_RMB, null);
						if (index == null) ue(ctx, "empty.array_index"); // TODO this maybe a array class ref
						cur = new ArrayGet(cur, index);
					}
					continue;
					case dot: case optional_chaining:
						if (!curIsObj) ue(ctx, w.val(), "type.literal");
						curIsObj = false;
						if (w.type() == optional_chaining) opFlag |= OP_OPTIONAL;
						else opFlag &= ~OP_OPTIONAL;
					continue;
					case CLASS:
					case Word.LITERAL: // a.b
						if (curIsObj) ue(ctx, w.val(), ".");
						curIsObj = true;
						cur = _dot(cur, w.val(), (opFlag&OP_OPTIONAL));
					continue;
					case left_s_bracket: // a(b...)
						if (!curIsObj) ue(ctx, w.val(), "type.literal");
						cur = _invoke(ctx, wr, cur);
					continue;
					default: wr.retractWord(); break;
				}
				break;
			}
			assert curIsObj;
			System.out.println(w);
			// endregion

			// set
			if (up != null) {
				String code = up.setRight(cur);
				if (code != null) throw wr.err(code);
				cur = tmp.get(tmp.size()-1);
			}

			// region 赋值运算符 | 后缀自增/自减
			w = wr.next();
			switch (w.type()) {
				// assign
				case assign:
				case add_assign: case sub_assign:
				case mul_assign: case div_assign:
				case pow_assign: case mod_assign:
				case and_assign: case or_assign: case xor_assign:
				case lsh_assign: case rsh_assign: case rsh_unsigned_assign: {
					// 没写单独的不继承Load的Expr，检查opFlag好了
					if ((opFlag&(OP_NEW|OP_DEL))!=0) throw wr.err("invalid_left_value");
					if (!(cur instanceof LoadNode)) throw wr.err("invalid_left_value");

					short vtype = w.type();

					// Mark assign
					// cur.var_op(ctx, 2);

					ExprNode right = parse(ctx, flag|STOP_COMMA, null);
					if (right == null) throw wr.err("empty.right_value");

					cur = new Assign((LoadNode) cur, vtype == assign ? right : new Binary(assign2op(vtype), cur, right));
				}
				break;
				case inc:
				case dec:
					if (!(cur instanceof LoadNode)) throw wr.err("expecting_variable:"+w.val());
					cur = new UnaryPost(w.type(), cur);
				break;
				default: wr.retractWord(); break;
			}
			// endregion

			/*if ((opFlag & OP_DEL) != 0) {
				if (!(cur instanceof LoadExpression)) throw wr.err("delete.non_deletable:"+cur);
				if (!((LoadExpression) cur).setDeletion()) throw wr.err("delete.unable_variable");
			}*/

			// region 二元运算符 | 三元运算符 | 终结符
			w = wr.next();
			switch (w.type()) {
				case Word.EOF: ue(ctx, "eof"); break;
				case ask: break;
				case colon:
					if ((flag & STOP_COLON) == 0) ue(ctx, w.val());
					wr.retractWord();
					break; // :
				case right_m_bracket:
					if ((flag & STOP_RMB) == 0) ue(ctx, w.val());
					if ((flag & SKIP_RMB) == 0) wr.retractWord();
					break; // ]
				case right_l_bracket:
					if ((flag & STOP_RLB) == 0) ue(ctx, w.val());
					wr.retractWord();
					break; // }
				case comma:
					if ((flag & SKIP_COMMA) == 0) wr.retractWord();
					break; // ,
				case right_s_bracket:
					if ((flag & STOP_RSB) == 0) ue(ctx, w.val());
					if ((flag & SKIP_RSB) == 0) wr.retractWord();
					break; // )
				case semicolon:
					if ((flag & STOP_SEMICOLON) == 0) ue(ctx, w.val());
					wr.retractWord();
					break; // ;

				default:
					if (JavaLexer.isBinaryOperator(w.type())) {
						if (up == null) tmp.add(cur);
						ordered.put(tmp.size(), symbolPriority(w));
						tmp.add(BinaryOpr.get(w.type()));

						w = wr.next();
					}
					continue;
			}
			// endregion

			if (cur != null) {
				if (up == null) tmp.add(cur);
			}
			break;
		}

		Int2IntMap tokens = ordered;

		ExprNode cur = null;
		if (!tokens.isEmpty()) {
			List<Int2IntMap.Entry> sort = this.sort;

			sort.addAll(Helpers.cast(tokens.selfEntrySet()));
			sort.sort(sorter);

			for (int i = 0, e = sort.size(); i < e; i++) {
				if (i > 0) {
					int lv = sort.get(i - 1).getIntKey();
					for (int j = i; j < e; j++) {
						Int2IntMap.Entry v = sort.get(j);
						if (v.getIntKey() > lv) {
							v._SetKey(v.getIntKey()-2);
						}
					}
				}

				int v = sort.get(i).getIntKey();

				ExprNode l = tmp.get(v-1), op, r;
				try {
					op = tmp.remove(v);
					r = tmp.remove(v);
				} catch (IndexOutOfBoundsException e1) {
					throw wr.err("missing_operand");
				}

				cur = new Binary(((BinaryOpr) op).op, l, r);
				tmp.set(v-1, cur.resolve());
			}

			((Binary) cur).setTarget(ifFalse);
			cur = cur.resolve();

			tokens.clear();
			sort.clear();
		} else {
			cur = tmp.isEmpty() ? null : tmp.get(0).resolve();
		}

		tmp.clear();

		// 优先级也不低
		// if ("5"+3 instanceof String ? "5"+3 instanceof String : "5"+3 instanceof String);
		if (w.type() == INSTANCEOF) {
			// no generic
			IType targetType = ctx.resolveType(0);
			cur = new InstanceOf(targetType.rawType(), cur);
		}

		if (w.type() == comma && (flag&STOP_COMMA) == 0) {
			Chained cd = new Chained();
			cd.append(cur);
			cur = cd;

			boolean hasComma = false;

			while (true) {
				ExprNode expr = parse(ctx, flag|STOP_COMMA, null);
				if (expr != null) {
					hasComma = false;
					cd.append(expr);
				}

				w = wr.next();
				switch (w.type()) {
					case comma:
						if (hasComma) ue(ctx, w.val());
						hasComma = true;
						continue;
					case semicolon:
						if (!hasComma) {
							wr.retractWord();
							break;
						}
					default: ue(ctx, w.val(), ";"); continue;
				}
				break;
			}
		}

		// 这也是终结. 但是优先级最高
		if (w.type() == ask) {
			if (cur == null) ue(ctx, w.val(), "type.object");
			ExprNode middle = parse(ctx, flag|STOP_COLON, null);
			if (middle == null) ue(ctx, "empty.trinary");
			wr.except(colon, ":");
			ExprNode right = parse(ctx, flag, null);
			if (right == null) ue(ctx, "empty.trinary");
			cur = new Trinary(cur, middle, right);
		}

		return cur;
	}

	private static DotGet _dot(ExprNode e, String name, int flag) { return e instanceof DotGet ? ((DotGet) e).add(name, flag) : new DotGet(e, name, flag); }
	private Method _invoke(CompileUnit ctx, JavaLexer wr, ExprNode e) throws ParseException {
		List<ExprNode> args = Helpers.cast(sort); args.clear();

		while (true) {
			ExprNode expr = parse(ctx, STOP_RSB|STOP_COMMA|SKIP_COMMA|ALLOW_SPREAD|_ENV_INVOKE, null);
			if (expr == null) {
				wr.except(right_s_bracket, ")");
				break;
			}
			args.add(expr);
		}

		Method m = new Method(e, args.isEmpty() ? Collections.emptyList() : new SimpleList<>(args));
		args.clear();
		return m;
	}

	private void handleLambda(CompileUnit ctx, JavaLexer wr, List<String> id) {

	}

	public void reset() {
		this.words.clear();
		this.sort.clear();
		this.ordered.clear();
	}

	private static void ue(CompileUnit ctx, String wd, String except) throws ParseException { throw ctx.lex().err("unexpected_2:"+wd+':'+except); }
	private static void ue(CompileUnit ctx, String wd) throws ParseException { throw ctx.lex().err("unexpected:"+wd); }

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