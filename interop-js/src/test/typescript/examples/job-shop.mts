// --8<-- [start:program]
// --8<-- [start:imports]
import { ModelBuilder, constant, disjunctive, loadZ3 } from "kt-constraints-interop-js";
// --8<-- [end:imports]

export const jobShop = {
    name: "tutorials/job-shop",
    async run(log: (line: string) => void) {
        // --8<-- [start:model]
        const builder = new ModelBuilder();
        const a0 = builder.intVar("A-machine0", 0n, 8n);
        const a1 = builder.intVar("A-machine1", 0n, 8n);
        const b0 = builder.intVar("B-machine1", 0n, 8n);
        const b1 = builder.intVar("B-machine0", 0n, 8n);
        builder.add(a0.plus(constant(3n)).le(a1));
        builder.add(b0.plus(constant(2n)).le(b1));
        builder.add(disjunctive([a0, b1], [3n, 1n]));
        builder.add(disjunctive([a1, b0], [2n, 2n]));
        const finish = builder.intVar("finish", 0n, 10n);
        builder.add(a1.plus(constant(2n)).le(finish));
        builder.add(b1.plus(constant(1n)).le(finish));
        builder.minimize(finish);
        // --8<-- [end:model]
        // --8<-- [start:solve]
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            const outcome = await solver.solve(builder.build());
            const solution = outcome.solution;
            if (outcome.status !== "OPTIMAL" || solution == null) {
                throw new Error(`Schedule: ${outcome.status}`);
            }
            const starts = [a0, a1, b0, b1].map(variable => {
                const value = solution.intValueOf(variable);
                if (value == null) throw new Error("Missing start time");
                return value;
            });
            log(`A: machine 0 at ${starts[0]}, machine 1 at ${starts[1]}`);
            log(`B: machine 1 at ${starts[2]}, machine 0 at ${starts[3]}`);
            log(`Finish: ${solution.intValueOf(finish)}`);
            // --8<-- [end:solve]
            const [sa0, sa1, sb0, sb1] = starts;
            const disjoint = (a: bigint, da: bigint, b: bigint, db: bigint): boolean => a + da <= b || b + db <= a;
            if (sa0 + 3n > sa1 || sb0 + 2n > sb1 ||
                !disjoint(sa0, 3n, sb1, 1n) || !disjoint(sa1, 2n, sb0, 2n) ||
                sa1 + 2n > 5n || sb1 + 1n > 5n || solution.intValueOf(finish) !== 5n || solution.objective !== 5n) {
                throw new Error("Invalid or suboptimal schedule");
            }
        // --8<-- [start:cleanup]
        } finally {
            solver.close();
            backend.close();
        }
        // --8<-- [end:cleanup]
    },
};
// --8<-- [end:program]
