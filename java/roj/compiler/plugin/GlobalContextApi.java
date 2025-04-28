package roj.compiler.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.asm.Attributed;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.collect.*;
import roj.compiler.LavaFeatures;
import roj.compiler.Tokens;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.ast.expr.PrefixOperator;
import roj.compiler.context.*;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.stc.StreamChain;
import roj.compiler.plugins.stc.StreamChainExpr;
import roj.compiler.plugins.stc.StreamChainPlugin;
import roj.config.Word;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.text.TextUtil;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2024/6/10 4:05
 */
public class GlobalContextApi extends GlobalContext implements LavaApi {
	// Basic
	public int maxVersion = LavaFeatures.JAVA_17;
	public MyBitSet features = new MyBitSet();

	// Processor API
	private AnnotationRepo repo;
	private final MyHashMap<String, List<Processor>> processors = new MyHashMap<>();

	// Resolve API
	private final IterablePriorityQueue<ImplResolver<Resolver>> resolveApi = new IterablePriorityQueue<>();

	// Tokenizer API
	private final TrieTree<Word> myJavaToken = Tokens.getTokenMap();
	private final MyBitSet myJavaLend = Tokens.getLiteralEnd();
	private final MyBitSet[] myJavaLst = Tokens.getLiteralState();
	private int nextTokenId = Tokens.getInitialTokenCount();

	// ExprParser API
	private final Int2IntMap stateMap = ExprParser.getStateMap();
	private final List<Object> callbacks = new SimpleList<>();
	private final IntMap<List<ExprOp>> operators = new IntMap<>();
	private final StreamChainPlugin scp = new StreamChainPlugin();

	// Misc API
	private Sandbox sandbox;

	public GlobalContextApi() {
		addAnnotationProcessor(new JavaLangAnnotations());
		callbacks.add(null);
		scp.pluginInit(this);
	}

	public void setOptions(MyHashMap<String, String> options) {
		var lookup = MethodHandles.lookup();
		for (Map.Entry<String, String> entry : options.entrySet()) {
			switch (entry.getKey()) {
				case "targetVersion":
					maxVersion = ClassNode.JavaVersion(Integer.parseInt(entry.getValue()));
					break;
				case "lavaFeature":
					for (String s : entry.getValue().split(",")) {
						int value;
						try {
							value = (int) lookup.findStaticVarHandle(LavaFeatures.class, s.substring(1), int.class).get();
						} catch (IllegalAccessException | NoSuchFieldException e) {
							throw new RuntimeException(e);
						}

						if (s.startsWith("+")) {
							features.add(value);
						} else if (s.startsWith("-")) {
							features.remove(value);
						}
					}
					break;
				case "processor":
					Processor processor;
					try {
						processor = (Processor) lookup.findConstructor(lookup.findClass(entry.getValue()), MethodType.methodType(void.class)).invoke();
					} catch (Throwable e) {
						throw new RuntimeException(e);
					}
					addAnnotationProcessor(processor);
					break;
				case "sandbox":
					for (String packageOrTypeName : TextUtil.split(entry.getValue(), ',')) {
						addSandboxWhitelist(packageOrTypeName, false);
					}
			}
		}
	}

	public boolean hasFeature(int specId) {return features.contains(specId);}
	public int getMaximumBinaryCompatibility() {return maxVersion;}

	@Override public LocalContext createLocalContext() {return new LC();}
	private final class LC extends LocalContext {
		public LC() {super(GlobalContextApi.this);}

		@Override
		protected boolean isGenericIgnore(ClassNode info) {
			// for TypeDeclPlugin, WIP
			return super.isGenericIgnore(info) || info.name().equals("roj/asm/type/IType");
		}

		@Override
		public ExprParser createExprParser() {
			var parser = new ExprParser(this);
			parser.stateMap = stateMap;
			parser.callbacks = callbacks;
			return parser;
		}

