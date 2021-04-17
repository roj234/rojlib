/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2018-20XX 版权没有，仿冒不究
 * <p>
 * File version : 76
 * Author: R__
 * Filename: java
 * Trie Tree的实现
 * 查询一个单词是否存在的时间复杂度仅为该单词的长度、存储空间小
 * = O(n.length)
 * 理想是美好的，实际上，这个除了让我算法知识有点长进，速度内存都是渣渣
 * <p>
 * 我想: entrySet需要CharList...
 * https://github.com/ZGJackie/Simple-Algorithms-Guide/wiki/%E5%AD%97%E5%85%B8%E6%A0%91---Trie-Tree
 * 这样还可以实现基本类型ArrayMap甚至是ObjectArrayMap (不过我懒得弄了)
 */
package roj.collect;

import roj.concurrent.OperationDone;
import roj.math.MutableBoolean;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static roj.collect.TrieTree.COMPRESS_START_DEPTH;
import static roj.collect.TrieTree.MIN_REMOVE_ARRAY_SIZE;

public final class TrieTreeSet implements Set<CharSequence> {
    private static class Entry extends TrieEntry<Entry> {
        boolean isEnd;

        private Entry(char c) {
            super(c);
        }

        public Entry(char c, Entry entry) {
            super(c);
            this.children = entry.children;
            this.isEnd = entry.isEnd;
        }

        protected int recursionSum() {
            int i = isEnd ? 1 : 0;
            if (!children.isEmpty()) {
                for (Entry value : children.values()) {
                    i += value.recursionSum();
                }
            }
            return i;
        }

        public int copyFrom(Entry node) {
            int v = 0;
            if(node.isEnd && !isEnd) {
                this.isEnd = true;
                v = 1;
            }

            for (Entry entry : node) {
                Entry sub = getChild(entry.c);
                if (sub == null) putChild(sub = newInstance());
                v += sub.copyFrom(entry);
            }
            return v;
        }

        @Override
        protected Entry newInstance() {
            return new Entry(c);
        }
    }

    private static final class PEntry extends Entry {
        CharSequence val;

        private PEntry(CharSequence val) {
            super(val.charAt(0));
            this.val = val;
        }

        public PEntry(CharSequence val, Entry entry) {
            super(val.charAt(0), entry);
            this.val = val;
        }

        @Override
        protected Entry newInstance() {
            return new PEntry(val);
        }

        CharSequence text() {
            return val;
        }

        @Override
        protected void append(CharList sb) {
            sb.append(val);
        }

        @Override
        protected int length() {
            return val.length();
        }

        @Override
        public String toString() {
            return "PE{" + val + '}';
        }
    }

    /**
     * Holder for ""
     */
    Entry root = new Entry((char) 0);
    int size = 0;

    public TrieTreeSet() {
    }

    public TrieTreeSet(String... args) {
        for (String s : args)
            add(s);
    }

    public TrieTreeSet(Collection<? extends CharSequence> args) {
        this.addAll(args);
    }

    public Entry getRoot() {
        return root;
    }

    Entry entryForPut(CharSequence s, int i, int len) {
        if (len - i < 0)
            throw new IllegalArgumentException("delta length < 0");

        Entry entry = root;
        Entry prev;
        for (; i < len; i++) {
            char c = s.charAt(i);
            prev = entry;
            entry = entry.getChild(c);
            if (entry == null) {
                // 前COMPRESS_START_DEPTH个字符, 避免频繁插入带来效率损失
                if (len - i == 1 || i < COMPRESS_START_DEPTH) {
                    prev.putChild(entry = new Entry(c));
                } else {
                    prev.putChild(entry = new PEntry(s.subSequence(i, len)));
                    break;
                }
            } else if (entry.text() != null) { // # split
                // PEntry

                final CharSequence text = entry.text();

                // lastMatch = 1; // of i(minAdd) off
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                // a - bcd
                if (lastMatch == text.length()) {
                    // do nothing... as it for next
                    i += lastMatch - 1;
                } else {

                    // [0 - 2) => bc
                    if (lastMatch == 1) {
                        prev.putChild(entry = new Entry(text.charAt(0), entry));
                    } else {
                        ((PEntry) entry).val = text.subSequence(0, lastMatch);
                    }

                    // [2, 3) => d
                    Entry child;
                    if (text.length() - 1 == lastMatch) {
                        // only one char
                        child = new Entry(text.charAt(lastMatch), entry);
                    } else {
                        child = new PEntry(text.subSequence(lastMatch, text.length()), entry);
                    }

                    entry.children = new CharMap<>(1, 1.5f);
                    entry.isEnd = false;
                    entry.putChild(child);

                    //System.out.println("E to " + entry + " and " + child);

                    if (len - 1 == i + lastMatch) {
                        child = new Entry(s.charAt(len - 1));
                    } else {
                        if (len == i + lastMatch) {
                            // entry = child
                            break;
                        } else {
                            child = new PEntry(s.subSequence(i + lastMatch, len));
                        }
                    }

                    entry.putChild(child);
                    entry = child;

                    break;
                }
            }
        }
        if (!entry.isEnd) {
            size++;
            entry.isEnd = true;
        }
        return entry;
    }

