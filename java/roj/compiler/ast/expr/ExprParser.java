package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.Int2IntMap;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.LavaFeatures;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.LPGeneric;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.BlockParser;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.VariableDeclare;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugin.LEC;
import roj.compiler.plugin.LEG;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.Word;
import roj.config.auto.Serializer;
import roj.config.auto.SerializerFactory;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.ArrayRef;
import roj.util.Helpers;
import roj.util.TimSortForEveryone;

import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

import static roj.compiler.JavaLexer.*;
import static roj.config.Word.LITERAL;
import static roj.reflect.ReflectionUtils.u;

/**
 * Lava Compiler - 表达式<p>
 * Parser levels: <ol>
 *     <li>{@link CompileUnit Class Parser}</li>
 *     <li>{@link ParseTask Segment Parser}</li>
 *     <li>{@link BlockParser Method Parser}</li>
 *     <li><b><i>Expression Parser</i></b></li>
 * </ol>
 * @version 2.6 (Nested new)
 * @author Roj233
 * @since 2024/01/22 13:51
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

		CHECK_VARIABLE_DECLARE = 2048,
		NAE = 4096,

		_ENV_INVOKE = 8192,
		_ENV_TYPED_ARRAY = 16384;

	static final int OP_OPTIONAL = 1;

	public static final int SM_UnaryPre = 1 << 29, SM_ExprStart = 2 << 29, SM_ExprNext = 3 << 29, SM_ExprTerm = 4 << 29, CU_TerminateFlag = 0x80000000;

	public static Int2IntMap getStateMap() { return SM; }
	public static IntMap<Type> getPrimitiveWords() { return PW; }

	private static final IntMap<Type> PW = new IntMap<>(8);
	private static final Int2IntMap SM = new Int2IntMap(64);
	static {
		String s = "VZBCSIJFD";
		for (int i = 0; i < s.length(); i++) {
			//noinspection MagicConstant
			PW.putInt(VOID+i, Type.std(s.charAt(i)));
		}

		SM.putInt(SM_UnaryPre | inc, -1);
		SM.putInt(SM_UnaryPre | dec, -1);

		SM.putInt(SM_UnaryPre | add, -1);
		SM.putInt(SM_UnaryPre | sub, -5);
		SM.putInt(SM_UnaryPre | logic_not, -1);
		SM.putInt(SM_UnaryPre | inv, -1);
		SM.putInt(SM_UnaryPre | lParen, -2);
		SM.putInt(SM_UnaryPre | Word.LITERAL, -3);
		SM.putInt(SM_UnaryPre | SWITCH, -4);

		SM.putInt(SM_ExprStart | NEW, -1);
		SM.putInt(SM_ExprStart | CHARACTER, -2);
		SM.putInt(SM_ExprStart | Word.STRING, -3);
		SM.putInt(SM_ExprStart | Word.INTEGER, -4);
		SM.putInt(SM_ExprStart | Word.LONG, -5);
		SM.putInt(SM_ExprStart | Word.FLOAT, -6);
		SM.putInt(SM_ExprStart | Word.DOUBLE, -7);
		SM.putInt(SM_ExprStart | TRUE, -8);
		SM.putInt(SM_ExprStart | FALSE, -9);
		SM.putInt(SM_ExprStart | NULL, -10);
		SM.putInt(SM_ExprStart | JavaLexer.SUPER, -11);
		SM.putInt(SM_ExprStart | JavaLexer.THIS, -12);
		SM.putInt(SM_ExprStart | lBrace, -13);
		SM.putInt(SM_ExprStart | lBracket, -14);
		batch(SM_ExprStart, -15, /*VOID, */BOOLEAN, BYTE, CHAR, SHORT, INT, LONG, FLOAT, DOUBLE);
		SM.putInt(SM_ExprStart | LITERAL, -16);
		SM.putInt(SM_ExprStart | INT_MIN_VALUE, -17);
		SM.putInt(SM_ExprStart | LONG_MIN_VALUE, -17);

		SM.putInt(SM_ExprNext | lss, -1);
		SM.putInt(SM_ExprNext | lBracket, -2);
		SM.putInt(SM_ExprNext | optional_chaining, -3);
		SM.putInt(SM_ExprNext | dot, -4);
		SM.putInt(SM_ExprNext | Word.LITERAL, -5);
		SM.putInt(SM_ExprNext | lParen, -6);
		SM.putInt(SM_ExprNext | assign, -7);
		for (int i = binary_assign_base_offset; i < binary_assign_base_offset+binary_assign_count; i++) {
			SM.putInt(SM_ExprNext | i, -7);
		}
		SM.putInt(SM_ExprNext | inc, -8);
		SM.putInt(SM_ExprNext | dec, -8);
		SM.putInt(SM_ExprNext | method_referent, -9);
		SM.putInt(SM_ExprNext | Word.STRING, -10);
		SM.putInt(SM_ExprNext | NEW, -11);

		// Spec
		SM.putInt(SM_ExprTerm | INSTANCEOF, -1);
		SM.putInt(SM_ExprTerm | ask, -1);

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
		// 20250111 按照“正确”的优先级重新排序它们，但是并未改变val
		SM.putInt(SM_ExprTerm | pow, 8);

		SM.putInt(SM_ExprTerm | mul, 7);
		SM.putInt(SM_ExprTerm | div, 7);
		SM.putInt(SM_ExprTerm | rem, 7);

		SM.putInt(SM_ExprTerm | add, 6);
		SM.putInt(SM_ExprTerm | sub, 6);

		SM.putInt(SM_ExprTerm | shl, 9);
		SM.putInt(SM_ExprTerm | shr, 9);
		SM.putInt(SM_ExprTerm | ushr, 9);

		SM.putInt(SM_ExprTerm | lss, 5);
		SM.putInt(SM_ExprTerm | gtr, 5);
		SM.putInt(SM_ExprTerm | geq, 5);
		SM.putInt(SM_ExprTerm | leq, 5);
		SM.putInt(SM_ExprTerm | equ, 5);
		SM.putInt(SM_ExprTerm | neq, 5);

		SM.putInt(SM_ExprTerm | and, 10);
		SM.putInt(SM_ExprTerm | or, 10);
		SM.putInt(SM_ExprTerm | xor, 10);

		SM.putInt(SM_ExprTerm | nullish_coalescing, 4);
		SM.putInt(SM_ExprTerm | logic_and, 4);
		SM.putInt(SM_ExprTerm | logic_or, 4);
	}
	private static void batch(int mask, int val, short... tokens) {
		for (short token : tokens) SM.putInt(token|mask, val);
	}

	private final LocalContext ctx;

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

	private int depth = -1;
	public Int2IntMap sm = SM;
	public List<Object> custom;

	public ExprParser(LocalContext ctx) {this.ctx = ctx;}

	public UnresolvedExprNode parse(int flag) throws ParseException {
		hasNullExpr = false;
		UnresolvedExprNode node;
		try {
			node = parse1(flag);
		} catch (ParseException e) {
			depth = -1;
			nodes.clear();
			binaryOps.clear();
			tmp0.clear();
			throw e;
		}
		return node;
	}
	@Nullable
	@SuppressWarnings({"fallthrough", "unchecked"})
	private ExprNode parse1(int flag) throws ParseException {
		var wr = ctx.lexer;
		Word w;

		if (++depth > 127) throw wr.err("expr.stackOverflow");

		var nodes = this.nodes;
		int bopCount = binaryOps.size();
		int nodeCount = nodes.size();

		while (true) {
			UnaryPreNode up = null;
			ExprNode cur = null;
			int _sid;

			w = wr.next();
			endValueConvNrt:{
			endValueConv:{
			endValueGen:{
			// region 前缀操作 (++ -- + - ! ~ 类型转换) 和 检测lambda 和 switch表达式
			// 一次性前缀操作(++ --)的判断在setRight中
			loop:
			while (true) {
				UnaryPreNode pf;
				switch (_sid = sm.getOrDefaultInt(w.type()|SM_UnaryPre, 0)) {
					case -5: //dec
						w = wr.next();
						if (w.type() == INT_MIN_VALUE) {
							w = wr.next();
							cur = Constant.valueOf(AnnVal.valueOf(Integer.MIN_VALUE));
							break endValueGen;
						} else if (w.type() == LONG_MIN_VALUE) {
							w = wr.next();
							cur = Constant.valueOf(AnnVal.valueOf(Long.MIN_VALUE));
							break endValueGen;
						} else {
							wr.retractWord();
							pf = unaryPre(sub);
							break;
						}
					case -1: //inc, add, sub, logic_not, rev
						pf = unaryPre(w.type());
					break;
					case -2: //lParen
						int startOff = w.pos();

						wr.mark();
						var o = detectParen(wr);
						if (o == null) {// 表达式
							wr.retract();

							cur = parse1(STOP_RSB|SKIP_RSB);
							if (cur == null) ctx.report(Kind.ERROR, "expr.lambda.illegal");
							w = wr.next();
							break endValueGen;
						} else if (o instanceof IType type) {// 类型转换
							wr.except(rParen);

							pf = cast(type);
							pf.wordStart = startOff;
						} else {// lambda 【(a,b,c.....) -> {}】 [terminal operator]
							// [cast] <lambda> [invoke]
							cur = lambda(wr, (List<String>) o);
							break endValueConv;
						}
					break;
					case -3: //LITERAL
						if ((flag&STOP_LAMBDA) == 0) {
							w = w.copy(); // no more singleton
							if (wr.nextIf(lambda)) {
								cur = lambda(wr, Collections.singletonList(w.val()));
								break endValueConv;
							}
						}
					break loop;
					case -4: //switch
						cur = new Switch(ctx.bp.parseSwitch(true));
					break endValueConv;

					case 0: break loop;
					default: // 自定义前缀 | 自定义完整表达式
						int alt_sid = _sid & ~CU_TerminateFlag;
						if (_sid != alt_sid) {
							cur = ((LEG) custom.get(alt_sid)).generate(wr, ctx);
							break endValueConv;
						}

						pf = ((BiFunction<JavaLexer, UnaryPreNode, UnaryPreNode>) custom.get(alt_sid)).apply(wr, up);
				}
				w = wr.next();

				if (up == null) nodes.add(pf);
				else {
					var code = up.setRight(pf);
					if (code != null) ctx.report(Kind.ERROR, code);
				}
				up = pf;
			}
			// endregion
			// region 一次性"值生成"(自造词)操作 (加载常量 new this 花括号(direct)数组内容 int[].class String.class)
			switch (_sid = sm.getOrDefaultInt(w.type()|SM_ExprStart, 0)) {
				case -1://NEW
					// double[]的部分不受支持
					// new <double[]>test<int[]>(new int[0], (Object) assign2op((short) 2));
					// test.<int[]>b();
					// String[] a = new test<int[]>(null, null).<String[]>a(new int[3]);
					int start = w.pos();
					IType newType = ctx.file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC|CompileUnit.TYPE_NO_ARRAY);

					w = wr.next();

					// String[][] array = new String[][0] {};
					// 从零开始，有数字则继续，至[]结束，往后均为null
					// 若有容量，则不能手动指定内容
					if (w.type() == lBracket) {
						List<ExprNode> args = tmp();
						int array = 1;
						arrayDef: {
							while (true) {
								ExprNode expr = parse1(STOP_RMB|SKIP_RMB);
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

						newType = newType.clone();
						newType.setArrayDim(array);
						// 以后可以把这个检查删了 CompileSpec
						if (newType instanceof LPGeneric && ((LPGeneric) newType).isGenericArray()) {
							ctx.report(Kind.ERROR, "expr.newArray.generic");
						}

						if (!args.isEmpty()) {
							cur = newArrayDef(newType, copyOf(args), true);
						} else {
							if (w.type() != lBrace) throw wr.err("expr.newArray.unInit");
							cur = newArray(newType);
							w = wr.next();
						}
					} else if (w.type() == lParen) {
						cur = invoke(newType, null);
						w = wr.next();
						if (w.type() == lBrace) {
							wr.retractWord();
							wr.state = STATE_CLASS;
							var _type = ctx.file.newAnonymousClass(ctx.method);
							cur = newAnonymousClass(cur, _type);
							wr.state = STATE_EXPR;

							w = wr.next();
						}
					} else {
						// 语法糖: new n => 无参数调用
						cur = newInvoke(newType, Collections.emptyList());
					}
					cur.wordStart = start;
				break endValueGen;
				// constant
				case -2: cur = new Constant(Type.std(Type.CHAR), AnnVal.valueOf(w.val().charAt(0))); break;
				case -3: cur = Constant.valueOf(w.val()); break;
				case -4: cur = Constant.valueOf(w.asInt()); break;
				case -5: cur = new Constant(Type.std(Type.LONG), AnnVal.valueOf(w.asLong())); break;
				case -6: cur = new Constant(Type.std(Type.FLOAT), AnnVal.valueOf(w.asFloat())); break;
				case -7: cur = new Constant(Type.std(Type.DOUBLE), AnnVal.valueOf(w.asDouble())); break;
				case -8: cur = Constant.valueOf(true); break;
				case -9: cur = Constant.valueOf(false); break;
				case -10: cur = new Constant(Asterisk.anyType, null); break;
				// MIN_VALUE_NUMBER_LITERAL
				case -17: LocalContext.get().report(Kind.ERROR, "lexer.number.overflow"); break;
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
							ExprNode val = parse1(STOP_RLB|STOP_COMMA|SKIP_COMMA|NAE);
							if (!npl.add(firstName, val))
								ctx.report(Kind.ERROR, "expr.invoke.paramName", firstName);

							w = wr.next();
							if (w.type() != Word.LITERAL) break;
							firstName = w.val();
							wr.except(colon);
						}
						if (w.type() != rBrace) ue(wr, "type.literal");
						break endValueConv;
					}

					if ((flag & _ENV_TYPED_ARRAY) != 0) {
						cur = newArray(null);
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
					// 形如 [ exprkey -> exprval, ... ] 的直接Map<K, V>构建
					do {
						key = parse1(STOP_LAMBDA|STOP_COMMA|SKIP_COMMA|NAE);
						if (w.type() != lambda) {
							if (w.type() == comma && easyMap.map.isEmpty()) break checkEasyMap;
							else ue(wr, w.val(), "expr.easyMap.kvOrRet");
						}
						ExprNode val = parse1(STOP_RMB|STOP_COMMA|NAE);

						easyMap.map.put(key, val);
						w = wr.next();
					} while (w.type() != rBracket);
					cur = easyMap;
					break;
					}

					List<ExprNode> args = tmp();
					args.add(key);

					while (w.type() == comma) {
						args.add(parse1(STOP_RMB|STOP_COMMA|SKIP_COMMA|NAE));
					}

					wr.except(rBracket, "]");
					cur = new EasyList(copyOf(args));
				break;
				case -15://primitive type ref
					Type typeRef = PW.get(w.type());
					assert typeRef != null;

					cur = _typeRef(wr.next(), typeRef, flag);
					if (cur == null) ctx.report(Kind.ERROR, "expr.typeRef.illegal");
					else if (cur.getClass() == VariableDeclare.class) return cur;
				break;
				case -16://type ref
					while (true) {
						cur = chain(cur, w.val(), 0);

						w = wr.next();
						if (w.type() != dot) {
							if (w.type() == lBracket) {
								// some[ ].class
								// some[ ] variable
								// some[ index] ...
								typeRef = ((DotGet) cur).toClassRef();
								var tmp = _typeRef(w = w.copy(), typeRef, flag);
								if (tmp != null) {
									if (tmp.getClass() == VariableDeclare.class) return tmp;
									cur = tmp;
									break;
								}
							} else if (nodes.isEmpty() && (flag&CHECK_VARIABLE_DECLARE) != 0) {
								if (w.type() == LITERAL) {
									// some  variable
									return new VariableDeclare(((DotGet) cur).toClassRef(), w.val());
								} else if (w.type() == lss) {
									// some< generic type> variable;
									w = w.copy();
									wr.state = STATE_TYPE;
									boolean b = detectGeneric(wr);
									wr.state = STATE_EXPR;
									wr.retract();

									if (b) {
										IType type = ctx.file.genericTypePart(((DotGet) cur).toClassRef().owner);

										w = wr.next();
										if (w.type() != LITERAL) throw wr.err("未预料的[类型]");
										return new VariableDeclare(type, w.val());
									}
								}
							}

							break endValueGen;
						}

						w = wr.next();
						if (w.type() != LITERAL) {
							if (w.type() == CLASS) {
								cur = Constant.classRef(((DotGet) cur).toClassRef());
								break;
							} else if (w.type() == JavaLexer.THIS || w.type() == JavaLexer.SUPER) {
								cur = newEncloseRef(w.type() == JavaLexer.THIS, ((DotGet) cur).toClassRef());
								break;
							}
							_sid = 0;
							break endValueGen;
						}
					}
				break;
				case 0: break endValueGen;
				default:
					cur = ((LEG) custom.get(_sid)).generate(wr, ctx);
				break;
			}
			w = wr.next();
			// endregion
			}//END endValueGen
			// region 重复性"值生成" (变量|字段访问 数组获取 函数调用) 和 值终止 (赋值运算符 后缀自增/自减 方法引用lambda)
			boolean waitDot = cur != null && _sid != 0;
			int opFlag = 0;
			while (true) {
				switch (_sid = sm.getOrDefaultInt(w.type()|SM_ExprNext, 0)) {
					case -1://lss
						// '小于'操作符
						if (waitDot) break endValueConvNrt;

						if (cur == null) ctx.report(Kind.ERROR, "noExpression");

						// 方法的泛型边界
						// 不能是<>
						// Helpers.<Helpers>nonnull().<int[]>nonnull();
						List<IType> bounds = tmp();
						do {
							bounds.add(ctx.file.readType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC));
							w = wr.next();
						} while (w.type() != gtr);
						bounds = copyOf(bounds);

						cur = chain(cur, wr.except(Word.LITERAL).val(), (opFlag&OP_OPTIONAL));
						opFlag = 0;
						wr.except(lParen);
						cur = invoke(cur, bounds);

						waitDot = true;
					break;
					case -2://lBracket a[b]
						if (!waitDot) ue(wr, w.val(), "type.literal");
						var index = parse1(STOP_RMB|SKIP_RMB);
						if (index == null) chain(cur, ";[", 0); // 无意义，完全可以在这里报错
						else cur = newArrayGet(cur, index);
					break;
					case -3://optional_chaining
						opFlag |= OP_OPTIONAL;
					//fallthrough
					case -4://dot
						if (!waitDot) ue(wr, w.val(), "type.literal");
						waitDot = false;
					break;
					case -5://LITERAL => a.b
						if (!waitDot) {
							waitDot = true;
							cur = chain(cur, w.val(), (opFlag&OP_OPTIONAL));
							opFlag = 0;
							break;
						}

						if (ctx.classes.hasFeature(LavaFeatures.OPTIONAL_SEMICOLON) && cur != null) {
							wr.retractWord();
							break endValueConv;
						}

						ue(wr, w.val(), ".");
					break;
					case -6://lParen a(b...)
						if (!waitDot) ue(wr, w.val(), "type.literal");
						cur = invoke(cur, null);
					break;
					case -11://xxx.new SomeClass(...)
						if (waitDot) ue(wr, w.val(), ".");
						String className = wr.except(LITERAL, "cu.name").val();
						wr.except(lParen);
						cur = invoke(new Type(className), cur).setEnclosingArg();
					break endValueConv;

					// 我是无敌可爱的分隔线

					case -10://字符串格式化 FMT. ""
						if (cur instanceof DotGet dg && dg.maybeStringTemplate()) {
							//if (waitDot) ue(wr, w.val(), ".");
							cur = new StringFormat(dg, w.val());
							break endValueConv;
						}
					case 0://其他token
						if (cur != null && !waitDot) ue(wr, w.val(), "type.literal");
					break endValueConvNrt;

					case -7: {//assign及其变种
						if (!(cur instanceof VarNode vn)) {
							ctx.report(Kind.ERROR, "expr.notVariable", cur);
							break endValueConv;
						}

						short vtype = w.type();

						ExprNode right = parse1(flag & ~(CHECK_VARIABLE_DECLARE|SKIP_SEMICOLON|SKIP_COMMA|SKIP_RMB|SKIP_RSB) | STOP_COMMA | NAE);
						cur = newAssign(vn, vtype == assign ? right : binary((short) (vtype + binary_assign_delta), cur, right));
					}
					break endValueConv;
					case -8://inc, dec
						if (!(cur instanceof VarNode vn)) ctx.report(Kind.ERROR, "expr.notVariable", cur);
						else cur = newUnaryPost(w.type(), vn);
					break endValueConv;
					case -9://method_referent this::stop
						if (!waitDot) ue(wr, w.val(), "type.literal");
						cur = new Lambda(chain(cur, wr.except(Word.LITERAL).val(), 0));
					break endValueConv;

					// 我是无敌可爱的分隔线

					default: // 自定义继续 | 自定义终止
						int alt_sid = _sid & ~CU_TerminateFlag;

						cur = ((LEC) custom.get(alt_sid)).generate(wr, cur, ctx);

						if (_sid == alt_sid) break;
						else break endValueConv;
				}
				w = wr.next();
			}
			// endregion
			}//END endValueConv
			w = wr.next();
			}//END endValueConvNrt
			// 总结-在valueConv之外的终止: lambda lambdaB methodReference namedParamList 嵌套数组定义(省略new array[])

			// 应用前缀算符
			if (up != null) {
				var code = up.setRight(cur);
				if (code != null) ctx.report(Kind.ERROR, code);
			} else {
				// Nullable
				nodes.add(cur);
			}

			// 二元运算符 | 三元运算符 | 终结符
			_sid = sm.getOrDefaultInt(SM_ExprTerm | w.type(), 0);
			if (_sid == 0) {
				if (ctx.classes.hasFeature(LavaFeatures.OPTIONAL_SEMICOLON) && cur != null) {
					wr.retractWord();
					break;
				}

				ue(wr, w.val());
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
					if (ctx.classes.hasFeature(LavaFeatures.OPTIONAL_SEMICOLON) && cur != null) {
						wr.retractWord();
						break;
					}

					ue(wr, w.val());
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
		if (w.type() == INSTANCEOF) {
			checkNullExpr(cur);

			IType targetType = ctx.file.readType(CompileUnit.TYPE_GENERIC);
			String variable = wr.nextIf(LITERAL) ? w.val() : null;
			cur = newInstanceOf(targetType.rawType(), cur, variable);
		}

		if (w.type() == comma) {
			if ((flag & STOP_COMMA) == 0) {
				checkNullExpr(cur);

				List<ExprNode> args = tmp();
				args.add(cur);

				int start = wr.current().pos();

				do {
					args.add(parse1(flag|STOP_COMMA|SKIP_COMMA|NAE));
				} while (w.type() == comma);
				if (w.type() != semicolon) ue(wr, w.val(), ";");

				cur = new Chained(copyOf(args));
				cur.wordStart = start;
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
			cur = newTrinary(cur, middle, right);
		}

		if (hasNullExpr) cur = null;

		if (cur == null && (flag&NAE) != 0) {
			if (!hasNullExpr) ctx.report(Kind.ERROR, "noExpression");
			cur = NaE.NOEXPR;
		}

		depth--;
		return cur;
	}

	private boolean hasNullExpr;
	private void checkNullExpr(ExprNode cur) {
		if (cur == null && !hasNullExpr) {
			ctx.report(Kind.ERROR, "noExpression");
			hasNullExpr = true;
		}
	}

	@Nullable
	private ExprNode _typeRef(Word w, Type type, int flag) throws ParseException {
		var wr = ctx.lexer;

		int array = 0;
		if (w.type() == lBracket) {
			if (!wr.nextIf(rBracket)) return null;
			array++;

			while ((w = wr.next()).type() == lBracket) {
				wr.except(rBracket);
				array++;
			}

			type = type.clone();
			type.setArrayDim(array);
		}

		// 变量定义
		if (w.type() == LITERAL && nodes.isEmpty() && (flag&CHECK_VARIABLE_DECLARE) != 0) {
			// int [] variable
			return new VariableDeclare(type, w.val());
		}

		// int [] . class
		if (w.type() == dot && wr.nextIf(CLASS)) return Constant.classRef(type);

		if (array != 0) ctx.report(Kind.ERROR, "expr.typeRef.arrayDecl");
		return null;
	}
	private boolean detectGeneric(JavaLexer wr) throws ParseException {
		wr.mark();
		int depth = 1;
		// T<
		while (true) {
			var w = wr.next();
			if (w.type() == ask) return true;

			if (w.type() == LITERAL) {
				while (true) {
					w = wr.next();
					if (w.type() != dot) break;
					w = wr.next();
					if (w.type() != LITERAL) return false;
				}

				// A<T>.X<T>
				if (w.type() == lss) {
					depth++;
					continue;
				}
			} else if (w.type() < VOID || w.type() > DOUBLE) return false;
			else w = wr.next();

			while (w.type() == lBracket) {
				w = wr.next();
				if (w.type() != rBracket) return false;
				w = wr.next();
			}

			if (w.type() != comma) {
				if (w.type() != gtr) return false;

				do {
					if (--depth == 0) return true;
					w = wr.next();
				} while (w.type() == gtr);
				wr.retractWord();
			}
		}
	}
	private Object detectParen(JavaLexer wr) throws ParseException {
		Word w = wr.next();
		if (w.type() >= VOID && w.type() <= DOUBLE) {
			wr.retractWord();
			wr.skip();
			return ctx.file.readType(CompileUnit.TYPE_PRIMITIVE);
		}
		if (w.type() != Word.LITERAL) {
			// () -> ...
			if (w.type() == rParen) {
				if (wr.next().type() == lambda) {
					wr.skip();
					return Collections.emptyList();
				} else {
					ctx.report(Kind.ERROR, "expr.lambda.pattern");
				}
			}
			return null;
		}

		String first = w.val();

		var sb = LocalContext.get().getTmpSb();
		sb.append(first);

		wr.state = STATE_TYPE;
		notLambda:{
			try {
				while (true) {
					w = wr.next();
					if (w.type() != dot) {
						// 如果是逗号，那么要么是表达式，要么是lambda
						// (a, b.c ...
						if (w.type() == comma) break;
						break notLambda;
					}
					w = wr.next();
					// often .class
					if (w.type() != LITERAL) return null;

					sb.append('/').append(w.val());
				}
			} finally {
				wr.state = STATE_EXPR;
			}

			List<String> names = tmp();
			names.add(first);

			while (true) {
				w = wr.next();
				if (w.type() != LITERAL) return null;
				names.add(w.val());

				w = wr.next();
				if (w.type() != comma) {
					if (w.type() == rParen) {
						w = wr.next();
						if (w.type() == lambda) {
							wr.skip();
							return names;
						} else {
							ctx.report(Kind.ERROR, "expr.lambda.pattern");
						}
						break;
					}
				}
			}
		}

		if (w.type() != rParen) {
			// 只能是 泛型 或 数组
			// 因为我没有设计 (TYPE NAME [, TYPE NAME]) -> ... 的语法 这很鸡肋
			if (w.type() == lss || w.type() == lBracket) {
				wr.skip();
				return ctx.file.genericTypePart(sb.toString());
			}
			return null;
		}

		// 必须是合法的表达式开始
		int flag = wr.next().type();
		flag = sm.getOrDefaultInt(flag|SM_UnaryPre, 0) | sm.getOrDefaultInt(flag|SM_ExprStart, 0);
		if (flag == 0) return null;

		wr.skip(-2);
		return new Type(sb.toString());
	}

	public ExprNode chain(ExprNode e, String name, int flag) {
		if (e instanceof DotGet d) {
			d.wordEnd = ctx.lexer.index;
			return d.add(name, flag);
		}
		return new DotGet(e, name, flag);
	}
	private Invoke invoke(Object fn, Object arg2) throws ParseException {
		int wordStart = ctx.lexer.current().pos();

		List<ExprNode> args = tmp();
		if (arg2 instanceof ExprNode x) args.add(x);
		while (true) {
			// _ENV_TYPED_ARRAY may not stable
			ExprNode expr = parse1(STOP_RSB|STOP_COMMA|SKIP_COMMA|_ENV_INVOKE|_ENV_TYPED_ARRAY);
			// noinspection all
			if (expr == null || (args.add(expr) & expr.getClass() == NamedParamList.class)) {
				ctx.lexer.except(rParen, ")");
				break;
			}
		}

		var node = newInvoke(fn, args.isEmpty() ? Collections.emptyList() : copyOf(args));
		node.wordStart = wordStart;
		if (arg2 instanceof List) node.setBounds(Helpers.cast(arg2));
		return node;
	}

	private ExprNode newArray(IType arrayType) throws ParseException {
		Word w = ctx.lexer.current();
		int wordStart = w.pos();

		List<ExprNode> args = tmp();
		do {
			var node = parse1(STOP_RLB|STOP_COMMA|SKIP_COMMA|_ENV_TYPED_ARRAY);
			if (node == null) break;
			args.add(node);
		} while (w.type() == comma);
		ctx.lexer.except(rBrace);

		var node = newArrayDef(arrayType, copyOf(args), false);
		node.wordStart = wordStart;
		return node;
	}
	//WIP
	private ExprNode lambda(JavaLexer wr, List<String> parNames) throws ParseException {
		var argNames = copyOf(parNames);

		var file = ctx.file;
		MethodNode mn = new MethodNode(Opcodes.ACC_PRIVATE|(ctx.in_static ? Opcodes.ACC_STATIC : 0)|Opcodes.ACC_SYNTHETIC, file.name, "lambda$", "()V");

		if (wr.nextIf(lBrace)) {
			ParseTask task = ParseTask.Method(file, mn, argNames);
			return new Lambda(argNames, mn, task);
		} else {
			int start = wr.prevIndex;
			int linePos = wr.LN;
			int lineIdx = wr.LNIndex;

			var expr = parse1(STOP_RSB|STOP_COMMA|STOP_SEMICOLON|NAE);
			int end = wr.prevIndex;

			var methodStr = "return "+wr.getText().subSequence(start, end)+";}";
			System.out.println(methodStr);
			ParseTask task = ctx -> {
				var file1 = ctx.file;
				ctx.lexer.init(start, linePos, lineIdx);
				MethodWriter cw = ctx.bp.parseMethod(file1, mn, argNames);

				cw.finish();

				mn.putAttr(new AttrUnknown("Code", cw.bw.toByteArray()));
			};

			return new Lambda(argNames, expr);
		}
	}

	private static void ue(JavaLexer wr, String wd, String except) throws ParseException { throw wr.err("unexpected_2:"+wd+':'+except); }
	private static void ue(JavaLexer wr, String wd) throws ParseException { throw wr.err("unexpected:"+wd); }

	public int binaryOperatorPriority(short op) {return sm.getOrDefaultInt(SM_ExprTerm | op, -1);}

	public <T> List<T> copyOf(List<T> args) {
		if (!(args instanceof SimpleList<T>)) return args;
		SimpleList<T> copy = new SimpleList<>(args);
		args.clear();
		return copy;
	}

	private final This THIS = new This(true), SUPER = new This(false);
	public ExprNode This() {return THIS;}
	public ExprNode Super() {return SUPER;}

	public UnaryPreNode unaryPre(short type) {return new UnaryPre(type);}
	public UnaryPreNode cast(IType type) {return new Cast(type);}

	public ExprNode newArrayGet(ExprNode array, ExprNode index) {return new ArrayGet(array, index);}
	public ExprNode newArrayDef(IType type, List<ExprNode> args, boolean sized) {return new ArrayDef(type, args, sized);}
	public ExprNode newAssign(VarNode cur, ExprNode node) {return new Assign(cur, node);}
	public ExprNode newEncloseRef(boolean ThisEnclosing, Type type) {return new EncloseRef(ThisEnclosing, type);}
	public Invoke newInvoke(Object fn, List<ExprNode> pars) {return new Invoke(fn, pars);}
	public ExprNode binary(short op, ExprNode left, ExprNode right) {return new Binary(op, left, right);}
	public ExprNode newUnaryPost(short op, VarNode prev) {return new UnaryPost(op, prev);}
	public ExprNode newInstanceOf(Type type, ExprNode cur, String variable) {return new InstanceOf(type, cur, variable);}
	public ExprNode newTrinary(ExprNode cur, ExprNode middle, ExprNode right) {return new Trinary(cur, middle, right);}
	public ExprNode newAnonymousClass(ExprNode expr, CompileUnit type) {return new NewAnonymousClass((Invoke) expr, type);}

	public static ExprNode fieldChain(ExprNode parent, IClass begin, IType type, boolean isFinal, FieldNode... chain) {return DotGet.fieldChain(parent, begin, type, isFinal, chain);}

	private static final Serializer<ExprNode> serializer = SerializerFactory.POOLED.serializer(ExprNode.class);
	public static String serialize(ExprNode node) { return ConfigMaster.JSON.writeObject(serializer(), node, new CharList()).toStringAndFree(); }
	public static void serialize(ExprNode node, CVisitor visitor) { serializer().write(visitor, node); }
	public static Serializer<ExprNode> serializer() { return serializer; }

	private final Serializer<ExprNode> deserializer = SerializerFactory.POOLED.serializer(ExprNode.class);
	public UnresolvedExprNode deserialize(String string) throws ParseException {return ConfigMaster.JSON.readObject(deserializer, string);}
}