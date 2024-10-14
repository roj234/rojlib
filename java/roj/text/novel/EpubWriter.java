package roj.text.novel;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipFile;
import roj.archive.zip.ZipFileWriter;
import roj.collect.SimpleList;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.source.MemorySource;
import roj.text.CharList;
import roj.text.LineReader;
import roj.text.TextWriter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2024/3/17 0017 2:18
 */
public class EpubWriter implements Closeable, Finishable {
	private static final boolean SHOW_MAKER_INFO = false;
	private static ZipFile TEMPLATE;
	static {
		try {
			TEMPLATE = new ZipFile(new MemorySource(IOUtil.getResource("META-INF/template.epub")), 0, StandardCharsets.UTF_8);
			TEMPLATE.reload();
		} catch (IOException ignored) {}
	}

	private final ZipFileWriter zfw;
	private TextWriter tw;

	private final String cover;
	private boolean hasIntro;

	private int tocNo, chapterNo;

	private final CharList xmlOpf = new CharList(), xmlToc = new CharList();
	private final List<Object> xmlRefs = new SimpleList<>();
	private final int userChapterStart;

	public EpubWriter(ZipFileWriter zfw, String title, String author, @Nullable File cover) throws IOException {
		this.zfw = zfw;
		for (ZEntry value : TEMPLATE.entries())
			zfw.copy(TEMPLATE, value);

		String uuid = UUID.randomUUID().toString();
		xmlOpf.append("""
			<?xml version="1.0" encoding="utf-8" standalone="yes"?>
			<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="BookId" version="2.0">
			  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">""");
		xmlOpf.append("    <dc:identifier id=\"BookId\" opf:scheme=\"UUID\">urn:uuid:").append(uuid).append("</dc:identifier>\n");
		xmlOpf.append("    <dc:title>").append(title).append("</dc:title>\n");
		xmlOpf.append("    <dc:creator opf:role=\"aut\">").append(author).append("</dc:creator>\n");
		xmlOpf.append("""
			    <dc:language>zh</dc:language>
			    <dc:subject>轻小说</dc:subject>
			    <dc:description>TODO: description</dc:description>
			    <dc:source>TODO: source</dc:source>
			    <dc:publisher>TODO: publisher</dc:publisher>
			    <dc:date opf:event="publication">1980-01-01</dc:date>
			    <dc:date opf:event="modification">1980-01-01</dc:date>
			    """);

		xmlToc.append("""
			<?xml version="1.0" encoding="UTF-8" standalone="no" ?>
			<!DOCTYPE ncx PUBLIC "-//NISO//DTD ncx 2005-1//EN"
			 "http://www.daisy.org/z3986/2005/ncx-2005-1.dtd">
			<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
			<head>
			   <meta name="dtb:uid" content="urn:uuid:""").append(uuid).append("""
			" />
			   <meta name="dtb:depth" content="0"/>
			   <meta name="dtb:totalPageCount" content="0"/>
			   <meta name="dtb:maxPageNumber" content="0"/>
			</head>
			<docTitle><text>""")
			  .append(title).append("</text></docTitle>\n<docAuthor><text>")
			  .append(author).append("</text></docAuthor>\n<navMap>");

		tw = new TextWriter(zfw, StandardCharsets.UTF_8);

		String coverExt;
		if (cover != null) {
			coverExt = IOUtil.extensionName(cover.getName());

			xmlOpf.append("<meta name=\"cover\" content=\"cover.").append(coverExt).append("\" />");
			endOpfMeta();
			xmlOpf.append("\n<item href=\"Images/cover.").append(coverExt).append("\" id=\"cover.").append(coverExt).append("\" media-type=\"image/").append(coverExt).append("\" />");

			zfw.beginEntry(new ZEntry("Images/cover."+coverExt));
			zfw.write(IOUtil.read(cover));

			CHAPTER("封面", "cover", true)
				.append("<div class='cover'>\n<img alt='").append(title).append("' src='../Images/cover.").append(coverExt)
				.append("' />\n</div></body></html>");
			tw.flush();
		} else {
			coverExt = null;
			endOpfMeta();
		}
		this.cover = coverExt;

		if (SHOW_MAKER_INFO) {
			CHAPTER("制作信息", "maker", true).append("<div>");
			HTML_LINE("""
				使用 ImpLib/EpubWriter v2.0 制作
				<a href="https://github.com/roj234/rojlib">开源地址(GitHub)</a>
				""");
			tw.append("\n</div></body></html>");
			tw.flush();
		}

		CHAPTER("目录", "index", false);

		userChapterStart = xmlRefs.size();
	}

	private void endOpfMeta() {
		xmlOpf.append("""
			</metadata>
			<manifest>
			<item href="toc.ncx" id="ncx" media-type="application/x-dtbncx+xml"/>
			<item href="Styles/style.css" id="css" media-type="text/css"/>
			""");
	}

	private void HTML_LINE(CharSequence seq) {
		LineReader lr = LineReader.create(seq);
		CharList sb = IOUtil.getSharedCharBuf();
		while (lr.readLine(sb)) {
			if (sb.length() == 0) tw.append("\n<p><br /></p>");
			else tw.append("\n<p>").append(sb).append("</p>");

			sb.clear();
		}
	}

