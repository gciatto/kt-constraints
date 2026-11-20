// Browser entry, served cross-origin isolated: Z3 Wasm and MiniZinc's Chuffed Wasm from the page's own origin.
import { loadMiniZincBrowser, loadZ3 } from "kt-constraints-interop-js";
import { check, runChecks } from "./checks.mjs";

const report = (globalThis as { report?: (line: string) => void }).report ?? console.log;

try {
    const z3 = await loadZ3();
    check(z3.id === "z3/wasm", `route ${z3.id}`);
    try {
        await runChecks(z3, { unboundedIntegers: true, reals: true, hardAbort: true }, report);
    } finally {
        z3.close();
    }
    // The worker loads its assets from a blob, so the URLs must be absolute.
    const asset = (name: string): string => new URL(name, location.href).href;
    const miniZinc = await loadMiniZincBrowser(asset("minizinc-worker.js"), asset("minizinc.wasm"), asset("minizinc.data"));
    check(miniZinc.id === "chuffed/minizinc-wasm", `route ${miniZinc.id}`);
    try {
        await runChecks(miniZinc, { unboundedIntegers: false, reals: false, hardAbort: false }, report);
    } finally {
        miniZinc.close();
    }
    report("TypeScript browser usage: all checks passed");
} catch (failure) {
    report(`FAILED: ${failure instanceof Error ? failure.stack ?? failure.message : String(failure)}`);
}
