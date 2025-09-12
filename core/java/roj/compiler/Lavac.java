package roj.compiler;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.AsmCache;
import roj.asm.ClassNode;
import roj.compiler.api.Compiler;
import roj.compiler.api.CompilerPlugin;
import roj.compiler.api.Processor;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.library.JarLibrary;
import roj.compiler.plugins.eval.Constexpr;
import roj.io.IOUtil;
import roj.text.DateTime;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * @author solo6975
 * @since 2022/9/16 19:05
 */
public final class Lavac extends LavaCompiler {
	@Constexpr
	public static String getCompileTime() {return DateTime.toLocalTimeString(System.currentTimeMillis());}
	public static String getCurrentTime() {return DateTime.toLocalTimeString(System.currentTimeMillis());}

	public static final String VERSION = "1.0.5-alpha (compiled on "+getCompileTime()+")";

	private Charset charset;
	private final ArrayList<CompileUnit> CompileUnits = new ArrayList<>();

	public static void main(String[] args) throws IOException, ReflectiveOperationException {
		if (args.length < 1) {
			System.out.println("""
				用法: lavac <配置> <源文件>[,<java文件>|<文件夹>]
				其中, 可能的选项包括:
				      -cache <目录>              指定编译器缓存文件夹的位置
				      -classpath/-cp <目录>      指定查找用户类文件的位置
				      -module                    使用模块编译模式，在上述文件夹中查找模块 (未实现)
				      -d <路径>                  指定放置编译的类文件的位置

				      -encoding <编码>           指定源文件使用的字符编码
				      -g                         选择生成哪些调试信息
				        可用的值有：compiler,lines,vars,source,params 或 all 并以逗号分隔

				      -maxWarn <数值>            最大允许显示的警告数量
				      -maxError <数值>           最大允许显示的错误数量
				      -Werror                    出现警告时终止编译
				      -nowarn                    不生成任何警告

				      -features +<feat1>[,-<feat2>]   启用或禁用Lava语言特性
				      -target <发行版>                生成支持不高于特定 JVM 版本的类文件
				        你可以在任何目标版本中使用编译器-only特性，例如var
				      -allow <class1>[,<class2>]      预编译沙盒白名单, 以逗号分隔的全限定名前缀
				      -processor <class1>[,<class2>]  指定实现了LavaAPI的注解处理程序的全限定名称
				      -plugin <class1>[,<class2>]     指定实现了LavaAPI的编译器插件的全限定名称
				        请注意，注解处理程序和编译器插件将在JVM的-cp选项中寻找，编译器的-cp选项只影响编译
				        这么做不仅简单，同时还能提升安全性，防止参数注入攻击

				      -version                   显示版本信息并退出
				""");
			return;
		}

		String cp = "";
		String bin = null;

		int maxWarn = 100, maxError = 100, warnOps = 0;
		int debugOps = 15;

		var lookup = MethodHandles.lookup();
		var compiler = new Lavac();

		compiler.features.add(Compiler.EMIT_INNER_CLASS);
		compiler.features.add(Compiler.OPTIONAL_SEMICOLON);
		compiler.features.add(Compiler.VERIFY_FILENAME);
		compiler.features.add(Compiler.OMISSION_NEW);
		compiler.features.add(Compiler.SHARED_STRING_CONCAT);
		compiler.features.add(Compiler.OMIT_CHECKED_EXCEPTION);

		var hasPlugins = false;

		CompileContext.set(compiler.createContext());
		int i = 0;
		loop:
		for (; i < args.length; i++) {
			switch (args[i]) {
				case "-run" -> {
					try {
						new LambdaLinker().linkLambda(Runnable.class, args[++i]).run();
					} catch (Exception e) {
						Helpers.athrow(e);
					}
				}
				case "-version" -> {
					System.out.println("lavac "+VERSION);
					System.exit(0);
				}
				case "-encoding" -> compiler.charset = Charset.forName(args[++i]);
				case "-maxWarn" -> maxWarn = Integer.parseInt(args[++i]);
				case "-maxError" -> maxError = Integer.parseInt(args[++i]);
				case "-nowarn" -> warnOps |= 1;
				case "-Werror" -> warnOps |= 2;
				case "-cache" -> System.setProperty("roj.compiler.symbolCache", args[++i]);
				case "-cp", "-classpath" -> cp = args[++i];
				case "-d" -> bin = args[++i];
				case "-g" -> {
					loop1:
					for (var name : TextUtil.split(args[++i], ',')) {
						switch (name.trim()) {
							case "none" -> {debugOps = 0;break loop1;}
							case "compiler" -> debugOps |= 16;
							case "line" -> debugOps |= 1<< Compiler.EMIT_LINE_NUMBERS;
							case "vars" -> debugOps |= 1<< Compiler.EMIT_LOCAL_VARIABLES;
							case "params" -> debugOps |= 1<< Compiler.EMIT_METHOD_PARAMETERS;
							case "source" -> debugOps |= 1<< Compiler.EMIT_SOURCE_FILE;
							case "all" -> debugOps |= 15;
							default -> throw new IllegalStateException("Unexpected value: "+name);
						}
					}
				}
				case "-target" -> compiler.maxVersion = ClassNode.JavaVersion(Integer.parseInt(args[++i]));
				case "-features" -> {
					for (var feat : TextUtil.split(args[++i], ',')) {
						int value;
						try {
							value = (int) lookup.findStaticVarHandle(Compiler.class, feat.substring(1), int.class).get();
						} catch (IllegalAccessException | NoSuchFieldException e1) {
							throw new RuntimeException("找不到这个特性: "+feat);
						}

						if (feat.charAt(0) == '+') {
							compiler.features.add(value);
						} else if (feat.charAt(0) == '-') {
							compiler.features.remove(value);
						}
					}
				}
				case "-processor" -> {
					for (var proc : TextUtil.split(args[++i], ',')) {
						Processor processor;
						try {
							processor = (Processor) lookup.findConstructor(lookup.findClass(proc), MethodType.methodType(void.class)).invoke();
						} catch (Throwable e1) {
							throw new RuntimeException(e1);
						}
						compiler.addAnnotationProcessor(processor);
					}
				}
				case "-plugin" -> {
					hasPlugins = true;
					for (var proc : TextUtil.split(args[++i], ',')) {
						try {
							var type = lookup.findClass(proc);
							var methodType = MethodType.methodType(void.class, Compiler.class);
							var annotation = type.getAnnotation(CompilerPlugin.class);
							if (annotation.instance()) {
								Object o = lookup.findConstructor(type, MethodType.methodType(void.class)).invoke();
								lookup.findVirtual(type, annotation.init(), methodType).invoke(o, compiler);
							} else {
								lookup.findStatic(type, annotation.init(), methodType).invoke(compiler);
							}
						} catch (Throwable e1) {
							throw new RuntimeException(e1);
						}
					}
				}
				case "-allow" -> {
					for (String packageOrTypeName : TextUtil.split(args[++i], ',')) {
						compiler.addSandboxWhitelist(packageOrTypeName, false);
					}
				}
				default -> {break loop;}
			}
		}
		CompileContext.set(null);

		while (i < args.length) compiler.addSource(new File(args[i++]));
		if (compiler.CompileUnits.isEmpty()) {
			System.err.println("错误：没有源文件");
			return;
		}

		var reporter = new TextDiagnosticReporter(maxError, maxWarn, warnOps);
		compiler.reporter = reporter;

		for (int j = 0; j < 4; j++) {
			if (((1 << j) & debugOps) != 0)
				compiler.features.add(j);
		}

		if (!hasPlugins) LambdaLinker.initDefaultPlugins(compiler);

		for (var path : TextUtil.split(cp, File.pathSeparatorChar))
			compiler.addClass(new File(path));

		File dst = bin == null ? new File("output.jar") : new File(bin);

		CompileContext.set(compiler.createContext());
		boolean ok = compiler.compile(dst);
		CompileContext.set(null);
		reporter.summary();

		if (ok) {
			System.out.println("编译成功");
			if (bin == null) {
				try (var archive = new ZipFile(dst)) {
					var scl = new ClassLoader(Lavac.class.getClassLoader()) {
						@Override
						protected Class<?> findClass(String name) throws ClassNotFoundException {
							String klass = name.replace('.', '/').concat(".class");
							ZEntry entry = archive.getEntry(klass);
							if (entry == null) throw new ClassNotFoundException(name);

							ByteList buf = null;
							try {
								buf = new ByteList().readStreamFully(archive.getStream(entry));
							} catch (IOException e) {
								throw new RuntimeException(e);
							}
							var clazz = defineClass(name, buf.list, 0, buf.wIndex());
							buf.release();
							return clazz;
						}
					};
					Class<?> test = Class.forName(compiler.CompileUnits.get(0).name().replace('/', '.'), true, scl);
					lookup.findStatic(test, "main", MethodType.methodType(void.class, String[].class)).invoke((Object) new String[0]);
					System.out.println("执行成功");
				} catch (Throwable e) {
					e.printStackTrace();
				}
			}
		}

		System.exit(ok ? 0 : -1);
	}

