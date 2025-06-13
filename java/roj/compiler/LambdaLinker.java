package roj.compiler;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.StringAttribute;
import roj.asm.type.TypeHelper;
import roj.compiler.api.Compiler;
import roj.compiler.ast.ParseTask;
import roj.compiler.ast.expr.Expr;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.library.JarLibrary;
import roj.compiler.plugins.TypeDeclPlugin;
import roj.compiler.plugins.UintPlugin;
import roj.compiler.plugins.annotations.AnnotationsPlugin;
import roj.compiler.plugins.asm.AsmPlugin;
import roj.compiler.plugins.eval.Evaluator;
import roj.compiler.plugins.moreop.MoreOpPlugin;
import roj.compiler.resolve.ImportList;
import roj.compiler.test.ComparisonChainPlugin;
import roj.compiler.test.TestPlugin;
import roj.compiler.test.TimeUnitPlugin;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.reflect.Reflection;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Map;

/**
 * @author Roj234
 * @since 2024/5/20 2:52
 */
public class LambdaLinker {
	public final LavaCompiler compiler = new LavaCompiler();
	public final CompileContext ctx = compiler.createContext();
	public final ClassLoader maker = new ClassDefiner(LambdaLinker.class.getClassLoader(), "LavaLambdaLinker");
	public final Map<String, Expr> injector;
	public String fileName = "<eval>";

	public LambdaLinker() throws IOException {
		CompileContext.set(ctx);
		initDefaultPlugins(compiler);
		CompileContext.set(null);

		injector = compiler.attachment(AsmPlugin.INJECT_PROPERTY);
		compiler.reporter = new TextDiagnosticReporter(1,1,1);
		compiler.features.add(Compiler.EMIT_SOURCE_FILE);
		compiler.features.add(Compiler.EMIT_LINE_NUMBERS);
		compiler.features.add(Compiler.EMIT_INNER_CLASS);
		compiler.features.add(Compiler.EMIT_STACK_FRAME);
		compiler.features.add(Compiler.OPTIONAL_SEMICOLON);
		compiler.features.add(Compiler.OMISSION_NEW);
		compiler.features.add(Compiler.SHARED_STRING_CONCAT);
		compiler.features.add(Compiler.OMIT_CHECKED_EXCEPTION);
	}

	public static final JarLibrary LIBRARY_SELF;
	static {
		try {
			LIBRARY_SELF = new JarLibrary(IOUtil.getJar(LambdaLinker.class));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	static void initDefaultPlugins(LavaCompiler api) throws IOException {
		api.addLibrary(LIBRARY_SELF);

		Evaluator.pluginInit(api);
		new AsmPlugin().pluginInit(api);
		AnnotationsPlugin.pluginInit(api);
		new MoreOpPlugin().pluginInit(api);
		new TypeDeclPlugin().pluginInit(api);
		new UintPlugin().pluginInit(api);
		new TestPlugin().pluginInit(api);
		TimeUnitPlugin.pluginInit(api);
		new ComparisonChainPlugin().pluginInit(api);

		api.attachment(AsmPlugin.INJECT_PROPERTY).put("咕咕咕", Expr.valueOf("咕咕咕咕，我是🕊"));
	}

	public <T> T linkLambda(Class<T> functionalInterface, String methodStr, String... parName) throws Exception {return linkLambda("roj/lavac/Lambda"+Reflection.uniqueId(), functionalInterface, methodStr, parName);}
	@SuppressWarnings("unchecked")
	public <T> T linkLambda(String className, Class<T> functionalInterface, String methodStr, String... parName) throws Exception {
		compiler.reset();

		CompileContext.set(ctx);

		Method myMethod = null;
		for (Method method : functionalInterface.getDeclaredMethods()) {
			if ((method.getModifiers()&(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC|Opcodes.ACC_ABSTRACT)) != (Opcodes.ACC_PUBLIC|Opcodes.ACC_ABSTRACT)) continue;
			if (myMethod == null) myMethod = method;
			else throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");
		}
		if (myMethod == null) throw new IllegalArgumentException(functionalInterface.getName()+"看起来不像FunctionalInterface");

		CompileUnit u = new JavaCompileUnit(fileName, methodStr + "}");

		u.version = CompileUnit.JavaVersion(8);
		u.name(className);
		u.addInterface(functionalInterface.getName().replace('.', '/'));
		u.npConstructor();
		u.addAttribute(new StringAttribute(Attribute.SourceFile, "<eval>"));

		compiler.addCompileUnit(u, false);

		ImportList tr = u.getImportList();
		tr.setImportAny(true);

		MethodNode mn = new MethodNode(Opcodes.ACC_PUBLIC, u.name(), myMethod.getName(), TypeHelper.class2asm(myMethod.getParameterTypes(), myMethod.getReturnType()));
		u.methods.add(mn);

		if (parName == null) {
			Parameter[] refPar = myMethod.getParameters();
			parName = new String[refPar.length];
			for (int i = 0; i < refPar.length; i++) parName[i] = refPar[i].getName();
		}

		ctx.setClass(u);
		ctx.lexer.index = 0;
		ctx.lexer.setState(Tokens.STATE_EXPR);
		ParseTask.Method(u, mn, Arrays.asList(parName)).parse(ctx);

		ctx.clear();
		CompileContext.set(null);

		for (ClassNode data : compiler.getGeneratedClasses()) {
			ClassDefiner.defineClass(maker, data);
		}

		return (T) ClassDefiner.newInstance(u, maker);
	}
}