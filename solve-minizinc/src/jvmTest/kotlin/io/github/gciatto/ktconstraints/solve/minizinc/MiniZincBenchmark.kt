package io.github.gciatto.ktconstraints.solve.minizinc

import io.github.gciatto.ktconstraints.core.IntLessOrEqual
import io.github.gciatto.ktconstraints.core.IntLiteral
import io.github.gciatto.ktconstraints.core.IntSum
import io.github.gciatto.ktconstraints.model.ConstraintModel
import io.github.gciatto.ktconstraints.solve.SolveResult
import io.github.gciatto.ktconstraints.solve.SolveStatus
import io.github.gciatto.ktconstraints.solve.project
import kotlinx.coroutines.runBlocking
import org.gciatto.kt.math.BigInteger
import kotlin.system.measureNanoTime

/** Manual phase benchmark with a fresh native process per sample; never part of routine check. */
object MiniZincBenchmark {
    /** Runs three raw samples; native flatten/solve times are reported separately from JVM wall measurements. */
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        check(args.isEmpty()) { "Configure MINIZINC_EXECUTABLE rather than passing arguments" }
        val executable = System.getenv("MINIZINC_EXECUTABLE") ?: "minizinc"
        println("| sample | model ns | translation ns | availability/setup ns | spawn ns | runtime wall ns | decode/validate ns | projection ns | flatTime s | solveTime s |")
        println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|")
        repeat(3) { sample ->
            lateinit var model: ConstraintModel
            val modelNanos = measureNanoTime {
                model = ConstraintModel.of {
                    val x = intVar("x", 0..10)
                    val y = intVar("y", 0..10)
                    val sum = IntSum(x, y)
                    add(IntLessOrEqual(sum, IntLiteral(12)))
                    maximize(sum)
                }
            }
            lateinit var compiled: MiniZincModel
            val translationNanos = measureNanoTime { compiled = MiniZincCompiler().compile(model) }
            val setupNanos = measureNanoTime { MiniZincGecodeBackend(executable).create().close() }
            val process = MiniZincProcess(executable, compiled.source, null, {}, {})
            try {
                val spawnNanos = measureNanoTime { process.start() }
                val lines = mutableListOf<String>()
                val started = System.nanoTime()
                while (true) lines += process.readLine() ?: break
                val exit = process.exitCode()
                val runtimeNanos = System.nanoTime() - started
                lateinit var result: SolveResult
                val decodeNanos = measureNanoTime {
                    val decoder = MiniZincDecoder(compiled)
                    lines.forEach { decoder.accept(it) }
                    result = decoder.finish(exit, process.stderr())
                }
                check(result.status == SolveStatus.OPTIMAL && result.objectiveValue == BigInteger.of(12))
                val projectionNanos = measureNanoTime {
                    val projected = requireNotNull(result.assignment).project(model.variables.take(1))
                    check(projected.valueOf(model.variables.first() as io.github.gciatto.ktconstraints.core.IntVar) != null)
                }
                val stats = result.metadata.statistics
                println("| $sample | $modelNanos | $translationNanos | $setupNanos | $spawnNanos | $runtimeNanos | $decodeNanos | $projectionNanos | ${stats["flatTime"]} | ${stats["solveTime"]} |")
            } finally {
                process.close()
                compiled.close()
            }
        }
    }
}
