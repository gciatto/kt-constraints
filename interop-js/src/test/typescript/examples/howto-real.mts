import { ModelBuilder, loadZ3, real } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-real",
    async run(log) {
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            // --8<-- [start:real]
            const builder = new ModelBuilder();
            const r = builder.realVar("r");
            builder.add(r.eq(real("0.1")));
            const outcome = await solver.solve(builder.build());
            if (outcome.solution?.realValueOf(r) !== "1/10") throw new Error("Expected exact tenth");
            // --8<-- [end:real]
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
    },
};
