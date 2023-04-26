package roj.config.data;

import roj.config.serial.CVisitor;
import roj.io.IOUtil;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;

/**
 * XML Value Base
 *
 * @author Roj234
 * @since 2020/10/31 15:28
 */
public abstract class XEntry implements Iterable<XEntry> {
	protected XEntry() {}

	public abstract boolean isString();
	public String asString() { throw new IllegalArgumentException("类型不是文字(提示,即使是<t>1</t>你也要调用child(0).asText() )"); }
	public XElement asElement() { throw new IllegalArgumentException("类型不是元素"); }

	// region attr
	public CEntry attr(String v) { return CNull.NULL;  }
	public void attr(String k, CEntry v) { throw new UnsupportedOperationException(); }
	public Map<String, CEntry> attributes() { return Collections.emptyMap(); }
	public Map<String, CEntry> attributesForRead() { return Collections.emptyMap(); }
	// endregion
	// region children
	public int size() { return 0; }
	public void add(@Nonnull XEntry entry) { throw new UnsupportedOperationException(); }
	@Nonnull
	public XEntry child(int index) { throw new ArrayIndexOutOfBoundsException(index); }
	@Nonnull
	@Override
	public final Iterator<XEntry> iterator() { return childrenForRead().iterator(); }
	public List<XEntry> children() { return Collections.emptyList(); }
	public List<XEntry> childrenForRead() { return Collections.emptyList(); }
	public void clear() {}
	// endregion
	// region children extended
	public final void forEachChildren(Consumer<? super XEntry> x) {
		x.accept(this);
		List<XEntry> children = childrenForRead();
		for (int i = 0; i < children.size(); i++) {
			children.get(i).forEachChildren(x);
		}
	}

	public final XElement childByTag(String tag) {
		List<XEntry> children = childrenForRead();
		for (int i = 0; i < children.size(); i++) {
			XEntry xml = children.get(i);
			if (!xml.isString() && xml.asElement().tag.equals(tag)) {
				return xml.asElement();
			}
		}
		return null;
	}

	public final List<XElement> childrenByTag(String tag) {
		List<XElement> result = new ArrayList<>();

		List<XEntry> children = childrenForRead();
		for (int i = 0; i < children.size(); i++) {
			XEntry xml = children.get(i);
			if (!xml.isString() && xml.asElement().tag.equals(tag)) {
				result.add(xml.asElement());
			}
		}

		return result;
	}

	public final XElement getFirstTag(String tag) {
		List<XEntry> children = childrenForRead();
		for (int i = 0; i < children.size(); i++) {
			XEntry xml = children.get(i);
			if (!xml.isString()) {
				XElement elem = xml.asElement();
				if (elem.tag.equals(tag)) return elem;
				elem = elem.getFirstTag(tag);
				if (elem != null) return elem;
			}
		}
		return null;
	}

	public final List<XElement> getAllByTagName(String tag) {
		List<XElement> result = new ArrayList<>();
		getAllByTagName(tag, result);
		return result;
	}
	public final void getAllByTagName(String tag, List<XElement> collector) {
		List<XEntry> children = childrenForRead();
		for (int i = 0; i < children.size(); i++) {
			XEntry xml = children.get(i);
			if (!xml.isString()) {
				XElement elem = xml.asElement();
				if (elem.tag.equals(tag)) collector.add(elem);
				elem.getAllByTagName(tag, collector);
			}
		}
	}
	// endregion

	public abstract void toJSON(CVisitor cc);

	@Override
	public final String toString() {
		CharList sb = IOUtil.getSharedCharBuf();
		toXML(sb, 0);
		return sb.toString();
	}
	public CharList toCompatXML() {
		CharList sb = new CharList(128);
		toCompatXML(sb);
		return sb;
	}
	protected abstract void toXML(CharList sb, int depth);
	protected abstract void toCompatXML(CharList sb);
}
