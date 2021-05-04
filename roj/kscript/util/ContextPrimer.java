package roj.kscript.util;

import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.ReuseStack;
import roj.kscript.ast.Context;
import roj.kscript.ast.Frame;
import roj.kscript.ast.Node;
import roj.kscript.type.KType;
import roj.kscript.util.opm.GlobalVarMap;
import roj.kscript.util.opm.KOEntry;
import roj.util.Helpers;

import java.util.ArrayList;
import java.util.Set;

/**
 * 作用域构建器
 *
 * @author solo6975
 * @since 2020/10/17 23:52
 */
public final class ContextPrimer {
    public final ArrayList<String> usedArgs = new ArrayList<>();
    public final GlobalVarMap globals;
    public final ArrayList<Variable> locals = new ArrayList<>();

    private MyHashMap<String, Variable> inRegion = new MyHashMap<>();
    public ReuseStack<Set<String>> creations = new ReuseStack<>();

    private ContextPrimer parent;
    private Context built;

    public ContextPrimer(Context ctx) {
        this.globals = (GlobalVarMap) ctx.getInternal();
    }

    private ContextPrimer(ContextPrimer parent) {
        this.parent = parent;
        this.globals = new GlobalVarMap();
    }

    // region 代码块

    public void enterRegion() {
        creations.push(new MyHashSet<>());
    }

    public void endRegion() {
        for(String name : creations.pop()) {
            inRegion.remove(name);
        }
    }

    public void finish(Context ctx) {
        built = ctx;

        // gc
        inRegion.clear();
        inRegion = null;
        creations.clear();
        creations = null;

        if(parent != null) {
            // remove arg name in global, check if needed?
            for (int i = 0; i < usedArgs.size(); i++) {
                String s = usedArgs.get(i);
                globals.remove(s);
            }

            // 因为现在移动了初始化时间, 所以上级一定已经初始化
            ((Frame)ctx)._parent(parent.built);
        }
    }

    // endregion

    public boolean exists(String name) {
        return globals.containsKey(name) || inRegion.containsKey(name) || (parent != null && parent.exists(name));
    }

    public boolean selfExists(String name) {
        return globals.containsKey(name) || inRegion.containsKey(name);
    }

    // region 作用域

    public void global(String key, KType val) {
        globals.putIfAbsent(key, val);
    }

    public void Const(String key, KType val) {
        globals.putIfAbsent(key, val);
        globals.markConst(key);
    }

    public void local(String key, KType val, Node node) {
        Variable v = inRegion.get(key);
        if(v == null) {
            inRegion.put(key, v = new Variable(key, val, node, null));
            locals.add(v);
        } else {
            System.err.println("Duplicate " + key);
        }

        v.end = node;

        if (!creations.isEmpty())
            creations.last().add(key);
    }

    // endregion

    public void loadArg(int id, String as) {
        if(parent == null) {
            throw new IllegalArgumentException("<global> function doesn't has any parameter.");
        }

        KType v = globals.get(as);
        if(v == null) {
            globals.put(as, null);

            usedArgs.ensureCapacity(id);
            while (usedArgs.size() < id) {
                usedArgs.add(null);
            }
            usedArgs.add(as);
        }
    }

    public ContextPrimer makeChild() {
        return new ContextPrimer(this);
    }

    public boolean isConst(String name) {
        KOEntry entry = (KOEntry) globals.getEntry(name);
        return entry != null && (entry.flags & 1) != 0;
    }

    public void updateRegion(String key, Node last) {
        Variable v = inRegion.get(key);
        if(v == null) {
            return;
        }

        v.end = last;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Ctx{");
        if(!globals.isEmpty()) {
            sb.append("vars={");
            final Set<KOEntry> set = Helpers.cast(globals.entrySet());
            for (KOEntry entry : set) {
                sb.append(entry.getKey()).append('=').append(entry.getValue());
                if ((entry.flags & 1) != 0)
                    sb.append("[final]");
                sb.append(',');
            }
            sb.setCharAt(sb.length() - 1, '}');
            sb.append(", ");
        }
        if(!locals.isEmpty()) {
            sb.append("lets=").append(locals).append(", ");
        }
        if(parent != null)
            sb.append("parent=").append(parent).append(", ");
        return sb.append('}').toString();
    }
}
