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
 * 也许我该提一个PR？
 * @return {Promise<boolean>}
 */
function injectViteConfigLoad() {
    const REPLACE_FROM = [
        `workerConstructor === "Worker" ? \`\${jsContent}
            const blob = typeof self !== "undefined" && self.Blob && new Blob([\${workerType === "classic" ? \`'(self.URL || self.webkitURL).revokeObjectURL(self.location.href);',\` : \`'URL.revokeObjectURL(import.meta.url);',\`}jsContent], { type: "text/javascript;charset=utf-8" });
            export default function WorkerWrapper(options) {
              let objURL;
              try {
                objURL = blob && (self.URL || self.webkitURL).createObjectURL(blob);
                if (!objURL) throw ''
                const worker = new \${workerConstructor}(objURL, \${workerTypeOption});
                worker.addEventListener("error", () => {
                  (self.URL || self.webkitURL).revokeObjectURL(objURL);
                });
                return worker;
              } catch(e) {
                return new \${workerConstructor}(
                  'data:text/javascript;charset=utf-8,' + encodeURIComponent(jsContent),
                  \${workerTypeOption}
                );
              }
            }\` : `
    ];
    const REPLACE_TO = [
        `workerConstructor === "Worker" ? \`\${jsContent}
            const blob = typeof self !== "undefined" && self.Blob && new Blob([\${workerType === "classic" ? \`'(self.URL || self.webkitURL).revokeObjectURL(self.location.href);',\` : \`'URL.revokeObjectURL(import.meta.url);',\`}jsContent], { type: "text/javascript;charset=utf-8" });
            export default function WorkerWrapper(options) {
                const objURL = URL.createObjectURL(blob);
                const worker = new \${workerConstructor}(objURL, \${workerTypeOption});
                worker.addEventListener("error", () => {
                  URL.revokeObjectURL(objURL);
                });
                return worker;
            }\` : `
    ];

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
                    let content1 = content;
                    for (let i = 0; i < REPLACE_FROM.length; i++) {
                        content1 = content1.replace(REPLACE_FROM[i], REPLACE_TO[i]);
                    }
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

function scriptPath(pkg) {
    const fileUrl = new URL(import.meta.resolve(pkg));
    return path.normalize(fileUrl.pathname.slice(process.platform === 'win32' ? 1 : 0));
}

const startDir = process.argv[2] || process.cwd();

let vitePath = scriptPath("vite").replaceAll("\\", "/");
let i = vitePath.lastIndexOf("node_modules/vite");
if (i < 0) throw new Error("Could not find node_modules");
vitePath = vitePath.substring(0, i+17);

// Mixin太好用了家人们！
// 你先别管这是否违反NPM的ToS（反正我发布在GitHub上），你就说好不好用吧
Promise.all([injectBabelAddExports(), injectViteConfigLoad(), deleteMapFiles(path.join(vitePath, '..'))])
    .then(([addExportOk, injectViteOk, deleteMapCount]) => {
        if (addExportOk) console.log('Babel注入成功.');
        if (injectViteOk) console.log('Vite Polyfill注入成功.');
        if (deleteMapCount) console.log(`Deleted ${deleteMapCount} unused files.`);
}).catch(err => {
    console.error('操作文件时出错:', err);
});