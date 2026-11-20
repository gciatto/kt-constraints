// Node entry: Z3 Wasm always; the MiniZinc CLI route too when MINIZINC_EXECUTABLE names an installed CLI.
import { loadMiniZincNode, loadZ3 } from "kt-constraints-interop-js";
import { check, runChecks } from "./checks.mjs";

const environment = (globalThis as { process?: { env: Record<string, string | undefined> } }).process?.env ?? {};

const z3 = await loadZ3();
check(z3.id === "z3/wasm", `route ${z3.id}`);
try {
    await runChecks(z3, { unboundedIntegers: true, reals: true, hardAbort: true }, console.log);
} finally {
    z3.close();
}

const executable = environment.MINIZINC_EXECUTABLE;
if (executable) {
    const miniZinc = await loadMiniZincNode(executable);
    check(miniZinc.id === "gecode/minizinc-js-cli", `route ${miniZinc.id}`);
    try {
        await runChecks(miniZinc, { unboundedIntegers: false, reals: false, hardAbort: false }, console.log);
    } finally {
        miniZinc.close();
    }
} else {
    console.log("MINIZINC_EXECUTABLE unset: the MiniZinc CLI route was not exercised");
}
console.log("TypeScript usage: all checks passed");
