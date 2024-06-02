package roj.compiler.macro;

import roj.compiler.context.CompileUnit;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.config.ParseException;
import roj.io.IOUtil;
import roj.net.http.server.HttpServer11;
import roj.net.http.server.StringResponse;
import roj.reflect.ClassDefiner;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.File;
import java.net.InetSocketAddress;

/**
 * @author Roj234
 * @since 2024/3/3 0003 3:27
 */
public class MyTemplateEngine {
	GlobalContext compiler = new GlobalContext();
	LocalContext ctx = new LocalContext(compiler);
	CharList meta = new CharList(), tmp = new CharList();

	public static void main(String[] args) throws Exception {
		String s = IOUtil.readString(new File("D:\\mc\\FMD-1.5.2\\projects\\implib\\resources\\META-INF\\html\\test.jsp"));
		CharList out = new CharList();
		Template template = new MyTemplateEngine().parse(s, out);
		HttpServer11.simple(new InetSocketAddress(8080), 256, (req, rh) -> {
			System.out.println(req);
			CharList out1 = new CharList();
			template.apply(req, out1);
			return new StringResponse(out1.toString(), "text/html");
		}).launch();
		System.out.println("Server launched");
	}

	public Template parse(CharSequence in, CharList out) throws ParseException {
		meta.clear();
		meta.append("");
		out.append("""
			package roj.myjsp.servlet;
			
			import roj.net.http.server.Request;
			import roj.text.CharList;
			
			public class MyJspServlet implements roj.myjsp.MyJspTemplate {
				@Override
				public void apply(Request request, CharList out) {
				""");

		int prev = 0, i = 0;

		while (i < in.length()) {
			i = TextUtil.gIndexOf(in, "{{", i, in.length());
			if (i < 0) i = in.length();

			if (prev < i) {
				out.append("out.append(\"");
				tmp.clear();
				out.append(tmp.append(in, prev, i).replace("\"", "\\\""));
				out.append("\");\n");
			}

			if (i+2 >= in.length()) break;

			char type = in.charAt(i+2);
			i = type == '{' ? parseCodeBlock(in, i+3, out) : parseToStringBlock(in, i+2, out);

			prev = i;
		}

		System.out.println("MetaBlock="+meta);

		LocalContext.set(ctx);
		CompileUnit u = new CompileUnit("<Generator MyJspServlet>");
		u.getLexer().init(out.append("\n}}").toString());

		ctx.setClass(u);

		u.S0_Init();
		u.S1_Struct();
		u.S2_Parse();
		u.S3_Annotation();
		u.S4_Code();

		ClassDefiner.premake(u);
		return (Template) ClassDefiner.make(u);
	}

	private static int parseToStringBlock(CharSequence in, int i, CharList out) {
		int j = TextUtil.gIndexOf(in, "}}", i, in.length());
		if (j < 0) throw new IllegalArgumentException("未终止的{{");

		out.append("out.append(").append(in, i, j).append(");");
		return j+2;
	}

	private int parseCodeBlock(CharSequence in, int i, CharList out) {
		int j = TextUtil.gIndexOf(in, "}}}", i, in.length());
		if (j < 0) throw new IllegalArgumentException("未终止的{{{");

		if (i == 3) {
			meta.append(in, 3, j);
			return j+3;
		}

		out.append(in, i, j);
		return j+3;
	}
}