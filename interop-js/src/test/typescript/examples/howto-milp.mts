import { ModelBuilder, asRealExpression, loadZ3, real } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-milp",
    async run(log) {
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            // --8<-- [start:milp]
            const builder = new ModelBuilder();
            const x = asRealExpression(builder.intVar("x", 0n, 10n));
            const y = builder.realVar("y");
            builder.add(y.ge(real("0")));
            builder.add(x.plus(y).le(real("4.5")));
            builder.add(x.plus(y.scale("3")).le(real("6")));
            builder.maximizeReal(x.scale("3").plus(y.scale("2")));
            const outcome = await solver.solve(builder.build());
            if (outcome.status !== "OPTIMAL" || outcome.realObjective !== "13") throw new Error("Expected optimum 13");
            if (outcome.solution?.realValueOf(y) !== "1/2") throw new Error("Expected y = 1/2");
            // --8<-- [end:milp]
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
    },
};
