// Backend-independent TypeScript checks of the packaged facade, shared by the Node and browser entry points.
import {
    Automaton,
    Backend,
    BoolExpression,
    IntExpression,
    Mark,
    ModelBuilder,
    Outcome,
    Solver,
    Store,
    StorePropagation,
    abs,
    allDifferent,
    circuit,
    constant,
    count,
    disjunctive,
    increasing,
    lexLess,
    nValue,
    regular,
    subCircuit,
    max,
    min,
    real,
    asRealExpression,
} from "kt-constraints-interop-js";

/** What a route must do beyond the common checks; routes lacking a feature must reject it instead. */
export interface Profile {
    /** Integers beyond 64 bits are solved exactly, not rejected. */
    readonly unboundedIntegers: boolean;
    /** Exact linear reals are solved, not rejected. */
    readonly reals: boolean;
    /** A hard refutation is hard enough on this route to observe abortion. */
    readonly hardAbort: boolean;
}

export function check(condition: boolean, message: string): void {
    if (!condition) throw new Error(message);
}

async function rejects(pending: Promise<unknown>): Promise<boolean> {
    try {
        await pending;
        return false;
    } catch {
        return true;
    }
}

export async function runChecks(backend: Backend, profile: Profile, log: (line: string) => void): Promise<void> {
    const capabilities: string[] = Array.from(backend.capabilities);
    const step = async (name: string, body: (solver: Solver) => Promise<void>): Promise<void> => {
        const solver = backend.createSolver();
        try {
            await body(solver);
            log(`${backend.id}: ${name}`);
        } finally {
            solver.close();
        }
    };

    // Satisfaction with method-based expressions: x + y = 10, x > y, all different.
    await step("satisfaction", async solver => {
        const builder = new ModelBuilder();
        const x: IntExpression = builder.intVar("x", 1n, 10n);
        const y = builder.intVar("y", 1n, 10n);
        builder.add(x.plus(y).eq(constant(10n)));
        const ordered: BoolExpression = x.gt(y).and(allDifferent([x, y]));
        builder.add(ordered);
        const outcome: Outcome = await solver.solve(builder.build());
        check(outcome.status === "FEASIBLE", `satisfaction status ${outcome.status}`);
        check(outcome.route === backend.id, `route ${outcome.route}`);
        const vx: bigint | null | undefined = outcome.solution?.intValueOf(x);
        const vy = outcome.solution?.intValueOf(y);
        check(vx != null && vy != null && vx + vy === 10n && vx > vy, `solution ${vx}, ${vy}`);
    });

    // Min and max globals: max(x, y) = 4 and min(x, y, 9) = 4 force x = y = 4, consistent with |x - 8| = 4.
    await step("min-max", async solver => {
        const builder = new ModelBuilder();
        const x = builder.intVar("x", 0n, 5n);
        const y = builder.intVar("y", 0n, 5n);
        builder.add(max([x, y]).eq(constant(4n)));
        builder.add(min([x, y, constant(9n)]).eq(constant(4n)));
        builder.add(abs(x.minus(constant(8n))).eq(constant(4n)));
        const outcome = await solver.solve(builder.build());
        check(outcome.status === "FEASIBLE", `min-max status ${outcome.status}`);
        check(outcome.solution?.intValueOf(x) === 4n && outcome.solution?.intValueOf(y) === 4n, "min-max solution");
    });

    // Common globals: a circuit fixed by its first successor, two disjunctive tasks, counting and ordering.
    await step("common-globals", async solver => {
        const builder = new ModelBuilder();
        const next = [0, 1, 2].map(i => builder.intVar(`n${i}`, 0n, 2n));
        const starts = [0, 1].map(i => builder.intVar(`s${i}`, 0n, 3n));
        builder.add(circuit(next));
        builder.add(next[0].eq(constant(2n)));
        builder.add(count(constant(0n), next).eq(constant(1n)));
        builder.add(disjunctive(starts, [2n, 2n]));
        builder.add(increasing(starts));
        builder.add(lexLess([starts[0]], [starts[1]]));
        const outcome = await solver.solve(builder.build());
        check(outcome.status === "FEASIBLE", `common-globals status ${outcome.status}`);
        const values = next.map(n => outcome.solution?.intValueOf(n));
        check(values.join(",") === "2,0,1", `circuit ${values}`);
        const [a, b] = starts.map(s => outcome.solution?.intValueOf(s) ?? -1n);
        check(b - a >= 2n, `disjunctive ${a}, ${b}`);
    });

    // Automata, subcircuits, distinct values and soft constraints.
    await step("sequencing-globals", async solver => {
        const builder = new ModelBuilder();
        const word = [0, 1, 2].map(i => builder.intVar(`w${i}`, 0n, 1n));
        const next = [0, 1, 2].map(i => builder.intVar(`n${i}`, 0n, 2n));
        const noDoubleOnes = new Automaton(0, [0, 1]).transition(0, 0n, 0).transition(0, 1n, 1).transition(1, 0n, 0);
        builder.add(regular(word, noDoubleOnes));
        builder.add(subCircuit(next));
        builder.add(next[2].eq(constant(2n)).and(next[0].eq(constant(1n))));
        builder.add(nValue(word).eq(constant(2n)));
        builder.minimizeViolations([word[0].eq(constant(1n)), word[1].eq(constant(1n))], [3n, 1n]);
        const outcome = await solver.solve(builder.build(), 60_000);
        check(outcome.status === "OPTIMAL", `sequencing status ${outcome.status}`);
        check(outcome.objective === 1n, `sequencing objective ${outcome.objective}`);
        check(outcome.solution?.intValueOf(word[0]) === 1n && outcome.solution?.intValueOf(word[1]) === 0n, "sequencing word");
        check(outcome.solution?.intValueOf(next[1]) === 0n, "subcircuit");
    });

    // Optimization: maximize 3x - y subject to x + y <= 12 over 0..10; optimum 30.
    await step("optimization", async solver => {
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

    if (capabilities.includes("ENUMERATION")) {
        // Async iteration over every solution of x < y over 0..2, then the single completion event.
        await step("enumeration", async solver => {
            const builder = new ModelBuilder();
            const x = builder.intVar("x", 0n, 2n);
            const y = builder.intVar("y", 0n, 2n);
            builder.add(x.lt(y));
            const found: string[] = [];
            let completion: Outcome | null | undefined = null;
            for await (const event of solver.solutions(builder.build())) {
                if (event.solution != null) found.push(`${event.solution.intValueOf(x)}<${event.solution.intValueOf(y)}`);
                else completion = event.outcome;
            }
            check(found.sort().join(",") === "0<1,0<2,1<2", `solutions ${found}`);
            check(completion?.termination === "COMPLETED", `completion ${completion?.termination}`);
        });
    }

    // Breaking out of the iteration closes the search; the solver stays usable.
    await step("early-exit", async solver => {
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

    // Integers beyond 2^64 are exact as bigint, or rejected, never rounded.
    await step("big-integers", async solver => {
        const builder = new ModelBuilder();
        const huge = 2n ** 70n;
        const x = builder.intVar("x", 0n, huge * 2n);
        builder.add(x.minus(constant(huge)).eq(constant(1n)));
        if (profile.unboundedIntegers) {
            const outcome = await solver.solve(builder.build());
            check(outcome.solution?.intValueOf(x) === huge + 1n, `value ${outcome.solution?.intValueOf(x)}`);
        } else {
            check(await rejects(solver.solve(builder.build())), "out-of-range integers must be rejected");
        }
    });

    // Exact reals: maximize n subject to 0.1 * n + r <= 1.25 and r >= 0.5, so n = 7 and 1/2 <= r <= 11/20.
    await step("reals", async solver => {
        const builder = new ModelBuilder();
        const n = builder.intVar("n", 0n, 100n);
        const r = builder.realVar("r");
        builder.add(asRealExpression(n).scale("0.1").plus(r).le(real("1.25")));
        builder.add(r.ge(real("1/2")));
        builder.maximize(n);
        if (!profile.reals) {
            check(await rejects(solver.solve(builder.build())), "reals must be rejected");
            return;
        }
        const outcome = await solver.solve(builder.build());
        check(outcome.status === "OPTIMAL" && outcome.solution?.intValueOf(n) === 7n, `real optimization ${outcome.status}`);
        const text: string | null | undefined = outcome.solution?.realValueOf(r);
        const [numerator, denominator = "1"] = (text ?? "").split("/");
        const p = BigInt(numerator), q = BigInt(denominator);
        check(2n * p >= q && 20n * p <= 11n * q, `r ${text}`);
    });

    if (capabilities.includes("INCREMENTAL_ASSERTIONS")) {
        // Incremental session: frames, checkpoints, assumptions and unsat cores made of the caller's own expressions.
        await step("session", async solver => {
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
                const value = restored.solution?.intValueOf(x);
                check(restored.status === "SATISFIABLE" && value != null && value >= 3n, `restored ${value}`);
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
        // Constraint store: post, mark, entail, undo, fail, explain with the caller's expressions, fork.
        const store: Store = backend.createStore();
        try {
            const x = store.intVar("x", 0n, 9n);
            const y = store.intVar("y", 0n, 9n);
            const posted: StorePropagation = await store.constrain(x.plus(y).lt(constant(3n)));
            check(posted.status !== "FAILED", `store post ${posted.status}`);
            const mark: Mark = store.mark();
            await store.constrain(x.eq(constant(2n)));
            check((await store.entails(y.eq(constant(0n)))).value === "ENTAILED", "y is forced to 0");
            store.undoTo(mark);
            const bound = await store.entails(x.lt(constant(3n)));
            check(bound.value === "ENTAILED" && bound.evidence === "SATISFIABILITY", `x < 3: ${bound.value}`);
            const domain: bigint[][] = store.domain(x);
            check(domain.length === 1 && domain[0][0] === 0n && domain[0][1] === 9n, `domain ${domain}`);
            const impossible = x.gt(constant(5n));
            check((await store.constrain(impossible)).status === "FAILED", "x > 5 must fail");
            check(store.isFailed, "the store is failed");
            check((await store.explain())?.includes(impossible) === true, "explanations keep the caller's expressions");
            const child = store.fork();
            try {
                check(child.isFailed, "forks keep the failure");
            } finally {
                child.close();
            }
        } finally {
            store.close();
        }
        log(`${backend.id}: store`);
    } else {
        let refused = false;
        try {
            backend.createStore().close();
        } catch {
            refused = true;
        }
        check(refused, "routes without incremental assertions must refuse stores");
        await step("no-session", async solver => {
            let refused = false;
            try {
                solver.openSession(new ModelBuilder().build());
            } catch {
                refused = true;
            }
            check(refused, "routes without incremental assertions must refuse sessions");
        });
    }

    if (profile.hardAbort) {
        // Aborting the signal rejects the promise and stops a hard refutation (13 pigeons, 12 holes).
        await step("abort", async solver => {
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
    }
}
