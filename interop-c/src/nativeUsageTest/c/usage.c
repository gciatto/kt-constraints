// Kt-Constraints as C code sees it, through the shared library and header Kotlin/Native generates
// (`./gradlew :interop-c:nativeUsageTest` builds them for the host, compiles this file against them and runs it).
// Every Kotlin object is a handle (a struct holding a pointer) to dispose of with DisposeStablePointer; every returned
// string is to dispose of with DisposeString. Invalid input gives null handles or false, never an exception.
#include <stdio.h>
#include <string.h>

#include "libktconstraints_api.h"

static libktconstraints_ExportedSymbols *lib;
static int failures = 0;

#define KC (lib->kotlin.root.io.github.gciatto.ktconstraints.c)
#define DISPOSE(handle) lib->DisposeStablePointer((handle).pinned)

typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_IntExpression IntExpression;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_BoolExpression BoolExpression;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_IntExpressionList IntExpressionList;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_ModelBuilder ModelBuilder;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_Model Model;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_Outcome Outcome;

// Checks that a string returned by Kotlin is the expected one, then frees it
static void expect(const char *what, const char *actual, const char *expected) {
    if (actual == NULL || strcmp(actual, expected) != 0) {
        printf("FAILED %s: expected %s, got %s\n", what, expected, actual == NULL ? "NULL" : actual);
        failures++;
    } else {
        printf("ok %s = %s\n", what, actual);
    }
    if (actual != NULL) lib->DisposeString(actual);
}

static void check(const char *what, int condition) {
    printf("%s %s\n", condition ? "ok" : "FAILED", what);
    if (!condition) failures++;
}

// Three different digits with x + y = 2z and x > y, maximizing x * y.
static void optimization(void) {
    ModelBuilder builder = KC.ModelBuilder.ModelBuilder();
    IntExpression x = KC.ModelBuilder.intVar(builder, "x", 1, 9);
    IntExpression y = KC.ModelBuilder.intVar(builder, "y", 1, 9);
    IntExpression z = KC.ModelBuilder.intVar(builder, "z", 1, 9);

    IntExpressionList digits = KC.IntExpressionList.IntExpressionList();
    KC.IntExpressionList.add(digits, x);
    KC.IntExpressionList.add(digits, y);
    KC.IntExpressionList.add(digits, z);
    KC.ModelBuilder.add(builder, KC.allDifferent(digits));
    KC.ModelBuilder.add(builder, KC.IntExpression.eq(KC.IntExpression.plus(x, y), KC.IntExpression.times(z, KC.constant(2))));
    KC.ModelBuilder.add(builder, KC.IntExpression.gt(x, y));
    KC.ModelBuilder.maximize(builder, KC.IntExpression.times(x, y));

    Outcome outcome = KC.solve(KC.ModelBuilder.build(builder), 0);
    expect("status", KC.Outcome.get_status(outcome), "OPTIMAL");
    expect("route", KC.Outcome.get_route(outcome), "kotlin/direct");
    expect("objective", KC.Outcome.get_objective(outcome), "63");
    expect("x", KC.Outcome.valueOf(outcome, x), "9");
    expect("y", KC.Outcome.valueOf(outcome, y), "7");
    DISPOSE(outcome);
    DISPOSE(digits);
    DISPOSE(builder);
}

// Booleans, unsatisfiability, exact big constants and rejected input.
static void edge_cases(void) {
    ModelBuilder builder = KC.ModelBuilder.ModelBuilder();
    BoolExpression p = KC.ModelBuilder.boolVar(builder, "p");
    check("duplicate name gives a null handle", KC.ModelBuilder.boolVar(builder, "p").pinned == NULL);
    check("empty interval gives a null handle", KC.ModelBuilder.intVar(builder, "e", 2, 1).pinned == NULL);
    check("malformed constant gives a null handle", KC.constantOf("12x").pinned == NULL);

    IntExpression big = KC.ModelBuilder.intVar(builder, "big", 0, 1);
    IntExpression huge = KC.constantOf("123456789012345678901234567890");
    KC.ModelBuilder.add(builder, KC.BoolExpression.implies(p, KC.IntExpression.eq(big, KC.constant(1))));
    KC.ModelBuilder.add(builder, p);
    KC.ModelBuilder.add(builder, KC.IntExpression.lt(KC.IntExpression.times(big, huge), KC.constantOf("200000000000000000000000000000")));
    Outcome satisfiable = KC.solve(KC.ModelBuilder.build(builder), 10000);
    expect("satisfiable status", KC.Outcome.get_status(satisfiable), "FEASIBLE");
    check("p is true", KC.Outcome.truthOf(satisfiable, p) == 1);
    expect("big", KC.Outcome.valueOf(satisfiable, big), "1");

    KC.ModelBuilder.add(builder, KC.BoolExpression.not_(p));
    Outcome unsatisfiable = KC.solve(KC.ModelBuilder.build(builder), 10000);
    expect("unsatisfiable status", KC.Outcome.get_status(unsatisfiable), "INFEASIBLE");
    check("no value without a solution", KC.Outcome.valueOf(unsatisfiable, big) == NULL);
    DISPOSE(unsatisfiable);
    DISPOSE(satisfiable);
    DISPOSE(builder);
}

int main(void) {
    lib = libktconstraints_symbols();
    optimization();
    edge_cases();
    if (failures > 0) {
        printf("%d check(s) failed\n", failures);
        return 1;
    }
    printf("all checks passed\n");
    return 0;
}
