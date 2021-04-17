package roj.excel;

import roj.archive.zip.ZEntry;
import roj.archive.zip.ZipArchive;
import roj.archive.zip.ZipFileWriter;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.config.word.ITokenizer;
import roj.io.source.MemorySource;
import roj.io.source.Source;
import roj.text.CharList;
import roj.util.ArrayUtil;
import roj.util.ByteList;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/5/5 0005 15:16
 */
public class XlsxWriter implements TableWriter {
	private static final ZipArchive TEMPLATE;
	static {
		byte[] data = ArrayUtil.unpackB(")\u0013a1!Q\u0001\u0001\u0001\u0001\u0001\u000bu\u0013KW\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0013\u0001\u0001\u0001\rG|\u000e!s8]\u000f3{B\u0017\u0004\u0003\u0006\u0001\u0001\u0001\u0001\u0011\u0001W\"\u0015V8o\u0015C{\u000b\u0001\u0011\u0001\u0001i\u0003\u0001\u0001\u0003\u0001\u0001\u0001\u0001e8Yk\b\u0014>at\u0018Y/\b\u0002:qn7(3\u001dk)\u00072\u000b\"?y\u0018=aepjK5&\u0012IS\u0003\u0005/r61?\\M/;14\u001b&\u0011>H,*\u007f\u0017\u001b\faNo\u000f/mRy\r\u001aV-e3\b,x4X`<De\u001e\u0012:T;S2U\t5(\u001aVBk>1\u0003,\u000flNGX\r/Lzu:\u001dYB*\u000eK{.O\"2\u0017I\u0014=_\r\b2\u00054I3\u0001\u000f2E\u0005#d\u0016YPs\u001atk\u001bv\u0002%:HZd3}\u0015\u0016\u0015R\u000f1r,^\ta\u001bh(\u0010kG\u0001& (\nYrbY\t7D`\u001dN[\u0004n\u0014\u001d+n\u001e\u007fD:jD\u0004nz3m@tS\u000f\u001f\\rR5R7\u000b>uWt9\u0007\"E\u0001O?@\u000b\u0006U*_u\u0015g\u0001Sk\u000e\u00165\rM\u0017\nN2|\bg+%|CE\u0003?\u001bCWaK\n7l u\u001d</_UC\u0002??_ 8T|uz?\u00068^r_f1\u0007nH\u001a\u0004!m51\u0019=sc +?\u0006&QE_\f\u0013cQA?A\u0015xp\u0001;\u0004vI,ki\u0005Rn}\u0002FqnJtxo}D6\u0003-\u0007\u0005\u000b\u0001\u0001\u0001\u0001!\u0002\b(9I\u0005X\u007f\u0001?&\u0001!\u0001\u0005\u0015\t\u0001\u0001\u0005a\u0001\u0001\u00027D8\\OG,:i`+\u001f/\u0007,N;/=\u001c.K-T\u0017ob\r\u0003\u00050_,{\be7sBFiR+F1\u0015)?\u0018\u0017\u0003?!\u000e=y!\f`z\u0007\u0012({~\u0014\u0003\u000bQ\u0013R\u0004\u0015',\u0010\u000f\u007fvfGZP4O~4\u00128\tU0&>N\u001124\u0007(\u001fEVn,J`&^?IP\u00171\u0005jqP\t:\u00167\u0004Z\u001f>Px;u]Dq\u0002LUw--h+(H\u00199f\u0007VM\u000f\t\rr++\u0010L*\u0010!p/=\u0005\u000f#k\\\u0001 d9isvv\u0018a*(D!z[8?\"\n'c4\u0016_<n<S%\"D\u00123LTJ\u0003c\u000b\u0017\u0011G&E&\rv`\u001e\u001e)}A;\n\u0004\u001eWBN2lB\t\u0003d@eV\b\u0015\u0006ai>j\u00159Ol\u00037\t-0QC-U/\u001f8\u0019ByA(?Q8(rF{V5C=L\u000eH)S!\u0005j\t\u000f*\u0007%1T\u0004(\u0004Q\u0017\\~\u0003 _\u000f\u001dgk^\u001fc4=cznhP\u0004<FOs\u0012`\rdN\u001fy\u0001\u000fd\u0019R\u0003\u0001T-\u0001egU$\u000ev\u0015O;'5 [9\u000et.gf=\u0001}@XpxVq?\u000e\u001e\b7vy!X6^a\\Y]OjJzkjI\u0010?w\u001d:=?R\rl{B\u0017\u0004\u0003\u0006\u0001\u0001\u0001\u0001\u0001\u0001DT]%\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001a\u0001\u0001\u0001`:\u001a.H\u001a>!L\u0002B\u0003A\u0001\u0001\u0001\t\u0001\"jo\u0013\u0002w9<0 !\u0001\u0001\u0002`\u0002\u0001\u0001\u0001Y\u0001\u0001\u00010]MWdM_/:\u001a.H\u001e7%N&Ag\u0012\u0005\u001c`\u0003?I=Nw7n\u000bE\u000e\u001c9Y#|\nk\bg\u00036M@m\u0014\u0005s+L<?>!}\u00156\u0017]|uM:\u0014S?=\u001a4p$\u000eb\u0006=1T$XZ7+F\u0016\u0002p%\u001eXf\u0010Ltx\u0019/\u0013[\u0016Fq\f\u0002_+H\u0015\u0004\u0014o\u0016\u0013r@-O\u001dA\u001dk\u0017Vo!ET?\u0015G/-:oK\u0013Ue)\u001fT!A\u0010X~!Q;q 2Ia*A{5U\u0016MXN\u0010EPD,\u0012jD\nX6G\u0017x:$\no\t\u001d\"jX/u4=\u0014mo\u007fHh\t\u0018`ET\u0011fF\u0004W:\b\u001bB_\u0018~\u0006U\fg&&,'/,}l`x:':3-\u0019\u000b\u00012\u000b]C'\u00163\u001c_2?;L\u0014\u001d<s;?\n\u0015i+`f\u0006?\bBiu4@#O\u0016Q\"\u0006\u001b\u001c0h\u001b\r`=(\u0011M}`\rTuX*Q.V^k\u0005Y\u0005\u0005@\u0001\u0006\u0001\u0001\u0001\u0001\u0001\u0001X\"\u0015V1\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002\u0011\u0002\u0011\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001!\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001e8Yk\b\u0014>at\u0018CA\u0003\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0011\u0001a\u0002\u001elz\u001b#d?3\u0002\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u000b\u0005Y\u0005\u0005@\u0001\u0006\u0001\u0001\u0001\u0001\u0011\u0001W\"\u0015V8o\u0015C{\u000b\u0001\u0011\u0001\u0001i\u0003\u0001\u0001\u0003\u0001\u0002\u0011\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001A\u0001\u0001\u0001\u0005q\u0001\u0001\u0001e8Yk\b\u0014>at\u0018Y/\b\u0002:qn7\u0003A\u0003\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0011\u0001a\u0002f\u000e\f{\u0003d?3\u0002\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u000b\u0005Y\u0005\u0005@\u0001\u0006\u0001\u0001\u0001\u0001\u0011\u0001DT]%\u0003,?\u0001@SA\u0011\u0001\u0003\u000b\u0005\u0001\u0001\u00031\u0002\u0011\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001A\u0001\u0001\u0001\u0010Q\t\u0001\u0001\\\"\\nh$\u0016]u0V\u0010\u0018\u0004\u0016g^\u0018\u001f\u000eWa)\u0001!\u0001\u0001\u0001\u0001\u0001\u0001\u0003\u0001\r\u0001\u0001\n[j\u001cv,t!\u0011\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0002!L\u0001AHq\u0001Q\u0001\u0001\u0001\u0001\u0001\t;<EA\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\r\u0001\u0013\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0005\u0001\u0001\u0001\u0004q\u0003\u0001\u0001\fx\u0014\u0016Yt\u0018CA\u0003\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0011\u0001a\u0001\u0001NWJ`+`\u001b\u0002\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u000b\u0005Y\u0005\u0005@\u0001\u0006\u0001\u0001\u0001\u0001\u0011\u0001DT]%\u0004mqw_?A\u0001\u0001\u0004?\u0003\u0001\u0001\u00021\u0002\u0011\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001A\u0001\u0001\u0001\u0004A\u0019\u0001\u0001`:\u001a.H\u001a=]s3\\\u000f1Q\u0001A\u0001\u0001\u0001\u0001\u0001\u0001\u0005\u0001\u0019\u0001\u0001\u00146S8kXgA!\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0001\u0003B\u0017\u0006\u0004\u0001\u0001\u0001\u0001\u0001\u000b\u0001\u0003A\u001bq\t\u0001\u0001@\u0003\u0001\u0001\u0001\u0001\u0001");
		TEMPLATE = new ZipArchive(new MemorySource(data), 0, StandardCharsets.UTF_8);
		try { TEMPLATE.reload(); } catch (IOException ignored) {}
	}

