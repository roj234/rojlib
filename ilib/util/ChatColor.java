package ilib.util;

import java.awt.*;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public enum ChatColor {
/*
  OBFUSCATED("OBFUSCATED", 'k', true),
  BOLD("BOLD", 'l', true),
  STRIKETHROUGH("STRIKETHROUGH", 'm', true),
  UNDERLINE("UNDERLINE", 'n', true),
  ITALIC("ITALIC", 'o', true),
  RESET("RESET", 'r', -1);*/

	BLACK('0', 0, 0, 0, 100), RED('c', 255, 0, 0, 100), DARK_GREEN('2', 0, 170, 0, 100), DARK_BLUE('1', 0, 0, 170, 100), DARK_PURPLE('5', 170, 0, 170, 100), DARK_AQUA('3', 0, 255, 100),
	GREY('7', 170, 170, 170, 100), DARK_GREY('8', 85, 85, 85), DARK_RED('4', 170, 0, 0, 100), AQUA('b', 85, 255, 255), GREEN('a', 85, 255, 85), YELLOW('e', 255, 255, 85), BLUE('9', 85, 85, 255),
	LIGHT_PURPLE('d', 255, 85, 255), ORANGE('6', 255, 170, 0, 100), WHITE('f', 255, 255, 255);

	public final String code;
	public final Color color;

	ChatColor(char s, int r, int g, int b) {
		code = new String(new char[] {'\u00a7', s});
		color = new Color(r, g, b, 150);
	}

	ChatColor(char s, int r, int g, int b, int a) {
		code = new String(new char[] {'\u00a7', s});
		color = new Color(r, g, b, a);
	}

	public String coloredName() {
		return code + name().toLowerCase();
	}

	public Color getColor() {
		return color;
	}

	@Override
	public String toString() {
		return code;
	}
}