package roj.compiler.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.tree.Attributed;
import roj.asm.tree.ConstantData;
import roj.asm.tree.IClass;
import roj.asmx.AnnotatedElement;
import roj.asmx.AnnotationRepo;
import roj.collect.SimpleList;
import roj.compiler.JavaLexer;
import roj.compiler.asm.AnnotationPrimer;
import roj.compiler.asm.Variable;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.ExprParser;
import roj.compiler.context.*;
import roj.compiler.diagnostic.Kind;
import roj.compiler.plugins.api.ExprApi;
import roj.compiler.plugins.api.LavaApi;
import roj.compiler.plugins.api.Processor;
import roj.compiler.plugins.api.Resolver;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * @author Roj234
 * @since 2024/6/10 4:05
 */
public class GlobalContextApi extends GlobalContext implements LavaApi {
	private final PriorityQueue<ImplResolver<Resolver>> resolveApi = new PriorityQueue<>();
	private static final class ImplResolver<T> implements Comparable<ImplResolver<T>> {
		final int priority;
		final T resolver;

		ImplResolver(int priority, T resolver) {
			this.priority = priority;
			this.resolver = resolver;
		}

		public int compareTo(ImplResolver<T> resolver) {return Integer.compare(priority, resolver.priority);}
	}

	private final ImplExprApi exprApi = new ImplExprApi();
	private AnnotationRepo repo;
	private List<Library> _libraries = new SimpleList<>();

	@Override
	public void addLibrary(Library library) {
		if (repo != null) addAnnotations(library);
		else _libraries.add(library);
		super.addLibrary(library);
	}

	// ◢◤
	public ConstantData getClassInfo(CharSequence name) {
		ConstantData info = ctx.get(name);
		if (info == null) {
			info = super.getClassInfo(name);
			for (var sortable : resolveApi) {
				sortable.resolver.classResolved(info);
			}
		}

		return info;
	}

	private void packageListed(String pkg, List<String> list) {
		for (ImplResolver<Resolver> sortable : resolveApi) {
			sortable.resolver.packageListed(pkg, list);
		}
	}

	// DEFINE BY USER
	public void invokeAnnotationProcessor(CompileUnit file, Attributed node, List<AnnotationPrimer> annotations) {
		for (AnnotationPrimer annotation : annotations) {
			if (annotation.type().equals("java/lang/Override") && annotation.values != Collections.EMPTY_MAP) {
				report(file, Kind.ERROR, annotation.pos, "annotation.override");
			}
			if (annotation.type().equals("roj/compiler/api/Ignore")) {
				debugLogger().info("\n\n当前的错误已被忽略\n\n");
				// 临时解决方案
				hasError = false;
			}

			LocalContext lc = LocalContext.get();
			for (Processor processor : processors) {
				if (processor.acceptedAnnotations().contains(annotation.type())) {
					processor.handle(lc, file, node, annotation);
				}
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

	public boolean isSpecEnabled(int specId) {return true;}

	@Override
	public ExprApi getExprApi() {return exprApi;}

	@Override
	public void addResolveListener(int priority, Resolver dtr) {resolveApi.add(new ImplResolver<>(priority, dtr));}

	public void invalidateResolveHelper(IClass file) {
		extraInfos.removeKey(file);
	}

	private final SimpleList<Processor> processors = new SimpleList<>();

	@Override
	public void addGenericProcessor(Processor processor) {
		processors.add(processor);
		if (processor.acceptClasspath()) {
			getClasspathAnnotations();
			for (String annotation : processor.acceptedAnnotations()) {
				for (AnnotatedElement element : repo.annotatedBy(annotation)) {
					processor.handle(LocalContext.get(), element.parent().node(), element.node(), element.annotations().get(annotation));
				}
			}
		}
	}

	@Override
	public void addGenericProcessor(Processor processor, int stage, int prefilter) {
		processors.add(processor);
	}

	@Override
	public void addLocalVariableAnnotationProcessor(String annotation, Stage4Processor<Variable> processor) {
		throw new UnsupportedOperationException();
	}

	public AnnotationRepo getClasspathAnnotations() {
		if (repo == null) {
			repo = new AnnotationRepo();
			for (Library library : _libraries) {
				addAnnotations(library);
			}
			_libraries = null;
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

	final class LCImpl extends LocalContext {
		public LCImpl() {super(GlobalContextApi.this);}

		@Override
		public @Nullable ExprNode getOperatorOverride(@NotNull ExprNode e1, @Nullable ExprNode e2, int operator) {
			ExprNode override = super.getOperatorOverride(e1, e2, operator);
			if (override != null) return override;
			return exprApi.override_getOperatorOverride(this, e1, e2, operator);
		}
	}
}