package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.JavaLexer;
import roj.compiler.asm.GenericPrimer;
import roj.compiler.context.CompileUnit;
import roj.config.ParseException;
import roj.config.word.NotStatementException;
import roj.config.word.Word;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;

import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static roj.compiler.JavaLexer.*;

/**
 * @author Roj233
 * @since 2024/01/22 13:51
 */
public final class ExprParser {
	private final SimpleList<ExprNode> words = new SimpleList<>();

	private final SimpleList<?> tmp0 = new SimpleList<>();
	private <T> List<T> tmp() { var t = tmp0; t.clear(); return Helpers.cast(t); }

	private final SimpleList<BinaryOp> binaryOps = new SimpleList<>();
	private record BinaryOp(int pos, int priority) {}
	private static final Comparator<BinaryOp> OPR_SORT = (a, b) -> Integer.compare(b.priority, a.priority);

	private ExprParser next;
	private ExprParser next() {
		if (next != null) return next;
		ExprParser ep = new ExprParser(depth+1);
		if (depth < 10) next = ep;
		return ep;
	}

	private final int depth;
	public ExprParser(int depth) { this.depth = depth; }

	public static final int
		STOP_COMMA = 1, SKIP_COMMA = 2,
		STOP_SEMICOLON = 4,
		STOP_COLON = 8,
		STOP_RSB = 16, SKIP_RSB = 32,
		STOP_RLB = 64,
		STOP_RMB = 128, SKIP_RMB = 256,
		ALLOW_SPREAD = 512,
		_ENV_FIELD = 1024,
		_ENV_INVOKE = 2048,
		_ENV_TYPED_ARRAY = 4096;
	static final int OP_OPTIONAL = 1;

	private static final MyBitSet CAST_DISALLOW = MyBitSet.from(dot, left_m_br);

