package roj.compiler;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;
import roj.asm.Attributed;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.ModuleAttribute;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.collect.*;
import roj.compiler.api.Compiler;
import roj.compiler.api.Processor;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.ast.MethodParser;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.PrefixOperator;
import roj.compiler.diagnostic.Kind;
import roj.compiler.doc.Javadoc;
import roj.compiler.doc.JavadocProcessor;
import roj.compiler.library.JarLibrary;
import roj.compiler.library.Library;
import roj.compiler.resolve.Resolver;
import roj.config.Token;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.util.ByteList;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 提供class信息的上下文、编译环境 每个编译器一个，或者说它就是编译器
 * 1. 提供类全限定名(String)到类ASM数据(ConstantData)的映射
 * 2. 一些和类绑定的数据的缓存，它们包括
 *   MethodList，FieldList，他们只以名字区分，不考虑权限或参数数量，前者是为了更好的错误提示而不是仅仅“找不到”，后者是为了varargs和named type，
 *   父类列表，之所以IntBiMap而不是数组是为了有序的同时，还能O(n)获取最近共同祖先
 *   List<IType> getTypeParamOwner(IClass info, IType superType)
 *   InnerClassFlags 获取内部类的真实权限
 * 3. 短名称缓存 (通过短名称获取全限定名) TypeResolver importAny需要用到
 * 4. 创建MethodWriter，为了以后切换编译目标，比如到C甚至x86
 * 5. 注解处理
 *
 * @author solo6975
 * @since 2021/7/22 19:05
 */
public class LavaCompiler extends Resolver implements Compiler {
	public LavaCompiler() {super();}
	public LavaCompiler(boolean addRuntimeLibrary) {super(addRuntimeLibrary);}

	private final ArrayList<CompileUnit> parsable = new ArrayList<>();
	private final GeneratedClasses generated = new GeneratedClasses();
	private static final class GeneratedClasses implements Library {
		final HashMap<String, ClassNode> classes = new HashMap<>();
		ModuleAttribute module;

		@Override public ClassNode get(CharSequence name) {return classes.get(name);}
		@Override public String moduleName() {return module == null ? null : module.self.name;}
	}

	public void getParsableUnits(List<CompileUnit> list) {
		list.addAll(parsable);
		parsable.clear();
	}
	public Collection<ClassNode> getGeneratedClasses() {return generated.classes.values();}
	public void reset() {
		//extraInfos.removeIf(v -> v.owner instanceof CompileUnit);
		for (CompileUnit unit : compileUnits) extraInfos.removeKey(unit);
		compileUnits.clear();

		libraryByName.values().removeIf(v -> v == generated);
		generated.classes.clear();
		generated.module = null;
	}
	//被其它类使用的
	public synchronized void addCompileUnit(CompileUnit unit, boolean needLexicalParse) {
		CompileUnit prev = compileUnits.putIfAbsent(unit.name(), unit);
		if (prev != null) throw new IllegalStateException("重复的编译单位: "+unit.name());
		if (needLexicalParse) parsable.add(unit);
	}

	public synchronized void addGeneratedClass(ClassNode data) {
		libraryByName.put(data.name(), generated);
		var prev = generated.classes.putIfAbsent(data.name(), data);
		if (prev != null) throw new IllegalStateException("重复的生成类: "+data.name());
	}

	public void setModule(ModuleAttribute module) {
		if (generated.module != null) report(null, Kind.ERROR, -1, "module.dup");
		generated.module = module;
	}

	public JavadocProcessor createJavadocProcessor(Javadoc javadoc, CompileUnit file) {return JavadocProcessor.NULL;}

	public ClassNode resolveArray(IType type) {
		String arrayTypeDesc = type.toDesc();
		Object o = libraryByName.get(arrayTypeDesc);
		if (o != null) return (ClassNode) o;

		var anyArray = AnyArray;
		var arrayInfo = new ClassNode();
		arrayInfo.name(arrayTypeDesc);
		arrayInfo.parent(anyArray.parent());
		arrayInfo.itfList().addAll(anyArray.itfList());
		arrayInfo.methods.addAll(anyArray.methods);
		arrayInfo.fields.addAll(anyArray.fields);

		synchronized (libraryByName) {
			libraryByName.putIfAbsent(arrayTypeDesc, arrayInfo);
		}

		return arrayInfo;
	}
	// Misc
	public int maxVersion = JAVA_17;
	public BitSet features = new BitSet();

