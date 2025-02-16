package roj.compiler;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.collect.MyHashMap;
import roj.compiler.context.*;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.plugin.GlobalContextApi;
import roj.compiler.plugins.eval.Constexpr;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.text.DateParser;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;

/**
 * @author solo6975
 * @since 2022/9/16 19:05
 */
public final class Lavac {
	@Constexpr
	public static String getCompileTime() {return DateParser.toLocalTimeString(System.currentTimeMillis());}
	public static String getCurrentTime() {return DateParser.toLocalTimeString(System.currentTimeMillis());}

	public static final String VERSION = "0.15.0[RC] (compiled on "+getCompileTime()+")";

	private int debugOps = 10;
	private GlobalContext ctx;
	private Charset charset;
	private final ArrayList<CompileUnit> CompileUnits = new ArrayList<>();

	public static void main(String[] args) throws IOException, ReflectiveOperationException {
		if (args.length < 1) {
			System.out.println("""
				用法: lavac <配置> <源文件>[,<java文件>|<文件夹>]
				其中, 可能的选项包括:
				      -cache <目录>              指定编译器缓存文件夹的位置
				      -classpath/-cp <目录>      指定查找用户类文件和注释处理程序的位置
				      -d <路径>                  指定放置编译的类文件的位置

				      -encoding <编码>           指定源文件使用的字符编码
				      -g                         选择生成哪些调试信息
				        可用的值有：compiler,lines,vars,source,params 或 all 并以逗号分隔

				      -maxWarn <数值>            最大允许显示的警告数量
				      -maxError <数值>           最大允许显示的错误数量

				      -ctx <class>               指定编译器上下文的全限定名称
				      -T <key> <val>             传递给上下文的参数

				      使用默认上下文时，下列选项一定可用: (未实现)
				      -T LavaFeature +<feat1>[,-<feat2>...] 启用或禁用Lava语言特性

				      -T VMSymbol <路径>                    指定基础符号表的位置
				      -T TargetVersion <发行版>             生成支持不高于特定 JVM 版本的类文件

				      -T sandbox <class1>[,<class2>...]     预编译沙盒白名单, 以逗号分隔的全限定名前缀
				      -T processor <class1>[,<class2>...]   指定实现了 LavaApi[0.10.x] - Processor 的注解处理程序

				      -version                   显示版本信息并退出
				""");
			return;
		}

		String cp = "";
		String bin = null;
		GlobalContext context = null;

		int maxWarn = 100, maxError = 100;

		MyHashMap<String, String> ctxOps = new MyHashMap<>();
		Lavac compiler = new Lavac();

		int i = 0;
		loop:
		for (; i < args.length; i++) {
			switch (args[i]) {
				case "-version" -> {
					System.out.println("lavac "+VERSION);
					System.exit(0);
				}
				case "-encoding" -> compiler.charset = Charset.forName(args[++i]);
				case "-maxWarn" -> maxWarn = Integer.parseInt(args[++i]);
				case "-maxError" -> maxError = Integer.parseInt(args[++i]);
				case "-cp", "-classpath" -> cp = args[++i];
				case "-d" -> bin = args[++i];
				case "-g" -> {
					int debugOps = 0;
					loop1:
					for (var name : TextUtil.split(args[++i], ',')) {
						switch (name.trim()) {
							case "none" -> {debugOps = 0;break loop1;}
							case "compiler" -> debugOps |= 16;
							case "line" -> debugOps |= 1<<LavaFeatures.ATTR_LINE_NUMBERS;
							case "vars" -> debugOps |= 1<<LavaFeatures.ATTR_LOCAL_VARIABLES;
							case "params" -> debugOps |= 1<<LavaFeatures.ATTR_METHOD_PARAMETERS;
							case "source" -> debugOps |= 1<<LavaFeatures.ATTR_SOURCE_FILE;
							case "all" -> debugOps |= 15;
							default -> throw new IllegalStateException("Unexpected value: "+name);
						}
					}
					compiler.debugOps = debugOps;
				}
				case "-ctx" -> context = (GlobalContext) Class.forName(args[++i]).newInstance();
				case "-T" -> ctxOps.put(args[++i], args[++i]);
				default -> {break loop;}
			}
		}

		while (i < args.length) compiler.addSource(new File(args[i++]));
		if (compiler.CompileUnits.isEmpty()) {
			System.err.println("错误：没有源文件");
			return;
		}

		compiler.ctx = context == null ? new GlobalContextApi() : context;
		GlobalContextApi api = (GlobalContextApi) compiler.ctx;

		for (int j = 0; j < 3; j++) {
			if (((1 << j) & compiler.debugOps) != 0)
				api.features.add(j);
		}

		api.features.add(LavaFeatures.ATTR_INNER_CLASS);
		api.features.add(LavaFeatures.ATTR_STACK_FRAME);
		api.features.add(LavaFeatures.OPTIONAL_SEMICOLON);
		api.features.add(LavaFeatures.VERIFY_FILENAME);
		api.features.add(LavaFeatures.OMISSION_NEW);
		api.features.add(LavaFeatures.SHARED_STRING_CONCAT);
		api.features.add(LavaFeatures.NESTED_MEMBER);
		api.features.add(LavaFeatures.SEALED_ENUM);

		// InitDefaultPlugins Requires LocalContext
		LocalContext.set(compiler.ctx.createLocalContext());
		LavaCompiler.initDefaultPlugins(api);

		for (var path : TextUtil.split(cp, File.pathSeparatorChar)) compiler.addClass(new File(path));

		File dst = bin == null ? new File("Lava.jar") : new File(bin);

		var reporter = new TextDiagnosticReporter(maxError, maxWarn, 0);
		compiler.ctx.reporter = reporter;

		boolean ok = compiler.compile(dst);

		reporter.printSum();
		System.out.println("编译状况="+ok);

		if (ok) {
			try {
				var classLoader = new ClassDefiner(Lavac.class.getClassLoader(), "lavac test");
				try (var za = new ZipFile(dst)) {
					for (ZEntry entry : za.entries()) {
						ClassDefiner.defineClass(classLoader, null, IOUtil.getSharedByteBuf().readStreamFully(za.getStream(entry)));
					}

					((Runnable) Class.forName("Test", true, classLoader).newInstance()).run();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.exit(ok ? 0 : -1);
	}

	private boolean compile(File output) {
		var files = CompileUnits;
		boolean done = false;
		try {
			for (int i = files.size() - 1; i >= 0; i--) {
				if (!files.get(i).S1_Struct())
					// 下列可能性
					// 文件是空的
					// package-info
					// module-info
					files.remove(i);
			}
			if (ctx.hasError()) return false;
			// TODO 一个源文件生成的所有CompileUnit都应该由同一个线程处理
			ctx.addGeneratedCompileUnits(files);
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2_ResolveSelf();
			}
			if (ctx.hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S2_ResolveRef();
			}
			if (ctx.hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S3_Annotation();
			}
			if (ctx.hasError()) return false;
			for (int i = 0; i < files.size(); i++) {
				files.get(i).S4_Code();
			}
			if (ctx.hasError()) return false;
			// write

			try (var zfw = new ZipFileWriter(output)) {
				for (int i = 0; i < files.size(); i++) {
					var data = files.get(i);
					data.S5_noStore();
					zfw.beginEntry(new ZEntry(data.name().concat(".class")));
					if (data.name().equals("Test")) {
						// FIXME NOVERIFY
						data.parent("java/lang/🔓_IL🐟");
					}
					ByteList x = Parser.toByteArrayShared(data);
					if (data.name().equals("Test")) {
						System.out.println(Parser.parse(x));
					}
					x.writeToStream(zfw);
				}
				for (var data : ctx.getGeneratedClasses()) {
					zfw.beginEntry(new ZEntry(data.name().concat(".class")));
					Parser.toByteArrayShared(data).writeToStream(zfw);
				}

				zfw.setComment("Lavac v"+VERSION);
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
			IOUtil.findAllFiles(file, file1 -> {
				addSource(file1);
				return false;
			});
		} else {
			if ("java".equals(IOUtil.extensionName(file.getName()))) {
				String code;
				try (var r = TextReader.from(file, charset)) {
					code = IOUtil.read(r);
				} catch (IOException e) {
					System.err.println("错误：无法读取源文件"+file+"的内容");
					e.printStackTrace();
					return;
				}
				CompileUnits.add(new LavaCompileUnit(file.getName(), code));
			}
		}
	}

	private void addClass(File file) {
		if (file.isDirectory()) {
			IOUtil.findAllFiles(file, file1 -> {
				addClass(file1);
				return false;
			});
		}

		String s = IOUtil.extensionName(file.getName());
		if (s.equals("zip") || s.equals("jar")) {
			try {
				ctx.addLibrary(new LibraryZipFile(file));
			} catch (IOException e) {
				System.err.println("错误：无法读取类文件"+file+"的内容");
				e.printStackTrace();
			}
		}
	}
}