@file:JvmName("AutoBackends")

package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import kotlinx.coroutines.runBlocking
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Blocking counterpart of [AutoBackend.load] for Java (as `AutoBackends.loadAutoBackend(...)`) and non-suspending
 * Kotlin on the JVM and Kotlin/Native: loads the routes bundled for the platform on the calling thread. Close the
 * result, with try-with-resources or `use`, to release their runtimes.
 */
@JvmOverloads
fun loadAutoBackend(
    strategy: AutoStrategy = AutoStrategy.Fallback,
    policy: RankingPolicy = RankingPolicy.DEFAULT,
): AutoBackend = runBlocking { AutoBackend.load(strategy, policy) }
