"use strict";

import fs from 'fs/promises';
import path from 'path';

/**
 * 它不导出我有什么办法
 * @return {Promise<boolean>}
 */
async function injectBabelAddExports() {
    const fileUrl = new URL(import.meta.resolve("@babel/parser"));
    const filePath = path.normalize(fileUrl.pathname.slice(process.platform === 'win32' ? 1 : 0));

    const MARKER = '/*UC_MARKER*/';
    const MY_EXPORT = 'exports._uc_mixinPlugins=mixinPlugins;exports._uc_mixinPluginNames=mixinPluginNames;exports._uc_tokenLabelName=tokenLabelName;exports._uc_tokenIsKeyword=tokenIsKeyword;';
    const markerLength = MARKER.length;

    const stats = await fs.stat(filePath);
    const fileSize = stats.size;

    const fd = await fs.open(filePath, 'a+');
    try {
        const buffer = Buffer.alloc(markerLength);
        await fd.read(buffer, 0, markerLength, fileSize - markerLength);

        if (buffer.toString() !== MARKER) {
            await fd.writeFile(MY_EXPORT+MARKER);
            return true;
        }

        return false;
    } finally {
        await fd.close();
    }
}

/**
 * 它先于任何我能控制的脚本执行我有什么办法 (可以写一个entrypoint替代vite cli，但是懒)
 * @return {Promise<boolean>}
 */
async function injectViteBin() {
    const MY_CODE = '/*UCBetterNodeImport*/{const a = process.cwd, b = a(), c=process.env.INIT_CWD;process.env.ORIG_CWD=b;process.cwd = () => {const d = a();return d===b?c:d};}';
    const vitePath = path.join(process.cwd(), 'node_modules', 'vite');

    const packageJsonPath = path.join(vitePath, 'package.json');
    const viteEntryPath = path.join(vitePath, JSON.parse(await fs.readFile(packageJsonPath, 'utf8')).bin.vite);

    let code = await fs.readFile(viteEntryPath, 'utf8');
    if (code.includes(MY_CODE)) return false;

    const lines = code.split('\n');

    if (lines[0].startsWith('#!')) {
        lines.splice(1, 0, MY_CODE);
    } else {
        console.warn('Warning: No shebang found in vite entrypoint.');
        lines.unshift(MY_CODE);
    }

    code = lines.join('\n');
    await fs.writeFile(viteEntryPath, code, {encoding: "utf8"});
    return true;
}

/**
 * 我必须这么做来处理导入语句
 * @return {Promise<boolean>}
 */
function injectViteConfigLoad() {
    const REPLACE_FROM = `[{
\t\t\tname: "externalize-deps",`;
    const REPLACE_TO = `[{
  name: "UCBetterNodeImport",
  setup(build) {
    build.onResolve({ filter: /^[^.@/].+\\// }, (args) => {
    const fileAtProjectRoot = path.join(process.env.ORIG_CWD, args.path);
      if (fs.existsSync(fileAtProjectRoot)) return { path: fileAtProjectRoot };
    });
  }
},`+REPLACE_FROM.substring(1);
    const vitePath = path.join(process.cwd(), 'node_modules', 'vite');

    return new Promise(resolve => {
        async function replaceInFiles(dir) {
            const entries = await fs.readdir(dir, { withFileTypes: true });
            for (const entry of entries) {
                const fullPath = path.join(dir, entry.name);
                if (entry.isDirectory()) {
                    // 如果是目录，递归处理
                    await replaceInFiles(fullPath);
                } else if (entry.isFile() && path.extname(entry.name) === '.js') {
                    // 如果是.js文件，读取内容并替换
                    let content = await fs.readFile(fullPath, 'utf8');
                    let content1 = content.replace(REPLACE_FROM, REPLACE_TO);
                    if (content !== content1) {
                        await fs.writeFile(fullPath, content1, {encoding: "utf8"});
                        console.log(`Processed: ${fullPath}`);
                        resolve(true);
                        return;
                    }
                }
            }
        }

        replaceInFiles(vitePath).then(() => resolve(false));
    });
}

/**
 * 清理垃圾
 * @return {Promise<number>}
 */
async function deleteMapFiles(dir) {
    let deletedCount = 0;
    const items = await fs.readdir(dir, { withFileTypes: true });
    for (const item of items) {
        const fullPath = path.join(dir, item.name);

        if (item.isDirectory()) {
            if (item.name === 'node_modules' || dir.includes('node_modules')) {
                deletedCount += await deleteMapFiles(fullPath);
            }
        } else if (item.isFile() && path.extname(item.name) === '.map') {
            await fs.unlink(fullPath);
            deletedCount++;
        }
    }

    return deletedCount;
}

const startDir = process.argv[2] || process.cwd();

// Mixin太好用了家人们！
// 你先别管这是否违反NPM的ToS（反正我发布在GitHub上），你就说好不好用吧
Promise.all([injectBabelAddExports(), injectViteBin(), injectViteConfigLoad(), deleteMapFiles(path.join(startDir, 'node_modules'))])
    .then(([addExportOk, injectViteOk, injectViteConfigOk, deleteMapCount]) => {
        if (addExportOk) console.log('Babel注入成功.');
        if (injectViteOk) console.log('Vite注入成功.');
        if (injectViteConfigOk) console.log('Vite MJS Bundler注入成功.');
        if (deleteMapCount) console.log(`Deleted ${deleteMapCount} unused files.`);
}).catch(err => {
    console.error('操作文件时出错:', err);
});
