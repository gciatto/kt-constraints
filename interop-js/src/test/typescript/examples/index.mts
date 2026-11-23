// Every documentation example, run by node.mts.
import type { Example } from "./example.mjs";
import { examples as howto } from "./howto.mjs";
import { examples as tutorials } from "./tutorials.mjs";

export const examples: Example[] = [...tutorials, ...howto];
