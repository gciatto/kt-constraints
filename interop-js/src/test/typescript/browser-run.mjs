// Serves the bundled browser checks with cross-origin isolation (needed by z3-solver) and runs them in Chromium.
import { createServer } from "node:http";
import { readFile } from "node:fs/promises";
import { extname, join, normalize } from "node:path";
import { chromium } from "playwright-core";

const root = process.argv[2];
const types = { ".html": "text/html", ".js": "text/javascript", ".wasm": "application/wasm", ".data": "application/octet-stream" };
const server = createServer(async (request, response) => {
    const path = normalize(new URL(request.url, "http://localhost").pathname).replace(/^\/+/, "") || "index.html";
    try {
        const body = await readFile(join(root, path));
        response.writeHead(200, {
            "Content-Type": types[extname(path)] ?? "application/octet-stream",
            "Cross-Origin-Opener-Policy": "same-origin",
            "Cross-Origin-Embedder-Policy": "require-corp",
        });
        response.end(body);
    } catch {
        response.writeHead(404).end();
    }
});
await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
const browser = await chromium.launch({ executablePath: process.env.CHROME_BIN });
let outcome = "timed out";
try {
    const page = await browser.newPage();
    const finished = new Promise(resolve => {
        page.exposeFunction("report", line => {
            console.log(line);
            if (line.startsWith("FAILED") || line.endsWith("all checks passed")) resolve(line);
        });
    });
    page.on("pageerror", error => console.log(`page error: ${error.message}`));
    await page.goto(`http://127.0.0.1:${server.address().port}/index.html`);
    outcome = await Promise.race([finished, new Promise(resolve => setTimeout(() => resolve("timed out"), 840_000))]);
} finally {
    await browser.close();
    server.close();
}
if (!outcome.endsWith("all checks passed")) {
    console.error(`Browser usage failed: ${outcome}`);
    process.exit(1);
}
