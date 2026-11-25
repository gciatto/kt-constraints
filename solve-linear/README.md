# :solve-linear

Exact linear and mixed-integer linear programs for backend authors. `LinearProgram.of(model)` translates the
linear fragment of a model, a conjunction of linear relations over Boolean, integer and real variables with a
linear objective, into columns and rows with exact rational coefficients; anything else is reported unsupported
by name. Programs keep strict real inequalities marked, so exact routes can honour them and floating-point routes
can reject them. `LinearSolution` checks candidate values exactly, measuring constraint violation and
integrality, and builds the model's assignment.
