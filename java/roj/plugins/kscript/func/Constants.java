package roj.plugins.kscript.func;

import roj.config.data.CList;
import roj.config.data.CMap;
import roj.config.data.CString;

/**
 * @author Roj234
 * @since  2020/9/21 22:45
 */
public final class Constants {
    public static final CMap OBJECT = new CMap();
    public static final KSObject FUNCTION = new KSObject(OBJECT);

    public static final KSFunction ARRAY = new KSFunction((self, args) -> new CList(args.getInteger(0)));
    public static final KSFunction TYPEOF = new KSFunction((self, args) -> CString.valueOf(args.get(0).getType().toString()));

    static {
        OBJECT.put("defineProperty", new KSFunction((self, args) -> {
            if(args.size() < 2) throw new IllegalArgumentException("Need 2, got " + args.size());
            //ObjectPropMap.Object_defineProperty($this.asKObject(), param.get(0).asString(), param.get(1).asObject());
            return null;
        }));
    }
}
