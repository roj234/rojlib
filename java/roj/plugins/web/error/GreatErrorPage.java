package roj.plugins.web.error;

import roj.ReferenceByGeneratedClass;
import roj.asmx.injector.Inject;
import roj.asmx.injector.Weave;
import roj.asmx.launcher.Autoload;
import roj.collect.HashMap;
import roj.http.server.Content;
import roj.http.server.HttpServer11;
import roj.http.server.Request;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.Formatter;
import roj.text.HtmlEntities;
import roj.text.TextReader;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2023/2/11 1:14
 */
public class GreatErrorPage {
	private static final String CODEBASE = "../projects/rojlib/java";
	private static final Map<String,Function<Request,Map<String, ?>>> customTag = new HashMap<>();
	private static Formatter template;
	static {
		try {
			template = Formatter.simple(IOUtil.getTextResourceIL("roj/plugins/web/error/template.html"));
		} catch (IOException e) {
			e.printStackTrace();
		}
		registerCustomTag();
	}

	@Autoload(Autoload.Target.NIXIM)
	@Weave(target = HttpServer11.class)
	private static final class Injector {
		@Inject
		static Content onUncaughtError(Request req, Throwable e) {return GreatErrorPage.display(req, e);}
	}

	@ReferenceByGeneratedClass
	public static void registerCustomTag() {}
	public static void addCustomTag(String tag, Function<Request,Map<String, ?>> fn) {customTag.put(tag, fn);}

	public static Content display(Request req, Throwable e) {
		StackTraceElement[] els = e.getStackTrace();
		CharList sb = IOUtil.getSharedCharBuf();

		HashMap<String, String> data = new HashMap<>();

		data.put("site", req.host());
		data.put("desc", HtmlEntities.escapeHtml(String.valueOf(e.getMessage())).toString().replace("\n", "<br />"));

		sb.clear();
		parse_class(sb.append("["), e.getClass().getName()).append("] at ");
		if (els.length > 0) parse_file(sb, els[0].getFileName(), els[0].getLineNumber());
		data.put("title", sb.toString());

		sb.clear();
		for (StackTraceElement el : els) {
			parse_class(sb.append("<li>"), el.getClassName()).append('.');
			HtmlEntities.escapeHtml(sb, el.getMethodName()).append("() at ");
			parse_file(sb, el.getFileName(), el.getLineNumber()).append("</li>");
		}
		data.put("trace", sb.toString());

		sb.clear();
		for (int i = 0; i < els.length; i++) {
			int pos = sb.length();
			try {
				if (make_line_info(sb, els[i], i)) {
					continue;
				}
			} catch (IOException ignored) {}
			sb.setLength(pos);
			sb.append("<pre><ol start=0><li class=\"line-error\" style=\"text-align:center;color:black\">没有源码</li></ol></pre>");
		}
		data.put("code", sb.toString());

		relation_data(req, sb, data);

		sb.clear();
		return Content.html(template.format(data, sb).toString());
	}

	private static boolean make_line_info(CharList sb, StackTraceElement el, int id) throws IOException {
		TextReader s;
		String cn = el.getClassName().replace('.', '/');
		File baseDir = new File(CODEBASE), f;
		while (true) {
			f = new File(baseDir, cn.concat(".java"));
			if (f.isFile()) {
				s = TextReader.auto(f);
				break;
			}

			int i = cn.lastIndexOf('$');
			if (i < 0) return false;
			cn = cn.substring(0, i);
		}

		int LINES = 9;

		int begin = Math.max(1, el.getLineNumber()-LINES);
		sb.append("<pre class=\"prettyprint lang-java\"><ol start=").append(begin).append('>');
		CharList sb2 = new CharList();
		int lineNum = 0;
		try {
			while (true) {
				sb2.clear();
				if (!s.readLine(sb2)) break;

				lineNum++;
				if (lineNum < begin) continue;
				if (lineNum > el.getLineNumber()+LINES) break;

				if (lineNum == el.getLineNumber()) sb.append("<li class=\"line-error\">");
				else sb.append("<li>");

				sb.append(HtmlEntities.escapeHtmlInline(sb2)).append("</li>");
			}
		} finally {
			s.close();
		}

		sb.append("</ol></pre>");
		return lineNum >= el.getLineNumber();
	}

	private static void relation_data(Request req, CharList sb, HashMap<String, String> data) {
		Map<String, ?> map;
		try {
			map = req.queryParam();
		} catch (Exception ex) {
			map = null;
		}
		sb.clear();
		data.put("get", display_table(sb, "GET", map).toString());

		try {
			map = req.formData();
		} catch (Exception ex) {
			map = null;
		}
		sb.clear();
		data.put("post", display_table(sb, "POST", map).toString());

		try {
			map = req.rawCookie();
		} catch (Exception ex) {
			map = null;
		}
		sb.clear();
		data.put("cookie", display_table(sb, "COOKIE", map).toString());

		try {
			map = req.session(false);
		} catch (Exception ex) {
			map = null;
		}
		sb.clear();
		data.put("session", display_table(sb, "SESSION", map).toString());

		sb.clear();
		for (Map.Entry<String, Function<Request, Map<String, ?>>> entry : customTag.entrySet()) {
			try {
				map = entry.getValue().apply(req);
			} catch (Exception ex) {
				map = null;
			}
			display_table(sb.append("<table>"), entry.getKey(), map).append("</table>");
		}
		data.put("extra", sb.toString());
	}

	private static CharList parse_class(CharList sb, String cn) {
		int i = cn.lastIndexOf('.');
		return i < 0 ? sb.append(cn) : sb.append("<abbr title=\"").append(cn).append("\">").append(cn.substring(i+1)).append("</abbr>");
	}

	private static CharList parse_file(CharList sb, String file, int line) {
		if (line == -2) return sb.append("<a>(Native method)</a>");

		if (file == null) sb.append("<a>(Unknown Source)");
		else sb.append("<a title='").append(CODEBASE).append('\\').append(file).append("'>").append(IOUtil.pathToName(file));

		sb.append("</a>");
		if (line != -1) sb.append(" line ").append(line);
		return sb;
	}

	private static CharList display_table(CharList sb, String name, Map<String, ?> map) {
		sb.append("<caption>").append(name);
		if (map == null || map.isEmpty()) return sb.append("<small>").append(map == null ? "&lt;failed&gt;" : "empty").append("</small></caption>");
		sb.append("</caption><tbody>");
		for (Map.Entry<String, ?> entry : map.entrySet()) {
			HtmlEntities.escapeHtml(sb.append("<tr><td>"), entry.getKey()).append("</td><td>");
			parse_args(sb, entry.getValue());
			sb.append("</td></tr>");
		}
		return sb.append("</tbody>");
	}

	private static void parse_args(CharList sb, Object value) {
		if (value == null) {
			sb.append("null");
			return;
		}

		try {
			HtmlEntities.escapeHtml(sb, String.valueOf(value));
		} catch (Exception e) {
			sb.append("<b style=\"color:red\">").append(value.getClass().getName()).append(".toString()失败: ");
			parse_args(sb, e);
			sb.append("</b>");
		}
	}
}