	@Nullable
	@SuppressWarnings("fallthrough")
	public ExprNode parse(CompileUnit file, int flag) throws ParseException {
		SimpleList<ExprNode> tmp = words;
		if ((tmp.size()|tmp0.size()|binaryOps.size()) != 0) // 使用中
			return next().parse(file, flag);

		JavaLexer wr = file.lex();

		Word w;
		while (true) {
			UnaryPre up = null;
			ExprNode cur = null;

			w = wr.next();
			// region 一次性前缀操作 (++ --)
			switch (w.type()) {
				case inc: case dec: up = unaryPre(w.type()); tmp.add(up); w = wr.next(); break;
			}
			// endregion
			terminate:{
			// region 可重复前缀操作 (+ - ! ~ 类型转换) 和 检测lambda
			skip:
			while (true) {
				UnaryPre pf;
				switch (w.type()) {
					case add: case sub: case logic_not: case rev:
						pf = unaryPre(w.type());
						w = wr.next();
						break;
					case left_s_br:
						int pos = wr.index;

						// 模板: (a,b,c.....) -> {}
						wr.startBlock();
						List<String> names = tmp();
						while (true) {
							w = wr.readWord();
							if (w.type() != Word.LITERAL) break;
							names.add(w.val());

							w = wr.readWord();
							if (w.type() != comma) {
								if (w.type() == right_s_br && wr.readWord().type() == lambda) {
									// lambda is a terminal operator
									// [cast] <lambda> [invoke]
									wr.endBlock(false);
									cur = _lambda(file, wr, names);
									break terminate;
								}
								break;
							}
						}
						wr.endBlock(true);
						names.clear();

						wr.index = pos;
						IType type = file.resolveType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC|CompileUnit.TYPE_OPTIONAL);

						if (type == null || wr.next().type() != right_s_br || CAST_DISALLOW.contains((w = wr.next()).type())) {
							wr.index = pos;
							cur = parse(file, STOP_RSB|SKIP_RSB);
							if (cur == null) ue(wr, "expr.error.empty.bracket");
							w = wr.next();
							break skip;
						}

						pf = cast(type);
						break;
					case Word.LITERAL:
						wr.retractWord();

						String name = w.val();
						if (wr.readWord().type() == lambda) {
							wr.clearRetract();
							cur = _lambda(file, wr, Collections.singletonList(name));
							break terminate;
						}
						w = wr.next();
					default: break skip;
				}

				if (up == null) tmp.add(pf);
				else up.setRight(pf);
				up = pf;
			}
			// endregion
			// region 一次性"值生成"(自造词)操作 (加载常量 new this 花括号(direct)数组内容)
			// 这里是因为 (expr) 会生成cur
			skip: if (cur == null) {
			switch (w.type()) {
				// 在方法或代码块退出时执行，try-finally的语法糖 TODO move to BlockParser
				//case DEFER:
				case NEW:
					// double[]的部分不受支持
					// new <double[]>test<int[]>(new int[0], (Object) assign2op((short) 2));
					// test.<int[]>b();
					// String[] a = new test<int[]>(null, null).<String[]>a(new int[3]);
					IType newType = file.resolveType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC|CompileUnit.TYPE_LEVEL2);

					w = wr.next();

					// String[][] array = new String[][0] {};
					// 从零开始，有数字则继续，至[]结束，往后均为null
					// 若有容量，则不能手动指定内容
					if (w.type() == left_m_br) {
						List<ExprNode> args = tmp();
						int array = 1;
						arrayDef: {
							while (true) {
								ExprNode expr = parse(file, STOP_RMB|SKIP_RMB);
								if (expr == null) break; // END OF COUNTED DEFINITION
								args.add(expr);

								w = wr.next();
								if (w.type() == left_m_br) array++;
								else break arrayDef; // END OF ARRAY DEFINITION
							}

							while (true) {
								w = wr.next();
								if (w.type() != left_m_br) break;

								wr.except(right_m_br);
								array++;
							}
						}

						newType.setArrayDim(array);
						if (newType instanceof GenericPrimer && ((GenericPrimer) newType).isGenericArray()) {
							file.fireDiagnostic(Diagnostic.Kind.ERROR, "expr.new.array.generic");
						}

						if (!args.isEmpty()) {
							cur = newArrayDef(newType, copyOf(args), true);
						} else {
							if (w.type() != left_l_br) throw wr.err("expr.new.array.no_init");
							cur = _arrayDef(file, newType);
							w = wr.next();
						}
						args.clear();
					} else if (w.type() == left_s_br) {
						cur = _invoke(file, newType, null);
						w = wr.next();
						if (w.type() == left_l_br) {
							throw wr.err("NewAnonymousClass not implemented");
							// FIXME not implemented
							// new Object(xxx) {}
						}
					} else {
						// 语法糖: new n => 无参数调用
						cur = newInvoke(newType, Collections.emptyList());
					}
				break skip;
				// constant
				case Word.CHARACTER: case Word.STRING:
				case Word.INTEGER: case Word.LONG:
				case Word.FLOAT: case Word.DOUBLE:
				case TRUE: case FALSE: case NULL:
					cur = Constant.valueOf(w);
				break;
				case SUPER: cur = Super(); break;
				// this
				case THIS: cur = This(); break;
				// define (unknown array)
				case left_l_br:
					// noinspection all
					check_param_map:
					if ((flag & _ENV_INVOKE) != 0) {
						// invoke_env:
						// a.b({xxx: yyy})

						int pos = wr.index;
						w = wr.next();
						String firstName = w.val();
						if (w.type() != Word.LITERAL || wr.next().type() != colon) {
							wr.index = pos;
							break check_param_map;
						}

						NamedParamList npl = newNamedParamList();
						while (true) {
							ExprNode val = parse(file, STOP_RLB|STOP_COMMA|SKIP_COMMA);
							if (val == null) throw wr.err("not_statement");
							if (!npl.add(firstName, val))
								file.fireDiagnostic(Diagnostic.Kind.ERROR, "expr.invoke.duplicate_param");

							w = wr.next();
							if (w.type() == right_l_br) break;
							else if (w.type() != Word.LITERAL) ue(wr, "type.literal");
							firstName = w.val();
							wr.except(colon);
						}
						cur = npl;
						break terminate;
					}

					if ((flag & _ENV_TYPED_ARRAY) != 0) {
						_arrayDef(file, null);
						break terminate;
					}

					// 可以直接写json，好像没啥用，先不加了
					// { "key" => "val" } like this
					// [t => 3, async => 4, testVal => [ ref => 1, tar => 2 ]];
					wr.unexpected(w.val());
				case left_m_br:
					//if ((flag & _ENV_FIELD) == 0) wr.unexpected(w.val());

					EasyMap easyMap = newEasyMap();
					// 形如 [ exprkey => exprval, ... ] 的直接Map<K, V>构建
					do {
						ExprNode key = parse(file, 0);
						if (key == null) throw wr.err("not_statement");
						wr.except(mapkv);
						ExprNode val = parse(file, _ENV_FIELD|STOP_RMB|STOP_COMMA);
						if (val == null) throw wr.err("not_statement");

						easyMap.map.put(key, val);
						w = wr.next();
					} while (w.type() != right_m_br);
					cur = easyMap;
					break;
				default: break skip;
			}
			w = wr.next();
			}
			// endregion
			// region 重复性"值生成" (变量|字段访问 数组获取 函数调用)
			//wr.disableOp(CAT_TYPE);
			boolean curIsObj = cur != null;
			int opFlag = 0;
			while (true) {
				switch (w.type()) {
					case lss:
						// 作为操作符
						if (curIsObj) {
							wr.retractWord();
							break terminate;
						}

						// 方法的泛型边界
						// must be at least one
						// Helpers.<Helpers>nonnull().<int[]>nonnull();
						List<IType> bounds = tmp();
						do {
							bounds.add(file.resolveType(CompileUnit.TYPE_PRIMITIVE|CompileUnit.TYPE_GENERIC));
							w = wr.next();
						} while (w.type() != gtr);
						bounds = copyOf(bounds);
						tmp0.clear();

						cur = _dot(cur, wr.except(Word.LITERAL).val(), (opFlag&OP_OPTIONAL));
						opFlag = 0;
						wr.except(left_s_br);
						cur = _invoke(file, cur, bounds);
						break;
					case left_m_br: { // a[b]
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						ExprNode index = parse(file, STOP_RMB|SKIP_RMB);
						if (index == null) _dot(cur, ";[", 0); // 用于.class
						else cur = createArrayGet(cur, index);
					}
					break;
					case dot: case optional_chaining:
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						curIsObj = false;
						if (w.type() == optional_chaining) opFlag |= OP_OPTIONAL;
						else opFlag &= ~OP_OPTIONAL;
						break;
					case THIS: case SUPER:
						if (curIsObj) ue(wr, w.val(), ".");
						curIsObj = true;
						cur = EncloseClass(w.type() == THIS, _classRef(cur, wr, w));
					break;
					default:
						if (category(w.type()) != CAT_TYPE) {
							wr.retractWord();
							break terminate;
						}
					case CLASS: // TERMINATOR
					case Word.LITERAL: // a.b
						if (curIsObj) ue(wr, w.val(), ".");
						curIsObj = true;
						if (w.type() == CLASS) {
							cur = Constant.classRef(_classRef(cur, wr, w));
							break;
						}
						cur = _dot(cur, w.val(), (opFlag&OP_OPTIONAL));
						opFlag = 0;
					break;
					case left_s_br: // a(b...)
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						cur = _invoke(file, cur, null);
					break;
					case method_referent: // this::stop
						if (!curIsObj) ue(wr, w.val(), "type.literal");
						cur = newLambda(cur, wr.except(Word.LITERAL).val());
					break terminate;
				}
				w = wr.next();
			}
			// endregion
			}
			w = wr.next();