		@Override
		protected Tokens createLexer() {
			var lexer = super.createLexer();
			lexer.literalState = myJavaLst;
			lexer.literalEnd(myJavaLend).tokenIds(myJavaToken);
			return lexer;
		}

		@Override
		@Nullable
		public Expr getOperatorOverride(@NotNull Expr e1, @Nullable Object e2, int operator) {
			IType left = e1.type(), right = e2 instanceof Expr n ? n.type() : (IType) e2;

			var ctx1 = new OperatorContext() {
				@Override public short symbol() {return (short) operator;}
				@Override public IType leftType() {return left;}
				@Override public IType rightType() {return right;}
			};

			for (var op : operators.getOrDefault(operator, Collections.emptyList())) {
				var node = op.test(this, ctx1, e1, e2);
				if (node != null) return node.resolve(this);
			}
			return null;
		}
	}

	public ClassNode getArrayInfo(IType type) {
		String arrayTypeDesc = type.toDesc();
		Object o = libraryCache.get(arrayTypeDesc);
		if (o != null) return (ClassNode) o;

		var rootInfo = super.getArrayInfo(null);

		var arrayInfo = new ClassNode();
		arrayInfo.name(arrayTypeDesc);
		arrayInfo.parent(rootInfo.parent());
		arrayInfo.itfList().addAll(rootInfo.itfList());
		arrayInfo.methods.addAll(rootInfo.methods);
		arrayInfo.fields.addAll(rootInfo.fields);

		libraryCache.put(arrayTypeDesc, arrayInfo);

		return arrayInfo;
	}

	//region 注解处理API
	@Override
	public void addLibrary(Library library) {
		if (repo != null) addAnnotations(library);
		super.addLibrary(library);
	}

	public AnnotationRepo getClasspathAnnotations() {
		if (repo == null) {
			repo = new AnnotationRepo();
			for (Library library : libraries) {
				addAnnotations(library);
			}
		}
		return repo;
	}