    Entry getEntry(CharSequence s, int i, int len) {
        if (len - i < 0)
            throw new IllegalArgumentException("delta length < 0");

        Entry entry = root;
        for (; i < len; i++) {
            entry = entry.getChild(s.charAt(i));
            if (entry == null)
                return null;
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length())
                    return null;
                i += text.length() - 1;
            }
        }
        return entry.isEnd ? entry : null;
    }

    public void addTrieTree(TrieTreeSet m) {
        size += root.copyFrom(m.root);
    }

    @Override
    public int size() {
        return size;
    }

    @Override
    public boolean isEmpty() {
        return size == 0;
    }

    @Override
    public boolean add(CharSequence key) {
        return add(key, 0, key.length());
    }

    public boolean add(CharSequence key, int len) {
        return add(key, 0, len);
    }

    public boolean add(CharSequence key, int off, int len) {
        int size = this.size;
        Entry entry = entryForPut(key, off, len);
        return size != this.size;
    }

    @Override
    public boolean addAll(@Nonnull Collection<? extends CharSequence> m) {
        if (m instanceof TrieTreeSet) {
            addTrieTree((TrieTreeSet) m);
            return true;
        }
        for (CharSequence entry : m) {
            this.add(entry);
        }
        return true;
    }

    @Override
    public boolean retainAll(@Nonnull Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean modified = false;
        for (Object o : c) {
            modified |= remove(o);
        }
        return modified;
    }

    @Override
    public boolean remove(Object o) {
        CharSequence s = (CharSequence) o;
        return remove(s, 0, s.length());
    }

    public boolean remove(CharSequence s, int len) {
        return remove(s, 0, len);
    }

    public boolean remove(CharSequence s, int i, int len) {
        if(len - i < 0)
            throw new IllegalArgumentException("delta length < 0");

        SimpleList<Entry> list = new SimpleList<>(Math.min(len - i, MIN_REMOVE_ARRAY_SIZE));

        Entry entry = root;
        for (; i < len; i++) {
            entry = entry.getChild(s.charAt(i));
            if (entry == null)
                return false;

            list.add(entry);

            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length())
                    return false;
                i += text.length() - 1;
            }
        }
        if (!entry.isEnd)
            return false;

        entry.isEnd = false;
        size--;

        //需要考虑的情况:
        // a-b-cd-e-fgh
        //         \
        //          jkl

        // on delete abcde
        // a => b => cd => e : any [child=1] => char
        // operate: 反向: e - cd - b - a
        // e => cd : cd.child=1
        // node = cde
        // cde => b
        // node = bcde

        if (!list.isEmpty()) {
            i = list.size;

            while (--i >= 0) {
                Entry prev = list.get(i);

                if (prev.recursionSum() != 0) {
                    prev.removeChild(entry);
                    break;
                }
                entry = prev;
            }
            list.size = i + 1;

            // 下面还有东西
            if (i >= COMPRESS_START_DEPTH) {
                entry = list.get(i);

                StringBuilder sb = new StringBuilder().append(entry.text() == null ? entry.c : entry.text());

                while (entry.childrenCount() == 1) {
                    entry = new CharMap.ValItr<>(entry.children).next();

                    sb.append(entry.text() == null ? entry.c : entry.text());
                }

                if (sb.length() > 0) {
                    (COMPRESS_START_DEPTH > 0 && i == 0 ? root : list.get(i - 1)).putChild(sb.length() == 1 ? new Entry(sb.charAt(0), entry) : new PEntry(sb.toString(), entry));
                }
            }

            list.clear();
        }

        return true;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o))
                return false;
        }
        return true;
    }

    @Override
    public boolean contains(Object i) {
        if (!(i instanceof CharSequence)) throw new ClassCastException();
        final CharSequence s = (CharSequence) i;
        return getEntry(s, 0, s.length()) != null;
    }

    public boolean contains(CharSequence i, int len) {
        return getEntry(i, 0, len) != null;
    }

    public boolean contains(CharSequence i, int off, int len) {
        return getEntry(i, off, len) != null;
    }

    public int longestMatches(CharSequence s) {
        return longestMatches(s, 0, s.length());
    }

    public int longestMatches(CharSequence s, int len) {
        return longestMatches(s, 0, len);
    }

    public int longestMatches(CharSequence s, int i, int len) {
        Entry entry = root;
        int d = 0;
        for (; i < len; i++) {
            entry = entry.getChild(s.charAt(i));
            if (entry == null)
                break;
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length()) {
                    d += lastMatch;
                    break;
                }
                i += text.length() - 1;
                d += text.length();
            } else {
                d++;
            }
        }
        return d;
    }

    public List<String> keyMatches(CharSequence s, int limit) {
        return keyMatches(s, 0, s.length(), limit);
    }

    public List<String> keyMatches(CharSequence s, int len, int limit) {
        return keyMatches(s, 0, len, limit);
    }

    public List<String> keyMatches(CharSequence s, int i, int len, int limit) {
        CharList sb = new CharList();

        Entry entry = root, next;
        for (; i < len; i++) {
            next = entry.getChild(s.charAt(i));
            if (next == null) {
                return Collections.emptyList();
            }
            final CharSequence text = next.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length()) {
                    if(lastMatch < len - i) // 字符串没匹配上
                        return Collections.emptyList();

                    entry = next;
                    sb.append(text);
                    break;
                }
                i += text.length() - 1;
                sb.append(text);
            } else {
                sb.append(next.c);
            }
            entry = next;
        }

        List<String> values = new ArrayList<>(Math.min(entry.recursionSum(), limit));
        try {
            recursionEntry(entry, (k) -> {
                if (values.size() >= limit) {
                    throw OperationDone.INSTANCE;
                }
                values.add(k.toString());
            }, sb);
        } catch (OperationDone ignored) {}
        return values;
    }

    /**
     * internal: nodes.forEach(if(s.startsWith(node)))
     * s: abcdef
     * node: abcdef / abcde / abcd... true
     * node: abcdefg  ... false
     */
    public boolean startsWith(CharSequence s) {
        return startsWith(s, 0, s.length());
    }

    public boolean startsWith(CharSequence s, int len) {
        return startsWith(s, 0, len);
    }

    public boolean startsWith(CharSequence s, int i, int len) {
        Entry entry = root;

        for (; i < len; i++) {
            entry = entry.getChild(s.charAt(i));
            if (entry == null)
                return false;
            final CharSequence text = entry.text();
            if (text != null) {
                int lastMatch = TextUtil.lastMatches(text, 0, s, i, len - i);
                if (lastMatch != text.length()) {
                    return false;
                    // 不符合规定: entry.isEnd && lastMatch == len - i;
                }
                i += text.length() - 1;
            }

            if (entry.isEnd)
                return true;
        }

        return entry.isEnd;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TrieTreeSet").append('{');
        if (!isEmpty()) {
            forEach((key) -> sb.append(key).append(','));
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.append('}').toString();
    }

    @Override
    public void clear() {
        size = 0;
        root.children.clear();
    }

    /**
     * 懒得弄
     *
     * @return null
     */
    @Nonnull
    @Override
    public Iterator<CharSequence> iterator() {
        CharSequence[] arr = new CharSequence[size];
        reuseLambda(arr);
        return new ArrayIterator<>(arr);
    }

    @Nonnull
    @Override
    public Object[] toArray() {
        Object[] arr = new Object[size];
        reuseLambda(arr);
        return arr;
    }

    private void reuseLambda(Object[] arr) {
        MutableInt i = new MutableInt(0);
        forEach((cs) -> arr[i.getAndIncrement()] = cs);
    }

    @Nonnull
    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] toArray(@Nonnull T[] arr) {
        if (arr.length < size) {
            Class<?> newType = arr.getClass();
            arr = (newType == Object[].class)
                    ? (T[]) new Object[size]
                    : (T[]) Array.newInstance(newType.getComponentType(), size);
        }
        reuseLambda(arr);
        return arr;
    }

    @Override
    public void forEach(Consumer<? super CharSequence> consumer) {
        recursionEntry(root, consumer, new CharList());
    }

    private static void recursionEntry(Entry parent, Consumer<? super CharSequence> consumer, CharList sb) {
        if (parent.isEnd) {
            consumer.accept(sb.toString());
        }
        for (Entry entry : parent) {
            entry.append(sb);
            recursionEntry(entry, consumer, sb);
            sb.setIndex(sb.length() - entry.length());
        }
    }

    @Override
    public Spliterator<CharSequence> spliterator() {
        return Spliterators.spliterator(toArray(), Spliterator.IMMUTABLE | Spliterator.SIZED);
    }

    @Override
    public boolean removeIf(Predicate<? super CharSequence> filter) {
        MutableBoolean mb = new MutableBoolean(false);
        forEach((k) -> {
            if(filter.test(k)) {
                remove(k);
                mb.set(true);
            }
        });
        return mb.get();
    }
}