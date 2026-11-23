// --8<-- [start:program]
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "libktconstraints_api.h"

static libktconstraints_ExportedSymbols *lib;
#define KC (lib->kotlin.root.io.github.gciatto.ktconstraints.c)
#define DISPOSE(handle) lib->DisposeStablePointer((handle).pinned)
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_IntExpression IntExpression;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_IntExpressionList IntExpressionList;

// add returns another handle to the list; release that returned handle too.
static void append(IntExpressionList list, IntExpression value) {
    IntExpressionList returned = KC.IntExpressionList.add(list, value);
    DISPOSE(returned);
}

int main(void) {
    lib = libktconstraints_symbols();
    // --8<-- [start:model]
    const long costs[3][3] = {{9, 2, 7}, {6, 4, 3}, {5, 8, 1}};
    IntExpression assigned[3][3];
    libktconstraints_kref_io_github_gciatto_ktconstraints_c_ModelBuilder builder = KC.ModelBuilder.ModelBuilder();
    IntExpression one = KC.constant(1);
    IntExpressionList terms = KC.IntExpressionList.IntExpressionList();
    for (int w = 0; w < 3; w++) {
        for (int t = 0; t < 3; t++) {
            char name[32];
            snprintf(name, sizeof(name), "worker%d-task%d", w, t);
            assigned[w][t] = KC.ModelBuilder.intVar(builder, name, 0, 1);
            assert(assigned[w][t].pinned != NULL);
            IntExpression cost = KC.constant(costs[w][t]);
            IntExpression term = KC.IntExpression.times(assigned[w][t], cost);
            append(terms, term);
            DISPOSE(term);
            DISPOSE(cost);
        }
    }
    for (int i = 0; i < 3; i++) {
        IntExpressionList row = KC.IntExpressionList.IntExpressionList();
        IntExpressionList column = KC.IntExpressionList.IntExpressionList();
        for (int j = 0; j < 3; j++) {
            append(row, assigned[i][j]);
            append(column, assigned[j][i]);
        }
        IntExpression rowSum = KC.sum(row);
        IntExpression columnSum = KC.sum(column);
        libktconstraints_kref_io_github_gciatto_ktconstraints_c_BoolExpression rowOne = KC.IntExpression.eq(rowSum, one);
        libktconstraints_kref_io_github_gciatto_ktconstraints_c_BoolExpression columnOne = KC.IntExpression.eq(columnSum, one);
        if (!KC.ModelBuilder.add(builder, rowOne)) return 1;
        if (!KC.ModelBuilder.add(builder, columnOne)) return 1;
        DISPOSE(rowOne);
        DISPOSE(columnOne);
        DISPOSE(rowSum);
        DISPOSE(columnSum);
        DISPOSE(row);
        DISPOSE(column);
    }
    IntExpression totalCost = KC.sum(terms);
    if (!KC.ModelBuilder.minimize(builder, totalCost)) return 1;
    libktconstraints_kref_io_github_gciatto_ktconstraints_c_Model model = KC.ModelBuilder.build(builder);
    // --8<-- [end:model]
    // --8<-- [start:solve]
    libktconstraints_kref_io_github_gciatto_ktconstraints_c_Outcome outcome = KC.solve(model, 10000);
    const char *status = KC.Outcome.get_status(outcome);
    const char *objective = KC.Outcome.get_objective(outcome);
    int failed = status == NULL || strcmp(status, "OPTIMAL") != 0 || objective == NULL || strcmp(objective, "9") != 0;
    printf("%s: cost = %s\n", status == NULL ? "missing status" : status, objective == NULL ? "none" : objective);
    if (failed) {
        const char *message = KC.Outcome.get_message(outcome);
        if (message != NULL) {
            fprintf(stderr, "%s\n", message);
            lib->DisposeString(message);
        }
    }
    int rows[3] = {0}, columns[3] = {0};
    long actualCost = 0;
    for (int w = 0; w < 3; w++) {
        for (int t = 0; t < 3; t++) {
            const char *value = KC.Outcome.valueOf(outcome, assigned[w][t]);
            if (value != NULL && strcmp(value, "1") == 0) {
                printf("worker %d -> task %d\n", w, t);
                rows[w]++;
                columns[t]++;
                actualCost += costs[w][t];
            } else if (value == NULL || strcmp(value, "0") != 0) {
                failed = 1;
            }
            if (value != NULL) lib->DisposeString(value);
        }
    }
    for (int i = 0; i < 3; i++) {
        if (rows[i] != 1 || columns[i] != 1) failed = 1;
    }
    if (actualCost != 9) failed = 1;
    // --8<-- [end:solve]
    // --8<-- [start:cleanup]
    if (status != NULL) lib->DisposeString(status);
    if (objective != NULL) lib->DisposeString(objective);
    DISPOSE(outcome);
    DISPOSE(model);
    DISPOSE(totalCost);
    DISPOSE(terms);
    DISPOSE(one);
    for (int w = 0; w < 3; w++) {
        for (int t = 0; t < 3; t++) DISPOSE(assigned[w][t]);
    }
    DISPOSE(builder);
    return failed;
    // --8<-- [end:cleanup]
}
// --8<-- [end:program]