			// set
			if (up != null) {
				String code = up.setRight(cur);
				if (code != null) throw wr.err(code);
				cur = tmp.get(tmp.size()-1);
			}

			// region 赋值运算符 | [terminate]load::LITERAL | 后缀自增/自减
			skip: {
				switch (w.type()) {
					// assign
					case assign:
					case add_assign: case sub_assign:
					case mul_assign: case div_assign:
					case mod_assign: case pow_assign:
					case lsh_assign: case rsh_assign: case rsh_unsigned_assign:
					case and_assign: case or_assign: case xor_assign: {
						if (!(cur instanceof VarNode)) throw wr.err("expr.assign.left");

						short vtype = w.type();

						// Mark assign
						// cur.var_op(ctx, 2);

						ExprNode right = parse(file, flag|STOP_COMMA);
						if (right == null) throw wr.err("expr.assign.right");

						cur = newAssign((VarNode) cur, vtype == assign ? right : binary((short) (add + (vtype - add_assign)), cur, right));
					}
					break;
					case inc: case dec:
						if (!(cur instanceof VarNode)) throw wr.err("expr.unary.not_variable:".concat(w.val()));
						cur = newUnaryPost(w.type(), cur);
					break;
					default: break skip;
				}
				w = wr.next();
			}
			// endregion

