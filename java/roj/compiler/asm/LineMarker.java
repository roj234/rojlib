package roj.compiler.asm;

import roj.asm.visitor.Label;

/**
 * @author Roj234
 * @since 2024/6/13 0013 7:38
 */
public class LineMarker extends Label {
	public void move(int b, int o) {
		if (block == 0) offset += o;
		block += b;
	}
}