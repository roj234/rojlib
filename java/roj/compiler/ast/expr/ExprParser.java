package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.Int2IntMap;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.compiler.CompilerSpec;
import roj.compiler.JavaLexer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.GenericPrimer;
import roj.compiler.ast.NaE;
import roj.compiler.ast.VariableDeclare;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.Word;
import roj.config.auto.Serializer;
import roj.config.auto.Serializers;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static roj.compiler.JavaLexer.*;

/**
 * @author Roj233
 * @since 2024/01/22 13:51
 */
public final class ExprParser {
	public static int debug__counter_typeMatchSuccess, debug__counter_typeMatchFailed;

	private UnresolvedExprNode pendNode;




	private final SimpleList<ExprNode> words = new SimpleList<>();

	private final SimpleList<?> tmp0 = new SimpleList<>();
	private <T> List<T> tmp() {
		if (depth > 0) return new SimpleList<>();
		var t = tmp0; t.clear(); return Helpers.cast(t);
	}

	private final SimpleList<BinaryOp> binaryOps = new SimpleList<>();
	private static final class BinaryOp {
		final int pos;
		final int priority;
		BinaryOp(int pos, int priority) {
			this.pos = pos;
			this.priority = priority;
		}
	}
	private static final Comparator<BinaryOp> OPR_SORT = (a, b) -> Integer.compare(b.priority, a.priority);

	private int depth;
	public ExprParser() {}

	public static final int
		STOP_COMMA = 1, SKIP_COMMA = 2,
		STOP_SEMICOLON = 4, SKIP_SEMICOLON = 32768,
		STOP_COLON = 8,
		STOP_RSB = 16, SKIP_RSB = 32,
		STOP_RLB = 64,
		STOP_RMB = 128, SKIP_RMB = 256,
		_ENV_FIELD = 1024,
		_ENV_INVOKE = 2048,
		_ENV_TYPED_ARRAY = 4096,
		STOP_LAMBDA = 8192,
		_ENV_EASYMAP = 16384,
		CHECK_VARIABLE_DECLARE = 32768,
		NAE = 65536;
	static final int OP_OPTIONAL = 1;

	// com.sun.tools.javac.parser.JavacParser LINE 1742
	private static final MyBitSet CAST_WHITELIST = MyBitSet.from(
		logic_not,rev,
		lParen,THIS,SUPER,
		Word.INTEGER,Word.LONG, Word.FLOAT,Word.DOUBLE,CHARACTER,Word.STRING,
		TRUE,FALSE,NULL,
		NEW,Word.LITERAL,ASSERT,ENUM,SWITCH,
		BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,BOOLEAN,VOID);

	public static final int
		SM_UnaryPreOnce = 0 << 29, SM_UnaryPreMany = 1 << 29, SM_ExprStart = 2 << 29, SM_ExprNext = 3 << 29,
		SM_UserContinue = 4 << 29, SM_UserTerminate = 5 << 29, SM_UserExprGen = 6 << 29;
	public Int2IntMap st = DST;
	public List<Object> custom;

