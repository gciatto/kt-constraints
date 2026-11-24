package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.integer.IntLiteral
import io.github.gciatto.ktconstraints.core.integer.arithmetic.IntSum
import io.github.gciatto.ktconstraints.core.integer.relation.IntLessOrEqual
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.assignment.project
import io.github.gciatto.ktconstraints.solve.minizinc.lowering.MiniZincDecoder
import io.github.gciatto.ktconstraints.solve.minizinc.runtime.MiniZincProcess
import io.github.gciatto.ktconstraints.solve.minizinc.runtime.solverArguments
import io.github.gciatto.ktconstraints.solve.result.SolveResult
import io.github.gciatto.ktconstraints.solve.result.SolveStatus
import kotlinx.coroutines.runBlocking
import org.gciatto.kt.math.BigInteger
import kotlin.system.measureNanoTime

/** Manual phase benchmark with a fresh native process per sample; never part of routine check. */
object MiniZincBenchmark {
    /** Runs three raw samples; native flatten/solve times are reported separately from JVM wall measurements. */
    @JvmStatic
    fun main(args: Array<String>) =
        runBlocking {
            check(args.isEmpty()) { "Configure MINIZINC_EXECUTABLE rather than passing arguments" }
            val executable = System.getenv("MINIZINC_EXECUTABLE") ?: "minizinc"
            val engine = benchmarkEngine()
            printHeader(engine)
            repeat(3) { sample ->
                lateinit var model: ConstraintModel
                val modelNanos =
                    measureNanoTime {
                        model = createModel()
                    }
                lateinit var compiled: MiniZincModel
                val translationNanos = measureNanoTime { compiled = MiniZincCompiler(engine).compile(model) }
                val setupNanos = measureNanoTime { MiniZincBackend(engine, executable).createSolver().close() }
                val process = MiniZincProcess(executable, compiled.source, null, {}, {}, engine.solverArguments())
                try {
                    val spawnNanos = measureNanoTime { process.start() }
                    val lines = mutableListOf<String>()
                    val started = System.nanoTime()
                    while (true) lines += process.readLine() ?: break
                    val exit = process.exitCode()
                    val runtimeNanos = System.nanoTime() - started
                    lateinit var result: SolveResult
                    val decodeNanos =
                        measureNanoTime {
                            val decoder = MiniZincDecoder(compiled)
                            lines.forEach { decoder.accept(it) }
                            result = decoder.finish(exit, process.stderr())
                        }
                    check(result.status == SolveStatus.OPTIMAL && result.objectiveValue == BigInteger.of(12))
                    val projectionNanos =
                        measureNanoTime {
                            val projected = requireNotNull(result.assignment).project(model.variables.take(1))
                            check(projected.valueOf(model.variables.first() as io.github.gciatto.ktconstraints.core.integer.IntVar) != null)
                        }
                    val stats = result.metadata.statistics
                    println(
                        listOf(
                            sample,
                            modelNanos,
                            translationNanos,
                            setupNanos,
                            spawnNanos,
                            runtimeNanos,
                            decodeNanos,
                            projectionNanos,
                            stats["flatTime"] ?: "unreported",
                            stats["solveTime"] ?: "unreported",
                        ).joinToString(" | ", prefix = "| ", postfix = " |"),
                    )
                } finally {
                    process.close()
                    compiled.close()
                }
            }
        }

    private fun printHeader(engine: MiniZincEngine) {
        println("Engine: ${engine.descriptor.id}")
        println(
            "| sample | model ns | translation ns | availability/setup ns | spawn ns | runtime wall ns | " +
                "decode/validate ns | projection ns | flatTime s | solveTime s |",
        )
        println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
    }

    private fun benchmarkEngine(): MiniZincEngine =
        MiniZincEngine.entries.single { it.engineId == (System.getenv("MINIZINC_BENCHMARK_ENGINE") ?: "gecode") }

    private fun createModel(): ConstraintModel =
        ConstraintModel.of {
            val x = intVar("x", 0..10)
            val y = intVar("y", 0..10)
            val sum = IntSum(x, y)
            add(IntLessOrEqual(sum, IntLiteral(12)))
            maximize(sum)
        }
}