	@Contract("_,_,true -> !null ; _,_,false -> null")
	private CharList CHAPTER(String name, String link, boolean immediate) throws IOException {
		if (link == null) link = String.valueOf(++chapterNo);

		xmlRefs.add(link);
		xmlRefs.add(name);
		xmlRefs.add(depth);
		tocNo++;

		if (immediate) {
			zfw.beginEntry(new ZEntry("Text/"+link+".html"));
			return tw.append("""
			<?xml version="1.0" encoding="utf-8" standalone="no"?>
			<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
			<html xmlns="http://www.w3.org/1999/xhtml">
			<head>
			  <link href="../Styles/style.css" rel="stylesheet" type="text/css" />
			  <title>""").append(name).append("</title></head><body>");
		}
		return null;
	}

	private int depth;
	public void addChapter(Chapter chap, @Nullable Function<Chapter, CharList> encoder) throws IOException {
		addChapter0(chap, encoder);
		if (chap.getChildCount() > 0) {
			depth++;
			try {
				for (int i = 0; i < chap.children.size(); i++) {
					addChapter(chap.children.get(i), encoder);
				}
			} finally {
				depth--;
			}
		}
	}
	public void addChapter0(Chapter c, Function<Chapter, CharList> encoder) throws IOException {
		if (c.no < 0) {
			if (chapterNo != 0) throw new IllegalStateException("必须在第一章之前插入小说简介");
			CHAPTER("简介", "intro", true).append("<div>\n<h1>简介</h1>");
			hasIntro = true;
		} else {
			if (c.applyOverride) {
				CHAPTER(c.displayName, null, true).append("<div>\n<h1>").append(c.displayName).append("</h1>");
			} else {
				String xn = "第" + c.no + c.type;
				CHAPTER(xn+" "+c.name, null, true).append("<div>\n<h1><span>").append(xn).append("</span>").append(c.name).append("</h1>");
			}
		}

		HTML_LINE(encoder != null ? encoder.apply(c) : c.text);
		tw.append("\n</div></body></html>");
		tw.flush();
	}

	public synchronized void finish() throws IOException {
		if (tw == null) return;
		int prevDepth;

		try {
			zfw.beginEntry(new ZEntry("content.opf"));
			var x = tw.append(xmlOpf); xmlOpf._free();
			for (int i = 0, len = xmlRefs.size()/3; i < len; i ++) {
				String link = xmlRefs.get(i*3).toString();
				x.append("\n<item href=\"Text/").append(link).append(".html\" id=\"").append(i).append("\" media-type=\"application/xhtml+xml\"/>");
			}

			x.append("</manifest>\n<spine toc=\"ncx\">");
			for (int i = 0; i < tocNo; i++) x.append("\n<itemref idref=\"").append(i).append("\"/>");
			x.append("</spine>\n<guide>\n<reference href=\"Text/index.html\" title=\"目录\" type=\"toc\"/>");
			if (hasIntro) x.append("<reference href=\"Text/intro.html\" title=\"简介\" type=\"cover\"/>");
			if (cover != null) x.append("<reference href=\"Text/cover.html\" title=\"封面\" type=\"cover\"/>");
			x.append("</guide></package>");

			tw.flush();


			zfw.beginEntry(new ZEntry("toc.ncx"));
			x = tw.append(xmlToc); xmlToc._free();
			prevDepth = -1;
			for (int i = 0, len = xmlRefs.size()/3; i < len; i ++) {
				String link = xmlRefs.get(i*3).toString();
				String name = xmlRefs.get(i*3 +1).toString();
				int depth = (int) xmlRefs.get(i*3 +2);

				if (depth > prevDepth) {
					prevDepth++;
					assert depth == prevDepth;
				} else while (true) {
					tw.append("</navPoint>\n");
					if (prevDepth == depth) break;
					prevDepth--;
				}

				x.append("<navPoint id=\"n").append(i).append("\">\n  <navLabel><text>").append(name)
				 .append("</text></navLabel>\n  <content src=\"Text/").append(link).append(".html\"/>\n");
			}

			while (prevDepth-- > 0) x.append("</navPoint>\n");
			x.append("</navPoint>\n</navMap>\n</ncx>");
			tw.flush();

			zfw.beginEntry(new ZEntry("Text/index.html"));
			x = tw.append("""
			<?xml version="1.0" encoding="utf-8" standalone="no"?>
			<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
			<html xmlns="http://www.w3.org/1999/xhtml">
			<head>
			  <link href="../Styles/style.css" rel="stylesheet" type="text/css" />
			  <title>目录</title>
			</head>
			<body>
			  <div>
			    <p class="toc">目录</p>
			    <hr />
			""");

			prevDepth = -1;
			for (int i = userChapterStart; i < xmlRefs.size(); i += 3) {
				String name = xmlRefs.get(i+1).toString();
				int depth = (int) xmlRefs.get(i+2);

				if (depth > prevDepth) {
					prevDepth++;
					assert depth == prevDepth;
					x.append("<ul>\n");
				} else {
					while (prevDepth > depth) {
						x.append("</li>\n</ul>");
						prevDepth--;
					}
					x.append("</li>\n");
				}

				x.append("<li><a href='").append(xmlRefs.get(i)).append(".html'>").append(name).append("</a>");
			}

			while (prevDepth-- > 0) x.append("</li>\n</ul>");
			x.append("</li>\n</ul></div></body></html>");
			tw.flush();
			zfw.closeEntry();
		} finally {
			tw.close();
			tw = null;
		}
	}

	@Override
	public void close() throws IOException {
		finish();
		zfw.close();
	}
}