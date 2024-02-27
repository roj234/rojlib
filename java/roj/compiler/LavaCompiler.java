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
import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/5/20 0020 2:52
 */
public class LavaCompiler {
	final GlobalContextApi ctx = new GlobalContextApi();
	final LocalContext cache = ctx.createLocalContext();
	final ClassLoader loader = new ClassDefiner(LavaCompiler.class.getClassLoader(), "LavaLambdaLink");

	public LavaCompiler() throws IOException {initDefaultPlugins(ctx);}

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
	public <T> T linkLambda(Class<T> functionalInterface, String methodStr) throws Exception {
		ctx.reset();

		LocalContext.set(cache);

		Method myMethod = null;
		for (Method method : functionalInterface.getDeclaredMethods()) {
			if ((method.getModifiers()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC)) != Opcodes.ACC_PUBLIC) continue;
			if (myMethod == null) myMethod = method;
			else throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");
		}
		if (myMethod == null) throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");

		CompileUnit u = new CompileUnit("<stdin>", methodStr+"}");

		u.version = CompileUnit.JavaVersion(8);
		u.name("roj/generated/HelloFromStdin"+ReflectionUtils.uniqueId());
		u.parent(Bypass.MAGIC_ACCESSOR_CLASS);
		u.addInterface(functionalInterface.getName().replace('.', '/'));
		u.npConstructor();

		ctx.addCompileUnit(u, false);

		TypeResolver tr = u.getTypeResolver();
		tr.setImportAny(true);

		MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, u.name(), myMethod.getName(), TypeHelper.class2asm(myMethod.getParameterTypes(), myMethod.getReturnType()));
		u.methods.add(mn);

		cache.setClass(u);
		ParseTask.Method(u, mn, Collections.emptyList()).parse(cache);

		LocalContext.set(null);

		for (ConstantData data : ctx.getGeneratedClasses()) {
			ClassDefiner.defineClass(loader, data);
		}

		ClassDefiner.premake(u);
		return (T) ClassDefiner.make(u, loader);
	}
}