import { ModelBuilder, constant, loadZ3 } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-optimize",
    async run(log) {
        const backend = await loadZ3();
        const solver = backend.create();
        try {
            // --8<-- [start:optimize]
            const builder = new ModelBuilder();
            const x = builder.intVar("x", 0n, 3n);
            const y = builder.intVar("y", 0n, 3n);
            builder.add(x.plus(y).eq(constant(3n))).maximize(x);
            const outcome = await solver.solve(builder.build(), 1_000);
            if (outcome.status !== "OPTIMAL" || outcome.objective !== 3n) throw new Error("Expected optimum 3");
            // --8<-- [end:optimize]
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
    },
};
