// The tutorials' TypeScript examples, quoted by the documentation site.
import type { Example } from "./example.mjs";
import { jobShop } from "./job-shop.mjs";
import { sudoku } from "./sudoku.mjs";

export const examples: Example[] = [sudoku, jobShop];
