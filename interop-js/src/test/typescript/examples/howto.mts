// Executable how-to examples, also usable by the browser runner.
import type { Example } from "./example.mjs";
import { example as optimize } from "./howto-optimize.mjs";
import { example as enumerate } from "./howto-enumerate.mjs";
import { example as session } from "./howto-session.mjs";
import { example as real } from "./howto-real.mjs";
import { example as lp } from "./howto-lp.mjs";
import { example as milp } from "./howto-milp.mjs";
import { example as cancel } from "./howto-cancel.mjs";
import { example as store } from "./howto-store.mjs";

export const examples: Example[] = [optimize, enumerate, session, real, lp, milp, cancel, store];
