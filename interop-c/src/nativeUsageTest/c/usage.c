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
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_RealExpression RealExpression;
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

// Exact reals, all comparisons, real minimization and preserved integer-objective accessors.
static void real_edge_cases(void) {
    ModelBuilder builder = KC.ModelBuilder.ModelBuilder();
    ModelBuilder foreign = KC.ModelBuilder.ModelBuilder();
    RealExpression x = KC.ModelBuilder.realVar(builder, "x");
    RealExpression other = KC.ModelBuilder.realVar(foreign, "other");
    check("real variable is declared", x.pinned != NULL);
    check("duplicate real name gives a null handle", KC.ModelBuilder.realVar(builder, "x").pinned == NULL);
    check("invalid real name gives a null handle", KC.ModelBuilder.realVar(builder, "").pinned == NULL);
    check("malformed real constant gives a null handle", KC.realConstant("4.5x").pinned == NULL);
    check("zero denominator gives a null handle", KC.realConstant("1/0").pinned == NULL);
    check("empty real constant gives a null handle", KC.realConstant("").pinned == NULL);

    RealExpression zero = KC.realConstant("0");
    RealExpression half = KC.realConstant("1/2");
    RealExpression three = KC.realConstant("3");
    RealExpression negative = KC.realConstant("-3");
    RealExpression fraction = KC.realConstant("9/2");
    RealExpression decimal = KC.realConstant("4.5");
    RealExpression sum = KC.RealExpression.plus(x, three);
    RealExpression difference = KC.RealExpression.minus(sum, x);
    RealExpression negated = KC.RealExpression.negate(three);
    BoolExpression constraints[] = {
        KC.RealExpression.eq(x, half),
        KC.RealExpression.neq(x, zero),
        KC.RealExpression.lt(x, three),
        KC.RealExpression.le(x, half),
        KC.RealExpression.gt(x, zero),
        KC.RealExpression.ge(x, half),
        KC.RealExpression.eq(difference, three),
        KC.RealExpression.eq(negated, negative),
        KC.RealExpression.eq(fraction, decimal)
    };
    for (unsigned i = 0; i < sizeof(constraints) / sizeof(constraints[0]); i++) {
        check("real constraint accepted", KC.ModelBuilder.add(builder, constraints[i]));
        DISPOSE(constraints[i]);
    }
    BoolExpression foreignConstraint = KC.RealExpression.eq(x, other);
    check("foreign real constraint rejected", !KC.ModelBuilder.add(builder, foreignConstraint));
    check("foreign real minimum rejected", !KC.ModelBuilder.minimizeReal(builder, other));
    check("foreign real maximum rejected", !KC.ModelBuilder.maximizeReal(builder, other));
    DISPOSE(foreignConstraint);
    check("real minimum accepted", KC.ModelBuilder.minimizeReal(builder, x));
    Model model = KC.ModelBuilder.build(builder);
    Outcome minimum = KC.solve(model, 10000);
    expect("real minimum status", KC.Outcome.get_status(minimum), "OPTIMAL");
    expect("real minimum", KC.Outcome.get_realObjective(minimum), "1/2");
    expect("real value", KC.Outcome.valueOfReal(minimum, x), "1/2");
    check("no integer objective for real minimum", KC.Outcome.get_objective(minimum) == NULL);
    check("no real value for a literal", KC.Outcome.valueOfReal(minimum, half) == NULL);
    check("no real value for a compound expression", KC.Outcome.valueOfReal(minimum, sum) == NULL);
    DISPOSE(minimum);
    DISPOSE(model);

    IntExpression one = KC.constant(1);
    check("integer objective replaces real minimum", KC.ModelBuilder.maximize(builder, one));
    model = KC.ModelBuilder.build(builder);
    Outcome integer = KC.solve(model, 10000);
    expect("integer objective with real constraints", KC.Outcome.get_objective(integer), "1");
    check("no real objective for integer maximum", KC.Outcome.get_realObjective(integer) == NULL);
    expect("real value with integer objective", KC.Outcome.valueOfReal(integer, x), "1/2");
    DISPOSE(integer);
    DISPOSE(model);
    DISPOSE(one);

    BoolExpression impossible = KC.RealExpression.neq(x, half);
    check("contradiction accepted", KC.ModelBuilder.add(builder, impossible));
    DISPOSE(impossible);
    model = KC.ModelBuilder.build(builder);
    Outcome infeasible = KC.solve(model, 10000);
    expect("real infeasible status", KC.Outcome.get_status(infeasible), "INFEASIBLE");
    check("no real objective without a solution", KC.Outcome.get_realObjective(infeasible) == NULL);
    check("no real value without a solution", KC.Outcome.valueOfReal(infeasible, x) == NULL);
    DISPOSE(infeasible);
    DISPOSE(model);
    DISPOSE(negated);
    DISPOSE(difference);
    DISPOSE(sum);
    DISPOSE(decimal);
    DISPOSE(fraction);
    DISPOSE(negative);
    DISPOSE(three);
    DISPOSE(half);
    DISPOSE(zero);
    // Reals have no product of two variable expressions, so the facade refuses to build one.
    RealExpression product = KC.RealExpression.times(other, other);
    check("real variable product is refused", product.pinned == NULL);
    DISPOSE(other);
    DISPOSE(x);
    DISPOSE(foreign);
    DISPOSE(builder);
}

int main(void) {
    lib = libktconstraints_symbols();
    optimization();
    edge_cases();
    real_edge_cases();
    if (failures > 0) {
        printf("%d check(s) failed\n", failures);
        return 1;
    }
    printf("all checks passed\n");
    return 0;
}
