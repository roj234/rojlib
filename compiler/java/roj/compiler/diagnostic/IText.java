package roj.compiler.diagnostic;

import org.jetbrains.annotations.PropertyKey;
import roj.collect.ArrayList;
import roj.text.CharList;
import roj.text.I18n;
import roj.util.ArrayCache;

import java.util.List;

/**
 * @author Roj234
 * @since 2026/01/27 19:11
 */
public abstract class IText {
	protected List<IText> extra = new ArrayList<>();

	public IText append(String text) {return append(literal(text));}
	public IText append(IText text) {extra.add(text);return this;}
	public IText prepend(String text) {return prepend(literal(text));}
	public IText prepend(IText text) {return text.append(this);}

	public abstract void appendTo(CharList buf, I18n i18n);

	public static IText empty() {return literal("");}
	public static IText translatable(@PropertyKey(resourceBundle = "roj.compiler.messages") String key) {return new TranslatableText(key, ArrayCache.OBJECTS);}
	public static IText translatable(@PropertyKey(resourceBundle = "roj.compiler.messages") String key, Object... args) {return new TranslatableText(key, args);}
	public static IText literal(Object text) {return new LiteralText(text);}
}