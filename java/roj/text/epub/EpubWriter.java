package roj.text.epub;

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

/**
 * @author Roj234
 * @since 2024/3/17 0017 2:18
 */
public class EpubWriter implements Closeable, Finishable {
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
	private final List<String> xmlRefs = new SimpleList<>();

	public EpubWriter(ZipFileWriter zfw, String title, String author, @Nullable File cover) throws Exception {
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
			   <meta content="urn:uuid:""").append(uuid).append("""
			" name="dtb:uid"/>
			   <meta content="0" name="dtb:depth"/>
			   <meta content="0" name="dtb:totalPageCount"/>
			   <meta content="0" name="dtb:maxPageNumber"/>
			</head>
			<docTitle><text>""")
			  .append(title).append("</text></docTitle>\n<docAuthor><text>")
			  .append(title).append("</text></docAuthor>\n<navMap>");

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
				.append("<div class='cover'>\n<img alt='").append(title).append("' class='bb' src='../Images/cover.").append(coverExt)
				.append("' />\n</div></body></html>");
			tw.flush();
		} else {
			coverExt = null;
			endOpfMeta();
		}
		this.cover = coverExt;

		CHAPTER("制作信息", "maker", true).append("<div>");
		HTML_LINE("""
			使用ImpLib/EpubWriter@1.1制作
			本软件开源在<a href="https://github.com/roj234/rojlib">GitHub</a>
			""");
		tw.append("\n</div></body></html>");
		tw.flush();

		CHAPTER("目录", "index", false);

		xmlRefs.clear();
	}

	private void endOpfMeta() {
		xmlOpf.append("""
			</metadata><manifest>
			<item href="toc.ncx" id="ncx" media-type="application/x-dtbncx+xml" />
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

	@Contract("_,_,true -> !null")
	private CharList CHAPTER(String name, String link, boolean immediate) throws IOException {
		if (link == null) link = "cp" + ++chapterNo;

		xmlRefs.add(link);
		xmlRefs.add(name);

		int id = tocNo++;
		xmlOpf.append("\n<item href=\"Text/").append(link).append(".html\" id=\"r").append(id).append("\" media-type=\"application/xhtml+xml\" />");
		xmlToc.append("<navPoint id=\"r").append(id).append("\" playOrder=\"").append(id).append("\">\n  <navLabel><text>").append(name)
			  .append("</text></navLabel>\n  <content src=\"Text/").append(link).append(".html\"/>\n</navPoint>\n");

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

	// TODO change CSS style
	// TODO - layered chapter - does EPUB support this ?
	public void addChapter(String name, CharList data) throws IOException {
		if (name.equals("@@intro")) {
			if (chapterNo != 0) throw new IllegalStateException("必须在第一章之前插入小说简介");
			CHAPTER("简介", "intro", true).append("<div>");
			hasIntro = true;
		} else {
			CHAPTER(name, null, true).append("<div>\n<h4>").append(name).append("</h4>");
		}

		HTML_LINE(data);
		tw.append("\n</div></body></html>");
		tw.flush();
	}

	public synchronized void finish() throws IOException {
		if (tw == null) return;

		CharList x = xmlOpf.append("</manifest><spine toc=\"ncx\">");
		for (int i = 0; i < tocNo; i++) x.append("\n<itemref idref=\"r").append(i).append("\" />");
		x.append("</spine><guide>\n<reference href=\"Text/index.html\" title=\"目录\" type=\"toc\" />");
		if (hasIntro) x.append("<reference href=\"Text/intro.html\" title=\"简介\" type=\"cover\" />");
		if (cover != null) x.append("<reference href=\"Text/cover.html\" title=\"Cover\" type=\"cover\" />");
		x.append("</guide></package>");

		try {
			zfw.beginEntry(new ZEntry("content.opf"));
			tw.append(x);
			tw.flush();
			x._free();

			zfw.beginEntry(new ZEntry("toc.ncx"));
			tw.append(xmlToc).append("</navMap>\n</ncx>");
			tw.flush();
			xmlToc._free();

			zfw.beginEntry(new ZEntry("Text/index.html"));
			tw.append("""
			<?xml version="1.0" encoding="utf-8" standalone="no"?>
			<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.1//EN" "http://www.w3.org/TR/xhtml11/DTD/xhtml11.dtd">
			<html xmlns="http://www.w3.org/1999/xhtml">
			<head>
			  <link href="../Styles/style.css" rel="stylesheet" type="text/css" />
			  <title>目录</title>
			</head>
			<body>
			  <div>
			    <!--p class="cont">目录</p-->
			    <hr class="line-index" />
			    <ul class="contents">
			""");

			for (int i = 0; i < xmlRefs.size(); i += 2) {
				tw.append("<li class='c-rules'><a href='../Text/").append(xmlRefs.get(i)).append(".html'>").append(xmlRefs.get(i + 1)).append("</a></li>\n");
			}
			tw.append("</ul></div></body></html>");
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