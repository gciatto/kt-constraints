package io.github.gciatto.ktconstraints.core.bitvector

import io.github.gciatto.ktconstraints.core.Sort
import io.github.gciatto.ktconstraints.core.Variable
import io.github.gciatto.ktconstraints.core.VariableId

/** Symbolic fixed-width bit-vector variable. */
class BitVecVar internal constructor(
    override val id: VariableId,
    override val name: String,
    override val width: Int,
) : BitVecExpr,
    Variable {
    init {
        require(width > 0) { "Bit-vector width must be positive" }
    }

    override val sort: Sort get() = Sort.BitVector(width)

    override fun equals(other: Any?): Boolean = other is BitVecVar && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = name
}
