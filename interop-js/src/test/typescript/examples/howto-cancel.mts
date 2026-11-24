import { ModelBuilder, loadZ3 } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-cancel",
    async run(log) {
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            // --8<-- [start:cancel]
            const builder = new ModelBuilder();
            builder.intVar("x", 0n, 3n);
            const controller = new AbortController();
            controller.abort();
            let rejected = false;
            try {
                await solver.solve(builder.build(), null, null, controller.signal);
            } catch {
                rejected = true;
            }
            if (!rejected) throw new Error("Aborted solve must reject");
            // --8<-- [end:cancel]
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
    },
};
