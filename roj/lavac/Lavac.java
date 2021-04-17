package roj.lavac;

import roj.collect.MyHashMap;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.lavac.parser.CompileContext;
import roj.lavac.parser.CompileUnit;
import roj.lavac.util.LibraryClassFile;
import roj.lavac.util.LibraryZipFile;
import roj.lavac.util.StdDiagnostic;
import roj.text.TextUtil;
import roj.text.logging.Logger;
import roj.text.logging.LoggingStream;

import javax.tools.DiagnosticListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author solo6975
 * @since 2022/9/16 19:05
 */
public final class Lavac {
	int debugOps = 10;

	CompileContext ctx;

	final ArrayList<CompileUnit> CompileUnits = new ArrayList<>();

	public static void main(String[] args) throws IOException, ReflectiveOperationException {
		if (args.length < 1) {
			System.out.println(
				"用法: lavac <options> <source>\n" +
					"            其中, 可能的选项包括:\n" +
					"            -classpath/-cp <路径>      指定查找用户类文件和注释处理程序的位置\n" +
					"            -rtpath <路径>             覆盖引导类文件的位置\n" +
					"\n" +
					"            -d <目录>                  指定放置生成的类文件的位置\n" +
					"            -s <目录>                  指定放置生成的源文件的位置\n" +
					"            -h <目录>                  指定放置生成的C语言头文件的位置\n" +
					"\n" +
					"            -encoding <编码>           指定源文件使用的字符编码\n" +
					"            -g                         选择生成哪些调试信息\n" +
					"              可用的值有：lines,vars,source,params 并以逗号分隔\n" +
					"            -nowarn                    不生成任何警告\n" +
					"            -Werror                    出现警告时终止编译\n" +
					"\n" +
					"            -processor <class1>[,<class2>...] 要运行的注释处理程序的名称; 绕过默认的搜索进程\n" +
					"            -processorpath <路径>      指定查找注释处理程序的位置\n" +
					"            -A关键字[=值]              传递给注释处理程序的选项\n" +
					"\n" +
					"            -tweaker                   设定编译上下文的类名称\n" +
					"            -T关键字[=值]              传递给编译上下文的选项\n" +
					"\n" +
					"            -EnableSpec/DisableSpec ID 启用或禁用不与Javac兼容的特性\n" +
					"            -target <发行版>           生成支持不高于特定 Java 版本的类文件\n" +
					"\n" +
					"            -version                   版本信息\n");
		}
		String src = null;
		String cp = "";
		String bin = null;

		String tweaker = "roj.lavac.parser.CompileContext";
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

		compiler.ctx = (CompileContext) Class.forName(tweaker).newInstance();
		try {
			compiler.ctx.config(tweakerOps);
		} catch (ParseException e) {
			e.printStackTrace();
		}

		for (String s : TextUtil.split(new ArrayList<>(), cp, File.pathSeparatorChar)) {
			File f = new File(s);
			if (!f.exists()) throw new RuntimeException(f + " not exist.");
			compiler.addClassPath(f);
		}

		while (i < args.length) {
			addSrc(new File(args[i++]), compiler.CompileUnits, compiler.ctx);
		}

		File dst = bin == null ? null : new File(bin);
		if (dst != null && !dst.isDirectory() && !dst.mkdirs()) {
			throw new RuntimeException("Binary path is not exist and unable to create.");
		}

		System.setOut(new LoggingStream(Logger.getLogger()));

		StdDiagnostic diagnostic = new StdDiagnostic(maxError, maxWarn, warnOps);

		System.out.println("begin " + compiler.CompileUnits.size());
		boolean fl = compiler.compile(dst, diagnostic);
		System.out.println("ok="+fl);

		diagnostic.conclusion();
		System.exit(fl ? 0 : -1);
	}

	public boolean compile(File dst, DiagnosticListener<CompileUnit> listener) throws IOException {
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
			done = true;
		} catch (Exception e) {
			e.printStackTrace();
		}

		if (done) {
			// write
			for (int i = 0; i < ctxs.size(); i++) {
				CompileUnit ctx = ctxs.get(i);
				try (FileOutputStream fos = new FileOutputStream(new File(dst, "/" + ctx.name + ".class"))) {
					ctx.getBytes().writeToStream(fos);
				} catch (IOException e) {
					done = false;
					e.printStackTrace();
				}
			}
		} else {

		}

		ctxs.clear();

		return done;
	}

	private static void handleSrc(List<File> srcFiles, List<CompileUnit> ctxs, CompileContext ctx) throws IOException {
		for (File f : srcFiles) {
			if (!f.exists()) {
				throw new RuntimeException(f.getAbsolutePath() + " not exist.");
			}
			if (f.isDirectory()) {
				for (File f1 : IOUtil.findAllFiles(f)) {
					addSrc(f1, ctxs, ctx);
				}
			} else if (!addSrc(f, ctxs, ctx)) {
				throw new IOException("Unable to detect file type by extension name, only support zip,jar,gz,txt,java and directory");
			}
		}
	}

	private static boolean addSrc(File f, List<CompileUnit> ctxs, CompileContext ctx) throws IOException {
		if (f.isDirectory()) {
			handleSrc(Collections.singletonList(f), ctxs, ctx);
			return true;
		}
		String ext = f.getName();

		if ("java".equalsIgnoreCase(ext.substring(ext.lastIndexOf('.') + 1))) {
			ctxs.add(new CompileUnit(f.getAbsolutePath(), new FileInputStream(f), ctx));
		} else {
			return false;
		}
		return true;
	}

	private void handleClassPath(List<File> cp) throws IOException {
		for (File f : cp) {
			if (!f.exists()) {
				throw new RuntimeException(f + " not exist.");
			}
			if (f.isDirectory()) {
				for (File f1 : IOUtil.findAllFiles(f)) {
					addClassPath(f1);
				}
			} else if (!addClassPath(f)) {
				throw new IOException("Unable to detect file type by extension name, only support zip,jar,gz,txt,class and directory");
			}
		}
	}

	private boolean addClassPath(File f) throws IOException {
		if (f.isDirectory()) {
			handleClassPath(Collections.singletonList(f));
			return true;
		}

		String ext = f.getName();
		if (!ext.contains(".")) {
			System.err.println("缺少扩展名无法判断 " + f.getName());
			return false;
		}

		switch (ext.substring(ext.lastIndexOf('.') + 1)) {
			case "zip":
			case "jar":
			case "gz":
				ctx.addLibrary(new LibraryZipFile(f));
				break;

			case "txt": {
				List<File> files = new ArrayList<>();
				for (String s : TextUtil.split(IOUtil.readUTF(f), '\n')) {
					s = s.trim();
					if (s.length() == 0 || s.charAt(0) == '#') {
						continue;
					}
					files.add(new File(s));
				}
				handleClassPath(files);
			}
			break;

			case "class":
				ctx.addLibrary(new LibraryClassFile(f));
				break;

			default:
				return false;
		}
		return true;
	}
}
