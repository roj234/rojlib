package roj.compiler;

import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.type.TypeHelper;
import roj.asmx.AnnotationRepo;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.ast.expr.Constant;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LibraryZipFile;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.asm.AsmHook;
import roj.compiler.plugins.sandbox.SandboxEvaluator;
import roj.compiler.resolve.TypeResolver;
import roj.reflect.ClassDefiner;
import roj.reflect.DirectAccessor;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/5/20 0020 2:52
 */
public class LavaCompiler {
	final GlobalContext ctx = new GlobalContext();
	final LocalContext cache = new LocalContext(ctx);

	public LavaCompiler() throws IOException {
		File ImpLib = Helpers.getJarByClass(LavaCompiler.class);
		ctx.addLibrary(new LibraryZipFile(ImpLib));

		AnnotationRepo repo = new AnnotationRepo();
		repo.add(ImpLib);

		AsmHook hook = AsmHook.init(ctx);
		hook.injectedProperties.put("咕咕咕", Constant.valueOf("咕咕咕咕，我是🕊"));
		SandboxEvaluator.init(ctx, repo);
	}

	public static void main(String[] args) throws Exception {
		LavaCompiler compiler = new LavaCompiler();

		System.out.println("Lava编译器v"+Lavac.VERSION+" 交互式命令行 输入空行来开始编译");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		CharList tmp = new CharList();
		while (true) {
			while (true) {
				String line = br.readLine();
				if (line.isEmpty()) break;
				tmp.append(line).append('\n');
			}

			if (tmp.length() > 0) {
				compiler.linkLambda(Runnable.class, tmp.toString()).run();
				tmp.clear();
			}
		}
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

		CompileUnit u = new CompileUnit("<stdin>");

		u.version = CompileUnit.JavaVersion(8);
		u.name("roj/generated/HelloFromStdin"+ReflectionUtils.uniqueId());
		u.parent(DirectAccessor.MAGIC_ACCESSOR_CLASS);
		u.addInterface(functionalInterface.getName().replace('.', '/'));
		u.npConstructor();

		ctx.addCompileUnit(u);

		u.getLexer().init(methodStr+"}");

		TypeResolver tr = u.getTypeResolver();
		tr.setImportAny(true);

		MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, u.name(), myMethod.getName(), TypeHelper.class2asm(myMethod.getParameterTypes(), myMethod.getReturnType()));
		u.methods.add(mn);

		cache.setClass(u);
		ParseTask.Method(u, mn, Collections.emptyList()).parse();

		LocalContext.set(null);

		for (ConstantData data : ctx.getGeneratedClasses()) {
			data.dump();
			ClassDefiner.defineGlobalClass(data);
		}

		u.dump();
		ClassDefiner.premake(u);
		return (T) ClassDefiner.make(u);
	}
}