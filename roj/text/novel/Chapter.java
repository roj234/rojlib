package roj.text.novel;

import roj.collect.*;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.ChinaNumeric;
import roj.text.LinedReader;
import roj.text.TextReader;

import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/26 0026 21:29
 */
public class Chapter implements MutableTreeNode {
	private static final MyBitSet CP_WHITESPACE = MyBitSet.from("\t 　=");
	private static final MyBitSet CP_DELIM = MyBitSet.from("·、．.");
	private static final Int2IntMap CP_KIND = new Int2IntMap();
	private static final Int2IntMap CP_NUMBER = new Int2IntMap();
	private static final TrieTreeSet CP_SPECIAL = new TrieTreeSet("番外", "完本感言", "序", "内容简介", "简介", "引子", "楔子", "作品相关", "尾声", "目录", "结局");
	static {
		CP_NUMBER.putAll(ChinaNumeric.NUMBER_MAP);
		CP_NUMBER.putInt('十', -2);
		CP_NUMBER.putInt('百', -2);
		CP_NUMBER.putInt('千', -2);
		CP_NUMBER.putInt('万', -2);
		CP_NUMBER.putInt('亿', -2);
		CP_NUMBER.putInt('序', 0);

		CP_KIND.putInt('节', 4);
		CP_KIND.putInt('節', 4);
		CP_KIND.putInt('回', 4);

		CP_KIND.putInt('章', 3);
		CP_KIND.putInt('幕', 3);

		CP_KIND.putInt('集', 2);
		CP_KIND.putInt('部', 2);
		CP_KIND.putInt('篇', 2);
		CP_KIND.putInt('卷', 2);
	}