	public static final Int2IntMap DST = new Int2IntMap();
	static {
		DST.putInt(SM_UnaryPreOnce | inc, -1);
		DST.putInt(SM_UnaryPreOnce | dec, -1);

		DST.putInt(SM_UnaryPreMany | add, -1);
		DST.putInt(SM_UnaryPreMany | sub, -1);
		DST.putInt(SM_UnaryPreMany | logic_not, -1);
		DST.putInt(SM_UnaryPreMany | rev, -1);
		DST.putInt(SM_UnaryPreMany | lParen, -2);
		DST.putInt(SM_UnaryPreMany | Word.LITERAL, -3);

		DST.putInt(SM_ExprStart | NEW, -1);
		DST.putInt(SM_ExprStart | CHARACTER, -2);
		DST.putInt(SM_ExprStart | Word.STRING, -3);
		DST.putInt(SM_ExprStart | Word.INTEGER, -4);
		DST.putInt(SM_ExprStart | Word.LONG, -5);
		DST.putInt(SM_ExprStart | Word.FLOAT, -6);
		DST.putInt(SM_ExprStart | Word.DOUBLE, -7);
		DST.putInt(SM_ExprStart | TRUE, -8);
		DST.putInt(SM_ExprStart | FALSE, -9);
		DST.putInt(SM_ExprStart | NULL, -10);
		DST.putInt(SM_ExprStart | SUPER, -11);
		DST.putInt(SM_ExprStart | THIS, -12);
		DST.putInt(SM_ExprStart | lBrace, -13);
		DST.putInt(SM_ExprStart | lBracket, -14);

		DST.putInt(SM_ExprNext | lss, -1);
		DST.putInt(SM_ExprNext | lBracket, -2);
		DST.putInt(SM_ExprNext | dot, -3);
		DST.putInt(SM_ExprNext | optional_chaining, -3);
		DST.putInt(SM_ExprNext | THIS, -4);
		DST.putInt(SM_ExprNext | SUPER, -4);
		DST.putInt(SM_ExprNext | CLASS, -5);
		DST.putInt(SM_ExprNext | Word.LITERAL, -5);
		//batch(STAGE_MASK_ExpC, -5, TRUE, FALSE, NULL, VOID, INT, LONG, DOUBLE, FLOAT, SHORT, BYTE, CHAR, BOOLEAN);
		DST.putInt(SM_ExprNext | lParen, -6);
		batch(SM_ExprNext, -7, assign, add_assign, sub_assign, mul_assign, div_assign, mod_assign, pow_assign, lsh_assign, rsh_assign, rsh_unsigned_assign, and_assign, or_assign, xor_assign);
		DST.putInt(SM_ExprNext | inc, -8);
		DST.putInt(SM_ExprNext | dec, -8);
		DST.putInt(SM_ExprNext | method_referent, -9);
	}
	private static void batch(int mask, int val, short... tokens) {
		for (short token : tokens) DST.putInt(token|mask, val);
	}