	public boolean hasFeature(int specId) {return features.contains(specId);}
	@Range(from = 6, to = 21)
	public int getMaximumBinaryCompatibility() {return maxVersion;}
	//
	//region CompileContext创建和扩展
	public CompileContext createContext() {
		var ctx = new CompileContext(this);
		ctx.ep.stateMap = stateMap;
		ctx.ep.callbacks = callbacks;
		return ctx;
	}
	// TODO 如何添加新的语句？
	protected MethodParser createMethodParser(CompileContext ctx) {return new MethodParser(ctx);}

	protected static final int TF_ANYARGC = 1, TF_PRIMITIVE = 2, TF_NORAW = 4, TF_NOARRAY = 8;
	/**
	 * 解析时忽略这个类的泛型限制，用于某些编译器内部类，需要在泛型中保存可变参数
	 */
	@MagicConstant(flags = {TF_ANYARGC, TF_PRIMITIVE, TF_NORAW, TF_NOARRAY})
	protected int getTypeFlag(ClassNode info) {
		return switch (info.name()) {
			case "roj/compiler/runtime/ReturnStack" -> TF_ANYARGC | TF_PRIMITIVE | TF_NORAW;
			case "roj/compiler/runtime/Generator" -> TF_PRIMITIVE | TF_NORAW;
			case "roj/compiler/runtime/F" -> TF_ANYARGC | TF_PRIMITIVE | TF_NORAW;
			case "roj/compiler/api/Union" -> TF_ANYARGC | TF_NORAW | TF_NOARRAY;
			default -> 0;
		};
	}
	/**
	 * @see CompileContext#getOperatorOverride(Expr, Object, int)
	 */
	@Nullable
	protected Expr getOperatorOverride(CompileContext ctx, @NotNull Expr e1, @Nullable Object e2, int operator) {
		IType left = e1.type(), right = e2 instanceof Expr n ? n.type() : (IType) e2;

		var ctx1 = new OperatorContext((short) operator, left, right);

		for (var op : operators.getOrDefault(operator, Collections.emptyList())) {
			var node = op.test(ctx, ctx1, e1, e2);
			if (node != null) return node.resolve(ctx);
		}
		return null;
	}
	//endregion
	//region 注解处理API
	private AnnotationRepo annotations;
	private final HashMap<String, List<Processor>> processors = new HashMap<>();

	public void addLibrary(Library library) {
		if (annotations != null) addAnnotations(library);
		super.addLibrary(library);
	}

	public AnnotationRepo getClasspathAnnotations() {
		if (annotations == null) {
			annotations = new AnnotationRepo();
			for (Library library : libraries) {
				addAnnotations(library);
			}
		}
		return annotations;
	}

