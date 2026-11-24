import { ModelBuilder, constant, loadZ3 } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-enumerate",
    async run(log) {
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            // --8<-- [start:enumerate]
            const builder = new ModelBuilder();
            const x = builder.intVar("x", 0n, 2n);
            const values: bigint[] = [];
            let completed = false;
            for await (const event of solver.solutions(builder.build(), 1_000)) {
                if (event.solution != null) values.push(event.solution.intValueOf(x)!);
                else completed = event.outcome?.termination === "COMPLETED";
            }
            if (values.sort().join(",") !== "0,1,2" || !completed) throw new Error("Incomplete enumeration");
            // --8<-- [end:enumerate]
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
    },
};
