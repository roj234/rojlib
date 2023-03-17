package roj.kscript.parser.ast;

import roj.collect.Int2IntMap;
import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.config.word.Word;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.LabelNode;
import roj.kscript.func.KFunction;
import roj.kscript.parser.JSLexer;
import roj.kscript.parser.ParseContext;
import roj.kscript.type.KType;
import roj.kscript.vm.KScriptVM;
import roj.util.Helpers;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static roj.kscript.parser.JSLexer.*;

/**
 * 操作符优先级靠它实现
 *
 * @author Roj233
 * @since 2020/10/13 22:14
 */
public final class ExprParser {
	private final ArrayList<Expression> words = new ArrayList<>();
	private final Int2IntMap ordered = new Int2IntMap();
	private final ArrayList<Int2IntMap.Entry> sort = new ArrayList<>();

	private final int depth;

	static final boolean SORT_CST = System.getProperty("kscript.sortConstant", "true").equalsIgnoreCase("true");
	static final Comparator<Int2IntMap.Entry> sorter = (a, b) -> {
		return Integer.compare(b.v, a.v);
	};

	public static void main(String[] args) throws ParseException, IOException {
		System.out.println("Roj234's ECMAScript Interpreter 2.4\n");

		JSLexer wr = new JSLexer();
		ExprParser epr = new ExprParser(0);

		if (args.length > 0) {
			String expression = String.join(" ", args);

			System.out.print("Output = ");

			wr.init(expression);

			Expression expr = epr.read(() -> wr, (short) 0, null);

			System.out.println(expr);

			System.out.print("AST = ");
			KS_ASM ast = KS_ASM.builder("");
			expr.write(ast, false);
			System.out.println(ast);

			return;
		}

		BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
		String line;
		MyHashMap<String, KType> env = new MyHashMap<>();

		System.out.print(">");
		while ((line = r.readLine()) != null) {
			if (!line.endsWith(";")) line += ';';
			wr.init(line);

			try {
				Expression expr = epr.read(() -> wr, (short) 0, null);

				if (expr == null) {
					System.out.println();
				} else {
					expr.write(KS_ASM.builder(""), false);
					System.out.println("Expr: " + expr);
					System.out.println(expr.compute(env));
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
			System.out.print('>');
		}
	}

	public ExprParser(int depth) {
		this.depth = depth;
	}

	/**
	 * 解析数组定义 <BR>
	 * [xxx, yyy, zzz] or []
	 */
	public Expression defineArray(ParseContext ctx, short flag) throws ParseException {
		JSLexer wr = ctx.getLexer();
		List<Expression> expr = new ArrayList<>();

		boolean hasMore = true;

		Word w;
		o:
		while (true) {
			w = wr.next();
			switch (w.type()) {
				case right_m_bracket: break o;
				case comma:
					if (hasMore) ue(ctx, ",");

					w = wr.next();
					if (w.type() == right_m_bracket) break o;
					wr.retractWord();

					hasMore = true;
					continue;
				default:
					wr.retractWord();
					break;
			}

			if (!hasMore) wr.unexpected(w.val(), "逗号");
			hasMore = false;

			Expression result = read(ctx, (short) 128, null);
			if (result != null) {
				expr.add(result);
			} else {
				if (wr.next().type() != right_m_bracket) {
					ue(ctx, "empty_statement");
				}
				break;
			}
		}

		return expr.isEmpty() ? ArrayDef.EMPTY : expr.get(expr.size() - 1) instanceof Spread ? new ArrayDefSpreaded(expr) : new ArrayDef(expr);
	}

	/**
	 * 解析对象定义 <BR>
	 * {xxx: yyy, zzz: uuu}
	 */
	public Expression defineObject(ParseContext ctx, short flag) throws ParseException {
		JSLexer wr = ctx.getLexer();
		MyHashMap<String, Expression> map = new MyHashMap<>();

		boolean hasMore = true;

        /*
          Map map = new Map();
          map.put(LoadData name, Expr xxx);
          ...
         */
		Word w;
		o:
		while (true) {
			w = wr.next();
			switch (w.type()) {
				case right_l_bracket: break o;
				case comma:
					if (hasMore) ue(ctx, ",");
					hasMore = true;
					continue;
				case Word.STRING:
				case Word.LITERAL: break;
				default: ue(ctx, w.val(), "type.string");
			}

			if (!hasMore) wr.unexpected(w.val(), "逗号");
			hasMore = false;

			String name = w.val();
			wr.except(colon, ":");

			Expression result = read(ctx, (short) 64, null);

			if (result != null) {
				map.put(name, result);
			} else {
				ue(ctx, "empty_statement");
			}
		}

		return map.isEmpty() ? ObjectDef.EMPTY : new ObjectDef(map);
	}

	/**
	 * @see #read(ParseContext, short, LabelNode)
	 */
	@Nullable
	public Expression read(ParseContext ctx, short exprFlag) throws ParseException {
		return read(ctx, exprFlag, null);
	}

	/**
	 * Expression parser(表达式解析器) <BR>
	 * 逻辑烦死人 <BR>
	 * 但是做好了之后感觉我太NB了! <BR>
	 *
	 * @param exprFlag <BR>
	 * 8   : in 解析数组索引 <BR>
	 * 16  : in 解析函数调用参数 <BR>
	 * 32  : in 解析if中的内容(为了压缩) <BR>
	 * 64  : in 定义对象 {xxx} <BR>
	 * 128 : in 定义数组 <BR>
	 * 256 : in 解析括号优先 <BR>
	 * 512 : in 三元运算符 - ? <BR>
	 * 1024: in for <BR>
	 * 2048: 逗号连接模式 <BR>
	 * 4096: 右值 <BR>
	 *
	 * @throws ParseException if error occurs.
	 */
	@SuppressWarnings("fallthrough")
	@Nullable
	public Expression read(ParseContext ctx, short exprFlag, LabelNode ifFalse) throws ParseException {
		ArrayList<Expression> tmp = words;
		if (!tmp.isEmpty() || !ordered.isEmpty() || !sort.isEmpty()) // 使用中
		{return KScriptVM.retainExprParser(depth + 1).read(ctx, exprFlag, null);}

		JSLexer wr = ctx.getLexer();

		Expression cur = null;
		// 这TM其实是个链表
		UnaryPrefix pf = null, pfTop = null;

		Word w;

		/**
		 *    1   : 有'.' <BR>
		 *    2   : 当前是对象类型 <BR>
		 *    4   : 当前是基本类型 <BR>
		 *    8   : new <BR>
		 *    16  : delete <BR>
		 *    32  : 逗号连接模式 <BR>
		 */
		int opFlag = 0;

		wr.disableSignedNumber();

		o:
		while (true) {
			w = wr.next();
			switch (w.type()) {
				case spread: // ES6 扩展运算符
					if ((exprFlag & 144) == 0) { // 128 / 16
						ue(ctx, w.val());
					}
					if (cur != null) // require empty
					{ue(ctx, w.val());}
					Spread spread = new Spread(read(ctx, (short) (exprFlag | 4096), ifFalse));
					w = wr.next();
					if ((exprFlag & 128) != 0) { // array
						if (!w.val().equals("]")) throw wr.err("spread.last_of_array", w);
					}
					wr.retractWord();
					return spread;
				case lambda:
				case FUNCTION: {
					if (cur != null || (opFlag != 0)) ue(ctx, w.val(), "empty");
					KFunction fn = ctx.parseInnerFunc(w.type());
					if (fn == null) throw wr.err("fn_error");
					cur = Constant.valueOf(fn);
				}
				break o;
				case DELETE:
					if (cur != null || (opFlag != 0)) ue(ctx, w.val(), "empty");
					opFlag |= 16;
					break;
				case NEW:
					if (cur != null || (opFlag != 0)) ue(ctx, w.val(), "empty");
					opFlag |= 8;
					break;
				// [ x ]
				case left_m_bracket: {// array start
					if ((opFlag & 5) != 0) ue(ctx, "[");

					if (cur == null) {
						if ((opFlag & 16) != 0) ue(ctx, "[");

						// define array
						cur = defineArray(ctx, exprFlag);
						opFlag |= 2;
					} else {
						if ((opFlag & 2) == 0) ue(ctx, "[");
						// get array
						Expression index = read(ctx, (short) 8, null);
						if (index == null) {
							ue(ctx, "empty.array_index");
						}

						cur = new ArrayGet(cur, index);
					}
				}
				break;
				// a.b.c.d
				case Word.LITERAL: {
					if (cur == null) {
						cur = new Variable(w.val());
						cur.mark_spec_op(ctx, 1);
					} else {
						// not first

						if ((opFlag & 4) != 0) {
							ue(ctx, w.val());
						}
						if ((opFlag & 1) == 0) ue(ctx, w.val(), ".");
						opFlag &= ~1;
						cur = new Field(cur, w.val());
					}
					opFlag |= 2;
				}
				break;
				// this
				case THIS: {
					if (cur == null) {
						cur = Std.STD1;
						opFlag |= 2;
					} else {ue(ctx, "this");}
				}
				break;
				case ARGUMENTS: {
					if (cur == null) {
						cur = Std.STD2;
						opFlag |= 2;
					} else {ue(ctx, "arguments");}
				}
				break;
				// constant
				//case Keyword.HEX:       // 十六进制
				//case Keyword.BINARY:    // 二进制
				//case Keyword.OCTAL:     // 八进制
				case Word.INTEGER: { // 整数
					if (cur != null) {ue(ctx, w.val(), "type.get_able");} else {
						cur = Constant.valueOf(w);
						opFlag |= 4;
					}
				}
				break;
				case Word.CHARACTER:
				case Word.STRING:
				case Word.DOUBLE:
				case Word.FLOAT:
				case TRUE:
				case NULL:
				case UNDEFINED:
				case NAN:
				case INFINITY:
				case FALSE:
					if (cur != null) {ue(ctx, w.val(), "type.get_able");} else {
						cur = Constant.valueOf(w);
						// string
						switch (cur.type()) {
							case 2:
							case -1:
								opFlag |= 2;
								break;
							default:
								opFlag |= 4;
								break;
						}
					}
					break;
				case colon:
					if ((exprFlag & 1536) == 0) ue(ctx, w.val());
					if ((exprFlag & 1024) != 0) wr.retractWord();
					// ? :
					break o;
				// assign
				case assign:
					// has 2 and not has 16: opFlag & 18 != 2
					if (cur == null || (opFlag & 18) != 2) ue(ctx, w.val());

					// Mark assign
					cur.mark_spec_op(ctx, 2);

					Expression right = read(ctx, (short) (exprFlag | 4096), null);

					if (right == null) {
						throw wr.err("empty.right_value");
					}

					if (!(cur instanceof LoadExpression)) throw wr.err("invalid_left_value");
					cur = new Assign((LoadExpression) cur, right);
					break;
				case add_assign:
				case div_assign:
				case and_assign:
				case lsh_assign:
				case mod_assign:
				case mul_assign:
				case rsh_assign:
				case rsh_unsigned_assign:
				case sub_assign:
				case xor_assign:
					// a &= 3;
					// =>
					// a = a & 3;

					if (cur == null || (opFlag & 18) != 2) ue(ctx, w.val());

					// Mark assign-op
					cur.mark_spec_op(ctx, 3);

					short vtype = w.type();

					Expression expr = read(ctx, (short) (exprFlag | 4096), null);
					if (expr == null) {
						throw wr.err("empty.right_value");
					}

					if (!(cur instanceof LoadExpression)) throw wr.err("invalid_left_value");
					cur = new Assign((LoadExpression) cur, new Binary(assign2op(vtype), cur, expr));
					break;
				case dot: {
					if ((opFlag & 1) == 0) {
						opFlag |= 1;
						opFlag &= ~2;
					} else {ue(ctx, ".");}
				}
				break;
				case left_l_bracket:
					if (cur != null/* || (opFlag & 1) != 0*/) // at beginning
					{ue(ctx, "{");}
					cur = defineObject(ctx, exprFlag);
					opFlag |= 2;
					break;

				// ( x )
				case left_s_bracket:
					// 1, (2), 16
					//if ((opFlag & 19) != 2)
					//    err(ctx, "(");
					if ((opFlag & 17) != 0) ue(ctx, "(");

					// bracket
					if (cur == null) {
						cur = read(ctx, (short) 256, null);
						if (cur == null) throw wr.err("empty.bracket");
					} else {
						if ((opFlag & 2) == 0) ue(ctx, "(");
						// function call
						List<Expression> args = Helpers.cast(this.sort);
						args.clear();

						while (true) {
							Expression e1 = read(ctx, (short) 16, null);
							if (e1 != null) {
								args.add(e1);
							} else {
								w = wr.next();
								if (w.type() != right_s_bracket) {
									ue(ctx, w.val(), ")");
								}
								break;
							}
						}

						cur = new Method(cur, args.isEmpty() ? Collections.emptyList() : new ArrayList<>(args), (opFlag & 8) != 0);
						args.clear();

						opFlag &= ~8;
					}
					break;
				default:
					if (isKeyword(w)) {
						ue(ctx, w.val(), "type.expr");
					}
					// 'Clean' parameters pass to Delete expr
					// maybe && ?
					if ((opFlag & 16) == 0 & isSymbol(w)) {
						switch (symbolOperateCount(w.type())) {
							case 1:
								// logic_not inc dec rev

								boolean iod;
								switch (w.type()) {
									case inc:
									case dec:
										iod = true;
										break;
									default:
										iod = false;
										break;
								}

								if (cur != null) {
									// i++
									if (iod) {
										if (!(cur instanceof Field) && !(cur instanceof ArrayGet)) {
											throw wr.err("unary.expecting_variable");
										}

										cur = new UnaryAppendix(w.type(), cur);
										continue;
									} else {
										ue(ctx, w.val());
									}
								}

								// ++i

								UnaryPrefix ul = new UnaryPrefix(w.type());
								if (pf != null) {
									String code = pf.setRight(ul);
									if (code != null) throw wr.err(code);
								} else {
									pfTop = ul;
								}
								pf = ul;

								break;
							case 2:
								if (cur != null) { // has right value
									if (pfTop != null) {

										String code = pf.setRight(cur);
										if (code != null) throw wr.err(code);

										tmp.add(pfTop);
										pf = pfTop = null;
									} else {
										tmp.add(cur);
									}
									wr.enableSignedNumber();
									cur = null;
									opFlag = 0;
								} else {
									switch (w.type()) {
										case add:
										case sub: {
											UnaryPrefix ul1 = new UnaryPrefix(w.type());
											if (pf != null) {
												String code = pf.setRight(ul1);
												if (code != null) throw wr.err(code);
											} else {
												pfTop = ul1;
											}
											pf = ul1;
										}
										continue;
										default:
											ue(ctx, w.val(), "type.get_able");
									}
								}

								ordered.put(tmp.size(), symbolPriority(w));
								tmp.add(SymTmp.retain(w.type()));
								break;
							case 3:
								// ? :
								if (cur == null) {
									ue(ctx, w.val(), "type.get_able");
								}
								Expression mid = read(ctx, (short) 512, null);
								if (mid == null) {
									ue(ctx, "empty.illegal");
								}
								Expression r = read(ctx, (short) (exprFlag | 4096), null);
								if (r == null) {
									ue(ctx, "empty.illegal");
								}
								tmp.add(new TripleIf(cur, mid, r));
								break;
							case 0:
								ue(ctx, w.val());
								break;
						}
					} else {
						ue(ctx, w.val());
					}
					break;
				case Word.EOF:
					ue(ctx, "eof");
					break o; // useless
				case right_m_bracket:
					if ((exprFlag & 136) == 0) { // 128 / 8
						ue(ctx, "]");
					} else {
						if ((exprFlag & 128) != 0) // define array
						{wr.retractWord();}
						break o;
					}
					break;
				case right_l_bracket:
					if ((exprFlag & 64) == 0) {ue(ctx, "}");} else {
						wr.retractWord();
						break o;
					}
					break;
				case comma:
					if ((exprFlag & 6616) == 0) { // 8 / 16 / 64 / 128 / 256 / 2048 / 4096
						if (exprFlag != 0) // skip normal tag
						{ue(ctx, ",");}

						opFlag |= 32;
					} else {
						// 16 / 2048
						if ((exprFlag & 2056) != 0) // is !!NOT[function]CURRENTLY / array index / comma-link
						{wr.retractWord();}
					}
					break o;
				case right_s_bracket:
					if ((exprFlag & 272) == 0) // 16 / 256
					{ue(ctx, ")");} else {
						if ((exprFlag & 16) != 0) // is function
						{wr.retractWord();}
						break o;
					}
					break;
				case semicolon:
					break o;
			}
		}
		wr.enableSignedNumber();

		Int2IntMap tokens = ordered;

		if (cur != null) {
			if (pf != null) {
				String code = pf.setRight(cur);
				if (code != null) throw wr.err(code);

				cur = pfTop;
			}

			if ((opFlag & 16) != 0) {
				if (!tokens.isEmpty()) throw new RuntimeException("Error: A coding error!!! delete param should be clean access of variables such as a.b, this.a or a[some ? 'a' : 'b']: " + ordered);
				if (!(cur instanceof LoadExpression)) throw wr.err("delete.non_deletable:" + cur);
				if (!((LoadExpression) cur).setDeletion()) throw wr.err("delete.unable_variable");

				opFlag &= ~16;
			}

			tmp.add(cur);
		} else if (pf != null) {
			throw wr.err("unary.missing_operand");
		}

		if (w.type() == semicolon) {
			if ((opFlag & 8) != 0) {
				// 语法糖: 无参数调用
				if (cur == null) ue(ctx, ";", "type.variable");
				tmp.set(tmp.size() - 1, new Method(cur, Collections.emptyList(), true));
			}

			if ((opFlag & 16) != 0) {
				ue(ctx, ";", "type.variable");
			}
			if ((exprFlag & 59391) != 0) // 65535 - 2048 - 4096
			{ue(ctx, ";", description(exprFlag));}
			wr.retractWord();
		}

		Expression result = null;
		if (!tokens.isEmpty()) {
			List<Int2IntMap.Entry> sort = this.sort;

			for (int i = 0; i < tmp.size(); i++) {
				tmp.set(i, tmp.get(i).compress()); // pre-compress
			}

			sort.addAll(Helpers.cast(tokens.selfEntrySet()));
			sort.sort(sorter);

			for (int i = 0, e = sort.size(); i < e; i++) {
				if (i > 0) {
					int lv = sort.get(i - 1).getIntKey();
					for (int j = i; j < e; j++) {
						Int2IntMap.Entry v = sort.get(j);
						if (v.getIntKey() > lv) {
							v.Internal_Set_Key(v.getIntKey() - 2);
						}
					}
				}

				int v = sort.get(i).getIntKey();

				Expression l = tmp.get(v - 1), op, r;
				try {
					op = tmp.remove(v);
					r = tmp.remove(v);
				} catch (IndexOutOfBoundsException e1) {
					throw wr.err("binary.missing_operand");
				}

				result = new Binary(((SymTmp) op).operator, l, r);
				tmp.set(v - 1, result);
			}

			((Binary) result).setTarget(ifFalse);
			result = result.compress();

			tokens.clear();
			sort.clear();
		} else {
			result = tmp.isEmpty() ? null : tmp.get(0).compress();
		}

		tmp.clear();

		if ((opFlag & 32) != 0) {
			Chained cd = new Chained();
			cd.append(result);
			result = cd;

			boolean lastComma = false;

			cyl:
			while (true) {
				Expression e1 = read(ctx, (short) 2048, null);
				if (e1 != null) {
					lastComma = false;
					cd.append(e1);
				}
				switch ((w = wr.next()).type()) {
					case comma:
						if (lastComma) {
							ue(ctx, w.val());
						}
						lastComma = true;
						break;
					case semicolon:
						if (!lastComma) break cyl;
					default:
						ue(ctx, w.val(), ";");
						break;
				}
			}
		}

		return result;
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
			case add_assign:
				return add;
			case div_assign:
				return div;
			case and_assign:
				return and;
			case lsh_assign:
				return lsh;
			case mod_assign:
				return mod;
			case mul_assign:
				return mul;
			case rsh_assign:
				return rsh;
			case rsh_unsigned_assign:
				return rsh_unsigned;
			case sub_assign:
				return sub;
			case xor_assign:
				return xor;
		}
		throw new IllegalStateException("Unknown assign type: " + type);
	}

	private static void ue(ParseContext context, String word, String expecting) throws ParseException {
		throw context.getLexer().err("unexpected:" + word + ':' + expecting);
	}

	private static void ue(ParseContext context, String s) throws ParseException {
		throw context.getLexer().err("unexpected:" + s + ": ");
	}
}
