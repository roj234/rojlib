package roj.config.data;

import roj.config.XMLParser;

/**
 * @author Roj234
 * @since 2020/11/22 15:53
 */
public final class XText extends XEntry {
	public static final byte NEVER_ENCODE = 2, ALWAYS_ENCODE = 1, CHECK_ENCODE = 0;

	public String value;
	public byte CDATA_flag;

	public XText(String text) {
		value = text;
	}

	@Override
	public boolean isString() {
		return true;
	}
	@Override
	public String asString() {
		return value;
	}

	public CString toJSON() {
		return CString.valueOf(value);
	}

	@Override
	protected void toXML(StringBuilder sb, int depth) {
		toCompatXML(sb);
	}
	@Override
	protected void toCompatXML(StringBuilder sb) {
		if (CDATA_flag == ALWAYS_ENCODE || (CDATA_flag == CHECK_ENCODE && !XMLParser.literalSafe(value))) sb.append("<![CDATA[").append(value).append("]]>");
		else sb.append(value);
	}
}
