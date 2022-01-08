package roj.config.data;

import java.util.List;

/**
 * @author Roj233
 * @since 2022/1/12 13:21
 */
public final class CTOMLFxList extends CList {
    public boolean fixed;

    public CTOMLFxList() {}

    public CTOMLFxList(int size) {
        super(size);
    }

    public CTOMLFxList(List<CEntry> list) {
        super(list);
    }

    @Override
    public StringBuilder toTOML(StringBuilder sb, int depth, CharSequence chain) {
        return super.toTOML(sb, fixed ? 3 : depth, chain);
    }
}
