// vite.config.js
import path from 'path';

import unconscious from 'unconscious/VitePlugin.mjs';
import purgecss from 'unconscious/VitePurgeCSS.mjs';

//https://cn.vite.dev/
export default {
    define: {
        UC_PERSIST_STORE: JSON.stringify('rojlib')
    },

    plugins: [
        unconscious(),
        purgecss()
    ],

    base: '', // 绝对路径什么的不要啊
    build: {
        modulePreload: { polyfill: false },
        //sourcemap: true,

        assetsInlineLimit: 512,
        rollupOptions: {
            output: {
                entryFileNames: `[name].[hash].js`,
                chunkFileNames: `chunk.[hash].js`,
                assetFileNames: `[name].[hash].[ext]`
            }
        }
    }
};