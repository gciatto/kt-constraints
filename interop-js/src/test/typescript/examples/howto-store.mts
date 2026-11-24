import { constant, loadZ3 } from "kt-constraints-interop-js";
import type { Example } from "./example.mjs";

export const example: Example = {
    name: "how-to-store",
    async run(log) {
        const backend = await loadZ3();
        try {
            // --8<-- [start:store]
            const store = backend.createStore();
            try {
                const x = store.intVar("x", 0n, 3n);
                const before = store.mark();
                await store.constrain(x.eq(constant(2n)));
                if ((await store.entails(x.eq(constant(2n)))).value !== "ENTAILED") throw new Error("Expected entailment");
                store.undoTo(before);
                if ((await store.entails(x.eq(constant(2n)))).value !== "UNDETERMINED") throw new Error("Expected restored store");
            } finally {
                store.close();
            }
            // --8<-- [end:store]
            log("passed");
        } finally {
            backend.close();
        }
    },
};
