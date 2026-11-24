@file:JvmName("AutoBackends")

package io.github.gciatto.ktconstraints.solve.auto

import io.github.gciatto.ktconstraints.solve.spi.ranking.RankingPolicy
import kotlinx.coroutines.runBlocking

/**
 * Blocking counterpart of [AutoBackend.load] for Java (as `AutoBackends.load(...)`) and non-suspending Kotlin: loads
 * the routes bundled for the JVM on the calling thread. Close the result, with try-with-resources or `use`, to release
 * their runtimes.
 */
@JvmName("load")
@JvmOverloads
fun loadAutoBackend(
    strategy: AutoStrategy = AutoStrategy.Fallback,
    policy: RankingPolicy = RankingPolicy.DEFAULT,
): AutoBackend = runBlocking { AutoBackend.load(strategy, policy) }
