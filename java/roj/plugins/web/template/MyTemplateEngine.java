package roj.plugins.web.template;

import roj.collect.XashMap;
import roj.compiler.CompileContext;
import roj.compiler.JavaCompileUnit;
import roj.compiler.LambdaLinker;
import roj.compiler.LavaCompiler;
import roj.concurrent.TaskPool;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.http.server.Content;
import roj.http.server.Request;
import roj.http.server.ResponseHeader;
import roj.io.IOUtil;
import roj.plugins.web.error.GreatErrorPage;
import roj.reflect.ClassDefiner;
import roj.text.CharList;
import roj.text.LineReader;

import java.io.File;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2024/3/3 3:27
 */
public class MyTemplateEngine {
	private final LavaCompiler compiler = new LavaCompiler();
	private final ThreadLocal<CompileContext> myContext = new ThreadLocal<>();
	private final ClassLoader loader = new ClassDefiner(MyTemplateEngine.class.getClassLoader(), "MyTemplateEngine");
	private final AtomicInteger classId = new AtomicInteger();

	private static final XashMap.Builder<File, Cache> BUILDER = XashMap.builder(File.class, Cache.class, "file", "_next");
	private final XashMap<File, Cache> cache = BUILDER.create();

	static final class Cache {
		Cache _next;
		File file;
		long lastUpdate;
		Template template;
		String textContent;
	}

	public MyTemplateEngine() {
		compiler.addLibrary(LambdaLinker.LIBRARY_SELF);
	}


	public Cache findTemplate(String substring) {
		return null;
	}

