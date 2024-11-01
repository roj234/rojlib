package roj.plugins.kscript.func;

import roj.config.data.CEntry;
import roj.config.data.CList;

/**
 * @author Roj234
 * @since 2024/11/24 0024 4:03
 */
@FunctionalInterface
public interface JavaFunc {CEntry invoke(CEntry self, CList args);}
