package roj.config.data;

import roj.collect.MyHashMap;

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

    @Override
    public final Map<String, String> getComments() {
        return comments;
    }
}
