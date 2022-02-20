package roj.asm.tree.insn;

import javax.annotation.Nonnull;

/**
 * @author Roj233
 * @since 2022/2/26 19:58
 */
public final class SwitchEntry implements Comparable<SwitchEntry> {
    public final int key;
    public Object node;

    public SwitchEntry(int key, int node) {
        this.key = key;
        this.node = node;
    }

    public SwitchEntry(int key, Number node) {
        this.key = key;
        this.node = node;
    }

    public SwitchEntry(int key, InsnNode node) {
        this.key = key;
        this.node = node;
    }

    @Override
    public int compareTo(@Nonnull SwitchEntry o) {
        return Integer.compare(key, o.key);
    }

    public int getBci() {
        return node instanceof InsnNode ? InsnNode.validate((InsnNode) node).bci : ((Number) node).intValue();
    }
}
