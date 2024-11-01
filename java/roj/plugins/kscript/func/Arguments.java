package roj.plugins.kscript.func;

import roj.config.data.CEntry;
import roj.config.data.CNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj234
 * @since  2020/10/27 22:44
 */
public class Arguments extends KSArray {
    public Arguments(int size) {super(size);}
    public Arguments(List<? extends CEntry> list) {super(list);}

    public List<StackTraceElement> trace() {
        ArrayList<StackTraceElement> collector = new ArrayList<>();
        trace(collector);
        return collector;
    }

    public void trace(List<StackTraceElement> collector) {

    }

    @Override
    public CEntry getVar(String name) {
        if (name.equals("caller")) return CNull.NULL;
        return super.getVar(name);
    }
}
