package roj.config.serial;

import roj.collect.MyHashMap;
import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.data.CObject;
import roj.config.data.Type;
import roj.util.ByteList;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * 结构压缩组件: 在map的key中寻找统一部份
 * @author Roj234
 * @since 2022/2/6 9:15
 */
public class Structs {
    public static final int STRUCT_VER = Type.VALUES.length;

    static final byte[] ENCODE_MAP, DECODE_MAP;
    static final int SHORT_ID_COUNT;
    static {
        ENCODE_MAP = new byte[256];
        DECODE_MAP = new byte[256];
        int k = 0;
        for (int mainType = 1; mainType < 16; mainType++) {
            for (int subType = mainType >= STRUCT_VER ? 0 : 1; subType < 16; subType++) {
                int b = (subType << 4) | mainType;
                ENCODE_MAP[k++] = (byte) b;
                DECODE_MAP[b] = (byte) k;
            }
        }
        SHORT_ID_COUNT = k;
    }

    public static Structs newCompressor() {
        return new Structs(false);
    }

    public static Structs newDecompressor() {
        return new Structs(true);
    }

    static final class Struct {
        String[] names;
        int id, hash;

        Struct sibling;
    }

    public ByteList p0;

    Struct[] tab;
    int mask, size;

    String[] tmp;
    int      matches, hit;

    // this is not so easy
    boolean partialMatch;

    private final Util         csm = new Util();
    private Comparator<String> cmp;

    protected Structs(boolean read) {
        this.tab = new Struct[16];
        this.mask = read ? 0 : 15;
        this.tmp = new String[20];
        this.cmp = csm;
        this.p0 = read ? new ByteList() : new ByteList(256).putInt(0);
    }

    public int hit() {
        return hit;
    }

    public int size() {
        return size;
    }

    public void setComparator(Comparator<String> cmp) {
        this.cmp = cmp;
    }

    public void add(String... m) {
        Arrays.sort(m, 0, m.length, cmp);
        Struct s = new Struct();
        s.names = m;

        if (mask == 0) {
            add(s);
        } else {
            int hash = 1;
            for (String v : m) {
                hash = 31 * hash + v.hashCode();
            }
            s.hash = hash;

            if (size > tab.length << 1) expand();

            Struct s1 = tab[hash & mask];
            if (s1 == null) {
                s.id = size++;
                tab[hash & mask] = s;
            } else {
                while (true) {
                    find:
                    if (s1.names.length == m.length) {
                        String[] a = s1.names;
                        for (int i = 0; i < m.length; i++) {
                            if (!a[i].equals(m[i]))
                                break find;
                        }
                        break;
                    }
                    if (s1.sibling == null) {
                        s.id = size++;
                        s1.sibling = s;
                        break;
                    }
                    s1 = s1.sibling;
                }
            }
        }
    }

    private void add(Struct s) {
        tab[size++] = s;
        if (tab.length == size) {
            Struct[] tab1 = new Struct[tab.length << 1];
            System.arraycopy(tab, 0, tab1, 0, tab.length);
            tab = tab1;
        }
    }

    public void read(ByteList b) {
        int len = b.readInt();
        if (tab.length < len)
            tab = new Struct[len];

        int i = 0;
        while (i < len) {
            Struct s = new Struct();
            String[] a = s.names = new String[b.readVarInt(false) + 1];
            for (int j = 0; j < a.length; j++) {
                a[j] = b.readVarIntUTF();
            }
            tab[i++] = s;
        }
    }

    public ByteList finish() {
        return p0.putInt(0, size);
    }

