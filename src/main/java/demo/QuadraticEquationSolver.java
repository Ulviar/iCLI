package demo;

import java.util.Arrays;

/** Provides methods for solving quadratic equations of the form ax^2 + bx + c = 0. */
public final class QuadraticEquationSolver {
  private static final double EPSILON = 1.0e-9;

  /**
   * Solves the quadratic equation {@code ax^2 + bx + c = 0}.
   *
   * <p>When {@code a} is nearly zero the equation is treated as linear. For quadratic equations
   * with a discriminant close to zero a single root is returned.
   *
   * @param a the quadratic coefficient
   * @param b the linear coefficient
   * @param c the constant term
   * @return an array containing one or two real roots sorted in ascending order
   * @throws IllegalArgumentException if the equation has no real roots
   */
  public double[] solve(double a, double b, double c) {
    if (isApproximatelyZero(a)) {
      return solveLinear(b, c);
    }

    double discriminant = b * b - 4.0 * a * c;
    if (discriminant < -EPSILON) {
      throw new IllegalArgumentException("Quadratic equation has no real roots.");
    }

    if (Math.abs(discriminant) <= EPSILON) {
      return new double[] {-b / (2.0 * a)};
    }

    double sqrtDiscriminant = Math.sqrt(discriminant);
    double denominator = 2.0 * a;
    double root1 = (-b - sqrtDiscriminant) / denominator;
    double root2 = (-b + sqrtDiscriminant) / denominator;
    double[] roots = new double[] {root1, root2};
    Arrays.sort(roots);
    return roots;
  }

  private double[] solveLinear(double b, double c) {
    if (isApproximatelyZero(b)) {
      throw new IllegalArgumentException("Equation has no unique solution.");
    }
    return new double[] {-c / b};
  }

  private boolean isApproximatelyZero(double value) {
    return Math.abs(value) <= EPSILON;
  }
}