	private void addAnnotations(Library library) {
		if (library instanceof LibraryZipFile zf) {
			try {
				ZEntry entry = zf.zf.getEntry("META-INF/annotations.repo");
				if (entry != null) {
					if (repo.deserialize(IOUtil.getSharedByteBuf().readStreamFully(zf.zf.getStream(entry)))) {
						return;
					}
				}

				repo.add(zf.zf);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			for (String name : library.content()) {
				repo.add(library.get(name));
			}
		}
	}

	public void runAnnotationProcessor(CompileUnit file, Attributed node, List<AnnotationPrimer> annotations) {
		for (AnnotationPrimer annotation : annotations) {
			var lc = LocalContext.get();
			for (Processor processor : processors.getOrDefault(annotation.type(), Collections.emptyList())) {
				processor.handle(lc, file, node, annotation);
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
				for (AnnotatedElement element : repo.annotatedBy(annotation)) {
					processor.handle(LocalContext.get(), element.parent().node(), element.node(), element.annotations().get(annotation));
				}
			}
		}
	}
	//endregion
	//region 库加载API
	private static final class ImplResolver<T> implements Comparable<ImplResolver<T>> {
		final int priority;
		final T resolver;

		ImplResolver(int priority, T resolver) {
			this.priority = priority;
			this.resolver = resolver;
		}

		public int compareTo(ImplResolver<T> resolver) {return Integer.compare(priority, resolver.priority);}
	}

	@Override
	public void addResolveListener(int priority, Resolver dtr) {resolveApi.add(new ImplResolver<>(priority, dtr));}

	@Override
	protected ClassNode onClassLoaded(String name, ClassNode clazz) {
		for (var sortable : resolveApi) {
			clazz = sortable.resolver.classResolved(clazz);
		}
		return super.onClassLoaded(name, clazz);
	}
	//endregion
	// region 词法分析API
	@Override
	public int tokenCreate(String token, int tokenCategory) {
		myJavaToken.put(token, new Word().init(nextTokenId, 0, token));
		for (int i = 0; i < myJavaLst.length; i++) {
			if (((1 << i) & tokenCategory) != 0)
				myJavaLst[i].add(nextTokenId);
		}
		if ((tokenCategory&0x80000000) != 0) {
			myJavaLend.add(token.charAt(0));
		}
		return nextTokenId++;
	}

	@Override
	public void tokenAlias(int token, String alias) {
		myJavaToken.put(alias, new Word().init(token, 0, alias));
	}

	@Override
	public void tokenLiteralEnd(String lend) {
		myJavaLend.add(lend.charAt(0));
	}
	// endregion
	//region 表达式解析API
	private int tokenId(String token) {
		Word w = myJavaToken.get(token);
		if (w != null) return w.type();
		return tokenCreate(token, 1 << Tokens.STATE_EXPR);
	}

	private void register(int mask, String token, Object fn, int flags) {
		int i = stateMap.putIntIfAbsent(tokenId(token) | mask, flags);
		if (i != flags) throw new IllegalStateException("token "+token+" was occupied");
		callbacks.add(fn);
	}

	@Override public void newUnaryOp(String token, ContinueOp<PrefixOperator> parser) {register(ExprParser.SM_UnaryPre, token, parser, callbacks.size());}
	@Override public void newExprOp(String token, StartOp parser) {register(ExprParser.SM_UnaryPre, token, parser, ExprParser.CU_TerminateFlag | callbacks.size());}
	@Override public void newStartOp(String token, StartOp parser) {register(ExprParser.SM_ExprStart, token, parser, callbacks.size());}
	@Override public void newContinueOp(String token, ContinueOp<Expr> parser) {register(ExprParser.SM_ExprNext, token, parser, callbacks.size());}
	@Override public void newTerminalOp(String token, ContinueOp<Expr> parser) {register(ExprParser.SM_ExprNext, token, parser, ExprParser.CU_TerminateFlag | callbacks.size());}
	@Override public void newBinaryOp(String token, int priority, BinaryOp parser, boolean rightAssoc) {register(ExprParser.SM_ExprTerm, token, parser, priority | ExprParser.CU_Binary | (rightAssoc ? ExprParser.SM_RightAssoc : 0) | (callbacks.size() << 12));}

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

	@Override public void onUnary(String operator, Type type, MethodNode node, int side) {operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(SimpleOp(type, null, node));}
	@Override public void onBinary(Type left, String operator, Type right, MethodNode node, boolean swap) {operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(SimpleOp(left, right, node));}
	@Override public void addOpHandler(String operator, ExprOp resolver) {operators.computeIfAbsentInt(tokenId(operator), x -> new SimpleList<>()).add(resolver);}

	@Override public StreamChain newStreamChain(String type, boolean existInRuntime, Consumer<StreamChainExpr> parser, Type realType) {return scp.newStreamChain(type, existInRuntime, parser, realType);}
	//endregion

	private synchronized Sandbox getSandbox() {
		if (sandbox == null) {
			report(null, Kind.SEVERE_WARNING, -1, "lava.sandbox");
			sandbox = new Sandbox(GlobalContextApi.class.getClassLoader());
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
	public Object createSandboxInstance(ClassNode data) {return ClassDefiner.make(data, getSandbox());}
	public void addSandboxClass(String className, byte[] data) {getSandbox().classBytes.put(className, data);}
	public Class<?> loadSandboxClass(String className, boolean resolve) {
		try {
			return getSandbox().loadClass(className, resolve);
		} catch (ClassNotFoundException e) {
			throw new AssertionError(e);
		}
	}

	private Map<TypedKey<?>, Object> attachments = Collections.emptyMap();
	@SuppressWarnings("unchecked")
	public final <T> T attachment(TypedKey<T> key) {return (T) attachments.get(key);}
	@SuppressWarnings("unchecked")
	public synchronized final <T> T attachment(TypedKey<T> key, T val) {
		if (attachments.isEmpty()) attachments = new MyHashMap<>(4);
		return (T) (val == null ? attachments.remove(key) : attachments.put(key, val));
	}
}