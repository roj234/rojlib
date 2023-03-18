package roj.config.data;

import roj.collect.MyBitSet;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.collect.TrieTree;
import roj.config.ParseException;
import roj.config.XMLParser;
import roj.config.word.ITokenizer;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.CharList;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * @author Roj234
 * @since 2021/5/31 21:17
 */
public class XElement extends XEntry {
	// region XPath / XSL / any name you want
	private static final short lsb = 10, rsb = 11, dot = 12, lmb = 13, rmb = 14, any = 15, equ = 16, neq = 17, or = 18, comma = 19;

	private static class XSLexer extends Tokenizer {
		private static final MyBitSet TOKEN_CHAR = new MyBitSet();
		private static final TrieTree<Word> TOKEN_ID = new TrieTree<>();
		static { addSymbols(TOKEN_ID, TOKEN_CHAR, 10, TextUtil.split1("( ) . [ ] * == != | ,", ' ')); addWhitespace(TOKEN_CHAR); }

		{
			literalEnd = TOKEN_CHAR;
			tokens = TOKEN_ID;
		}
	}

	@SuppressWarnings("fallthrough")
	/**
	 * module.component[name="NewModuleRootManager"].content
	 * my.data(2).com[type="xyz" | 456, ntr != 233].*
	 */
	public List<XEntry> getXS(String key) throws ParseException {
		List<XEntry> result = new ArrayList<>();
		result.add(this);
		List<XEntry> result2 = new ArrayList<>();

		ITokenizer wr = new XSLexer().init(key);

		int dotFlag = 0;
		while (wr.hasNext() && !result.isEmpty()) {
			Word w = wr.readWord();
			switch (w.type()) {
				case Word.LITERAL:
				case Word.STRING: {
					if ((dotFlag & 1) != 0) {
						throw wr.err("缺失 '.'");
					}

					String x = w.val();
					for (int i = 0; i < result.size(); i++) {
						List<XEntry> xmls = result.get(i).asElement().children;
						for (int j = 0; j < xmls.size(); j++) {
							XEntry xml = xmls.get(j);
							if (!xml.isString() && xml.asElement().tag.equals(x)) {
								result2.add(xml);
							}
						}
					}

					List<XEntry> tmp = result;
					result = result2;
					result2 = tmp;
					result2.clear();

					dotFlag |= 1;
				}
				break;
				case any: {
					if ((dotFlag & 1) != 0) {
						throw wr.err("缺失 '.'");
					}

					for (int i = 0; i < result.size(); i++) {
						result2.addAll(result.get(i).asElement().children);
					}

					List<XEntry> tmp = result;
					result = result2;
					result2 = tmp;
					result2.clear();

					dotFlag |= 1;
				}
				break;
				case dot:
					if ((dotFlag & 1) == 0) {
						throw wr.err("未预料到的 '.'");
					}
					dotFlag &= ~1;
					break;
				case lsb: {
					int prevI = -1;
					x:
					while (wr.hasNext()) {
						w = wr.next();
						switch (w.type()) {
							default:
								throw wr.err("未预料到的 '" + w.val() + "'");
							case Word.INTEGER:
								int i = w.asInt();
								if ((dotFlag & 2) != 0) {
									if (i < 0) { // 5-10
										i = -i;
										for (int j = prevI; j < i; j++) {
											result2.add(result.get(i));
										}
										continue;
									} else {
										throw wr.err("缺失 ','");
									}
								}
								if (i < 0 || i > result.size()) return Collections.emptyList();
								result2.add(result.get(i));
								dotFlag |= 2;
								prevI = i;
								break;
							case comma:
								if ((dotFlag & 2) == 0) {
									throw wr.err("未预料到的 ','");
								}
								dotFlag &= ~2;
								break;
							case rsb:
								break x;
						}
					}

					dotFlag &= ~2;
					List<XEntry> tmp = result;
					result = result2;
					result2 = tmp;
					result2.clear();
				}
				break;
				case lmb: {
					String name = null;
					x:
					while (wr.hasNext()) {
						w = wr.next();
						switch (w.type()) {
							default:
								throw wr.err("未预料到的 '" + w.val() + "'");
							case Word.LITERAL:
							case Word.STRING:
								if (name != null) throw wr.err("重复 key");
								name = w.val();
								break;
							case equ:
							case neq:
								if (name == null) throw wr.err("缺少 key");
								if ((dotFlag & 2) != 0) throw wr.err("缺失 ','");
								filter(result, name, (byte) w.type(), wr);
								dotFlag |= 2;
								break;
							case comma:
								if (name == null || (dotFlag & 2) == 0) {
									throw wr.err("未预料到的 ','");
								}
								dotFlag &= ~2;
								name = null;
								break;
							case rmb:
								break x;
						}
					}

					dotFlag &= ~2;
				}
				break;
				default:
					throw wr.err("未预料到的 '" + w.val() + "'");

			}
		}
		return result;
	}

	// todo compare type
	private static void filter(List<XEntry> dst, String name, byte type, ITokenizer wr) throws ParseException {
		List<CEntry> find = new ArrayList<>();

		boolean or = false;
		while (true) {
			Word w = wr.next();
			if (!or) {
				find.add(XMLParser.of(w));
				or = true;
			} else {
				if (w.type() != XElement.or) {
					wr.retractWord();
					break;
				}
				or = false;
			}
		}

		o:
		for (int i = 0; i < dst.size(); i++) {
			XElement e = dst.get(i).asElement();
			CEntry attr = e.attr(name);
			for (int j = 0; j < find.size(); j++) {
				if (/* cmp */attr.isSimilar(find.get(j))) {
					continue o;
				}
			}
			dst.remove(i--);
		}
	}

