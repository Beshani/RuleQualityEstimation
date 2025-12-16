package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

import ch.obermuhlner.math.big.BigDecimalMath;
import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// https://www.jstor.org/stable/2290733
public class PoissonEstimator extends FrequencyEstimator {

	public PoissonEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public BigDecimal getEstimation() {
		if (this.successes.intValue() == 0)
			return BigDecimal.ZERO;
		else {
			Map<Integer, Long> histogram = getHistogram();

			BigDecimal f1 = getF1(histogram), f2 = getF2(histogram);

			BigDecimal poisson = BigDecimal.ONE.subtract(BigDecimalMath.exp(
					BigDecimal.valueOf(2).negate().multiply(f1).divide(f2.add(BigDecimal.ONE), MathContext.DECIMAL128),
					MathContext.DECIMAL128));

			if (poisson.compareTo(BigDecimal.ZERO) == 0)
				return BigDecimal.ZERO;

			return getTotal(histogram).divide(poisson, MathContext.DECIMAL128);
		}
	}

}
