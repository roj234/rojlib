package roj.plugins.ci;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Roj234
 * @since 2023/1/7 0007 0:55
 */
@Deprecated
final class ConfigEditWindow {
	static final Matcher WINDOWS_FILE_NAME = Pattern.compile("^[^<>|\"\\\\/:]+$").matcher("");
	static final Matcher MOD_VERSION = Pattern.compile("^(\\d+\\.?)+?([-_][a-zA-Z0-9]+)?$").matcher("");

	static void open(Project p, JFrame win) {
		System.out.println("ConfigEditWindow was Obsoleted, use new WebUI instead.");
	}
}