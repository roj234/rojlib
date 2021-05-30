package roj.mod;

import roj.config.JSONConfiguration;
import roj.config.data.CMapping;

import java.io.File;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/29 8:53
 */
final class Config extends JSONConfiguration {
    String currentProject, currentVersion, atName, charset, required;
    boolean at;

    public Config(File file) {
        super(file);
    }

    @Override
    protected void readConfig(CMapping map) {
        charset = map.putIfAbsent("charset", "UTF-8");
        currentProject = map.putIfAbsent("project", "example");
        currentVersion = map.putIfAbsent("version", "1.0.0");
        atName = map.putIfAbsent("atName", currentProject + "_at.cfg");
        at = map.putIfAbsent("enableAccessTransformer", false);
        required = map.putIfAbsent("beforeThis", "");
    }

    @Override
    protected void saveConfig(CMapping map) {
        map.put("charset", charset);
        map.put("project", currentProject);
        map.put("version", currentVersion);
        map.put("atName", atName);
        map.put("enableAccessTransformer", at);
        map.put("beforeThis", required);
    }
}
