package roj.kscript.ast;

import roj.collect.ReuseStack;
import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;
import roj.kscript.type.KUndefined;
import roj.kscript.util.LineInfo;
import roj.kscript.util.VInfo;
import roj.kscript.vm.ErrorInfo;
import roj.kscript.vm.Func;
import roj.kscript.vm.ResourceManager;
import roj.util.ArrayUtil;

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
public final class Frame extends IContext {
    public Frame(ArrayList<roj.kscript.util.LineInfo> lineIndexes, Consumer<Frame> postProcessor) {
        super();
        this.lineIndexes = lineIndexes;
        lineIndexes.trimToSize();

        this.builder = postProcessor;

        // 默认栈大小 = 4
        stack = new KType[4];
    }

    private Frame() {}

    Consumer<Frame> builder;

    // region 函数执行周期
    /**
     * 运行前初始化
     */
    public void init(IObject $this, ArgList args) {
        ResourceManager.get().pushStack();

        this.$this = $this;
        this.args = args;

        if(builder != null) {
            builder.accept(this);
            initChk();
            builder = null;
        }

        // 初始化参数
        int i = 0;
        for (; i < usedArgs.length; i++) {
            lvt[i] = args.get(usedArgs[i]);
        }

        for (int j = 0; i < lvt.length; i++, j++) {
            KType def = this.lvtDef[j];
            if (def != null) {
                KType chk = this.lvtChk[j];
                if (!def.equalsTo(chk)) chk.copyFrom(def);
                def = chk;
            } else {
                def = KUndefined.UNDEFINED;
            }
            lvt[i] = def;
        }
    }

    private void initChk() {
        if(lvtDef.length > 0) {
            lvtChk = new KType[lvtDef.length];
            for (int i = 0; i < lvtDef.length; i++) {
                KType def = this.lvtDef[i];
                if (def != null) {
                    this.lvtChk[i] = def.copy();
                }
            }
        }
        result = KUndefined.UNDEFINED;
    }

    /**
     * 运行后还原状态
     */
    public void reset() {
        stackClear();

        tryCatch.clear();
        exInfo.clear();

        $this = null;
        args = null;

        result = KUndefined.UNDEFINED;

        ResourceManager.get().popStack();
    }

    /**
     * 获取返回值
     */
    public KType returnVal() {
        KType result = this.result;
        reset();
        return result instanceof KFunction ? ((KFunction) result).onReturn(this) : result;
    }

    /**
     * 暂存返回值，若函数运行到此结束，则{@link Node#execute(Frame)}会返回null
     */
    // rw
    public KType result;

    // endregion
    // region 栈

    // rw
    KType[] stack;
    // rw
    int stackSize;

    @Nonnull
    public KType last() {
        return stack[stackSize - 1];
    }

    @Nonnull
    public KType pop() {
        KType v = stack[--stackSize];
        stack[stackSize] = null;
        return v;
    }

    public void setLast(@Nonnull KType base) {
        stack[stackSize - 1] = base;
    }

    public void push(@Nonnull KType base) {
        if(stackSize == stack.length) {
            KType[] plus = new KType[(int) (stackSize * 1.5)];
            System.arraycopy(stack, 0, plus, 0, stackSize);
            stack = plus;
        }

        stack[stackSize] = base;

        if (++stackSize > 2048)
            throw new IllegalStateException("Stack overflow(2048): " + this);
    }

    public void stackClear() {
        for (int i = 0; i < stackSize; i++) {
            stack[i] = null;
        }
        stackSize = 0;
    }

    @Nonnull
    public KType last(int i) {
        if (i >= stackSize)
            throw new ArrayIndexOutOfBoundsException(stackSize - 1 - i);
        return stack[stackSize - 1 - i];
    }

    public Frame closure() {
        Frame fr = duplicate();

        IContext[] arr = fr.parents;
        for (int i = 1; i < arr.length - 1; i++) {
            arr[i] = new Closure(parents[i]);
        }

        return fr;
    }

    public boolean working() {
        return args != null;
    }

    // endregion
    // region 异常处理

    public final ReuseStack<ErrorInfo> exInfo = new ReuseStack<ErrorInfo>() {
        @Nonnull
        @Override
        public ErrorInfo pop() {
            if(tail == head)
                return ErrorInfo.NONE;

            ErrorInfo v = tail.value;
            tail = tail.prev;
            size--;
            return v;
        }
    };
    // try-catch exception temp
    public final ReuseStack<TryNode> tryCatch = new ReuseStack<>();        // try-catch section

    public void trace(Node node, List<StackTraceElement> collector) {
        int line = -1;
        final ArrayList<roj.kscript.util.LineInfo> linf = this.lineIndexes;
        if(owner.getSource() != null && !linf.isEmpty()) {
            Node st = owner.begin;

            int lId = 1;
            roj.kscript.util.LineInfo info = linf.get(0);

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
    Func owner;

    public Frame init(Func owner) {
        this.owner = owner;
        return this;
    }

    public Func owner() {
        return owner;
    }

    // this
    IObject $this;

    // arguments
    ArgList args;

    // endregion
    // region 变量, and its 作用域

    // var and let by index
    KType[] lvt, lvtDef, lvtChk;
    String[] varNames;

    // 使用的参数 (ordered)
    int[] usedArgs;

    // 线性lets
    Map<Node, VInfo> linearDiff;

    public void linear(Node curr) {
        applyDiff(linearDiff.get(curr));
    }

    public void applyDiff(VInfo diff) {
        while (diff != null) {
            // ? linear
            lvt[diff.idx] = diff.v;
            diff = diff.next;
        }
    }

    // 修改锁定后的上下文 (多层闭包节省时间, 还有int化的变量)
    IContext[] parents;

    @Override
    public KType get(String key) {
        return parents[parents.length - 1].get(key);
    }

    @Override
    KType getEx(String keys, KType def) {
        return parents[parents.length - 1].getEx(keys, def);
    }

    @Override
    public void put(String id, KType val) {
        parents[parents.length - 1].put(id, val);
    }

    @Override
    KType getIdx(int index) {
        return lvt[index];
    }

    @Override
    void putIdx(int index, KType value) {
        lvt[index] = value;
    }

    // endregion
    /**
     * 防止与{@link IObject#copy()}重名
     */
    public final Frame duplicate() {
        Frame copy = new Frame();
        copy.stack = new KType[stack.length]; // inheritance
        copy.usedArgs = usedArgs;
        copy.linearDiff = linearDiff;
        if(lvt.length == 0) {
            copy.lvt = copy.lvtChk = copy.lvtDef = lvt;
            copy.parents = parents;
        } else {
            copy.lvtDef = lvtDef;
            copy.lvt = new KType[lvt.length];
            copy.initChk();
            copy.parents = new IContext[parents.length];
            if(parents.length > 0)
                System.arraycopy(parents, 1, copy.parents, 1, parents.length - 1);
            copy.parents[0] = copy;
        }
        copy.owner = owner;
        copy.lineIndexes = lineIndexes;
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("KFrame{");
        if(result != null) {
            sb.append("<return>: ").append(result).append(", ");
        }
        if(stackSize > 0) {
            sb.append("<stack>: [").append(ArrayUtil.toString(stack, 0, stackSize)).append("], ");
        }
        sb.append("<var>: [").append(ArrayUtil.toString(lvt, 0, lvt.length)).append("], ");
        if(!exInfo.isEmpty())
            sb.append("<try>: ").append(exInfo).append("], ");
        if($this != null) {
            sb.append("this: ").append($this)
                    .append(", arg: ").append(args);
        }
        return sb.append('}').toString();
    }
}
