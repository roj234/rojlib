package roj.compiler;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.type.TypeHelper;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.Constant;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.LibraryZipFile;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.plugins.GlobalContextApi;
import roj.compiler.plugins.annotations.AnnotationProcessor1;
import roj.compiler.plugins.annotations.AnnotationProcessor2;
import roj.compiler.plugins.asm.AsmHook;
import roj.compiler.plugins.constant.ConstantEvaluator;
import roj.compiler.resolve.TypeResolver;
import roj.compiler.test.CandyTestPlugin;
import roj.io.IOUtil;
import roj.reflect.Bypass;
import roj.reflect.ClassDefiner;
import roj.reflect.ReflectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2024/5/20 0020 2:52
 */
public class LavaCompiler {
	public final GlobalContextApi gctx = new GlobalContextApi();
	public final LocalContext lctx = gctx.createLocalContext();
	public final ClassLoader maker = new ClassDefiner(LavaCompiler.class.getClassLoader(), "LavaLambdaLink");

	public LavaCompiler() throws IOException {
		LocalContext.set(lctx);
		initDefaultPlugins(gctx);
		LocalContext.set(null);
		((TextDiagnosticReporter) gctx.listener).errorOnly = true;
	}

	static void initDefaultPlugins(GlobalContextApi ctx) throws IOException {
		ctx.addLibrary(new LibraryZipFile(IOUtil.getJar(LavaCompiler.class)));

		AsmHook hook = AsmHook.init(ctx);
		hook.injectedProperties.put("咕咕咕", Constant.valueOf("咕咕咕咕，我是🕊"));
		ConstantEvaluator.init(ctx);

		ctx.addGenericProcessor(new AnnotationProcessor1());
		ctx.addGenericProcessor(new AnnotationProcessor2());

		new CandyTestPlugin().register(ctx);
	}

	@SuppressWarnings("unchecked")
	public <T> T linkLambda(Class<T> functionalInterface, String methodStr, String... parName) throws Exception {
		gctx.reset();

		LocalContext.set(lctx);

		Method myMethod = null;
		for (Method method : functionalInterface.getDeclaredMethods()) {
			if ((method.getModifiers()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT)) != (Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT)) continue;
			if (myMethod == null) myMethod = method;
			else throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");
		}
		if (myMethod == null) throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");

		CompileUnit u = new CompileUnit("<stdin>", methodStr+"}");

		u.version = CompileUnit.JavaVersion(8);
		u.name("roj/lavac/Lambda"+ReflectionUtils.uniqueId());
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