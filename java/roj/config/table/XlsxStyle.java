package roj.config.table;

/**
 * @author Roj234
 * @since 2024/11/8 0008 18:46
 */
public class XlsxStyle {
	// numFmt
	public String numberFormat;

	// font
	public String font;
	public int fontSize;
	private int color;
	private int colorType;
	public int charset = 134;
	public String scheme = "minor";

	// fill
	public String xmlFill;
	// fill

	// border
	public String xmlBorder;
	// border

	// left right center
	public enum Align {left, right, center}
	public String verticalAlign, horizontalAlign;
	// <xf numFmtId="44" fontId="1" fillId="0" borderId="0" applyFont="0" applyFill="0" applyBorder="0" applyAlignment="0" applyProtection="0"><alignment vertical="center"/></xf><xf numFmtId="41" fontId="1" fillId="0" borderId="0" applyFont="0" applyFill="0" applyBorder="0" applyAlignment="0" applyProtection="0"><alignment vertical="center"/></xf>

	public String name;

	public void setIndexedColor(int color) {
		this.color = color;
		this.colorType = 2;
	}
	public void setThemeColor(int color) {
		this.color = color;
		this.colorType = 1;
	}
	public void setARGBColor(int color) {
		this.color = color;
		this.colorType = 0;
	}

}
