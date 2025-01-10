package roj.compiler;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.type.TypeHelper;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LibraryZipFile;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.plugin.GlobalContextApi;
import roj.compiler.plugins.TypeDeclPlugin;
import roj.compiler.plugins.UintPlugin;
import roj.compiler.plugins.annotations.AnnotationsPlugin;
import roj.compiler.plugins.asm.AsmPlugin;
import roj.compiler.plugins.eval.Evaluator;
import roj.compiler.plugins.moreop.MoreOpPlugin;
import roj.compiler.resolve.TypeResolver;
import roj.compiler.test.ComparisonChainPlugin;
import roj.compiler.test.TestPlugin;
import roj.compiler.test.TimeUnitPlugin;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/5/20 0020 2:52
 */
public class LavaCompiler {
	public final GlobalContextApi gctx = new GlobalContextApi();
	public final LocalContext lctx = gctx.createLocalContext();
	public final ClassLoader maker = new ClassDefiner(LavaCompiler.class.getClassLoader(), "LavaLambdaLink");
	public final Map<String, ExprNode> injector;
	public String fileName = "<eval>";

	public LavaCompiler() throws IOException {
		LocalContext.set(lctx);
		initDefaultPlugins(gctx);
		injector = gctx.attachment(AsmPlugin.INJECT_PROPERTY);
		LocalContext.set(null);
		((TextDiagnosticReporter) gctx.reporter).errorOnly = true;
	}

	public static final LibraryZipFile Implib_Archive;
	static {
		try {
			Implib_Archive = new LibraryZipFile(IOUtil.getJar(LavaCompiler.class));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static void initDefaultPlugins(GlobalContextApi api) throws IOException {
		api.addLibrary(Implib_Archive);

		Evaluator.pluginInit(api);
		new AsmPlugin().pluginInit(api);
		AnnotationsPlugin.pluginInit(api);
		new MoreOpPlugin().pluginInit(api);
		new TypeDeclPlugin().pluginInit(api);
		new UintPlugin().pluginInit(api);
		new TestPlugin().pluginInit(api);
		TimeUnitPlugin.pluginInit(api);
		new ComparisonChainPlugin().pluginInit(api);

		api.attachment(AsmPlugin.INJECT_PROPERTY).put("咕咕咕", Constant.valueOf("咕咕咕咕，我是🕊"));
	}

	public <T> T linkLambda(Class<T> functionalInterface, String methodStr, String... parName) throws Exception {return linkLambda("roj/lavac/Lambda"+ReflectionUtils.uniqueId(), functionalInterface, methodStr, parName);}
	@SuppressWarnings("unchecked")
	public <T> T linkLambda(String className, Class<T> functionalInterface, String methodStr, String... parName) throws Exception {
		gctx.reset();

		LocalContext.set(lctx);

		Method myMethod = null;
		for (Method method : functionalInterface.getDeclaredMethods()) {
			if ((method.getModifiers()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT)) != (Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT)) continue;
			if (myMethod == null) myMethod = method;
			else throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");
		}
		if (myMethod == null) throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");

		CompileUnit u = new CompileUnit(fileName, methodStr+"}");

		u.version = CompileUnit.JavaVersion(8);
		u.name(className);
		u.parent(Bypass.MAGIC_ACCESSOR_CLASS);
		u.addInterface(functionalInterface.getName().replace('.', '/'));
		u.npConstructor();

		gctx.addCompileUnit(u, false);

		TypeResolver tr = u.getTypeResolver();
		tr.setImportAny(true);

		MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, u.name(), myMethod.getName(), TypeHelper.class2asm(myMethod.getParameterTypes(), myMethod.getReturnType()));
		u.methods.add(mn);

		if (parName == null) {
			Parameter[] refPar = myMethod.getParameters();
			parName = new String[refPar.length];
			for (int i = 0; i < refPar.length; i++) parName[i] = refPar[i].getName();
		}

		lctx.setClass(u);
		lctx.lexer.index = 0;
		lctx.lexer.setState(JavaLexer.STATE_EXPR);
		ParseTask.Method(u, mn, Arrays.asList(parName)).parse(lctx);

		lctx.clear();
		LocalContext.set(null);

		for (ConstantData data : gctx.getGeneratedClasses()) {
			ClassDefiner.defineClass(maker, data);
		}

		ClassDefiner.premake(u);
		return (T) ClassDefiner.make(u, maker);
	}
}