	public Content render(File file, Request req, ResponseHeader rh) {
		Cache fc;
		synchronized (cache) {
			fc = cache.computeIfAbsent(file);
		}

		long modified = file.lastModified();
		if (modified > fc.lastUpdate) {
			try {
				synchronized (fc) {
					if (modified > fc.lastUpdate) {
						String str = IOUtil.readString(file);
						if (!str.equals(fc.textContent)) {
							fc.template = parse(file.getName(), fc.textContent = str, new CharList());
						}
						fc.lastUpdate = modified;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				return GreatErrorPage.display(req, e);
			}
		}

		var tpl = fc.template;
		if (tpl == null) return Content.html(fc.textContent);

		rh.enableCompression();
		if (tpl.isFast()) {
			CharList tmp = new CharList();
			tpl.render(req, tmp, null);
			return Content.html(tmp);
		} else {
			var renderer = new TemplateRenderer();
			TaskPool.common().submit(() -> {
				try {
					CharList tmp = new CharList();
					tpl.render(req, tmp, renderer);
					if (tmp.length() > 0) {
						renderer.offer(IOUtil.getSharedByteBuf().putUTFData(tmp));
					}
				} finally {
					renderer.setEof();
				}
			});
			return renderer;
		}
	}

	public Template parse(String sourceFile, String template, CharList code) throws ParseException {
		code.append("package roj.plugins.web.template.servlet;" +
				   "package-restricted;" +
				   "import roj.http.server.Request;" +
				   "import roj.http.server.ResponseHeader;" +
				   "import roj.text.CharList;" +
				   "import roj.plugins.web.template.*;");

		var itr = LineReader.create(template);
		String line;

		var fast = false;
		var debug = false;
		var lines = 0;

		loop:
		while (true) {
			line = itr.next();

			if (line.length() > 2 && line.charAt(0) == '#') {
				int i = line.indexOf(' ');

				switch (line.substring(1, i < 0 ? line.length() : i)) {
					case "c" -> {}
					case "fast" -> fast = true;
					case "debug" -> debug = true;
					case "import" -> code.append(line, 1, line.length()).append(';');
					default -> {break loop;}
				}

				lines++;
				if (!itr.hasNext()) return null;
				continue;
			}
			break;
		}

		var paramType = "Object";

		code.append("public class ExpressoServlet$").append(classId.incrementAndGet()).append(" implements Template {");
		if (fast) code.append("public boolean isFast() {return true;}");
		code.append("public void render(Request request, CharList response, TemplateRenderer renderer){")
		   .append("const param = (").append(paramType).append(") renderer.param;")
		   .padEnd('\n', lines+1); // 对齐行号;

		var blockMethod = new CharList();

		templateBody(code, line, itr, paramType, blockMethod);

		code.append('}').append(blockMethod).append('}');

		if (debug) {
			System.out.println("准备编译:");
			System.out.println(code);
		}

		var unit = new JavaCompileUnit(sourceFile, code.toStringAndFree());

		var ctx = myContext.get();
		if (ctx == null) myContext.set(ctx = compiler.createContext());

		CompileContext.set(ctx);
		try {
			unit.S1parseStruct();
			unit.S2p1resolveName();
			unit.S2p2resolveType();
			unit.S2p3resolveMethod();
			unit.S3processAnnotation();
			unit.S4parseCode();
			unit.S5serialize();
		} finally {
			CompileContext.set(null);
		}

		return (Template) ClassDefiner.newInstance(unit, loader);
	}

	private void templateBody(CharList code, String line, LineReader.Impl itr, String paramType, CharList miscMethod) {
		boolean isWriting = false;

		while (true) {
			isDirective: {
				if (line.length() > 1 && line.charAt(0) == '#') {
					if (line.startsWith("#c ")) line = "";
					else if (!line.startsWith("##")) {
						if (isWriting) {
							code.setLength(code.length()-1);
							code.append("\");\n");
							isWriting = false;
						}

						int i = line.indexOf(' ');

						if (i > 0) {
							switch (line.substring(1, i)) {
								default -> throw new IllegalArgumentException("unknown directive "+line);
								case "if" -> code.append("if(").append(line.substring(i + 1)).append("){");
								case "elif" -> code.append("} else if(").append(line.substring(i + 1)).append("){");
								case "for" -> code.append("for(").append(line.substring(i + 1)).append("){");
								case "call" -> {
									int second = line.indexOf(' ', i+1);
									String templateName = line.substring(i+1, second < 0 ? line.length() : second);
									String argument = second < 0 ? null : line.substring(second+1);

									if (templateName.startsWith("@")) {
										code.append("this.").append(templateName, 1, templateName.length()).append("(request,response,param,");
										if (argument != null) code.append(argument);
										code.append(");");
									} else {
										code.append("renderer.findTemplate(\"");
										Tokenizer.escape(code, templateName).append("\").render(request,response,").append(argument).append(");");
									}
								}
								case "include" -> {
									String tplName = line.substring(i + 1);
									var template = findTemplate(tplName);
									//if (template == null) throw new IllegalStateException("找不到导入的模板"+tplName);

									//templateBody(code, "", LineReader.create(template.textContent), paramType);
								}
								case "block" -> {
									String nameAndArg = line.substring(i+1);
									int pos = nameAndArg.indexOf('(');
									String name = pos < 0 ? nameAndArg : nameAndArg.substring(0, pos);
									String arg = pos < 0 ? "" : ","+nameAndArg.substring(pos+1, nameAndArg.length()-1);

									miscMethod.append("private void ").append(name).append("(Request request, CharList response, ").append(paramType).append(" param").append(arg).append("){");
									CharList nextLevel = new CharList();
									templateBody(miscMethod, "", itr, paramType, nextLevel);
									miscMethod.append("}");
									nextLevel.appendToAndFree(miscMethod);
								}
							}
						} else if (line.equals("#else")) {
							code.append("} else {");
						} else if (line.equals("#{")) {
							code.append('{');
						} else if (line.startsWith("#/") || line.equals("#}")) {
							if (line.equals("#/block")) return;
							code.append('}');
						} else if (line.equals("#code")) {
							while (itr.hasNext()) {
								line = itr.next();
								if (line.startsWith("#/")) break;
								code.append(line).append('\n');
							}
						} else {
							throw new IllegalArgumentException("unknown directive "+line);
						}

						break isDirective;
					} else {
						line = line.substring(1);
					}
				}

				if (!isWriting) {
					code.append("response.append(\"");
					isWriting = true;
				}
				Tokenizer.escape(code, line);
			}

			if (!itr.hasNext()) break;
			line = itr.next();

			code.append('\n');
		}

		if (isWriting) code.append("\");");
	}
}