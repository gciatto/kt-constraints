// --8<-- [start:imports]
import { ModelBuilder, asRealExpression, loadKotlin, real } from "kt-constraints-interop-js";
// --8<-- [end:imports]
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-milp",
    async run(log) {
        // --8<-- [start:milp]
        const backend = await loadKotlin();
        const solver = backend.createSolver();
        try {
            const builder = new ModelBuilder();
            const integerX = builder.intVar("x", 0n, 10n);
            const x = asRealExpression(integerX);
            const y = builder.realVar("y");
            builder.add(y.ge(real("0")));
            builder.add(x.plus(y).le(real("4.5")));
            builder.add(x.plus(y.scale("3")).le(real("6")));
            builder.maximizeReal(x.scale("3").plus(y.scale("2")));
            const outcome = await solver.solve(builder.build());
            if (outcome.status !== "OPTIMAL" || outcome.realObjective !== "13") throw new Error("Expected optimum 13");
            if (outcome.solution?.realValueOf(y) !== "1/2") throw new Error("Expected y = 1/2");
            if (outcome.solution?.intValueOf(integerX) !== 4n) throw new Error("Expected x = 4");
            if (outcome.realBestBound !== "13") throw new Error("Expected exact bound 13");
            builder.maximize(integerX);
            const integerGoal = await solver.solve(builder.build());
            if (integerGoal.status !== "OPTIMAL" || integerGoal.objective !== 4n) throw new Error("Expected integer optimum 4");
            if (integerGoal.realObjective != null) throw new Error("Expected integer objective field only");
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
        // --8<-- [end:milp]
    },
};
