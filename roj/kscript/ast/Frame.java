package roj.kscript.ast;

import roj.collect.ReuseStack;
import roj.kscript.api.IArguments;
import roj.kscript.api.IObject;
import roj.kscript.func.KFuncAST;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.*;
import roj.kscript.util.opm.KOEntry;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * 运行时栈帧 StackFrame <BR>
 *     保存运行时所有数据 (理论上支持多线程了)
 *
 * @author Roj233
 * @since 2020/9/27 12:41
 */
public final class Frame extends Context {
    public Frame(ArrayList<LineInfo> lineIndexes, ContextPrimer ctx, Consumer<Frame> buildCallback) {
        super();
        this.lineIndexes = lineIndexes;
        lineIndexes.trimToSize();

        this.buildCallback = buildCallback;

        this.usedArgs = ctx.usedArgs;
        usedArgs.trimToSize();

        GlobalVarMap globals = ctx.globals;
        globals.applyDefaults();
        this.vars = globals;
    }

    private Frame() {}

    Consumer<Frame> buildCallback;

    // region 函数执行周期
    /**
     * 运行前初始化
     */
    public void reset(IObject $this, IArguments args) {
        this.$this = $this;
        this.args = args;

        if(buildCallback != null) {
            buildCallback.accept(this);
            buildCallback = null;
        }

        // 初始化参数
        final ArrayList<String> args1 = this.usedArgs;
        for (int i = 0; i < args1.size(); i++) {
            String k = args1.get(i);
            if (k != null) {
                vars.put(k, args.get(i));
            }
        }
        // GVM已经reset
    }

    /**
     * 运行后还原状态
     */
    public void cleanup() {
        tail = head;
        stackSize = 0;

        tryCatch.clear();
        exInfo.clear();

        vars.reset();

        $this = null;
        args = null;

        result = null;
    }

    /**
     * 获取返回值
     */
    public KType returnVal() {
        KType result = this.result;
        cleanup();
        if(result == null)
            return KUndefined.UNDEFINED;
        return result instanceof KFuncAST ? ((KFuncAST) result).export(this) : result;
    }

    /**
     * 暂存返回值，若函数运行到此结束，则{@link Node#execute(Frame)}会返回null
     */
    // rw
    public KType result;

    // endregion
    // region 栈

    static final V head = new V(null);
    static {
        head.prev = head;
    }

    // rw
    V tail = head, tmp;
    // rw
    int stackSize, tmpC;

    V tail(int req) {
        if(stackSize < req)
            throw new IllegalStateException("Stack underflow");
        return tail;
    }

    void tail(V tail) {
        this.tail = tail;
    }

    @Nonnull
    public KType last() {
        ce();
        return tail.v;
    }

    @Nonnull
    public KType pop() {
        ce();
        KType v = tail.v;

        final V t = this.tail;
        tail = t.prev;

        if (tmpC < 64) { // what is best?
            V d = tmp;
            if (d != null)
                d.prev = t;
            tmp = t;
            t.prev = null;
            t.v = null;
            tmpC++;
        }

        stackSize--;
        return v;
    }

    private void ce() {
        if (tail == head) throw new IllegalStateException("Stack underflow");
    }

    public void setLast(@Nonnull KType base) {
        ce();
        tail.v = base;
    }

    public void push(@Nonnull KType base) {
        V entry;
        if (tmp != null) {
            tmpC--;
            entry = tmp;
            entry.v = base;
            tmp = tmp.prev;
        } else {
            entry = new V(base);
        }

        entry.prev = tail;
        tail = entry;

        if (++stackSize > 2048)
            throw new IllegalStateException("Stack overflow(2048): " + this);
    }

    public void stackClear() {
        tail = head;
        stackSize = 0;
    }

    @Nonnull
    public KType last(int i) {
        if (i >= stackSize)
            throw new ArrayIndexOutOfBoundsException(i);

        V entry = tail;
        while (i-- > 0) {
            entry = entry.prev;
        }
        return entry.v;
    }

    public Frame staticize(Frame theOne) {
        Frame fr = duplicate();
        Context pr = parent;
        ArrayList<Context> chain = new ArrayList<>();
        chain.add(null);

        while (pr != null) {
            pr = pr.parent;
            chain.add(pr);
            if(theOne == pr) { // theMostUpper
                chain.set(0, new FrameStatic(pr.parent, pr));
                for (int i = 1; i < chain.size(); i++) {
                    chain.set(i, new FrameStatic(chain.get(i - 1), chain.get(i)));
                }
                fr.parent = chain.get(chain.size() - 1);
                return fr;
            }
        }

        throw new IllegalArgumentException("Frame parent not found " + theOne);
    }

