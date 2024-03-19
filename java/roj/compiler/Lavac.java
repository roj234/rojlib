package roj.compiler;

import roj.asm.Parser;
import roj.collect.MyHashMap;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LibraryZipFile;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Diagnostic;
import roj.compiler.diagnostic.SimpleDiagnosticListener;
import roj.io.IOUtil;
import roj.reflect.ClassDefiner;
import roj.text.TextUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author solo6975
 * @since 2022/9/16 19:05
 */
public final class Lavac {
	int debugOps = 10;

	GlobalContext ctx;

	final ArrayList<CompileUnit> CompileUnits = new ArrayList<>();

	public static void main(String[] args) throws Exception {
		GlobalContext ctx = new GlobalContext();
		LocalContext cache = new LocalContext(ctx);
		LocalContext.set(cache);

		CompileUnit u = new CompileUnit("<stdin>", ctx);

		u.getLexer().init(IOUtil.readUTF(new File("fulltest.java")));
		u.S0_Init();
		u.S1_Struct();

		ctx.addCompileUnit(u);

		u.S2_Parse();
		u.S3_Code();

		byte[] array = Parser.toByteArray(u);

		FileOutputStream fos = new FileOutputStream("fulltest.class");
		fos.write(array);
		fos.close();

		System.out.println(Parser.parse(array));

		((Runnable) ClassDefiner.INSTANCE.defineClass(null, array).newInstance()).run();
	}

	public static void main2(String[] args) throws IOException, ReflectiveOperationException {
		if (args.length < 1) {
			System.out.println("""
				用法: lavac <options> <source>
				            其中, 可能的选项包括:
				            -rtpath <路径>             覆盖JVM类文件的位置
				            -classpath/-cp <路径>      指定查找用户类文件和注释处理程序的位置

				            -d <目录>                  指定放置生成的类文件的位置
				            -s <目录>                  指定放置生成的源文件的位置
				            -h <目录>                  指定放置生成的C语言头文件的位置

				            -encoding <编码>           指定源文件使用的字符编码
				            -g                         选择生成哪些调试信息
				              可用的值有：lines,vars,source,params 并以逗号分隔
				            -debug                    生成调试日志

				            -lap <class1>[,<class2>...] 实现了Lavac Api 1.0的注解处理程序

				            -precomp <flag1>[,<flag2>...] 预编译沙盒白名单
				            -apiv1                        实现了LavacApi 1.0接口的编译处理程序

				            -GC <class>                设定全局上下文的类名称
				            -LC <class>                设定本地上下文的类名称

				            -EnableSpec/DisableSpec ID 启用或禁用不与Javac兼容的特性
				            -target <发行版>           生成支持不高于特定 Java 版本的类文件

				            -version                   版本信息
				""");
		}
		String src = null;
		String cp = "";
		String bin = null;

		String tweaker = "roj.compiler.context.CompileContext";
		MyHashMap<String, String> tweakerOps = new MyHashMap<>();

		int maxWarn = 100, maxError = 100, warnOps = 0;

		Lavac compiler = new Lavac();

		int i = 0;
		for (; i < args.length; i++) {
			String argi = args[i];
			if (!argi.startsWith("-")) break;
			switch (argi) {
				case "-maxWarn":
					maxWarn = Integer.parseInt(args[++i]);
					break;
				case "-maxError":
					maxError = Integer.parseInt(args[++i]);
					break;

				case "-Werror":
					if (warnOps != 0) throw new RuntimeException("-Werror, -ignoreWarn 参数不能同时使用");

					warnOps = 1;
					break;

				case "-ignoreWarn":
					if (warnOps != 0) throw new RuntimeException("-Werror, -ignoreWarn 参数不能同时使用");
					warnOps = 2;
					break;

				case "-stdOutAsErr":
					System.setErr(System.out);
					break;


				case "-cp":
				case "-classpath":
					cp = args[++i];
					break;

				case "-dest":
				case "-bin":
					bin = args[++i];
					break;


				case "-g":
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
					break;
				case "-debug":
					compiler.debugOps |= 1;
					break;

				case "-tweaker":
					tweaker = args[++i];
					break;

				case "-T":
					tweakerOps.put(args[++i], args[++i]);
					break;
				default:
					throw new RuntimeException("Invalid config[" + i + "]=" + args[i]);
			}
		}

		if (i == args.length) throw new RuntimeException("没有源文件");

		compiler.ctx = (GlobalContext) Class.forName(tweaker).newInstance();

		for (String s : TextUtil.split(new ArrayList<>(), cp, File.pathSeparatorChar)) {
			File f = new File(s);
			if (!f.exists()) throw new RuntimeException(f + " not exist.");
			compiler.addCp(f);
		}

		while (i < args.length) {
			addSrc(new File(args[i++]), compiler.CompileUnits, compiler.ctx);
		}

		File dst = bin == null ? null : new File(bin);
		if (dst != null && !dst.isDirectory() && !dst.mkdirs()) {
			throw new RuntimeException("Binary path is not exist and unable to create.");
		}

		SimpleDiagnosticListener diagnostic = new SimpleDiagnosticListener(maxError, maxWarn, warnOps);

		System.out.println("begin " + compiler.CompileUnits.size());
		boolean fl = compiler.compile(dst, diagnostic);
		System.out.println("ok="+fl);

		diagnostic.conclusion();
		System.exit(fl ? 0 : -1);
	}

	public boolean compile(File dst, Consumer<Diagnostic> listener) throws IOException {
		ArrayList<CompileUnit> ctxs = this.CompileUnits;
		ctx.listener = listener;

		boolean done = false;
		compile:
		try {
			for (int i = ctxs.size() - 1; i >= 0; i--) {
				if (!ctxs.get(i).S0_Init())
					// special process for package-info or empty java
					ctxs.remove(i);
			}
			if (ctx.hasError()) break compile;
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S1_Struct();
			}
			if (ctx.hasError()) break compile;
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S2_Parse();
			}
			if (ctx.hasError()) break compile;
			for (int i = 0; i < ctxs.size(); i++) {
				ctxs.get(i).S3_Code();
			}
			if (ctx.hasError()) break compile;
			// write
			for (int i = 0; i < ctxs.size(); i++) {
				CompileUnit ctx = ctxs.get(i);
				try (FileOutputStream fos = new FileOutputStream(new File(dst, "/" + ctx.name + ".class"))) {
					ctx.getBytes().writeToStream(fos);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		ctxs.clear();

		return done;
	}

	private static boolean addSrc(File f, List<CompileUnit> ctxs, GlobalContext ctx) throws IOException {
		if (f.isDirectory()) {
			for (File f1 : IOUtil.findAllFiles(f)) {
				addSrc(f1, ctxs, ctx);
			}
			return true;
		}

		if ("java".equalsIgnoreCase(IOUtil.extensionName(f.getName()))) {
			ctxs.add(new CompileUnit(f.getAbsolutePath(), new FileInputStream(f), ctx));
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

		switch (IOUtil.extensionName(f.getName()).toLowerCase()) {
			case "zip", "jar":
				ctx.addLibrary(new LibraryZipFile(f));
			return true;

			default: return false;
		}
	}
}