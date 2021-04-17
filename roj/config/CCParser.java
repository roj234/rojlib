package roj.config;

import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/19 0019 11:01
 */
interface CCParser {
	void ccElement(int flag) throws ParseException;
	CVisitor cc();
}