	@Deprecated
	public ExprNode enumHelper(CompileUnit file, IType type) throws ParseException { return invoke(file, type, null); }
	public UnresolvedExprNode parse(CompileUnit file, int flag) throws ParseException {
		depth = -1;
		words.clear();
		binaryOps.clear();
		tmp0.clear();

		UnresolvedExprNode node = parse1(file, flag);
		if (node == null) {
			node = pendNode;
			pendNode = null;

			if (node == null && (flag&NAE) != 0) {
				node = NaE.INSTANCE;
				file.report(Kind.ERROR, "noExpression");
			}
		}
		return node;
	}
	@Nullable
	@SuppressWarnings({"fallthrough", "unchecked"})
	private ExprNode parse1(CompileUnit file, int flag) throws ParseException {
		JavaLexer wr = file.getLexer();
		Word w;

		if (++depth > 127) throw wr.err("expr.stackOverflow");

		SimpleList<ExprNode> words = this.words;
		int opsOff = binaryOps.size();
		int wordsOff = words.size();

		while (true) {
			UnaryPreNode up = null;
			ExprNode cur = null;

			w = wr.next();
			// region 一次性前缀操作 (++ --)
			int _sid = st.getOrDefaultInt(w.type()|SM_UnaryPreOnce, 0);
			if (_sid != 0) {
				if (_sid < 0) {
					up = unaryPre(w.type());
				} else {
					up = ((Function<JavaLexer, UnaryPreNode>) custom.get(_sid)).apply(wr);
				}
				words.add(up);
				w = wr.next();
			}
			// endregion
			endValueConvNrt:{
			endValueConv:{
			endValueGen:{
			// region 可重复前缀操作 (+ - ! ~ 类型转换) 和 检测lambda
			loop:
			while (true) {
				UnaryPreNode pf;
				switch (_sid = st.getOrDefaultInt(w.type()|SM_UnaryPreMany, 0)) {
					case -1: //add, sub, logic_not, rev
						pf = unaryPre(w.type());
						w = wr.next();
					break;
					case -2: //lParen
						// 模板: (a,b,c.....) -> {}
						wr.mark();
						List<String> names = tmp();
						while (true) {
							w = wr.next();
							if (w.type() != Word.LITERAL) break;

							names.add(w.val());

							w = wr.next();
							if (w.type() != comma) {
								if (w.type() == rParen && wr.next().type() == lambda) {
									// lambda is a terminal operator
									// [cast] <lambda> [invoke]
									wr.skip();
									cur = lambda(file, wr, names);
									break endValueConv;
								}
								break;
							}
						}
						names.clear();

						wr.reset();
						IType type = file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC|CompileUnit.TYPE_OPTIONAL);

						if (type == null || wr.next().type() != rParen || !CAST_WHITELIST.contains((w = wr.next()).type())) {
							debug__counter_typeMatchFailed++;
							wr.retract();
							cur = parse1(file, STOP_RSB|SKIP_RSB);
							if (cur == null) file.report(Kind.ERROR, "expr.cast.lambda");
							w = wr.next();
							break endValueGen;
						}

						debug__counter_typeMatchSuccess++;
						wr.skip();
						pf = cast(type);
					break;
					case -3: //LITERAL
						String name = w.val();
						wr.mark();
						if (wr.next().type() == lambda) {
							wr.skip();
							cur = lambda(file, wr, Collections.singletonList(name));
							break endValueConv;
						}

						wr.retract();
					break loop;
					case -4: // [Dynamic]UserExprGen
						cur = ((Function<JavaLexer, ExprNode>) custom.get(st.getOrDefaultInt(w.type()|SM_UserExprGen, -1))).apply(wr);
					break endValueConv;
					default:
						if (_sid <= 0) break loop;
						pf = ((Function<JavaLexer, UnaryPreNode>) custom.get(_sid)).apply(wr);
				}

				if (up == null) words.add(pf);
				else up.setRight(pf);
				up = pf;
			}
			// endregion
			// region 一次性"值生成"(自造词)操作 (加载常量 new this 花括号(direct)数组内容)
			switch (_sid = st.getOrDefaultInt(w.type()|SM_ExprStart, 0)) {
				case -1://NEW
					// double[]的部分不受支持
					// new <double[]>test<int[]>(new int[0], (Object) assign2op((short) 2));
					// test.<int[]>b();
					// String[] a = new test<int[]>(null, null).<String[]>a(new int[3]);
					IType newType = file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC|CompileUnit.TYPE_LEVEL2);

					w = wr.next();

					// String[][] array = new String[][0] {};
					// 从零开始，有数字则继续，至[]结束，往后均为null
					// 若有容量，则不能手动指定内容
					if (w.type() == lBracket) {
						List<ExprNode> args = tmp();
						int array = 1;
						arrayDef: {
							while (true) {
								ExprNode expr = parse1(file, STOP_RMB|SKIP_RMB);
								if (expr == null) break; // END OF COUNTED DEFINITION
								args.add(expr);

								w = wr.next();
								if (w.type() == lBracket) array++;
								else break arrayDef; // END OF ARRAY DEFINITION
							}

							while (true) {
								w = wr.next();
								if (w.type() != lBracket) break;

								wr.except(rBracket);
								array++;
							}
						}

						newType.setArrayDim(array);
						if (newType instanceof GenericPrimer && ((GenericPrimer) newType).isGenericArray()) {
							file.report(Kind.ERROR, "expr.newArray.generic");
						}

						if (!args.isEmpty()) {
							cur = newArrayDef(newType, copyOf(args), true);
						} else {
							if (w.type() != lBrace) throw wr.err("expr.newArray.unInit");
							cur = newArray(file, newType);
							w = wr.next();
						}
					} else if (w.type() == lParen) {
						cur = invoke(file, newType, null);
						w = wr.next();
						if (w.type() == lBrace) {
							var _type = file.newAnonymousClass(LocalContext.get().method);
							cur = new NewAnonymousClass((Invoke) cur, _type);
						}
					} else {
						// 语法糖: new n => 无参数调用
						cur = newInvoke(newType, Collections.emptyList());
					}
				break endValueGen;
				// constant
				case -2: cur = new Constant(Type.std(Type.CHAR), AnnVal.valueOf(w.val().charAt(0))); break;
				case -3: cur = Constant.valueOf(w.val()); break;
				case -4: cur = new Constant(Type.std(Type.INT), AnnVal.valueOf(w.asInt())); break;
				case -5: cur = new Constant(Type.std(Type.LONG), AnnVal.valueOf(w.asLong())); break;
				case -6: cur = new Constant(Type.std(Type.FLOAT), AnnVal.valueOf(w.asFloat())); break;
				case -7: cur = new Constant(Type.std(Type.DOUBLE), AnnVal.valueOf(w.asDouble())); break;
				case -8: cur = Constant.valueOf(true); break;
				case -9: cur = Constant.valueOf(false); break;
				case -10: cur = new Constant(Asterisk.anyType, null); break;
				// this
				case -11: cur = Super(); break;
				case -12: cur = This(); break;
				// lBrace (define unknown array)
				case -13:
					//noinspection TextLabelInSwitchStatement
					check_param_map:
					if ((flag & _ENV_INVOKE) != 0) {
						// invoke_env:
						// a.b({xxx: yyy})

						wr.mark();
						w = wr.next();
						String firstName = w.val();
						if (w.type() != Word.LITERAL || wr.next().type() != colon) {
							wr.retract();
							break check_param_map;
						}
						wr.skip();

						var npl = new NamedParamList();
						while (true) {
							ExprNode val = parse1(file, STOP_RLB|STOP_COMMA|SKIP_COMMA);
							if (val == null) file.report(Kind.ERROR, "expr.namedCall.noExpr");
							if (!npl.add(firstName, val))
								file.report(Kind.ERROR, "expr.invoke.paramName", firstName);

							w = wr.next();
							if (w.type() == rBrace) break;
							else if (w.type() != Word.LITERAL) ue(wr, "type.literal");
							firstName = w.val();
							wr.except(colon);
						}
						cur = npl;
						break endValueConv;
					}

					if ((flag & _ENV_TYPED_ARRAY) != 0) {
						cur = newArray(file, null);
						break endValueConv;
					}

					// 可以直接写json，好像没啥用，先不加了
					// { "key" => "val" } like this
					// [t => 3, async => 4, testVal => [ ref => 1, tar => 2 ]];
					throw wr.err("expr.newArray.noTypePresent");
				case -14://lBracket
					//if ((flag & _ENV_FIELD) == 0) wr.unexpected(w.val());

					ExprNode key;
					//noinspection TextLabelInSwitchStatement
					checkEasyMap: {
					var easyMap = new EasyMap();
					// 形如 [ exprkey => exprval, ... ] 的直接Map<K, V>构建
					do {
						key = parse1(file, _ENV_EASYMAP|STOP_COMMA|SKIP_COMMA);
						if (key == null) file.report(Kind.ERROR, "expr.easyMap.noKey");
						if (w.type() != mapkv) {
							if (w.type() == comma && easyMap.map.isEmpty()) break checkEasyMap;
							else ue(wr, w.val(), "expr.easyMap.kvOrRet");
						}
						ExprNode val = parse1(file, _ENV_FIELD|STOP_RMB|STOP_COMMA);
						if (val == null) file.report(Kind.ERROR, "expr.easyMap.noValue");

						easyMap.map.put(key, val);
						w = wr.next();
					} while (w.type() != rBracket);
					cur = easyMap;
					break;
					}

					List<ExprNode> args = tmp();
					args.add(key);

					while (w.type() == comma) {
						args.add(parse1(file, STOP_RMB|STOP_COMMA|SKIP_COMMA|NAE));
					}

					wr.except(rBracket, "]");
					cur = new MultiReturn(copyOf(args));
					break;
				default:
					if (_sid == 0) break endValueGen;
					cur = ((Function<JavaLexer, ExprNode>) custom.get(_sid)).apply(wr);
				break;
			}
			w = wr.next();
			// endregion
			}
			// region 重复性"值生成" (变量|字段访问 数组获取 函数调用) 和 值终止 (赋值运算符 后缀自增/自减 方法引用lambda)
			boolean curIsObj = cur != null;
			int opFlag = 0;
			while (true) {
				switch (st.getOrDefaultInt(w.type()|SM_ExprNext, 0)) {
					case -1://lss
						// 作为操作符
						if (curIsObj) {
							// 是第一个子表达式
							if (up == null && words.size() == 0 && cur instanceof DotGet dot && dot.parent == null && (flag & CHECK_VARIABLE_DECLARE) != 0) {
								wr.retractWord();
								wr.mark();
								IType type = file._genericUse(dot.toClassRef().owner, 5);
								if (type != null) {
									Word w2 = wr.next();
									// TYPE<GENERIC> + LITERAL
									if (w2.type() == Word.LITERAL) {
										wr.skip();
										pendNode = new VariableDeclare(type, w2.val());
										return null;
									}
								}
								wr.retract();
							}
							break endValueConvNrt;
						}

						// 方法的泛型边界
						// must be at least one
						// Helpers.<Helpers>nonnull().<int[]>nonnull();
						List<IType> bounds = tmp();
						do {
							bounds.add(file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC));
							w = wr.next();
						} while (w.type() != gtr);
						bounds = copyOf(bounds);

						cur = chain(cur, wr.except(Word.LITERAL).val(), (opFlag&OP_OPTIONAL));
						opFlag = 0;
						wr.except(lParen);
						cur = invoke(file, cur, bounds);
					break;
					case -2: {//lBracket a[b]
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						ExprNode index = parse1(file, STOP_RMB|SKIP_RMB);
						if (index == null) chain(cur, ";[", 0); // 用于.class
						else cur = newArrayGet(cur, index);
					}
					break;
					case -3://dot, optional_chaining
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						curIsObj = false;
						if (w.type() == optional_chaining) opFlag |= OP_OPTIONAL;
						else opFlag &= ~OP_OPTIONAL;
					break;
					case -4://THIS, SUPER
						if (curIsObj) ue(wr, w.val(), ".");
						curIsObj = true;
						cur = newEncloseRef(w.type() == THIS, _classRef(cur, wr, w));
					break;
					case -5:
						// .class => TERMINATOR
						// LITERAL => a.b

						if (curIsObj) {
							if ((flag&CHECK_VARIABLE_DECLARE) != 0) {
								// 是第一个子表达式
								if (up == null && words.size() == 0 && cur instanceof DotGet dot && dot.parent == null) {
									assert wordsOff == 0 : "must be outside of any other expression";
									// TYPE + LITERAL
									pendNode = new VariableDeclare(dot.toClassRef(), w.val());
									return null;
								}
							}

							if (file.ctx().isSpecEnabled(CompilerSpec.KOTLIN_SEMICOLON)) {
								wr.retractWord();
								break endValueConv;
							}

							ue(wr, w.val(), ".");
						}
						curIsObj = true;
						if (w.type() == CLASS) {
							cur = Constant.classRef(_classRef(cur, wr, w));
							break;
						}
						cur = chain(cur, w.val(), (opFlag&OP_OPTIONAL));
						opFlag = 0;
					break;
					case -6: //lParen a(b...)
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						cur = invoke(file, cur, null);
					break;
					case -10: // 自定义继续
						_sid = st.getOrDefaultInt(w.type() | SM_UserContinue, -1);
						cur = ((BiFunction<JavaLexer, ExprNode, ExprNode>) custom.get(_sid)).apply(wr, cur);
					break;

					// 我是无敌可爱的分隔线

					case -11: // 自定义终止
						_sid = st.getOrDefaultInt(w.type() | SM_UserTerminate, -1);
						cur = ((BiFunction<JavaLexer, ExprNode, ExprNode>) custom.get(_sid)).apply(wr, cur);
					break endValueConv;

					case 0: // 其他token
						if (cur != null && !curIsObj) ue(wr, w.val(), "type.literal");
					break endValueConvNrt;

					case -7: { //assign及其变种
						if (!(cur instanceof VarNode vn)) throw wr.err("expr.assign.left");

						short vtype = w.type();

						ExprNode right = parse1(file, flag & (~CHECK_VARIABLE_DECLARE) | STOP_COMMA);
						if (right == null) throw wr.err("expr.assign.right");

						cur = newAssign(vn, vtype == assign ? right : binary((short) (add + (vtype - add_assign)), cur, right));
					}
					break endValueConv;
					case -8: // inc, dec
						if (!(cur instanceof VarNode)) throw wr.err("expr.unary.notVariable:".concat(w.val()));
						cur = newUnaryPost(w.type(), cur);
					break endValueConv;
					case -9: //method_referent this::stop
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						cur = new Lambda(cur, wr.except(Word.LITERAL).val());
					break endValueConv;
				}
				w = wr.next();
			}
			// endregion
			}
			w = wr.next();
			}
			// 总结-在valueConv之外的终止: lambda lambdaB methodReference namedParamList 嵌套数组定义(省略new array[])

			// 应用前缀算符
			if (up != null) {
				String code = up.setRight(cur);
				if (code != null) file.report(Kind.ERROR, code);
			} else {
				// Nullable
				words.add(cur);
			}

			// region 二元运算符 | 三元运算符 | 终结符
			switch (w.type()) {
				case INSTANCEOF: case ask: break;

				case colon:
					if ((flag & STOP_COLON) == 0) ue(wr, w.val());
					wr.retractWord();
				break; // :
				case rBracket:
					if ((flag & STOP_RMB) == 0) ue(wr, w.val());
					if ((flag & SKIP_RMB) == 0) wr.retractWord();
				break; // ]
				case rBrace:
					if ((flag & STOP_RLB) == 0) ue(wr, w.val());
					wr.retractWord();
				break; // }
				case comma:
					if ((flag & SKIP_COMMA) == 0) wr.retractWord();
				break; // ,
				case rParen:
					if ((flag & STOP_RSB) == 0) ue(wr, w.val());
					if ((flag & SKIP_RSB) == 0) wr.retractWord();
				break; // )
				case semicolon:
					if ((flag & STOP_SEMICOLON) == 0) ue(wr, w.val());
					if ((flag & SKIP_SEMICOLON) == 0) wr.retractWord();
				break; // ;
				case mapkv:
					if ((flag & _ENV_EASYMAP) == 0) ue(wr, w.val());
				break; // =>
				default:
					int priority = binaryOperatorPriority(w.type());
					if (priority >= 0) {
						// 非法的表达式开始
						if (cur == null) throw wr.err("noExpression");

						binaryOps.add(new BinaryOp(words.size(), priority));
						words.add(new Binary(w.type()));
						continue;
					} else if (file.ctx().isSpecEnabled(CompilerSpec.KOTLIN_SEMICOLON) && words.size() > wordsOff) {
						wr.retractWord();
						break;
					}
				case Word.EOF: ue(wr, w.val());
			}
			// endregion

			break;
		}

		// region 表达式优先级
		ExprNode cur;
		SimpleList<BinaryOp> ops = binaryOps;
		if (ops.size() > opsOff) {
			ops.sort(opsOff, ops.size(), OPR_SORT);

			int i = opsOff;
			do {
				int v = ops.get(i).pos;

				Binary op = (Binary) words.get(v);

				op.left = words.set(v-1, op);
				op.right = words.set(v+1, op);
				if (op.right == null) throw wr.err("expr.unary.noOperand:".concat(byId(op.operator)));

				cur = op;
			} while (++i != ops.size());

			ops.removeRange(opsOff, ops.size());
		} else {
			assert words.size() == wordsOff+1;
			// 必定不是空的
			cur = words.getLast();
		}
		words.removeRange(wordsOff, words.size());
		// endregion

		// 优先级也不低
		// if ("5"+3 instanceof String ? "5"+3 instanceof String : "5"+3 instanceof String);
		if (w.type() == INSTANCEOF) {
			IType targetType = file.readType(CompileUnit.TYPE_GENERIC);
			w = wr.next();
			String variable;
			if (w.type() == Word.LITERAL) {
				variable = w.val();
			} else {
				wr.retractWord();
				variable = null;
			}
			cur = newInstanceOf(targetType.rawType(), cur, variable);
		}

		if (w.type() == comma && (flag&STOP_COMMA) == 0) {
			List<ExprNode> args = tmp();
			args.add(cur);

			boolean hasComma = false;

			while (true) {
				ExprNode expr = parse1(file, flag|STOP_COMMA);
				if (expr != null) {
					hasComma = false;
					args.add(expr);
				}

				w = wr.next();
				switch (w.type()) {
					case comma:
						if (hasComma) ue(wr, w.val());
						hasComma = true;
						continue;
					case semicolon:
						if (!hasComma) {
							wr.retractWord();
							break;
						}
					default: ue(wr, w.val(), ";"); continue;
				}
				break;
			}
			cur = new Chained(copyOf(args));
		}

		// 这也是终结. 但是优先级最高
		if (w.type() == ask) {
			if (cur == null) throw wr.err("noExpression");
			ExprNode middle = parse1(file, flag|STOP_COLON);
			if (middle == null) throw wr.err("expr.trinary.noMiddle");
			wr.except(colon, ":");
			ExprNode right = parse1(file, flag);
			if (right == null) throw wr.err("expr.trinary.noRight");
			cur = newTrinary(cur, middle, right);
		}

		depth--;
		return cur;
	}

	private static Type _classRef(ExprNode cur, JavaLexer wr, Word w) throws ParseException {
		if (!(cur instanceof DotGet)) ue(wr, w.val(), "type.literal");
		DotGet dg = (DotGet) cur;
		if (dg.parent != null) throw wr.err("expr.symbol.refCheck:".concat(dg.parent.toString()));
		return dg.toClassRef();
	}
	public ExprNode chain(ExprNode e, String name, int flag) { return e instanceof DotGet ? ((DotGet) e).add(name, flag) : new DotGet(e, name, flag); }
	private ExprNode invoke(CompileUnit file, Object fn, List<IType> bounds) throws ParseException {
		assert fn instanceof ExprNode || fn instanceof IType : "validation error";

		List<ExprNode> args = tmp();

		while (true) {
			ExprNode expr = parse1(file, STOP_RSB|STOP_COMMA|SKIP_COMMA|_ENV_INVOKE|_ENV_TYPED_ARRAY);
			// noinspection all
			if (expr == null || (args.add(expr) & expr.getClass() == NamedParamList.class)) {
				file.getLexer().except(rParen, ")");
				break;
			}
		}

		Invoke m = newInvoke(fn, args.isEmpty() ? Collections.emptyList() : copyOf(args));
		if (bounds != null) m.setBounds(bounds);
		return m;
	}

	private IType childArrayType;
	private ExprNode newArray(CompileUnit file, IType arrayType) throws ParseException {
		List<ExprNode> args = tmp();
		JavaLexer wr = file.getLexer();

		IType prev = childArrayType;

		updateChild: {
			if (arrayType == null) {
				arrayType = childArrayType;
				if (arrayType == null) {
					file.report(Kind.ERROR, "expr.newArray.noTypePresent");
					break updateChild;
				} else if (arrayType.array() == 0) {
					file.report(Kind.ERROR, "expr.newArray.illegalDimension", arrayType);
					break updateChild;
				}
			}
			childArrayType = arrayType.clone();
			childArrayType.setArrayDim(arrayType.array()-1);
		}

		while (true) {
			ExprNode val = parse1(file, STOP_RLB|STOP_COMMA|SKIP_COMMA|_ENV_TYPED_ARRAY);
			if (val == null) break;
			args.add(val);
		}

		childArrayType = prev;

		wr.except(rBrace);
		return newArrayDef(arrayType, copyOf(args), false);
	}
	private ExprNode lambda(CompileUnit file, JavaLexer wr, List<String> parNames) throws ParseException {
		SimpleList<String> strings = copyOf(parNames);

		Word w = wr.next();
		if (w.type() == lBrace) {
			MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, file.name, "unusedLambdaRef", "()V");
			ParseTask lambdaTask = ParseTask.Method(file, mn, strings);
			return new Lambda(mn, lambdaTask);
		} else {
			wr.retractWord();
			ExprNode expr = parse1(file, STOP_RSB|STOP_COMMA|STOP_SEMICOLON);
			if (expr == null) throw wr.err("noExpression");
			return new Lambda(strings, expr);
		}
	}

	private static void ue(JavaLexer wr, String wd, String except) throws ParseException { throw wr.err("unexpected_2:"+wd+':'+except); }
	private static void ue(JavaLexer wr, String wd) throws ParseException { throw wr.err("unexpected:"+wd); }

	// region cache
	public <T> SimpleList<T> copyOf(List<T> args) {
		SimpleList<T> copy = new SimpleList<>(args);
		args.clear();
		return copy;
	}

	private final This _THIS = new This(true), _SUPER = new This(false);

	public ExprNode This() { return _THIS; }
	public ExprNode Super() { return _SUPER; }
	public UnaryPreNode unaryPre(short type) {return new UnaryPre(type);}
	public UnaryPreNode cast(IType type) {return new Cast(type);}

	public ExprNode newArrayGet(ExprNode array, ExprNode index) {return new ArrayGet(array, index);}
	public ExprNode newArrayDef(IType type, SimpleList<ExprNode> args, boolean sized) {return new ArrayDef(type, args, sized);}
	public ExprNode newAssign(VarNode cur, ExprNode node) {return new Assign(cur, node);}
	public ExprNode newEncloseRef(boolean ThisEnclosing, Type type) { return new EncloseRef(ThisEnclosing, type); }
	public Invoke newInvoke(Object fn, List<ExprNode> pars) {return new Invoke(fn, pars);}
	public ExprNode binary(short op, ExprNode left, ExprNode right) { return new Binary(op, left, right); }
	public ExprNode newUnaryPost(short op, ExprNode prev) { return new UnaryPost(op, prev); }
	public ExprNode newInstanceOf(Type type, ExprNode cur, String variable) {return new InstanceOf(type, cur, variable);}
	public ExprNode newTrinary(ExprNode cur, ExprNode middle, ExprNode right) {return new Trinary(cur, middle, right);}
	// endregion

	public static UnresolvedExprNode deserialize(String string) throws ParseException {return ConfigMaster.JSON.readObject(serializer(), string);}
	public static String serialize(ExprNode node) { return ConfigMaster.JSON.writeObject(node, serializer(), new CharList()).toStringAndFree(); }
	public static void serialize(ExprNode node, CVisitor visitor) { serializer().write(visitor, node); }
	public static Serializer<ExprNode> serializer() { return Serializers.ANY_OBJECT.serializer(ExprNode.class); }
}