// Serve the pinned package's worker/Wasm/data from Karma's own origin.
const fs = require('node:fs');
const path = require('node:path');
let directory = process.cwd();
let assets;
while (!assets) {
    const candidate = path.join(directory, 'node_modules/minizinc/dist');
    if (fs.existsSync(path.join(candidate, 'minizinc-worker.js'))) {
        assets = candidate;
    } else {
        const parent = path.dirname(directory);
        if (parent === directory) throw new Error('MiniZinc npm assets were not installed');
        directory = parent;
    }
}
config.files.push({ pattern: path.join(assets, '*'), included: false, served: true, watched: false });
config.proxies = { ...config.proxies, '/minizinc/': '/absolute' + assets + '/' };
config.set({
    failOnFailingTestSuite: true,
    failOnEmptyTestSuite: true,
    browserConsoleLogOptions: { level: 'debug', terminal: true },
});
