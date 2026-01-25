package roj.compiler.diagnostic;

import roj.text.CharList;
import roj.text.I18n;

/**
 * @author Roj234
 * @since 2026/01/27 19:12
 */
public class TranslatableText extends IText {
	private final String key;
	private final Object[] args;

	public TranslatableText(String key, Object... args) {
		this.key = key;
		this.args = args;
	}

	@Override
	public void appendTo(CharList buf, I18n i18n) {
		String pattern = i18n.translate(key);
		// 使用高效的解析逻辑处理占位符
		int last = 0;
		for (int i = 0; i < pattern.length(); i++) {
			if (pattern.charAt(i) == '%' && i + 1 < pattern.length()) {
				buf.append(pattern, last, i);
				int argIdx = pattern.charAt(i + 1) - '1';
				if (argIdx >= 0 && argIdx < args.length) {
					renderArg(args[argIdx], buf, i18n);
				}
				i++; // 跳过数字
				last = i + 1;
			}
		}
		buf.append(pattern, last, pattern.length());

		for (IText text : extra) text.appendTo(buf, i18n);
	}

	private void renderArg(Object arg, CharList buf, I18n i18n) {
		if (arg instanceof IText it) {
			it.appendTo(buf, i18n);
		} else {
			LiteralText.staticAppendTo(buf, i18n, arg);
		}
	}

	public String getTranslationKey() {
		return key;
	}
}
