package roj.compiler;

import roj.asm.Opcodes;
import roj.asm.tree.MethodNode;
import roj.asm.type.TypeHelper;
import roj.compiler.ast.block.ParseTask;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LibraryZipFile;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.TypeResolver;
import roj.reflect.FastInit;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Collections;

/**
 * @author Roj234
 * @since 2024/5/20 0020 2:52
 */
public class LavaCompiler {
	GlobalContext ctx;
	LocalContext cache;

	public LavaCompiler() throws IOException {
		GlobalContext ctx = new GlobalContext();
		ctx.addLibrary(new LibraryZipFile(Helpers.getJarByClass(LavaCompiler.class)));

		this.ctx = ctx;
		this.cache = new LocalContext(ctx);
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Lava编译器v0.8 交互式命令行 输入空行来开始编译");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		CharList tmp = new CharList();
		while (true) {
			String line = br.readLine();
			if (line.isEmpty()) break;
			tmp.append(line).append('\n');
		}
		new LavaCompiler().linkLambda(Runnable.class, tmp.toString()).run();
	}

	@SuppressWarnings("unchecked")
	public <T> T linkLambda(Class<T> functionalInterface, String methodStr) throws Exception {
		LocalContext.set(cache);

		Method myMethod = null;
		for (Method method : functionalInterface.getDeclaredMethods()) {
			if ((method.getModifiers()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC)) != Opcodes.ACC_PUBLIC) continue;
			if (myMethod == null) myMethod = method;
			else throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");
		}
		if (myMethod == null) throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");

		CompileUnit u = new CompileUnit("<stdin>", ctx);

		u.name("roj/generated/HelloFromStdin"+ReflectionUtils.uniqueId());
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

		FastInit.prepare(u);
		return (T) FastInit.make(u);
	}
}