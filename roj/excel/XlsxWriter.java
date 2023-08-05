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
		byte[] data = ArrayUtil.unpackB(")\23a1!Q\1\1\1\1\1\13u\23KW\1\1\1\1\1\1\1\1\1\1\1\1\1\1\23\1\1\1\rG|\16!s8]\u000f3{B\27\4\3\6\1\1\1\1\21\1W\"\25V8o\25C{\13\1\21\1\1i\3\1\1\3\1\1\1\1e8Yk\b\24>at\30Y/\b\2:qn7(3\35k)\u00072\13\"?y\30=aepjK5&\22IS\3\5/r61?\\M/;14\33&\21>H,*\177\27\33\faNo\17/mRy\r\32V-e3\b,x4X`<De\36\22:T;S2U\t5(\32VBk>1\3,\17lNGX\r/Lzu:\35YB*\16K{.O\"2\27I\24=_\r\b2\u00054I3\1\u000f2E\5#d\26YPs\32tk\33v\2%:HZd3}\25\26\25R\u000f1r,^\ta\33h(\20kG\1& (\nYrbY\t7D`\35N[\4n\24\35+n\36\177D:jD\4nz3m@tS\17\37\\rR5R7\13>uWt9\7\"E\1O?@\13\6U*_u\25g\1Sk\16\u00165\rM\27\nN2|\bg+%|CE\3?\33CWaK\n7l u\35</_UC\2??_ 8T|uz?\u00068^r_f1\7nH\32\4!m51\31=sc +?\6&QE_\f\23cQA?A\25xp\1;\4vI,ki\5Rn}\2FqnJtxo}D6\3-\7\5\13\1\1\1\1!\2\b(9I\5X\177\1?&\1!\1\5\25\t\1\1\5a\1\1\u00027D8\\OG,:i`+\37/\7,N;/=\34.K-T\27ob\r\3\u00050_,{\be7sBFiR+F1\25)?\30\27\3?!\16=y!\f`z\7\22({~\24\3\13Q\23R\4\25',\20\17\177vfGZP4O~4\u00128\tU0&>N\u001124\7(\37EVn,J`&^?IP\u00171\5jqP\t:\u00167\4Z\37>Px;u]Dq\2LUw--h+(H\u00199f\7VM\17\t\rr++\20L*\20!p/=\5\17#k\\\1 d9isvv\30a*(D!z[8?\"\n'c4\26_<n<S%\"D\u00123LTJ\3c\13\27\21G&E&\rv`\36\36)}A;\n\4\36WBN2lB\t\3d@eV\b\25\6ai>j\u00159Ol\u00037\t-0QC-U/\u001f8\31ByA(?Q8(rF{V5C=L\16H)S!\5j\t\17*\7%1T\4(\4Q\27\\~\3 _\17\35gk^\37c4=cznhP\4<FOs\22`\rdN\37y\1\17d\31R\3\1T-\1egU$\16v\25O;'5 [9\16t.gf=\1}@XpxVq?\16\36\b7vy!X6^a\\Y]OjJzkjI\20?w\35:=?R\rl{B\27\4\3\6\1\1\1\1\1\1DT]%\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1a\1\1\1`:\32.H\32>!L\2B\3A\1\1\1\t\1\"jo\23\2w9<0 !\1\1\2`\2\1\1\1Y\1\1\u00010]MWdM_/:\32.H\u001e7%N&Ag\22\5\34`\3?I=Nw7n\13E\16\u001c9Y#|\nk\bg\u00036M@m\24\5s+L<?>!}\u00156\27]|uM:\24S?=\u001a4p$\16b\6=1T$XZ7+F\26\2p%\36Xf\20Ltx\31/\23[\26Fq\f\2_+H\25\4\24o\26\23r@-O\35A\35k\27Vo!ET?\25G/-:oK\23Ue)\37T!A\20X~!Q;q 2Ia*A{5U\26MXN\20EPD,\22jD\nX6G\27x:$\no\t\35\"jX/u4=\24mo\177Hh\t\30`ET\21fF\4W:\b\33B_\30~\6U\fg&&,'/,}l`x:':3-\31\13\u00012\13]C'\u00163\34_2?;L\24\35<s;?\n\25i+`f\6?\bBiu4@#O\26Q\"\6\33\u001c0h\33\r`=(\21M}`\rTuX*Q.V^k\5Y\5\5@\1\6\1\1\1\1\1\1X\"\25V1\1\1\1\1\1\1\1\1\1\1\1\1\1\2\21\2\21\1\1\1\1\1\1\1\1!\1\1\1\1\1\1\1\1e8Yk\b\24>at\30CA\3\1\1\1\1\1\1\1\21\1a\2\36lz\33#d?3\2\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\13\5Y\5\5@\1\6\1\1\1\1\21\1W\"\25V8o\25C{\13\1\21\1\1i\3\1\1\3\1\2\21\1\1\1\1\1\1\1\1A\1\1\1\5q\1\1\1e8Yk\b\24>at\30Y/\b\2:qn7\3A\3\1\1\1\1\1\1\1\21\1a\2f\16\f{\3d?3\2\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\13\5Y\5\5@\1\6\1\1\1\1\21\1DT]%\3,?\1@SA\21\1\3\13\5\1\1\u00031\2\21\1\1\1\1\1\1\1\1A\1\1\1\20Q\t\1\1\\\"\\nh$\26]u0V\20\30\4\26g^\30\37\16Wa)\1!\1\1\1\1\1\1\3\1\r\1\1\n[j\34v,t!\21\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\2!L\1AHq\1Q\1\1\1\1\1\t;<EA\1\1\1\1\1\1\1\1\1\1\1\1\1\1\r\1\23\1\1\1\1\1\1\1\1\5\1\1\1\4q\3\1\1\fx\24\26Yt\30CA\3\1\1\1\1\1\1\1\21\1a\1\1NWJ`+`\33\2\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\13\5Y\5\5@\1\6\1\1\1\1\21\1DT]%\4mqw_?A\1\1\4?\3\1\1\u00021\2\21\1\1\1\1\1\1\1\1A\1\1\1\4A\31\1\1`:\32.H\32=]s3\\\u000f1Q\1A\1\1\1\1\1\1\5\1\31\1\1\u00146S8kXgA!\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\1\3B\27\6\4\1\1\1\1\1\13\1\3A\33q\t\1\1@\3\1\1\1\1\1");
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
