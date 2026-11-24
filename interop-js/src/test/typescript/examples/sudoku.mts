// --8<-- [start:program]
// --8<-- [start:imports]
import { ModelBuilder, allDifferent, constant, loadZ3 } from "kt-constraints-interop-js";
// --8<-- [end:imports]

export const sudoku = {
    name: "tutorials/sudoku",
    async run(log: (line: string) => void) {
        // --8<-- [start:model]
        const clues = [
            [1, 0, 0, 4],
            [0, 4, 1, 0],
            [2, 0, 4, 0],
            [0, 3, 0, 1],
        ];
        const builder = new ModelBuilder();
        const cells = clues.map((row, r) => row.map((_, c) => builder.intVar(`r${r}c${c}`, 1n, 4n)));
        for (let r = 0; r < 4; r++) {
            builder.add(allDifferent(cells[r]));
            builder.add(allDifferent(cells.map(row => row[r])));
            for (let c = 0; c < 4; c++) {
                if (clues[r][c] !== 0) {
                    builder.add(cells[r][c].eq(constant(BigInt(clues[r][c]))));
                }
            }
        }
        for (let r = 0; r < 4; r += 2) {
            for (let c = 0; c < 4; c += 2) {
                builder.add(allDifferent([cells[r][c], cells[r][c + 1], cells[r + 1][c], cells[r + 1][c + 1]]));
            }
        }
        // --8<-- [end:model]
        // --8<-- [start:solve]
        const backend = await loadZ3();
        const solver = backend.createSolver();
        try {
            const outcome = await solver.solve(builder.build());
            const solution = outcome.solution;
            if (outcome.status !== "FEASIBLE" || solution == null) {
                throw new Error(`Sudoku: ${outcome.status}`);
            }
            const grid = cells.map(row => row.map(cell => {
                const value = solution.intValueOf(cell);
                if (value == null) throw new Error("Missing cell");
                return value;
            }));
            for (const row of grid) log(row.map(String).join(" "));
            // --8<-- [end:solve]
            const valid = (group: bigint[]): boolean =>
                new Set(group).size === 4 && group.every(value => value >= 1n && value <= 4n);
            for (let r = 0; r < 4; r++) {
                if (!valid(grid[r]) || !valid(grid.map(row => row[r]))) throw new Error("Invalid row or column");
                for (let c = 0; c < 4; c++) {
                    if (clues[r][c] !== 0 && grid[r][c] !== BigInt(clues[r][c])) throw new Error("Changed clue");
                }
            }
            for (let r = 0; r < 4; r += 2) {
                for (let c = 0; c < 4; c += 2) {
                    if (!valid([grid[r][c], grid[r][c + 1], grid[r + 1][c], grid[r + 1][c + 1]])) {
                        throw new Error("Invalid box");
                    }
                }
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
