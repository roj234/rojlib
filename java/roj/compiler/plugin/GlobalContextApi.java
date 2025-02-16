package roj.compiler.plugin;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Attributed;
import roj.asm.ClassNode;
import roj.asm.type.IType;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.collect.IterablePriorityQueue;
import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.context.*;
import roj.compiler.diagnostic.Kind;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/6/10 4:05
 */
public class GlobalContextApi extends GlobalContext implements LavaApi {
	//region ResolveApi
	private final IterablePriorityQueue<ImplResolver<Resolver>> resolveApi = new IterablePriorityQueue<>();
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
	protected ClassNode onClassLoaded(String name, ClassNode clazz) {
		for (var sortable : resolveApi) {
			clazz = sortable.resolver.classResolved(clazz);
		}
		return super.onClassLoaded(name, clazz);
	}
	//endregion

	private final ImplExprApi exprApi = new ImplExprApi();
	{
		exprApi.scp.pluginInit(this);
	}
	private AnnotationRepo repo;

	@Override
	public void addLibrary(Library library) {
		if (repo != null) addAnnotations(library);
		super.addLibrary(library);
	}

	public void runAnnotationProcessor(CompileUnit file, Attributed node, List<AnnotationPrimer> annotations) {
		for (AnnotationPrimer annotation : annotations) {
			if (annotation.type().equals("java/lang/Override") && annotation.raw() != Collections.EMPTY_MAP) {
				report(file, Kind.ERROR, annotation.pos, "annotation.override");
			}

			if (annotation.type().equals("roj/compiler/api/Ignore")) {
				debugLogger().info("\n\n当前的错误已被忽略\n\n");
				// 临时解决方案
				hasError = false;
			}

			var lc = LocalContext.get();
			for (Processor processor : processors.getOrDefault(annotation.type(), Collections.emptyList())) {
				processor.handle(lc, file, node, annotation);
			}
		}
	}

	// region Token Api
	@Override
	public int tokenCreate(String token, boolean literalToken, int tokenCategory) {
		return 0;
	}

	@Override
	public void tokenRename(int token, String name) {

	}

	@Override
	public void tokenAlias(int token, String alias) {

	}

	@Override
	public void tokenAliasLiteral(String alias, String literal) {

	}
	@Override
	public JavaLexer createLexer() {
		return super.createLexer();
	}
	// endregion

	@Override
	public LocalContext createLocalContext() {
		return new LCImpl();
	}

	@Override
	public ExprParser createExprParser(LocalContext ctx) {
		var parser = new ExprParser(ctx);
		parser.sm = exprApi.st;
		parser.custom = exprApi.custom;
		return parser;
	}

	public MyBitSet features = new MyBitSet();
	public boolean hasFeature(int specId) {return features.contains(specId);}

	@Override
	public ExprApi getExprApi() {return exprApi;}

	private Map<TypedKey<?>, Object> attachments = Collections.emptyMap();
	@SuppressWarnings("unchecked")
	public final <T> T attachment(TypedKey<T> key) {return (T) attachments.get(key);}
	@SuppressWarnings("unchecked")
	public synchronized final <T> T attachment(TypedKey<T> key, T val) {
		if (attachments.isEmpty()) attachments = new MyHashMap<>(4);
		return (T) (val == null ? attachments.remove(key) : attachments.put(key, val));
	}

	@Override
	public void addResolveListener(int priority, Resolver dtr) {resolveApi.add(new ImplResolver<>(priority, dtr));}

	private final MyHashMap<String, List<Processor>> processors = new MyHashMap<>();

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

	final class LCImpl extends LocalContext {
		public LCImpl() {super(GlobalContextApi.this);}

		@Override
		@Nullable
		public ExprNode getOperatorOverride(@NotNull ExprNode e1, @Nullable Object e2, int operator) {
			return exprApi.getOperatorOverride(this, e1, e2, operator);
		}
	}
}