package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;

import org.eclipse.collections.api.list.ImmutableList;

public class EstimationUtils {
	public static BigDecimal getMean(ImmutableList<BigDecimal> values) {
		if (values.isEmpty())
			return BigDecimal.ZERO;
		else
			return getSum(values).divide(new BigDecimal(values.size()), MathContext.DECIMAL128);
	}

	public static BigDecimal getSum(ImmutableList<BigDecimal> values) {
		return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	public static BigDecimal getVariance(ImmutableList<BigDecimal> values) {
		if (values.isEmpty())
			return BigDecimal.ZERO;
		else {
			BigDecimal mean = getMean(values), N = BigDecimal.valueOf(values.size());

			// Sum of squared differences from the mean.
			BigDecimal squaredDiffSum = values.stream()
					.map(x -> x.subtract(mean).pow(2).divide(N, MathContext.DECIMAL128))
					.reduce(BigDecimal.ZERO, BigDecimal::add);

			return squaredDiffSum;
		}
	}
}