    public boolean working() {
        return args != null;
    }

    public void _parent(Context frame) {
        if(this.parent != null)
            throw new IllegalStateException();
        this.parent = frame;
    }

    static final class V {
        KType v;
        V prev;

        V(KType val) {
            this.v = val;
        }
    }

    // endregion
    // region 异常处理

    public final ReuseStack<ExcpInfo> exInfo = new ReuseStack<ExcpInfo>() {
        @Nonnull
        @Override
        public ExcpInfo pop() {
            if(tail == head)
                return ExcpInfo.NONE;

            ExcpInfo v = tail.value;
            tail = tail.prev;
            size--;
            return v;
        }
    }; // try-catch exception temp
    public final ReuseStack<TryNode> tryCatch = new ReuseStack<>();        // try-catch section

    public void trace(Node node, List<StackTraceElement> collector) {
        int line = -1;
        final ArrayList<LineInfo> linf = this.lineIndexes;
        if(owner.getSource() != null && !linf.isEmpty()) {
            Node st = owner.begin;

            int lId = 1;
            LineInfo info = linf.get(0);

            while (st != null) {
                if(st == info.node) {
                    line = info.line;

                    // wssb
                    if(lId < linf.size()) {
                        info = linf.get(lId++);
                    }
                }

                if(st == node)
                    break;

                st = st.next;
            }
        }

        collector.add(new StackTraceElement(owner.getClassName(), owner.getName(), owner.getSource(), line));
        args.trace(collector);
    }

    // endregion
    // region 异常处理.行号计算

    private ArrayList<LineInfo> lineIndexes;

    // endregion
    // region 内部变量

    // owner
    KFuncAST owner;

    public Frame init(KFuncAST owner) {
        this.owner = owner;
        return this;
    }

    public KFuncAST owner() {
        return owner;
    }

    // this
    IObject $this;

    // arguments
    IArguments args;

    // endregion
    // region 变量作用域

    // 使用的参数 (ordered)
    ArrayList<String> usedArgs;

    Map<Node, VInfo> linearDiff;

    public void linear(Node curr) {
        VInfo diff = linearDiff.get(curr);
        while (diff != null) {
            vars.put(diff.id, diff.def);
            diff = diff.next;
        }
    }

    public void applyDiff(VInfo diff) {
        while (diff != null) {
            // ? linear
            vars.put(diff.id, diff.def); // null as remove => not create Entry
            diff = diff.next;
        }
    }

    Frame findProvider(String key) {
        return vars.containsKey(key) ? this : (parent instanceof Frame) ? ((Frame) parent).findProvider(key) : null;
    }

    // 修改锁定后的上下文 (多层闭包节省时间)
    Context[] parents;

    /**
     * 下级只能修改上级通过var导出的函数
     */
    KType getEx(String keys, KType def) {
        if(parents == null) // 退化了, wwwww
            return super.getEx(keys, def);

        Context self = this;
        int i = parents.length;
        while (self != null) {
            KType base = vars.get(keys);
            if (base != null) {
                return base;
            }

            if(i == 0)
                break;
            self = parents[--i];
        }
        return def;
    }

    /**
     * 下级只能修改上级通过var导出的函数
     */
    @Override
    void putEx(String id, KType val) {
        if(parents == null) {
            super.putEx(id, val);
            return;
        }

        Context self = this;
        int i = parents.length;
        while (self != null) {
            KOEntry entry = (KOEntry) self.vars.getEntry(id);
            if (entry != null) {
                if((entry.flags & 1) != 0)
                    throw new JavaException("尝试写入常量 " + id);
                entry.v = val;
                return;
            }

            if(i == 0)
                break;
            self = parents[--i];
        }

        throw new JavaException("未定义的 " + id);
    }

    // endregion
    /**
     * 防止与{@link IObject#copy()}重名
     */
    public final Frame duplicate() {
        Frame copy = new Frame();
        copy.parent = parent;
        copy.vars = new GlobalVarMap(vars);
        copy.usedArgs = usedArgs;
        copy.owner = owner;
        copy.lineIndexes = lineIndexes;
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("KStackFrame{");
        if(result != null) {
            sb.append("<returning>: ").append(result).append(", ");
        }
        sb.append("<stack>: [");
        if (tail != head) {
            V e = tail;
            while (e != head) {
                sb.append(e.v).append(", ");
                e = e.prev;
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        sb.append("], <try-catch>: ").append(exInfo);
        if($this != null) {
            sb.append(", this: ").append($this).append(", arguments: ").append(args);
        }
        return sb.append(", variables: ").append(vars).append('}').toString();
    }
}
