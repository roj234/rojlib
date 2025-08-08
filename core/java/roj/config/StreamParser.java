package roj.config;

import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/19 11:01
 */
sealed interface StreamParser permits StreamJson, StreamYaml {
	void streamElement(int flag) throws ParseException;
	CVisitor visitor();
}