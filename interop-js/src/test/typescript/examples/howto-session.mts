import { ModelBuilder, constant, loadZ3 } from "kt-constraints-interop-js";
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
            log("passed");
        } finally {
            solver.close();
            backend.close();
        }
    },
};