	// endregion

	public String tag;
	public boolean likeClose;

	public XElement(String tag) {
		this.tag = tag;
	}

	@Override
	public boolean isString() {
		return false;
	}
	@Override
	public XElement asElement() {
		return this;
	}

	public String valueAsString() {
		if (children.size() > 1) throw new UnsupportedOperationException("child count > 1");
		return children.size()==0?"":children.get(0).asString();
	}

	public String namespace() {
		int i = tag.indexOf(':');
		if (i == -1) {
			return "";
		} else {
			return tag.substring(0, i);
		}
	}
	
	// region 属性
	Map<String, CEntry> attributes = Collections.emptyMap();

	public void put(String name, String value) {
		attributes().put(name, CString.valueOf(value));
	}
	public void put(String name, int value) {
		attributes().put(name, CInteger.valueOf(value));
	}
	public void put(String name, double value) {
		attributes().put(name, CDouble.valueOf(value));
	}
	public void put(String name, boolean value) {
		attributes().put(name, CBoolean.valueOf(value));
	}

	@Override
	public CEntry attr(String name) {
		return attributes.getOrDefault(name, CNull.NULL);
	}
	@Override
	public void attr(String k, CEntry v) {
		attributes().put(k, v);
	}
	@Override
	public Map<String, CEntry> attributes() {
		if (!(attributes instanceof MyHashMap))
			attributes = new MyHashMap<>(attributes);
		return attributes;
	}
	@Override
	public Map<String, CEntry> attributesForRead() {
		return attributes;
	}
	// endregion
	// region 子节点
	List<XEntry> children = Collections.emptyList();

	@Override
	public int size() {
		return children.size();
	}
	@Override
	public void add(@Nonnull XEntry entry) {
		if (children == Collections.EMPTY_LIST) children = Collections.singletonList(entry);
		else children().add(entry);
	}
	@Override
	@Nonnull
	public XEntry child(int index) {
		return children.get(index);
	}
	@Override
	public List<XEntry> children() {
		if (!(children instanceof SimpleList)) {
			SimpleList<XEntry> s = new SimpleList<>(children);
			children = s;
			s.capacityType = 2;
		}
		return children;
	}
	@Override
	public List<XEntry> childrenForRead() {
		return children;
	}
	@Override
	public void clear() {
		if (children instanceof SimpleList) children.clear();
		else children = Collections.emptyList();
	}
	// endregion

	public void toXML(CharList sb, int depth) {
		sb.append('<').append(tag);
		if (!attributes.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
				sb.append(' ').append(entry.getKey()).append('=');
				entry.getValue().toJSON(sb, 0);
			}
		}

		if (likeClose && children.isEmpty()) {
			sb.append(" />");
			return;
		}

		sb.append('>');

		int depth1 = depth + 4;
		if (!children.isEmpty()) {
			for (int i = 0; i < children.size(); i++) {
				XEntry entry = children.get(i);
				if (!entry.isString() && (i == 0 || !children.get(i - 1).isString())) {
					sb.append('\n');
					for (int j = 0; j < depth1; j++) sb.append(' ');
				}
				entry.toXML(sb, depth1);
			}
			if (!children.get(children.size() - 1).isString()) {
				sb.append('\n');
				for (int j = 0; j < depth; j++)sb.append(' ');
			}
		}

		sb.append("</").append(tag).append('>');
	}

	public void toCompatXML(CharList sb) {
		sb.append('<').append(tag);
		if (!attributes.isEmpty()) {
			for (Map.Entry<String, CEntry> entry : attributes.entrySet()) {
				sb.append(' ').append(entry.getKey()).append('=');
				entry.getValue().toJSON(sb, 0);
			}
		}

		if (likeClose && children.isEmpty()) {
			sb.append("/>");
			return;
		}

		for (int i = 0; i < children.size(); i++) {
			children.get(i).toCompatXML(sb);
		}
		sb.append("</").append(tag).append('>');
	}

	public CMapping toJSON() {
		CMapping map = new CMapping();
		if (!attributes.isEmpty()) map.put("A", new CMapping(attributes));
		if (!children.isEmpty()) {
			if (children.size() == 1) {
				map.put("C", children.get(0).toJSON());
			} else {
				CList list = new CList(children.size());
				List<XEntry> xmls = children;
				for (int i = 0; i < xmls.size(); i++) {
					list.add(xmls.get(i).toJSON());
				}
				map.put("C", list);
			}
		}

		map.put("I", tag);
		return map;
	}

	final XEntry read(CMapping map) {
		tag = map.getString("I");

		if (map.containsKey("A", Type.MAP)) {
			Map<String, CEntry> map1 = attributes();
			map1.clear();
			map1.putAll(map.get("A").asMap().map);
		}

		CEntry ch = map.get("C");
		if (ch.getType() != Type.NULL) {
			List<XEntry> d = children(); d.clear();

			if (ch.getType() == Type.LIST) {
				List<CEntry> children = ch.asList().raw();
				for (int i = 0; i < children.size(); i++) {
					d.add(fromJSON(children.get(i)));
				}
			} else {
				d.add(fromJSON(ch));
			}
		}
		return this;
	}
}