			// region 二元运算符 | 三元运算符 | 终结符
			switch (w.type()) {
				case INSTANCEOF: case ask: break;

				case colon:
					if ((flag & STOP_COLON) == 0) ue(wr, w.val());
					wr.retractWord();
					break; // :
				case right_m_br:
					if ((flag & STOP_RMB) == 0) ue(wr, w.val());
					if ((flag & SKIP_RMB) == 0) wr.retractWord();
					break; // ]
				case right_l_br:
					if ((flag & STOP_RLB) == 0) ue(wr, w.val());
					wr.retractWord();
					break; // }
				case comma:
					if ((flag & SKIP_COMMA) == 0) wr.retractWord();
					break; // ,
				case right_s_br:
					if ((flag & STOP_RSB) == 0) ue(wr, w.val());
					if ((flag & SKIP_RSB) == 0) wr.retractWord();
					break; // )
				case semicolon:
					if ((flag & STOP_SEMICOLON) == 0) ue(wr, w.val());
					wr.retractWord();
					break; // ;
				case mapkv:
					wr.retractWord();
					break; // =>

				default:
					int priority = binaryOperatorPriority(w.type());
					if (priority >= 0) {
						if (cur == null) throw new NotStatementException();
						if (up == null) tmp.add(cur);
						binaryOps.add(new BinaryOp(tmp.size(), priority));
						tmp.add(binary(w.type()));
						continue;
					}
				case Word.EOF: ue(wr, w.val());
			}
			// endregion