	private boolean compile(File output) {
		var files = CompileUnits;
		boolean done = false;
		try {
			for (int i = files.size() - 1; i >= 0; i--) {
				// 如果解析失败或者不需要解析
				if (!files.get(i).S1parseStruct()) files.remove(i);
			}
			if (hasError()) return false;
			getParsableUnits(files);
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2p1resolveName();
			}
			if (hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2p2resolveType();
			}
			if (hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2p3resolveMethod();
			}
			if (hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S3processAnnotation();
			}
			if (hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S4parseCode();
			}
			if (hasError()) return false;
			// write

			try (var zfw = new ZipFileWriter(output)) {
				for (int i = 0; i < files.size(); i++) {
					var data = files.get(i);
					data.S5serialize();
					zfw.beginEntry(new ZEntry(data.name().concat(".class")));
					ByteList x = AsmCache.toByteArrayShared(data);
					x.writeToStream(zfw);
					if (data.name().equals("Test")) {
						System.out.println(ClassNode.parseAll(x));
					}
				}
				for (var data : getGeneratedClasses()) {
					zfw.beginEntry(new ZEntry(data.name().concat(".class")));
					AsmCache.toByteArrayShared(data).writeToStream(zfw);
				}

				zfw.setComment("lavac "+VERSION);
			}
			done = true;
		} catch (Exception e) {
			System.err.println("发生了意料之外的编译器内部错误");
			e.printStackTrace();
		}

		return done;
	}

	private void addSource(File file) {
		if (file.isDirectory()) {
			IOUtil.listFiles(file, file1 -> {
				addSource(file1);
				return false;
			});
		} else {
			if (IOUtil.extensionName(file.getName()).endsWith("ava")) {
				String code;
				try (var r = TextReader.from(file, charset)) {
					code = IOUtil.read(r);
				} catch (IOException e) {
					System.err.println("错误：无法读取源文件"+file+"的内容");
					e.printStackTrace();
					return;
				}
				CompileUnits.add(new JavaCompileUnit(file.getName(), code));
			}
		}
	}

	private void addClass(File file) {
		if (file.isDirectory()) {
			IOUtil.listFiles(file, file1 -> {
				addClass(file1);
				return false;
			});
		}

		String s = IOUtil.extensionName(file.getName());
		if (s.equals("zip") || s.equals("jar")) {
			try {
				addLibrary(new JarLibrary(file));
			} catch (IOException e) {
				System.err.println("错误：无法读取类文件"+file+"的内容");
				e.printStackTrace();
			}
		}
	}
}