	public static List<Chapter> parse(File file) throws IOException {
		try (TextReader sr = TextReader.auto(file)) {
			List<List<Chapter>> cbd = parse(sr);
			return cbd.isEmpty() ? Collections.emptyList() : groupChapter(cbd, 0);
		}
	}
	public static List<List<Chapter>> parse(LinedReader in) throws IOException {
		IntMap<List<Chapter>> chapters = new IntMap<>();

		CharList buf = IOUtil.getSharedCharBuf();

		int offset = 0;
		Chapter chap = new Chapter();
		chap.name = "Prologue";
		chapters.putInt(0, Collections.singletonList(chap));

		line:
		while (true) {
			buf.clear();
			if (!in.readLine(buf)) break;
			offset += buf.length()+1;

			int off = 0;
			int len = buf.length();

			if (off >= len) continue;
			while (CP_WHITESPACE.contains(buf.charAt(off))) {
				if (++off >= len) continue line;
			}
			while (CP_WHITESPACE.contains(buf.charAt(len-1))) {
				if (--len <= off) continue line;
			}

			block:
			if (CP_SPECIAL.strStartsWithThis(buf, off, len)) {
				if (buf.indexOf("序", off) == off && off+1 < len) break block;
				if (buf.indexOf("番外", off) == off && off+20 < len) break block;

				if (buf.startsWith("尾声") && checkTOC(chap, 99999)) continue;
				chap.end = offset-buf.length()-1;

				chap = new Chapter();
				chap.start = offset;
				chap.type = buf.equals("番外") ? '卷' : '章';

				chap.name = buf.toString(off, len);
				chap.no = -1;

				chapters.computeIfAbsentIntS((chap.type << 16), SimpleList::new).add(chap);
				continue;
			}

			int flag = 1;
			if (CP_KIND.containsKey(buf.charAt(off))) {
				flag = buf.charAt(off) << 8;
				off++;
			} else if (buf.charAt(off) == '第') {
				flag = 2;
				off++;
			}

			while (CP_WHITESPACE.contains(buf.charAt(off))) {
				if (++off >= len) continue line;
			}

			int off1 = off;
			while (CP_NUMBER.containsKey(buf.charAt(off1))) {
				if (++off1 >= len) break;
			}

			if (flag <= 2 && off1 < len) {
				if (!CP_KIND.containsKey(buf.charAt(off1))) {
					if (!CP_DELIM.contains(buf.charAt(off1))) {
						continue;
					} else {
						flag |= 4;
						buf.set(off1, '章');
					}
				} else {
					flag |= 8;
				}
			}

			if (off == off1) continue;
			if (len-off1 > 64) {
				while (!CP_WHITESPACE.contains(buf.charAt(len-1)) && !CP_DELIM.contains(buf.charAt(len-1))) {
					if (--len <= off) continue line;
				}
			}

			long no;
			try {
				no = parseChapterNo(buf.list, off, off1);
				if (no < 0) continue;
			} catch (NumberFormatException e) {
				continue;
			}

			if (checkTOC(chap, no)) continue;
			chap.end = offset-buf.length()-1;

			chap = new Chapter();
			chap.start = offset;
			chap.type = flag > 15 ? (char) flag : off1 >= len ? '节' : buf.charAt(off1);

			char last = ++off1 >= len ? 0 : buf.charAt(off1);
			int state = (flag&15) | (CP_WHITESPACE.contains(last) ? 4 : 0);

			while (off1 < len && (CP_WHITESPACE.contains(buf.charAt(off1)) || CP_DELIM.contains(buf.charAt(off1)))) off1++;

			chap.name = off1 >= len ? "" : new String(buf.list, off1, len-off1).trim();
			chap.no = no;

			chapters.computeIfAbsentIntS((chap.type << 16) | state, SimpleList::new).add(chap);
		}
		chap.end = offset-1;

		List<List<Chapter>> chapterByDepth = new SimpleList<>();

		int cStage = 0, prevSize = 0;
		while (cStage < 5) {
			RSegmentTree<RSegmentTree.Wrap<List<Chapter>>> tree = new RSegmentTree<>();

			for (IntMap.Entry<List<Chapter>> entry : chapters.selfEntrySet()) {
				List<Chapter> chaps = entry.getValue();

				int stage = CP_KIND.getOrDefaultInt(chaps.get(0).type, 3);
				if (stage == cStage)
					tree.add(new RSegmentTree.Wrap<>(chaps, chaps.get(0).start, chaps.get(chaps.size()-1).start +1));
			}

			// todo: checkDisOrder
			MyHashSet<Object> added = new IdentitySet<>();
			List<Chapter> chaps = new SimpleList<>();
			for (RSegmentTree.Region region : tree) {
				List<RSegmentTree.Wrap<List<Chapter>>> value = region.value();
				List<Chapter> maxChap = Collections.emptyList();
				for (RSegmentTree.Wrap<List<Chapter>> wrap : value) {
					if (wrap.sth.size() > maxChap.size()) {
						added.add(maxChap);
						maxChap = wrap.sth;
					}
				}

				if (added.add(maxChap)) chaps.addAll(maxChap);
			}

			if (added.size() > 1) chaps.sort((o1, o2) -> Long.compare(o1.start, o2.start));

			if (chaps.size() > prevSize) {
				if (!chapterByDepth.isEmpty() && chaps.get(0).start+2000 < chapterByDepth.get(chapterByDepth.size()-1).get(0).start) {
					List<Chapter> remove = chapterByDepth.remove(chapterByDepth.size()-1);
					System.out.println(remove);
				}

				chapterByDepth.add(chaps);
				prevSize = chaps.size();
			}

			cStage++;
		}

		return chapterByDepth;
	}
	private static boolean checkTOC(Chapter chap, long no) {
		if (chap.name.startsWith("目录")) {
			if (chap.type < 65535) {
				chap.type |= no << 16;
				return true;
			} else return no > chap.type >>> 16;
		}
		return false;
	}
	public static long parseChapterNo(char[] cbuf, int off, int len) {
		long out = 0;

		int i = off;
		while (i < len) {
			char c = cbuf[i++];
			int num = CP_NUMBER.getOrDefaultInt(c, -1);
			if (num < 0) {
				if (num == -1) return -1;
				return ChinaNumeric.parse(cbuf, off, len);
			}

			out *= 10;
			out += num;
		}

		return out;
	}
	public static List<Chapter> groupChapter(List<List<Chapter>> chapters, int pos) {
		if (pos >= chapters.size()-1) return chapters.get(chapters.size()-1);
		groupChapter(chapters, pos+1);

		List<Chapter> chap = chapters.get(pos), sub = chapters.get(pos+1);
		int prevOff = 0;
		for (int i = 0; i < chap.size()-1; i++) {
			int off = groupChapter(sub, chap.get(i+1).start);
			if (off < 0) off = -off - 1;

			chap.get(i).subChapter = new SimpleList<>(sub.subList(prevOff, off));
			prevOff = off;
		}
		chap.get(chap.size()-1).subChapter = new SimpleList<>(sub.subList(prevOff, sub.size()));

		return chap;
	}
	private static int groupChapter(List<Chapter> a, long key) {
		int low = 0;
		int high = a.size()-1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			long midVal = a.get(mid).start - key;

			if (midVal < 0) {
				low = mid + 1;
			} else if (midVal > 0) {
				high = mid - 1;
			} else {
				return mid; // key found
			}
		}

