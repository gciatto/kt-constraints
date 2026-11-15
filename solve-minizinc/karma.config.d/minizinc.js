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
// The Kotlin reporter drops browser output emitted outside a test; keep all of it for diagnosis.
const consoleLog = path.join(process.cwd(), 'browser-console.log');
fs.writeFileSync(consoleLog, '');
function BrowserConsoleFile() {
    this.onBrowserLog = (browser, log, type) => fs.appendFileSync(consoleLog, `[${type}] ${log}\n`);
    this.onBrowserError = (browser, error) => fs.appendFileSync(consoleLog, `[browser-error] ${JSON.stringify(error)}\n`);
}
config.plugins.push({ 'reporter:browser-console-file': ['type', BrowserConsoleFile] });
config.reporters = [...(config.reporters || []), 'browser-console-file'];
// Simulated latency for the worker script makes lifecycle races with loading pool workers deterministic.
function SlowWorkerScript() {
    return (request, response, next) =>
        request.url.includes('minizinc-worker.js') ? setTimeout(next, 300) : next();
}
config.plugins.push({ 'middleware:slow-minizinc-worker': ['factory', SlowWorkerScript] });
config.beforeMiddleware = [...(config.beforeMiddleware || []), 'slow-minizinc-worker'];
