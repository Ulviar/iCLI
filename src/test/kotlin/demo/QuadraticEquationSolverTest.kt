package demo

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuadraticEquationSolverTest {
    private val solver = QuadraticEquationSolver()

    @Test
    fun `solves equation with two distinct real roots`() {
        val roots = solver.solve(1.0, -5.0, 6.0)

        assertContentEquals(doubleArrayOf(2.0, 3.0).toList(), roots.toList())
    }

    @Test
    fun `solves equation with a repeated root`() {
        val roots = solver.solve(1.0, -4.0, 4.0)

        assertEquals(listOf(2.0), roots.toList())
    }

    @Test
    fun `solves linear equation when quadratic term is zero`() {
        val roots = solver.solve(0.0, 2.0, -4.0)

        assertEquals(listOf(2.0), roots.toList())
    }

    @Test
    fun `throws when equation has no unique solution`() {
        assertFailsWith<IllegalArgumentException> {
            solver.solve(0.0, 0.0, 3.0)
        }
    }

    @Test
    fun `throws when discriminant is negative`() {
        assertFailsWith<IllegalArgumentException> {
            solver.solve(1.0, 1.0, 1.0)
        }
    }
}