    public boolean toBinary(CMapping map, ByteList w) {
        if (mask == 0) throw new IllegalStateException("Not at write state");

        Map<String, CEntry> intl = map.raw();
        if (intl.isEmpty()) return false;

        int len = match(intl);
        String[] m = this.tmp;
        int hash = 1;
        for (int i = 0; i < len; i++) {
            hash = 31 * hash + m[i].hashCode();
        }

        if (size > tab.length << 1) expand();

        Struct s = tab[hash & mask];
        if (s == null) {
            tab[hash & mask] = s = plus1(m, len, hash);
            size++;
        } else {
            while (true) {
                find:
                if (s.names.length == len) {
                    String[] a = s.names;
                    for (int i = 0; i < len; i++) {
                        if (!a[i].equals(m[i]))
                            break find;
                    }
                    hit++;
                    break;
                }
                if (s.sibling == null) {
                    s = s.sibling = plus1(m, len, hash);
                    size++;
                    break;
                }
                s = s.sibling;
            }
        }

        int rid = s.id << 1;
        if (map.getType() == Type.OBJECT) rid++;
        if (rid < SHORT_ID_COUNT) {
            w.put(ENCODE_MAP[rid]);
        } else {
            w.put((byte) 255)
             .putVarInt(rid - SHORT_ID_COUNT, false);
        }

        m = s.names;
        for (int i = 0; i < len; i++) {
            intl.get(m[i]).toBinary(w, this);
        }
        return true;
    }

    public CMapping fromBinary(int rid, ByteList r, Serializers ser) {
        if (mask != 0) throw new IllegalStateException("Not at read state");
        if (rid != 255 && DECODE_MAP[rid] == 0) return null;

        if (rid == 255) {
            rid = r.readVarInt(false) + SHORT_ID_COUNT;
        } else {
            rid = (DECODE_MAP[rid] & 0xFF) - 1;
        }

        String[] s;
        try {
            s = tab[rid >>> 1].names;
        } catch (Exception e) {
            throw new IllegalArgumentException("Corrupted data: unknown struct #" + (rid >>> 1));
        }
        hit++;

        CMapping dst;
        if ((rid & 1) != 0) {
            dst = new CObject<>(null, ser);
        } else {
            dst = new CMapping(new MyHashMap<>());
        }

        MyHashMap<String, CEntry> ent = (MyHashMap<String, CEntry>) dst.raw();
        ent.ensureCapacity(s.length);
        for (String value : s) {
            ent.put(value, CEntry.fromBinary(r, this, ser));
        }

        return dst;
    }

    private void expand() {
        Struct[] newTab = new Struct[tab.length << 1];
        int mask = this.mask = newTab.length - 1;

        Struct[] tab = this.tab;
        for (Struct s : tab) {
            while (s != null) {
                Struct o = newTab[s.hash & mask];
                newTab[s.hash & mask] = s;

                Struct next = s.sibling;
                s.sibling = o;
                s = next;
            }
        }

        this.tab = newTab;
    }

    private int match(Map<String, ?> map) {
        int len;
        if (map instanceof MyHashMap) {
            matches = 0;
            map.forEach(csm);
            len = matches;
        } else {
            String[] m = tmp;
            len = 0;
            for (String s : map.keySet()) {
                m[len++] = s;
                if (len == m.length) {
                    tmp = new String[m.length + 10];
                    System.arraycopy(m, 0, tmp, 0, m.length);
                    m = tmp;
                }
            }
        }
        Arrays.sort(tmp, 0, len, cmp);
        return len;
    }

    private Struct plus1(String[] m, int len, int hash) {
        Struct s = new Struct();
        s.id = size;
        s.hash = hash;
        s.names = new String[len];
        System.arraycopy(m, 0, s.names, 0, len);

        p0.putVarInt(len - 1, false);
        for (int i = 0; i < len; i++) {
            p0.putVarIntUTF(m[i]);
        }
        return s;
    }

    private class Util implements BiConsumer<String, Object>, Comparator<String> {
        @Override
        public void accept(String s, Object e) {
            String[] m = Structs.this.tmp;
            m[matches++] = s;
            if (matches == m.length) {
                tmp = new String[m.length + 10];
                System.arraycopy(m, 0, tmp, 0, m.length);
            }
        }

        @Override
        public int compare(String o1, String o2) {
            return o1.compareTo(o2);
        }
    }

    @Override
    public String toString() {
        return "Structs{" +
                "size=" + size +
                ", hit=" + hit +
                '}';
    }
}