	private final ZipFileWriter zip;

	private String sheetName;
	private ByteList sheet;
	private final List<Object> sheets = new SimpleList<>();

	public static XlsxWriter to(Source out) throws IOException {
		return new XlsxWriter(new ZipFileWriter(out, 5, false, 0));
	}
	public static XlsxWriter to(File out) throws IOException {
		return new XlsxWriter(new ZipFileWriter(out));
	}

	public XlsxWriter(ZipFileWriter zfw) throws IOException {
		zip = zfw;
		for (ZEntry entry : TEMPLATE.getEntries().values()) {
			if (!entry.getName().endsWith("/")) zfw.copy(TEMPLATE, entry);
		}
		zip.beginEntry(new ZEntry("xl/sharedStrings.xml"));
		tmpB.putAscii("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">").writeToStream(zip);
		tmpB.clear();
	}

	private final ToIntMap<String> sharedStrings = new ToIntMap<>();
	private final ByteList tmpB = new ByteList();
	private int sst(String key) throws IOException {
		int id = sharedStrings.getOrDefault(key, -1);
		if (id < 0) {
			sharedStrings.putInt(key, id = sharedStrings.size());

			tmpB.clear();
			tmpB.putAscii("<si><t>").putUTFData(xmlEncode(key)).putAscii("</t></si>").writeToStream(zip);
		}
		return id;
	}

	private final CharList tmpC2 = new CharList();
	private CharList xmlEncode(String key) {
		tmpC2.clear();
		return tmpC2.append(key).replace("&", "&amp;").replace("<", "&lt;");
	}

	private final CharList tmpC = new CharList();
	private CharList rc(int col) {
		int i = tmpC.length();

		do {
			tmpC.append((char) ('A' + (col % 26)));
			col /= 26;
		} while (col != 0);

		char[] arr = tmpC.list;
		int length = tmpC.length();
		for (int e = Math.max((length + 1) >> 1, 1); i < e; i++) {
			char a = arr[i];
			arr[i] = arr[length - i];
			arr[length - i] = a;
		}

		System.out.println(tmpC);
		return tmpC;
	}

	public void beginSheet(String name) {
		if (sheet != null) {
			sheets.add(sheetName);
			sheets.add(sheet);
		}

		maxHeight = 0;
		if (name != null) {
			sheetName = name;
			sheet = new ByteList();
		} else {
			sheet = null;
		}
	}

	private int maxHeight;
	@Override
	public void writeRow(List<?> row) throws IOException {
		int r = ++maxHeight;

		// <row r="1" spans="1:1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
		int min = 0, max = row.size();
		for (int i = 0; i < max; i++) {
			Object o = row.get(i);
			if (o != null && !"".equals(o)) {
				min = i; break;
			}
		}
		for (int i = max - 1; i >= min; i--) {
			Object o = row.get(i);
			if (o != null && !"".equals(o)) {
				max = i; break;
			}
		}

		tmpC.append("<row r=\"").append(r).append("\" spans=\"").append(min+1).append(":").append(max+1).append("\">");
		for (int i = 0; i < row.size(); i++) {
			tmpC.append("<c r=\"");
			rc(i).append(r);

			Object key = row.get(i);
			if (key instanceof Number) {
				tmpC.append("\"><v>").append(key.toString());
			} else {
				tmpC.append("\" t=\"s\"><v>").append(sst(key == null ? "" : key.toString()));
			}
			tmpC.append("</v></c>");
		}
		sheet.putUTFData(tmpC.append("</row>"));
		tmpC.clear();
	}

	@Override
	public void close() throws IOException {
		tmpB.clear();
		tmpB.putAscii("</sst>").writeToStream(zip);

		zip.closeEntry();
		beginSheet(null);
		for (int i = 0; i < sheets.size(); i += 2) {
			zip.beginEntry(new ZEntry("xl/worksheets/sheet"+(i/2+1)+".xml"));

			tmpB.clear();
			tmpB.putAscii("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:xdr=\"http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing\" xmlns:x14=\"http://schemas.microsoft.com/office/spreadsheetml/2009/9/main\" xmlns:mc=\"http://schemas.openxmlformats.org/markup-compatibility/2006\"><sheetData>").writeToStream(zip);

			((ByteList)sheets.get(i+1)).writeToStream(zip);

			tmpB.clear();
			tmpB.putAscii("</sheetData></worksheet>").writeToStream(zip);

			zip.closeEntry();
		}
		zip.beginEntry(new ZEntry("xl/workbook.xml"));
		tmpB.clear();
		tmpB.putAscii("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><fileVersion appName=\"xl\" lastEdited=\"3\" lowestEdited=\"5\" rupBuild=\"9302\"/><sheets>");
		for (int i = 0; i < sheets.size(); i += 2) {
			tmpB.putAscii("<sheet name=\""+ITokenizer.addSlashes(sheets.get(i).toString())+"\" sheetId=\""+(i/2+1)+"\" r:id=\"rId"+(i/2+1)+"\"/>");
		}
		tmpB.putAscii("</sheets></workbook>").writeToStream(zip);
		zip.closeEntry();
		zip.beginEntry(new ZEntry("xl/_rels/workbook.xml.rels"));
		tmpB.clear();
		tmpB.putAscii("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
		for (int i = 0; i < sheets.size(); i += 2) {
			tmpB.putAscii("<Relationship Id=\"rId"+(i/2+1)+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet"+(i/2+1)+".xml\"/>");
		}
		tmpB.putAscii("<Relationship Id=\"rId"+(sheets.size()/2+1)+"\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>");

		tmpB.putAscii("</Relationships>").writeToStream(zip);
		zip.close();

		sharedStrings.clear();
	}
	@Override
	public void flush() {}
}
