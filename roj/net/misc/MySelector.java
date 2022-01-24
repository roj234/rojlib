package roj.net.misc;

import roj.collect.SimpleList;
import roj.reflect.DirectAccessor;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Roj233
 * @since 2022/1/24 11:32
 */
public class MySelector {
    public static final class MyKeySet extends SimpleList<SelectionKey> implements Set<SelectionKey> {
        MyKeySet() {
            super(100);
        }
    }

    private interface H {
        void setSelectedSet(Selector selector, Set<SelectionKey> selectedSet);
        void setPublicSelectedSet(Selector selector, Set<SelectionKey> totalSet);
        void setSet(Selector selector, HashSet<SelectionKey> selectedSet);
        HashSet<SelectionKey> getSet(Selector selector);
        void setPublicSet(Selector selector, Set<SelectionKey> totalSet);
    }

    static H setter;
    public static Selector open() throws IOException {
        Selector t = Selector.open();
        if (setter == null) {
            synchronized (H.class) {
                if (setter == null) {
                    String[] fields = new String[] {
                            "selectedKeys", "publicSelectedKeys", "keys", "publicKeys"
                    };
                    String[] setters = new String[] {
                            "setSelectedSet", "setPublicSelectedSet", "setSet", "setPublicSet"
                    };
                    String[] getters = new String[] {
                            null, null, "getSet" , null
                    };
                    setter = DirectAccessor
                            .builder(H.class).unchecked()
                            .access(t.getClass(), fields, getters, setters)
                            .build();
                }
            }
        }
        MyKeySet set = new MyKeySet();
        setter.setSelectedSet(t, set);
        setter.setPublicSelectedSet(t, set);
        //MyHashSet<SelectionKey> set1 = new MyHashSet<>();
        //Hotspot调用用的method index
        //setter.setSet(t, set1);
        setter.setPublicSet(t, setter.getSet(t));
        return t;
    }
}
