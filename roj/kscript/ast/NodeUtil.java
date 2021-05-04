package roj.kscript.ast;

import roj.collect.CrossFinder;
import roj.collect.MyHashSet;
import roj.kscript.util.Variable;

import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2021/4/28 18:32
 */
public class NodeUtil {
    public static VInfo calcDiff(List<CrossFinder.Wrap<Variable>> self, List<CrossFinder.Wrap<Variable>> dest) {
        MyHashSet<Variable> cvHas = new MyHashSet<>(self.size());
        VInfo root = new VInfo(), curr = root;
        // Pass 0
        for (int i = 0; i < self.size(); i++) {
            CrossFinder.Wrap<Variable> wrap = self.get(i);
            cvHas.add(wrap.sth);
        }
        // Pass 1: check dest.add
        for (int i = 0; i < dest.size(); i++) {
            CrossFinder.Wrap<Variable> wrap = dest.get(i);
            if(!cvHas.remove(wrap.sth)) {
                // append variable
                curr.id = wrap.sth.name;
                curr.v = wrap.sth.def;
                curr.next = new VInfo();
                curr = curr.next;
            //} else {
            //     duplicate variable, pass
            }
        }
        // Pass 2: check dest.remove
        for (Variable var : cvHas) {
            curr.id = var.name;
            curr.next = new VInfo();
            curr = curr.next;
        }
        // Pass 3: remove useless curr
        if(curr == root)
            return null;
        VInfo end = curr;
        curr = root;
        while (true) {
            if(curr.next == end) {
                curr.next = null;
                break;
            }
            curr = curr.next;
        }

        return root;
    }
}
