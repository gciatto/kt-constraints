// The official z3-solver browser build needs its Emscripten script on the page and SharedArrayBuffer,
// which requires cross-origin isolation: serve every response with COOP/COEP from Karma's own origin.
const fs = require('node:fs');
const path = require('node:path');
let directory = process.cwd();
let assets;
while (!assets) {
    const candidate = path.join(directory, 'node_modules/z3-solver/build');
    if (fs.existsSync(path.join(candidate, 'z3-built.js'))) {
        assets = candidate;
    } else {
        const parent = path.dirname(directory);
        if (parent === directory) throw new Error('z3-solver npm assets were not installed');
        directory = parent;
    }
}
config.files.unshift({ pattern: path.join(assets, 'z3-built.js'), included: true, served: true, watched: false });
config.files.push({ pattern: path.join(assets, 'z3-built.wasm'), included: false, served: true, watched: false });
config.set({
    customHeaders: [
        { match: '.*', name: 'Cross-Origin-Opener-Policy', value: 'same-origin' },
        { match: '.*', name: 'Cross-Origin-Embedder-Policy', value: 'require-corp' },
    ],
    failOnFailingTestSuite: true,
    failOnEmptyTestSuite: true,
});
