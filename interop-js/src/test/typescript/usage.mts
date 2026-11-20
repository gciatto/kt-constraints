// Type-checked by tsc against the generated declarations, then run by Node against the packaged ES module.
import {
    BoolExpression,
    IntExpression,
    ModelBuilder,
    Outcome,
    Solver,
    allDifferent,
    constant,
    loadZ3,
    real,
    toReal,
} from "kt-constraints-interop-js";

function check(condition: boolean, message: string): void {
    if (!condition) throw new Error(message);
}

const backend = await loadZ3();
check(backend.id === "z3/wasm", `route ${backend.id}`);

async function withSolver(body: (solver: Solver) => Promise<void>): Promise<void> {
    const solver = backend.create();
    try {
        await body(solver);
    } finally {
        solver.close();
    }
}

// Satisfaction with method-based expressions: x + y = 10, x > y, all different.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const x: IntExpression = builder.intVar("x", 1n, 10n);
    const y = builder.intVar("y", 1n, 10n);
    builder.add(x.plus(y).eq(constant(10n)));
    const ordered: BoolExpression = x.gt(y).and(allDifferent([x, y]));
    builder.add(ordered);
    const outcome: Outcome = await solver.solve(builder.build());
    check(outcome.status === "FEASIBLE", `satisfaction status ${outcome.status}`);
    const vx: bigint | null | undefined = outcome.solution?.valueOf(x);
    const vy = outcome.solution?.valueOf(y);
    check(vx != null && vy != null && vx + vy === 10n && vx > vy, `solution ${vx}, ${vy}`);
});

// Optimization: maximize 3x - y subject to x + y <= 12 over 0..10; optimum 30.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const x = builder.intVar("x", 0n, 10n);
    const y = builder.intVar("y", 0n, 10n);
    builder.add(x.plus(y).le(constant(12n)));
    builder.maximize(x.times(constant(3n)).minus(y));
    const outcome = await solver.solve(builder.build(), 60_000);
    check(outcome.status === "OPTIMAL", `optimization status ${outcome.status}`);
    check(outcome.objective === 30n, `objective ${outcome.objective}`);
    check(outcome.termination === "COMPLETED", `termination ${outcome.termination}`);
});

// Async iteration over every solution of x < y over 0..2, then the single completion event.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const x = builder.intVar("x", 0n, 2n);
    const y = builder.intVar("y", 0n, 2n);
    builder.add(x.lt(y));
    const found: string[] = [];
    let completion: Outcome | null | undefined = null;
    for await (const event of solver.solutions(builder.build())) {
        if (event.solution != null) found.push(`${event.solution.valueOf(x)}<${event.solution.valueOf(y)}`);
        else completion = event.outcome;
    }
    check(found.sort().join(",") === "0<1,0<2,1<2", `solutions ${found}`);
    check(completion?.termination === "COMPLETED", `completion ${completion?.termination}`);
});

// Breaking out of the iteration closes the search; the solver stays usable.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const x = builder.intVar("x", 0n, 1000n);
    const flag = builder.boolVar("flag");
    builder.add(flag.implies(x.ge(constant(500n))));
    builder.add(flag);
    const model = builder.build();
    for await (const event of solver.solutions(model)) {
        check(event.solution?.boolValueOf(flag) === true, "first solution");
        break;
    }
    check((await solver.solve(model)).status === "FEASIBLE", "solver usable after break");
});

// Integers beyond 2^53 and 2^64 stay exact as bigint.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const huge = 2n ** 70n;
    const x = builder.intVar("x", 0n, huge * 2n);
    builder.add(x.minus(constant(huge)).eq(constant(1n)));
    const outcome = await solver.solve(builder.build());
    check(outcome.solution?.valueOf(x) === huge + 1n, `value ${outcome.solution?.valueOf(x)}`);
});

// Aborting the signal rejects the promise and stops a hard refutation (13 pigeons, 12 holes).
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const pigeons = Array.from({ length: 13 }, (_, i) => builder.intVar(`p${i}`, 0n, 11n));
    builder.add(allDifferent(pigeons));
    const controller = new AbortController();
    const started = Date.now();
    const pending = solver.solve(builder.build(), null, null, controller.signal);
    setTimeout(() => controller.abort(), 200);
    const stuck = new Promise<"stuck">(resolve => setTimeout(() => resolve("stuck"), 10_000));
    const settled = await Promise.race([pending.then(() => "resolved", () => "rejected"), stuck]);
    check(settled === "rejected", `an aborted solve must reject promptly, but it was ${settled}`);
    check(Date.now() - started < 10_000, "abort did not stop the search");
});

// Exact reals: maximize n subject to 0.1 * n + r <= 1.25 and r >= 0.5, so n = 7 and 1/2 <= r <= 11/20.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const n = builder.intVar("n", 0n, 100n);
    const r = builder.realVar("r");
    builder.add(toReal(n).scale("0.1").plus(r).le(real("1.25")));
    builder.add(r.ge(real("1/2")));
    builder.maximize(n);
    const outcome = await solver.solve(builder.build());
    check(outcome.status === "OPTIMAL" && outcome.solution?.valueOf(n) === 7n, `real optimization ${outcome.status}`);
    const text: string | null | undefined = outcome.solution?.realValueOf(r);
    const [numerator, denominator = "1"] = (text ?? "").split("/");
    const p = BigInt(numerator), q = BigInt(denominator);
    check(2n * p >= q && 20n * p <= 11n * q, `r ${text}`);
});

// Incremental session: frames, checkpoints, assumptions and unsat cores made of the caller's own expressions.
await withSolver(async solver => {
    const builder = new ModelBuilder();
    const x = builder.intVar("x", 0n, 10n);
    const session = solver.openSession(builder.build());
    try {
        session.add(x.ge(constant(3n)));
        const before = session.push();
        session.add(x.lt(constant(3n)));
        check((await session.check()).status === "UNSATISFIABLE", "contradictory frame");
        session.popTo(before);
        let stale = false;
        try {
            session.popTo(before);
        } catch {
            stale = true;
        }
        check(stale, "a restored checkpoint must be stale");
        const restored = await session.check();
        const value = restored.solution?.valueOf(x);
        check(restored.status === "SATISFIABLE" && value != null && value >= 3n, `restored ${restored.status} ${value}`);
        const small = x.le(constant(1n));
        const large = x.ge(constant(5n));
        const assumed = await session.check([small, large]);
        check(assumed.status === "UNSATISFIABLE", `assumptions ${assumed.status}`);
        check(assumed.unsatCore?.includes(small) === true, "the core must contain the conflicting assumption");
        check((await session.check([large])).status === "SATISFIABLE", "assumptions hold for one check only");
    } finally {
        session.close();
    }
});

backend.close();
console.log("TypeScript usage: all checks passed");
