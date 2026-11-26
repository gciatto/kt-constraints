// --8<-- [start:program]
#include <assert.h>
#include <string.h>
#include "libktconstraints_api.h"

static libktconstraints_ExportedSymbols *lib;
#define KC (lib->kotlin.root.io.github.gciatto.ktconstraints.c)
#define DISPOSE(handle) lib->DisposeStablePointer((handle).pinned)
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_ModelBuilder ModelBuilder;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_Model Model;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_Outcome Outcome;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_IntExpression IntExpression;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_RealExpression RealExpression;
typedef libktconstraints_kref_io_github_gciatto_ktconstraints_c_BoolExpression BoolExpression;

static void expect(const char *actual, const char *expected) {
    assert(actual != NULL && strcmp(actual, expected) == 0);
    lib->DisposeString(actual);
}

static void post(ModelBuilder builder, BoolExpression constraint) {
    int added = KC.ModelBuilder.add(builder, constraint);
    assert(added);
    DISPOSE(constraint);
}

// --8<-- [start:model]
// Maximize 3x + 2y with x,y >= 0, x+y <= 4.5 and x+3y <= 6.
static Model linear_model(ModelBuilder builder, RealExpression x, RealExpression y) {
    RealExpression zero = KC.realConstant("0");
    RealExpression two = KC.realConstant("2");
    RealExpression three = KC.realConstant("3");
    RealExpression limit = KC.realConstant("4.5"); // Exactly 9/2; "9/2" is accepted too.
    RealExpression six = KC.realConstant("6");
    RealExpression sum = KC.RealExpression.plus(x, y);
    RealExpression threeY = KC.RealExpression.times(three, y);
    RealExpression row = KC.RealExpression.plus(x, threeY);
    post(builder, KC.RealExpression.ge(x, zero));
    post(builder, KC.RealExpression.ge(y, zero));
    post(builder, KC.RealExpression.le(sum, limit));
    post(builder, KC.RealExpression.le(row, six));
    RealExpression threeX = KC.RealExpression.times(x, three);
    RealExpression twoY = KC.RealExpression.times(two, y);
    RealExpression objective = KC.RealExpression.plus(threeX, twoY);
    int set = KC.ModelBuilder.maximizeReal(builder, objective);
    assert(set);
    Model model = KC.ModelBuilder.build(builder);
    DISPOSE(objective);
    DISPOSE(twoY);
    DISPOSE(threeX);
    DISPOSE(row);
    DISPOSE(threeY);
    DISPOSE(sum);
    DISPOSE(six);
    DISPOSE(limit);
    DISPOSE(three);
    DISPOSE(two);
    DISPOSE(zero);
    return model;
}
// --8<-- [end:model]

static void continuous_lp(void) {
    // --8<-- [start:lp]
    ModelBuilder builder = KC.ModelBuilder.ModelBuilder();
    RealExpression x = KC.ModelBuilder.realVar(builder, "x");
    RealExpression y = KC.ModelBuilder.realVar(builder, "y");
    assert(x.pinned != NULL && y.pinned != NULL);
    Model model = linear_model(builder, x, y);
    Outcome outcome = KC.solve(model, 10000);
    expect(KC.Outcome.get_status(outcome), "OPTIMAL");
    expect(KC.Outcome.get_realObjective(outcome), "27/2");
    expect(KC.Outcome.valueOfReal(outcome, x), "9/2");
    expect(KC.Outcome.valueOfReal(outcome, y), "0");
    assert(KC.Outcome.get_objective(outcome) == NULL);
    // --8<-- [end:lp]
    // --8<-- [start:cleanup]
    DISPOSE(outcome);
    DISPOSE(model);
    DISPOSE(y);
    DISPOSE(x);
    DISPOSE(builder);
    // --8<-- [end:cleanup]
}

static void mixed_integer_lp(void) {
    // --8<-- [start:milp]
    ModelBuilder builder = KC.ModelBuilder.ModelBuilder();
    IntExpression integerX = KC.ModelBuilder.intVar(builder, "x", 0, 10);
    assert(integerX.pinned != NULL);
    RealExpression x = KC.IntExpression.toReal(integerX);
    RealExpression y = KC.ModelBuilder.realVar(builder, "y");
    assert(y.pinned != NULL);
    Model model = linear_model(builder, x, y);
    Outcome outcome = KC.solve(model, 10000);
    expect(KC.Outcome.get_status(outcome), "OPTIMAL");
    expect(KC.Outcome.get_realObjective(outcome), "13");
    expect(KC.Outcome.valueOf(outcome, integerX), "4");
    expect(KC.Outcome.valueOfReal(outcome, y), "1/2");
    assert(KC.Outcome.get_objective(outcome) == NULL);
    // toReal() is a compound expression; only a declared real variable has valueOfReal().
    assert(KC.Outcome.valueOfReal(outcome, x) == NULL);
    // --8<-- [end:milp]
    DISPOSE(outcome);
    DISPOSE(model);
    DISPOSE(y);
    DISPOSE(x);
    DISPOSE(integerX);
    DISPOSE(builder);
}

int main(void) {
    lib = libktconstraints_symbols();
    continuous_lp();
    mixed_integer_lp();
    return 0;
}
// --8<-- [end:program]
