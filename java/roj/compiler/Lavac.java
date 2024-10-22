package roj.compiler;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFileWriter;
import roj.asm.Parser;
import roj.asm.tree.ConstantData;
import roj.collect.MyHashMap;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LibraryZipFile;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.TextDiagnosticReporter;
import roj.compiler.plugins.GlobalContextApi;
import roj.compiler.plugins.eval.Constant;
import roj.io.IOUtil;
import roj.text.ACalendar;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2022/9/16 19:05
 */
public final class Lavac {
	@Constant
	public static String getCompileTime() {return ACalendar.toLocalTimeString(System.currentTimeMillis());}
	public static String getCurrentTime() {return ACalendar.toLocalTimeString(System.currentTimeMillis());}

	public static final String VERSION = "0.12.0[RC] (compiled on "+getCompileTime()+")";

	int debugOps = 10;
	GlobalContext ctx;
	final ArrayList<CompileUnit> CompileUnits = new ArrayList<>();

	public static void main(String[] args) throws IOException, ReflectiveOperationException {
		if (args.length < 1) {
			System.out.println("""
				用法: lavac <options> <source>
				            其中, 可能的选项包括:
				            -rtpath <路径>             覆盖JVM类文件的位置 (未实现)
				            -classpath/-cp <路径>      指定查找用户类文件和注释处理程序的位置

				            -d <目录>                  指定放置生成的类文件的位置
				            -s <目录>                  指定放置生成的源文件的位置 (未实现)
				            -h <目录>                  指定放置生成的头文件的位置 (未实现)

				            -encoding <编码>           指定源文件使用的字符编码 (未实现)
				            -g                         选择生成哪些调试信息 (未实现)
				              可用的值有：lines,vars,source,params 并以逗号分隔
				            -debug                     生成调试日志 (未实现)

				            -lap <class1>[,<class2>...] 实现了Lavac Api 1.0的注解处理程序 (未实现)

				            -precomp <flag1>[,<flag2>...] 预编译沙盒白名单 (未实现)

				            -GC <class>                设定全局上下文的类名称
				            -T <key> <val>             传递给上下文的参数 (未实现)

				            -EnableSpec/DisableSpec ID 启用或禁用Lavac特性 (未实现)
				            -target <发行版>           生成支持不高于特定 Java 版本的类文件 (未实现)

				            -version                   版本信息
				""");
			return;
		}
		String cp = "";
		String bin = null;

		String gcName = "roj.compiler.plugins.GlobalContextApi";

		MyHashMap<String, String> tweakerOps = new MyHashMap<>();

		int maxWarn = 100, maxError = 100;

		Lavac compiler = new Lavac();

		int i = 0;
		for (; i < args.length; i++) {
			String argi = args[i];
			if (!argi.startsWith("-")) break;
			switch (argi) {
				case "-version" -> {
					System.out.println("lavac "+VERSION);
					System.exit(0);
				}
				case "-maxWarn" -> maxWarn = Integer.parseInt(args[++i]);
				case "-maxError" -> maxError = Integer.parseInt(args[++i]);
				case "-cp", "-classpath" -> cp = args[++i];
				case "-d" -> bin = args[++i];
				case "-g" -> {
					compiler.debugOps &= 1;
					o:
					for (String s : TextUtil.split(new ArrayList<>(), args[++i], ',')) {
						switch (s.trim()) {
							case "none":
								compiler.debugOps &= 1;
								break o;
							case "line":
								compiler.debugOps |= 2;
								break;
							case "vars":
								compiler.debugOps |= 4;
								break;
							case "source":
								compiler.debugOps |= 8;
								break;
							case "all":
								compiler.debugOps |= 14;
								break;
							default:
								throw new RuntimeException("Invalid config[" + i);
						}
					}
				}
				case "-debug" -> compiler.debugOps |= 1;
				case "-GC" -> gcName = args[++i];
				case "-T" -> tweakerOps.put(args[++i], args[++i]);
				default -> throw new RuntimeException("Invalid config["+i+"]="+args[i]);
			}
		}

		if (i == args.length) throw new RuntimeException("没有源文件");

		compiler.ctx = (GlobalContext) Class.forName(gcName).newInstance();
		LocalContext.set(compiler.ctx.createLocalContext());

		for (String s : TextUtil.split(new ArrayList<>(), cp, File.pathSeparatorChar)) {
			File f = new File(s);
			if (!f.exists()) throw new RuntimeException(f + " not exist.");
			compiler.addCp(f);
		}

		LavaCompiler.initDefaultPlugins((GlobalContextApi) compiler.ctx);

		while (i < args.length) {
			addSrc(new File(args[i++]), compiler.CompileUnits);
		}

		File dst = bin == null ? new File("Lava.jar") : new File(bin);

		var diagnostic = new TextDiagnosticReporter(maxError, maxWarn, 0);

		boolean ok = compiler.compile(dst, diagnostic);

		diagnostic.printSum();
		System.out.println("编译状况="+ok);

		try {
			var ucl = new URLClassLoader(new URL[] {dst.toURL()});
			((Runnable) Class.forName("Test", true, ucl).newInstance()).run();
		} catch (Exception e) {
			e.printStackTrace();
		}

		System.exit(ok ? 0 : -1);
	}

	public boolean compile(File dst, Consumer<Diagnostic> listener) throws IOException {
		ArrayList<CompileUnit> ctxs = this.CompileUnits;
		ctx.listener = listener;

		boolean done = false;
		compile:
		try (ZipFileWriter zfw = new ZipFileWriter(dst)) {
			for (int i = ctxs.size() - 1; i >= 0; i--) {
				if (!ctxs.get(i).S1_Struct())
					// 下列可能性
					// 文件是空的
					// package-info
					// module-info
					ctxs.remove(i);
			}
			if (ctx.hasError()) break compile;
			ctx.addGeneratedCompileUnits(ctxs);
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S2_ResolveSelf();
			}
			if (ctx.hasError()) break compile;
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S2_ResolveRef();
			}
			if (ctx.hasError()) break compile;
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S3_Annotation();
			}
			if (ctx.hasError()) break compile;
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S4_Code();
			}
			if (ctx.hasError()) break compile;
			// write

			for (int i = 0; i < ctxs.size(); i++) {
				CompileUnit data = ctxs.get(i);
				data.S5_noStore();
				zfw.beginEntry(new ZEntry(data.name.concat(".class")));
				Parser.toByteArrayShared(data).writeToStream(zfw);
			}
			for (ConstantData data : ctx.getGeneratedClasses()) {
				zfw.beginEntry(new ZEntry(data.name.concat(".class")));
				Parser.toByteArrayShared(data).writeToStream(zfw);
			}

			zfw.setComment("Lavac v"+VERSION);
			done = true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		ctxs.clear();
		return done;
	}

	private static boolean addSrc(File f, List<CompileUnit> ctxs) throws IOException {
		if (f.isDirectory()) {
			for (File f1 : IOUtil.findAllFiles(f)) {
				addSrc(f1, ctxs);
			}
			return true;
		}

		if ("java".equals(IOUtil.extensionName(f.getName()))) {
			ctxs.add(new CompileUnit(f.getName(), new FileInputStream(f)));
			return true;
		} else {
			return false;
		}
	}

	private boolean addCp(File f) throws IOException {
		if (f.isDirectory()) {
			for (File f1 : IOUtil.findAllFiles(f)) {
				addCp(f1);
			}
			return true;
		}

		switch (IOUtil.extensionName(f.getName())) {
			case "zip", "jar":
				ctx.addLibrary(new LibraryZipFile(f));
			return true;

			default: return false;
		}
	}
}