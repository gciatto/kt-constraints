import { ModelBuilder, constant, loadKotlin, loadZ3 } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-session",
    async run(log) {
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            // --8<-- [start:session]
            const builder = new ModelBuilder();
            const x = builder.intVar("x", 0n, 3n);
            builder.add(x.ge(constant(1n)));
            const session = solver.openSession(builder.build());
            try {
                const before = session.push();
                session.add(x.eq(constant(0n)));
                if ((await session.check()).status !== "UNSATISFIABLE") throw new Error("Expected contradiction");
                session.popTo(before);
                const impossible = x.lt(constant(1n));
                const checked = await session.check([impossible]);
                if (!checked.unsatCore?.includes(impossible)) throw new Error("Expected assumption in core");
                if ((await session.check()).status !== "SATISFIABLE") throw new Error("Expected restored assertions");
            } finally {
                session.close();
            }
            // --8<-- [end:session]
        } finally {
            solver.close();
            backend.close();
        }
        const kotlin = await loadKotlin();
        const optimizer = kotlin.createSolver();
        try {
            // --8<-- [start:session-optimization]
            const builder = new ModelBuilder();
            const x = builder.intVar("x", 0n, 9n);
            const y = builder.intVar("y", 0n, 9n);
            builder.add(x.plus(y).le(constant(12n)));
            const goal = x.times(constant(2n)).plus(y);
            const session = optimizer.openSession(builder.build());
            try {
                if ((await session.maximize(goal)).objective !== 21n) throw new Error("Expected 21");
                const before = session.push();
                session.add(y.ge(constant(5n)));
                if ((await session.maximize(goal)).objective !== 19n) throw new Error("Expected 19 in the frame");
                session.popTo(before);
                if ((await session.maximize(goal, [x.le(constant(4n))])).objective !== 16n) throw new Error("Expected 16");
                if ((await session.maximize(goal)).objective !== 21n) throw new Error("Expected 21 again");
            } finally {
                session.close();
            }
            // --8<-- [end:session-optimization]
            log("passed");
        } finally {
            optimizer.close();
            kotlin.close();
        }
    },
};