	private void addAnnotations(Library library) {
		if (library instanceof JarLibrary lib) {
			File cache = getCacheDirectory("annotations$"+Helpers.sha1Hash(lib.jar.source().toString())+".repo");
			if (cache != null) {
				if (cache.isFile()) {
					try (var in = new FileInputStream(cache)) {
						if (annotations.deserialize(IOUtil.getSharedByteBuf().readStreamFully(in))) {
							return;
						}
					} catch (Exception e) {
						debugLogger().error("Failed to load annotation cache", e);
					}
				}

				if (lib.jar.getEntry(AnnotationRepo.CACHE_NAME) == null) {
					var repo = new AnnotationRepo();
					try (var fos = new FileOutputStream(cache)) {
						repo.add(lib.jar);
						ByteList buf = IOUtil.getSharedByteBuf();
						repo.serialize(buf);
						buf.writeToStream(fos);

						annotations.deserialize(buf);
						return;
					} catch (Exception e) {
						debugLogger().error("Failed to save annotation cache", e);
					}
				}
			}
			try {
				annotations.loadCacheOrAdd(lib.jar);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			for (String name : library.indexedContent()) {
				annotations.add(library.get(name));
			}
		}
	}

	@Override
	public void addAnnotationProcessor(Processor processor) {
		for (String annotation : processor.acceptedAnnotations()) {
			processors.computeIfAbsent(annotation, Helpers.fnArrayList()).add(processor);
		}

		if (processor.acceptClasspath()) {
			getClasspathAnnotations();
			for (String annotation : processor.acceptedAnnotations()) {
				for (AnnotatedElement element : annotations.annotatedBy(annotation)) {
					processor.handle(CompileContext.get(), element.parent().node(), element.node(), element.annotations().get(annotation));
				}
			}
		}
	}

	public void runAnnotationProcessor(CompileUnit file, Attributed node, List<AnnotationPrimer> annotations) {
		for (AnnotationPrimer annotation : annotations) {
			var lc = CompileContext.get();
			for (Processor processor : processors.getOrDefault(annotation.type(), Collections.emptyList())) {
				processor.handle(lc, file, node, annotation);
			}
		}
	}
	//endregion
	//region 库加载API
	private final IterablePriorityQueue<Prioritized<Resolver>> resolvers = new IterablePriorityQueue<>();
	private record Prioritized<T>(int priority, T value) implements Comparable<Prioritized<T>> {
		public int compareTo(Prioritized<T> o) {return Integer.compare(priority, o.priority);}
	}

	@Override
	public void addResolveListener(int priority, Resolver dtr) {resolvers.add(new Prioritized<>((priority << 16) | resolvers.size(), dtr));}

	protected ClassNode onClassLoaded(String name, ClassNode clazz) {
		for (var sortable : resolvers) {
			clazz = sortable.value.classResolved(clazz);
		}
		return clazz;
	}
	//endregion
	// region 词法分析API
	private final TrieTree<Token> myJavaToken = JavaTokenizer.getTokenMap();
	private final BitSet myJavaLend = JavaTokenizer.getLiteralEnd();
	private final BitSet[] myJavaLst = JavaTokenizer.getLiteralState();
	private int nextTokenId = JavaTokenizer.getInitialTokenCount();

	public JavaTokenizer createLexer() {
		var lexer = new JavaTokenizer();
		lexer.literalEnd(myJavaLend).tokenIds(myJavaToken);
		lexer.literalState = myJavaLst;
		return lexer;
	}

	@Override public int tokenCreate(String token, int tokenCategory) {
		Token word = myJavaToken.putIfAbsent(token, new Token().init(nextTokenId, 0, token));
		int tokenId = word == null ? nextTokenId++ : word.type();
		for (int i = 0; i < myJavaLst.length; i++) {
			if (((1 << i) & tokenCategory) != 0)
				myJavaLst[i].add(tokenId);
		}
		return tokenId;
	}
	@Override public void tokenAlias(int token, String alias) {myJavaToken.put(alias, new Token().init(token, 0, alias));}
	@Override public void tokenLiteralEnd(String lend) {myJavaLend.add(lend.charAt(0));}
	@Override public void tokenDelete(String token) {myJavaToken.remove(token);}

	private int tokenId(String token) {
		Token w = myJavaToken.get(token);
		if (w != null) return w.type();
		return tokenCreate(token, STATE_EXPR);
	}
	// endregion
	//region 表达式解析API
	private final Int2IntMap stateMap = ExprParser.getStateMap();
	private final List<Object> callbacks = new ArrayList<>();
	private final IntMap<List<ExprOp>> operators = new IntMap<>();

	private void register(int mask, String token, Object fn, int flags) {
		int i = stateMap.putIntIfAbsent(tokenId(token) | mask, flags);
		if (i != flags) throw new IllegalStateException("token "+token+" was occupied");
		// id=0不可用
		if (callbacks.isEmpty()) callbacks.add(null);
		callbacks.add(fn);
	}

	@Override public final void newUnaryOp(String token, ContinueOp<PrefixOperator> parser) {register(ExprParser.SM_UnaryPre, token, parser, callbacks.size());}
	@Override public final void newExprOp(String token, StartOp parser) {register(ExprParser.SM_UnaryPre, token, parser, ExprParser.CU_TerminateFlag | callbacks.size());}
	@Override public final void newStartOp(String token, StartOp parser) {register(ExprParser.SM_ExprStart, token, parser, callbacks.size());}
	@Override public final void newContinueOp(String token, ContinueOp<Expr> parser) {register(ExprParser.SM_ExprNext, token, parser, callbacks.size());}
	@Override public final void newTerminalOp(String token, ContinueOp<Expr> parser) {register(ExprParser.SM_ExprNext, token, parser, ExprParser.CU_TerminateFlag | callbacks.size());}
	@Override public final void newBinaryOp(String token, int priority, BinaryOp parser, boolean rightAssoc) {register(ExprParser.SM_ExprTerm, token, parser, priority | ExprParser.CU_Binary | (rightAssoc ? ExprParser.SM_RightAssoc : 0) | (callbacks.size() << 12));}

	private static ExprOp SimpleOp(Type left1, Type right1, MethodNode node) {
		return (ctx, opctx, left, right) -> {
			if (ctx.castTo(opctx.leftType(), left1, -8).type >= 0 &&
					(right == null ? right1 == null : ctx.castTo(opctx.rightType(), right1, -8).type >= 0)) {

				boolean isObject = (node.modifier() & Opcodes.ACC_STATIC) == 0;
				if (isObject) return Invoke.virtualMethod(node, left, (Expr) right);
				return Invoke.staticMethod(node, left, (Expr) right);
			}
			return null;
		};
	}

	@Override public void onUnary(String operator, Type type, MethodNode node, int side) {operators.computeIfAbsentI(tokenId(operator), x -> new ArrayList<>()).add(SimpleOp(type, null, node));}
	@Override public void onBinary(Type left, String operator, Type right, MethodNode node, boolean swap) {operators.computeIfAbsentI(tokenId(operator), x -> new ArrayList<>()).add(SimpleOp(left, right, node));}
	@Override public void addOpHandler(String operator, ExprOp resolver) {operators.computeIfAbsentI(tokenId(operator), x -> new ArrayList<>()).add(resolver);}
	//endregion
	//region 沙盒API
	private Sandbox sandbox;

	private synchronized Sandbox getSandbox() {
		if (sandbox == null) {
			report(null, Kind.SEVERE_WARNING, -1, "lava.sandbox");
			sandbox = new Sandbox(LavaCompiler.class.getClassLoader());
			for (var packages : new String[] {"java.lang", "java.util", "java.util.regex", "java.util.function", "java.text", "roj.compiler", "roj.text", "roj.config.data"}) {
				addSandboxWhitelist(packages, false);
			}
			for (var classes : new String[] {"java.lang.Process", "java.lang.ProcessBuilder", "java.lang.Thread", "java.lang.ClassLoader"}) {
				addSandboxWhitelist(classes, false);
			}
		}
		return sandbox;
	}

	public void addSandboxWhitelist(String packageOrTypename, boolean childInheritance) {getSandbox().permits.add(packageOrTypename, 1, false, childInheritance);}
	public void addSandboxBlacklist(String packageOrTypename, boolean childInheritance) {getSandbox().permits.add(packageOrTypename, 0, false, childInheritance);}
	public Object createSandboxInstance(ClassNode data) {return ClassDefiner.newInstance(data, getSandbox());}
	public void addSandboxClass(String className, byte[] data) {getSandbox().classBytes.put(className, data);}
	public Class<?> loadSandboxClass(String className, boolean resolve) {
		try {
			return getSandbox().loadClass(className, resolve);
		} catch (ClassNotFoundException e) {
			throw new AssertionError(e);
		}
	}
	//endregion
	//region 附件API
	private Map<TypedKey<?>, Object> attachments = Collections.emptyMap();
	@SuppressWarnings("unchecked")
	public final <T> T attachment(TypedKey<T> key) {return (T) attachments.get(key);}
	@SuppressWarnings("unchecked")
	public synchronized final <T> T attachment(TypedKey<T> key, T val) {
		if (attachments.isEmpty()) attachments = new HashMap<>(4);
		return (T) (val == null ? attachments.remove(key) : attachments.put(key, val));
	}
	//endregion
}