		// low ...

		return -(low + 1);  // key not found.
	}

	public int start, end;
	public int type;

	public String name;
	public long no;

	public transient Chapter parent;
	public List<Chapter> subChapter;

	public CharList data;

	public String fullName, displayName;

	public String toString() {
		String name = displayName!=null ? displayName : "第" + no + (char)type + "【" + this.name + "】";

		name += " | 长度 " + (data == null ? end-start : data.length()) + " | 序号 " + no;
		if (subChapter != null) {
			if (subChapter.isEmpty()) name += " +0";
			else name += " +"+subChapter.size()+(char)subChapter.get(0).type;
		}

		return name;
	}

	public Chapter getParent() { return parent; }
	public void setParent(MutableTreeNode newParent) { this.parent = (Chapter) newParent; }
	public void removeFromParent() { parent.subChapter.remove(this); parent = null; }

	public void setParents() {
		if (subChapter == null) return;
		for (int i = 0; i < subChapter.size(); i++) {
			Chapter c = subChapter.get(i);
			c.parent = this;
			c.setParents();
		}
	}

	public Chapter getChildAt(int i) { return subChapter.get(i); }
	public int getChildCount() { return subChapter == null ? 0 : subChapter.size(); }

	public int getIndex(TreeNode node) { return subChapter == null ? -1 : subChapter.indexOf(node); }

	public boolean getAllowsChildren() { return subChapter != null; }

	public boolean isLeaf() { return subChapter == null || subChapter.isEmpty(); }
	public Enumeration<?> children() { return subChapter == null ? Collections.emptyEnumeration() : Collections.enumeration(subChapter); }

	public void insert(MutableTreeNode child, int index) {
		if (subChapter == null) subChapter = new SimpleList<>();
		Chapter c = (Chapter) child;
		c.parent = this;
		subChapter.add(index, c);
	}

	public void remove(int index) { subChapter.remove(index); }
	public void remove(MutableTreeNode node) { subChapter.remove(node); }

	public void setUserObject(Object object) { throw new UnsupportedOperationException(); }

	public int sumChildrenCount() {
		if (getChildCount() == 0) return 1;
		int val = 1;
		for (int i = 0; i < subChapter.size(); i++) {
			val += subChapter.get(i).sumChildrenCount();
		}
		return val;
	}

	public void flat(Collection<Chapter> out) {
		out.add(this);
		if (getChildCount() > 0) {
			for (int i = 0; i < subChapter.size(); i++) {
				subChapter.get(i).flat(out);
			}
		}
	}
}
