package roj.plugins.http.template;

import roj.collect.XHashSet;
import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.plugins.annotations.Getter;
import roj.compiler.plugins.annotations.Setter;
import roj.concurrent.TaskPool;
import roj.config.ConfigMaster;
import roj.config.ParseException;
import roj.config.auto.Optional;
import roj.io.IOUtil;
import roj.net.http.server.Request;
import roj.net.http.server.Response;
import roj.net.http.server.ResponseHeader;
import roj.plugins.http.error.GreatErrorPage;
import roj.reflect.ClassDefiner;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Roj234
 * @since 2024/3/3 0003 3:27
 */
public class MyTemplateEngine {
	private final GlobalContext compiler = new GlobalContext();
	private final ThreadLocal<LocalContext> myContext = new ThreadLocal<>();
	private final ClassLoader loader = new ClassDefiner(MyTemplateEngine.class.getClassLoader(), "MyTemplateEngine");
	private final AtomicInteger classId = new AtomicInteger();
	@Getter
	@Setter
	private TemplateConfig defaultConfig = new TemplateConfig();

	private static final XHashSet.Shape<File, Cache> SHAPE = XHashSet.shape(File.class, Cache.class, "file", "_next");
	private final XHashSet<File, Cache> cache = SHAPE.create();

	static final class Cache {
		Cache _next;
		File file;
		long lastUpdate;
		Template template;
		String nothingSpecial;
	}

	public Response render(File file, Request req, ResponseHeader rh) {
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
						if (!str.equals(fc.nothingSpecial)) {
							fc.template = parse(file.getName(), fc.nothingSpecial = str, new CharList());
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
		if (tpl == null) return Response.html(fc.nothingSpecial);
		if (tpl.isFast(req, rh.enableCompression())) {
			CharList tmp = new CharList();
			tpl.render(req, tmp, null);
			return Response.html(tmp);
		} else {
			var resp = new TemplateRenderer();
			TaskPool.Common().submit(() -> {
				try {
					CharList tmp = new CharList();
					tpl.render(req, tmp, resp);
					if (tmp.length() > 0) {
						resp.offer(IOUtil.getSharedByteBuf().putUTFData(tmp));
					}
				} finally {
					resp.setEof();
				}
			});
			return resp;
		}
	}

	@Optional
	public static final class TemplateConfig {
		public boolean restricted = true, fast, debug;
		public List<String> imports;
		public Map<String, String> headers;
		public String prepend;
	}

	public Template parse(String source, CharSequence in, CharList out) throws ParseException {
		int prev = 0, i = 0;

		var cfg = defaultConfig;
		if (in.charAt(0) == '{' && in.charAt(1) == '{' && in.charAt(2) == '{') {
			int j = TextUtil.gIndexOf(in, "}}}", i, in.length());
			if (j < 0) throw new IllegalArgumentException("未终止的{{{");
			cfg = ConfigMaster.YAML.readObject(TemplateConfig.class, in.subSequence(3, j));
			prev = i = j+3;
		}

		out.append("package roj.plugins.http.template.servlet;\n");
		if (cfg.restricted) out.append("package-restricted;\n");
		if (cfg.imports != null) {
			List<String> imports = cfg.imports;
			for (int j = 0; j < imports.size(); j++) {
				out.append("import ").append(imports.get(j)).append(";\n");
			}
		}
		out.append("""
			import roj.net.http.server.Request;
			import roj.net.http.server.ResponseHeader;
			import roj.text.CharList;
			import roj.plugins.http.template.*;
			public class MyJspServlet""")
		   .append(classId.incrementAndGet())
		   .append(" implements Template {\n");
		if (cfg.prepend != null) out.append(cfg.prepend).append('\n');
		if (cfg.headers != null || cfg.fast) {
			out.append("""
			@Override
			public boolean isFast(Request req, ResponseHeader rh) {
			""");

			if (cfg.headers != null) {
				out.append("rh");
				for (var entry : cfg.headers.entrySet()) {
					out.append(".header(\"").append(entry.getKey().replace("\"", "\\\"")).append("\",\"").append(entry.getValue().replace("\"", "\\\"")).append("\")");
				}
				out.append(';');
			}

			out.append("return ").append(cfg.fast).append(";}\n");
		}
		out.append("""
			@Override
			public void render(Request request, CharList out, TemplateRenderer\s""")
		   .append(cfg.fast ? "_" : "renderer").append("){\n");

		var escape = new CharList();
		while (true) {
			i = TextUtil.gIndexOf(in, "{{", i, in.length());
			if (i < 0) {
				i = in.length();
				// 没有模板标记，这是纯静态的
				if (prev == 0) return null;
			} else if (i+2 >= in.length()) throw new ParseException(in, "未终止的{{", i);

			if (prev < i) {
				out.append("out.append(\"");
				escape.clear();
				out.append(escape.append(in, prev, i).replace("\"", "\\\""));
				out.append("\");\n");
			}

			if ((i+=2) >= in.length()) break;

			if (in.charAt(i) == '{') {
				int j = TextUtil.gIndexOf(in, "}}}", ++i, in.length());
				if (j < 0) throw new ParseException(in, "未终止的{{{", i);

				out.append(in, i, j);
				i = j + 3;
			} else {
				int j = TextUtil.gIndexOf(in, "}}", i, in.length());
				if (j < 0) throw new ParseException(in, "未终止的{{", i);

				out.append("out.append(").append(in, i, j).append(");");
				i = j + 2;
			}

			prev = i;
		}
		escape._free();

		if (cfg.debug) {
			System.out.println("准备编译的代码:");
			System.out.println(out);
		}

		var cu = new CompileUnit(source, out.append("\n}}").toStringAndFree());

		var ctx = myContext.get();
		if (ctx == null) myContext.set(ctx = compiler.createLocalContext());

		LocalContext.set(ctx);
		try {
			cu.S1_Struct();
			cu.S2_ResolveSelf();
			cu.S2_ResolveRef();
			cu.S3_Annotation();
			cu.S4_Code();
			cu.S5_noStore();
		} finally {
			LocalContext.set(null);
		}

		ClassDefiner.premake(cu);
		return (Template) ClassDefiner.make(cu, loader);
	}
}