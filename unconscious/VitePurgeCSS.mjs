import purgeCSS from "purgecss";

/**
 * @param {Object | purgeCSS.UserDefinedOptions} options
 * @return {vite.Plugin}
 */
export default (options = {}) => {
    let _html = "";
    return {
      name: "vite-plugin-purgecss",
      enforce: "post",
      transformIndexHtml(html) {
        _html += html;
      },
      async generateBundle(_options, bundle) {
        const cssFiles = Object.keys(bundle).filter((key) => key.endsWith(".css"));
        if (!cssFiles)
          return;
        for (const file of cssFiles) {
          const purged = await (new purgeCSS.PurgeCSS).purge({
            content: [{ raw: _html + " " + Object.entries(bundle).map(([k, v]) => {
              return v.code;
            }).join("; "), extension: "html" }],
            css: [{ raw: bundle[file].source }],
            ...options
          });
          bundle[file].source = purged[0].css;
        }
      }
    };
  }
