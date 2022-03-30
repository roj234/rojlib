package roj.config.data;

import roj.collect.MyHashMap;
import roj.text.CharList;

import java.util.Map;

/**
 * @author Roj233
 * @since 2022/1/14 20:07
 */
public class CCommMap extends CMapping {
    final MyHashMap<String, String> comments = new MyHashMap<>();

    public CCommMap() {}

    public CCommMap(Map<String, CEntry> map) {
        super(map);
    }

    public CCommMap(int size) {
        super(size);
    }

    @Override
    public final String getCommentInternal(String key) {
        return comments.get(key);
    }

    @Override
    public final CMapping withComments() {
        return this;
    }

    @Override
    public final boolean isCommentSupported() {
        return true;
    }

    public void putCommentDotted(String key, String val) {
        if (dot != null) {
            comm1(key, val);
        } else {
            comments.put(key, val);
        }
    }

    public void comm1(String keys, String val) {
        CharList tmp = dot;
        tmp.clear();

        CEntry entry = this;
        int i = 0;
        do {
            i = _name(keys, tmp, i);

            CMapping map1 = entry.asMap();
            if (i == keys.length()) {
                map1.getComments().put(tmp.toString(), val);
                return;
            } else {
                entry = map1.map.getOrDefault(tmp, CNull.NULL);
                if (entry.getType() == Type.NULL) {
                    map1.map.put(tmp.toString(), entry = new CCommMap());
                } else if (!entry.asMap().isCommentSupported()) {
                    map1.map.put(tmp.toString(), entry = entry.asMap().withComments());
                }
            }
            tmp.clear();
        } while (i < keys.length());
        comments.put(keys, val);
    }

    @Override
    public final Map<String, String> getComments() {
        return comments;
    }
}
