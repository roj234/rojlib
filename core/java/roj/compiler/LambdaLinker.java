package roj.compiler;

import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.StringAttribute;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asmx.mapper.ParamNameMapper;
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
import roj.compiler.plugins.stc.StreamChainPlugin;
import roj.compiler.test.ComparisonChainPlugin;
import roj.compiler.test.TestPlugin;
import roj.compiler.test.TimeUnitPlugin;
import roj.io.IOUtil;
import roj.reflect.Reflection;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/5/20 2:52
 */
public class LambdaLinker {
	public final LavaCompiler compiler = new LavaCompiler();
	public final CompileContext ctx;
	public final ClassLoader classLoader = Reflection.newClassDefiner("LavaLambdaLinker", LambdaLinker.class.getClassLoader());
	public final Map<String, Expr> injectedExpressions;
	public String fileName = "<eval>";

	public LambdaLinker() {
		CompileContext.set(compiler.createContext());
		initDefaultPlugins(compiler);
		CompileContext.set(null);

		injectedExpressions = compiler.attachment(AsmPlugin.INJECT_PROPERTY);
		compiler.reporter = new TextDiagnosticReporter(1,1,1);
		compiler.features.add(Compiler.EMIT_SOURCE_FILE);
		compiler.features.add(Compiler.EMIT_LINE_NUMBERS);
		compiler.features.add(Compiler.EMIT_INNER_CLASS);
		compiler.features.add(Compiler.OPTIONAL_SEMICOLON);
		compiler.features.add(Compiler.OMISSION_NEW);
		compiler.features.add(Compiler.SHARED_STRING_CONCAT);
		compiler.features.add(Compiler.OMIT_CHECKED_EXCEPTION);

		ctx = compiler.createContext();
	}

	public static final JarLibrary LIBRARY_SELF;
	static {
		try {
			LIBRARY_SELF = new JarLibrary(IOUtil.getJar(LambdaLinker.class));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Deprecated
	static void initDefaultPlugins(LavaCompiler api) {
		api.addLibrary(LIBRARY_SELF);

		Evaluator.pluginInit(api);
		new AsmPlugin().pluginInit(api);
		AnnotationsPlugin.pluginInit(api);
		new MoreOpPlugin().pluginInit(api);
		new TypeDeclPlugin().pluginInit(api);
		new UintPlugin().pluginInit(api);
		new TestPlugin().pluginInit(api);
		TimeUnitPlugin.pluginInit(api);
		new StreamChainPlugin().pluginInit(api);
		new ComparisonChainPlugin().pluginInit(api);

		api.attachment(AsmPlugin.INJECT_PROPERTY).put("咕咕咕", Expr.valueOf("咕咕咕咕，我是🕊"));
	}

	public <T> T linkLambda(Class<T> functionalInterface, String methodStr, String... parName) {return linkLambda("roj/lavac/Lambda"+Reflection.uniqueId(), Type.getType(functionalInterface), methodStr, parName);}
	@SuppressWarnings("unchecked")
	public <T> T linkLambda(String className, IType functionalInterfaceType, String methodStr, String... parName) {
		ClassNode node = compiler.resolve(functionalInterfaceType.owner());
		MethodNode lambdaMethod = compiler.link(node).getLambdaMethod();
		if (lambdaMethod == null)
			throw new IllegalArgumentException(functionalInterfaceType+" is not a FunctionalInterface");

		CompileUnit unit = new JavaCompileUnit(fileName, methodStr + "}");

		unit.version = CompileUnit.JavaVersion(8);
		unit.name(className);
		unit.addInterface(functionalInterfaceType.owner());

		if (functionalInterfaceType.genericType() != IType.STANDARD_TYPE) {
			var sign = unit.getSignature();
			sign.values.add(Signature.placeholder());
			sign.values.add(functionalInterfaceType);
		}

		unit.defaultConstructor();
		unit.addAttribute(new StringAttribute(Attribute.SourceFile, fileName));
		unit.getImportList().setImportAny(true);

		MethodNode impl = new MethodNode(Opcodes.ACC_PUBLIC, unit.name(), lambdaMethod.name(), lambdaMethod.rawDesc());
		unit.methods.add(impl);

		List<String> list;
		if (parName == null) {
			list = Objects.requireNonNull(ParamNameMapper.getParameterNames(node.cp, lambdaMethod), "Could not get parameter name");
		} else {
			list = Arrays.asList(parName);
		}

		compiler.addCompileUnit(unit, false);
		CompileContext.set(ctx);
		ctx.setClass(unit);
		ctx.lexer.index = 0;
		ctx.lexer.setState(JavaTokenizer.STATE_EXPR);

		try {
			ParseTask.method(unit, impl, list).parse(ctx);
			// anonymous classes, etc
			for (ClassNode data : compiler.getGeneratedClasses()) {
				Reflection.defineClass(classLoader, data);
			}
		} catch (Throwable e) {
			throw new IllegalArgumentException("Exception compiling lambda for "+functionalInterfaceType, e);
		} finally {
			ctx.clear();
			CompileContext.set(null);
			compiler.reset();
		}

		return (T) Reflection.createInstance(classLoader, unit);
	}
}