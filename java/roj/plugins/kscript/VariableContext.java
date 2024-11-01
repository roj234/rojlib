package roj.plugins.kscript;

import roj.config.data.CEntry;

/**
 * @author Roj234
 * @since  2021/5/28 22:45
 */
public interface VariableContext {
    default CEntry getThis() {return null;}
    CEntry getVar(String name);
    void putVar(String name, CEntry value);
}
