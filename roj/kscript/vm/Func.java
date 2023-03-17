package roj.kscript.vm;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.asm.Frame;
import roj.kscript.asm.KS_ASM;
import roj.kscript.asm.Node;
import roj.kscript.func.KFunction;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * @author Roj234
 * @since 2020/9/27 12:28
 */
public class Func extends KFunction {
	public Func(Node begin, Frame frame) {
		this.begin = begin;
		this.frame0 = frame.init(this);
	}

	public Node begin;
	public Frame frame0, curr;

	protected ArrayList<WeakReference<Frame>> unused;
	protected ReferenceQueue<Frame> queue;

	@Override
	public KType invoke(@Nonnull IObject $this, ArgList param) {
		Frame f = curr = getIdleFrame();
		f.init($this, param);

		Node p = begin;

		while (p != null) {
			f.linear(p);
			try {
				p = p.exec(f);
			} catch (Throwable e) {
				ScriptException se = collect(f, p, e);

				f.reset();
				throw se;
			}
		}

		return f.returnVal();
	}

	Frame getIdleFrame() {
		Frame f = frame0;
		if (!f.working()) return f;
		f = f.duplicate();
        /*if(unused == null) {
            unused = new ArrayList<>();
            queue = InstantEnqueuer.set(this);
        }

        ArrayList<WeakReference<Frame>> unused = this.unused;
        synchronized (unused) {
            if(!unused.isEmpty()) {
                do {
                    f = unused.remove(unused.size() - 1).get();
                } while (f == null && !unused.isEmpty());
            }
            if(f == null)
                unused.add(new WeakReference<>(f = frame0.duplicate(), queue));
        }*/

		return f;
	}

	protected ScriptException collect(Frame f, Node p, Throwable e) {
		ScriptException se;
		if (e instanceof ScriptException) {
			se = (ScriptException) e;
		} else {
			ArrayList<StackTraceElement> trace = new ArrayList<>();
			f.trace(p, trace);
			se = new ScriptException("Node#" + nodeId(begin, p) + ": " + p + "\n\n" + f, trace.toArray(new StackTraceElement[trace.size()]), e);
		}
		return se;
	}

	static int nodeId(Node begin, Node target) {
		int i = 0;
		do {
			if (begin == target) {
				return i;
			}
			i++;
		} while ((begin = begin.next) != null);
		return -1;
	}

	@Override
	public StringBuilder toString0(StringBuilder sb, int depth) {
		sb.append("function ").append(getName()).append("() {");
		return KS_ASM.DEBUG ? KS_ASM.toString(begin, sb.append("\n")) : sb.append(" ... ").append('}');
	}

	public KFunction onReturn(Frame frame) {
		return new Func(begin, curr.closure()).set(source, name, clazz);
	}
}
