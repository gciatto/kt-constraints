package io.github.gciatto.ktconstraints.solve.portfolio

/** What scripted backends observed: creations, cancellations and the peak number of concurrent solves. */
internal class PortfolioLog {
    val created = mutableListOf<String>()
    val cancelled = mutableListOf<String>()
    var running = 0
    var peak = 0
}