			if (cur != null) {
				if (up == null) tmp.add(cur);
			}
			break;
		}

		ExprNode cur;
		List<BinaryOp> ops = binaryOps;
		if (!ops.isEmpty()) {
			ops.sort(OPR_SORT);

			int i = 0;
			do {
				int v = ops.get(i).pos;

				Binary op = (Binary) tmp.get(v);
				if (tmp.size() == v+1) throw wr.err("expr.unary.no_operand:".concat(byId(op.operator)));

				op.left = tmp.set(v-1, op);
				op.right = tmp.set(v+1, op);

				cur = op;
			} while (++i != ops.size());

			ops.clear();
		} else {
			cur = tmp.isEmpty() ? null : tmp.get(0);
		}

		tmp.clear();

		// 优先级也不低
		// if ("5"+3 instanceof String ? "5"+3 instanceof String : "5"+3 instanceof String);
		if (w.type() == INSTANCEOF) {
			// no generic
			IType targetType = file.resolveType(0);
			cur = newInstanceOf(targetType.rawType(), cur);
			// TODO sub-check
		}

		if (w.type() == comma && (flag&STOP_COMMA) == 0) {
			List<ExprNode> args = tmp();
			args.add(cur);

			boolean hasComma = false;

			while (true) {
				ExprNode expr = parse(file, flag|STOP_COMMA);
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
			cur = newChained(copyOf(args));
		}

		// 这也是终结. 但是优先级最高
		if (w.type() == ask) {
			if (cur == null) ue(wr, w.val(), "type.expr");
			ExprNode middle = parse(file, flag|STOP_COLON);
			if (middle == null) throw wr.err("expr.trinary.empty_middle");
			wr.except(colon, ":");
			ExprNode right = parse(file, flag);
			if (right == null) throw wr.err("expr.trinary.empty_right");
			cur = newTrinary(cur, middle, right);
		}

		return cur;
	}

	private static Type _classRef(ExprNode cur, JavaLexer wr, Word w) throws ParseException {
		if (cur == null) ue(wr, w.val(), "type.literal");
		DotGet dg = (DotGet) cur;
		if (dg.parent != null) throw wr.err("expr.symbol.ref_check:".concat(dg.parent.toString()));
		return dg.toClassRef();
	}
	private ExprNode _dot(ExprNode e, String name, int flag) { return e instanceof DotGet ? ((DotGet) e).add(name, flag) : newDotGet(e, name, flag); }
	private ExprNode _invoke(CompileUnit file, Object fn, List<IType> bounds) throws ParseException {
		// this is just assert, always succeed
		if (!(fn instanceof ExprNode) && !(fn instanceof IType)) throw file.lex().err("illegal_invoke");

		List<ExprNode> args = tmp();

		while (true) {
			ExprNode expr = parse(file, STOP_RSB|STOP_COMMA|SKIP_COMMA|ALLOW_SPREAD|_ENV_INVOKE|_ENV_TYPED_ARRAY);
			// noinspection all
			if (expr == null || (args.add(expr) & expr.getClass() == NamedParamList.class)) {
				file.lex().except(right_s_br, ")");
				break;
			}
		}

		Invoke m = newInvoke(fn, args.isEmpty() ? Collections.emptyList() : copyOf(args));
		if (bounds != null) m.setBounds(bounds);
		args.clear();
		return m;
	}
	private ExprNode _arrayDef(CompileUnit file, IType arrayType) throws ParseException {
		List<ExprNode> args = tmp();
		JavaLexer wr = file.lex();
		IType clone = null;
		while (true) {
			ExprNode val = parse(file, STOP_RLB|STOP_COMMA|SKIP_COMMA|_ENV_TYPED_ARRAY);
			if (val == null) break;
			if (val.getClass() == ArrayDef.class) {
				ArrayDef def = (ArrayDef) val;
				if (def.type == null && arrayType != null) {
					if (clone == null) {
						clone = arrayType.clone();
						clone.setArrayDim(arrayType.array()-1);
					}
					def.type = clone;
				}
			}
			args.add(val);
		}
		wr.except(right_l_br);
		ExprNode def = newArrayDef(arrayType, copyOf(args), false);
		args.clear();
		return def;
	}
	private ExprNode _lambda(CompileUnit file, JavaLexer wr, List<String> parNames) throws ParseException {
		SimpleList<String> strings = copyOf(parNames);

		Word w = wr.next();
		if (w.type() == left_l_br) {
			// FIXME not implemented
			throw wr.err("not implemented");
		} else {
			wr.retractWord();
			ExprNode expr = parse(file, STOP_RSB|STOP_COMMA|STOP_SEMICOLON);
			if (expr == null) throw wr.err("not_statement");
			return newLambda(strings, expr);
		}
	}

	public ExprNode enumHelper(CompileUnit file, IType type) throws ParseException { return _invoke(file, type, null); }

	public void reset() {
		this.words.clear();
		this.tmp0.clear();
		this.binaryOps.clear();
	}

	private static void ue(JavaLexer wr, String wd, String except) throws ParseException { throw wr.err("unexpected_2:"+wd+':'+except); }
	private static void ue(JavaLexer wr, String wd) throws ParseException { throw wr.err("unexpected:"+wd); }

	// region cache

	private static final ToIntMap<Class<?>> CACHED_TYPES = new ToIntMap<>();
	private static void cache(Class<?> type) { CACHED_TYPES.putInt(type, CACHED_TYPES.size()); }
	static {
		cache(ArrayDef.class);
		cache(ArrayGet.class);
		cache(Assign.class);
		cache(Binary.class);
		cache(Cast.class);
		cache(Chained.class);
		cache(Constant.class);
		cache(DotGet.class);
		cache(EasyMap.class);
		cache(InstanceOf.class);
		cache(Invoke.class);
		cache(Lambda.class);
		cache(NamedParamList.class);
		cache(StringConcat.class);
		cache(This.class);
		cache(Trinary.class);
		cache(UnaryPost.class);
		cache(UnaryPre.class);
	}

	static final class Cache {
		final Thread owner = Thread.currentThread();
		final SimpleList<CacheBlock> ref = new SimpleList<>();
		final SimpleList<Object> using = new SimpleList<>();
		final SimpleList<Object>[] pool;

		Cache() {
			pool = Helpers.cast(new SimpleList<?>[CACHED_TYPES.size()]);
			for (int i = 0; i < pool.length; i++) pool[i] = new SimpleList<>(10);
		}

		@SuppressWarnings("unchecked")
		final <T> T get(Class<T> type) {
			assert !ref.isEmpty();

			int i = CACHED_TYPES.getOrDefault(type, -1);
			SimpleList<Object> list = pool[i];
			if (!list.isEmpty()) return (T) list.pop();
			Object o;
			try {
				o = ReflectionUtils.u.allocateInstance(type);
			} catch (InstantiationException e) {
				throw new RuntimeException(e);
			}
			using.add(o);
			return (T) o;
		}

		final void begin() {
			if (ref.size() > 0) ref.get(ref.size()-1).end = using.size();
			ref.add(new CacheBlock(this, using.size()));
		}
		final CacheBlock end() {
			CacheBlock block = ref.pop();
			block.end = using.size();
			return block;
		}
		final void free(CacheBlock block) {
			assert block.owner == this;
			if (block.begin == block.end) return;

			int pos = Arrays.binarySearch(ref.getInternalArray(), 0, ref.size(), block);
			assert pos >= 0;

			Object[] array = using.getInternalArray();
			for (int i = block.begin; i < block.end; i++) {
				Object o = array[i];
				SimpleList<Object> list = pool[CACHED_TYPES.getOrDefault(o.getClass(), -1)];
				if (list.size() < 100) list.add(o);
			}
			using.removeRange(block.begin, block.end);

			int delta = block.end - block.begin;
			ref.remove(pos);
			for (int i = pos; i < ref.size(); i++) {
				CacheBlock b = ref.get(i);
				b.begin -= delta;
				b.end -= delta;
			}
		}
	}
	static final class CacheBlock {
		final Cache owner;
		int begin, end;

		CacheBlock(Cache owner, int begin) {
			this.owner = owner;
			this.begin = begin;
		}
	}

	private static final ThreadLocal<Cache> CACHE = ThreadLocal.withInitial(Cache::new);
	private Cache cache1;
	private Cache getCache() {
		Cache c = cache1;
		if (c == null || c.owner != Thread.currentThread()) {
			cache1 = c = CACHE.get();
		}
		return c;
	}

	public ExprNode This() { return This.THIS; }
	public ExprNode Super() { return This.SUPER; }
	public ExprNode EncloseClass(boolean ThisEnclosing, Type type) { return new EncloseRef(ThisEnclosing, type); }

	public ExprNode createArrayGet(ExprNode array, ExprNode index) {
		return new ArrayGet(array, index);
	}

	public UnaryPre unaryPre(short type) {
		return new UnaryPre(type);
	}

	public UnaryPre cast(IType type) {
		return new Cast(type);
	}

	public <T> SimpleList<T> copyOf(List<T> args) {
		return new SimpleList<>(args);
	}

	public ExprNode newArrayDef(IType type, SimpleList<ExprNode> args, boolean sized) {
		return new ArrayDef(type, args, sized);
	}

	public Invoke newInvoke(Object fn, List<ExprNode> pars) {
		return new Invoke(fn, pars);
	}

	public NamedParamList newNamedParamList() {
		return new NamedParamList();
	}

	public EasyMap newEasyMap() {
		return new EasyMap();
	}

	public ExprNode newLambda(ExprNode cur, String val) {
		return new Lambda(cur, val);
	}

	public ExprNode newAssign(VarNode cur, ExprNode node) {
		return new Assign(cur, node);
	}
	public ExprNode binary(short op) { return new Binary(op); }
	public ExprNode binary(short op, ExprNode left, ExprNode right) { return new Binary(op, left, right); }

	public ExprNode newUnaryPost(short op, ExprNode prev) { return new UnaryPost(op, prev); }


	public ExprNode newInstanceOf(Type type, ExprNode cur) {
		return new InstanceOf(type, cur);
	}

	private ExprNode newChained(SimpleList<ExprNode> ts) {
		return new Chained(ts);
	}

	public ExprNode newTrinary(ExprNode cur, ExprNode middle, ExprNode right) {
		return new Trinary(cur, middle, right);
	}

	public ExprNode newLambda(SimpleList<String> strings, ExprNode expr) {
		return new Lambda(strings, expr);
	}

	public DotGet newDotGet(ExprNode e, String name, int flag) {
		return new DotGet(e, name, flag);
	}
	// endregion
}