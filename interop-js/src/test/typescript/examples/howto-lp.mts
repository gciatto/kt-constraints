// --8<-- [start:imports]
import { ModelBuilder, loadKotlin, real } from "kt-constraints-interop-js";
// --8<-- [end:imports]
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-lp",
    async run(log) {
        // --8<-- [start:lp]
        const backend = await loadKotlin();
        const solver = backend.createSolver();
        try {
            const builder = new ModelBuilder();
            const x = builder.realVar("x");
            const y = builder.realVar("y");
            builder.add(x.ge(real("0")));
            builder.add(y.ge(real("0")));
            builder.add(x.plus(y).le(real("4.5")));
            builder.add(x.plus(y.scale("3")).le(real("6")));
            builder.maximizeReal(x.scale("3").plus(y.scale("2")));
            const outcome = await solver.solve(builder.build());
            if (outcome.status !== "OPTIMAL" || outcome.realObjective !== "27/2") throw new Error("Expected optimum 27/2");
            if (outcome.solution?.realValueOf(y) !== "0") throw new Error("Expected y = 0");
            if (outcome.solution?.realValueOf(x) !== "9/2") throw new Error("Expected x = 9/2");
            if (outcome.realBestBound !== "27/2") throw new Error("Expected exact bound 27/2");
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
        // --8<-- [end:lp]
    },
};
