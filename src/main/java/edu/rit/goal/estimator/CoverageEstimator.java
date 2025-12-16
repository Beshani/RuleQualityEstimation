package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// https://www.jstor.org/stable/2290733
public class CoverageEstimator extends FrequencyEstimator {

	public CoverageEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public BigDecimal getEstimation() {
		if (this.successes.intValue() == 0)
			return BigDecimal.ZERO;
		else {
			Map<Integer, Long> histogram = getHistogram();

			BigDecimal f1 = getF1(histogram);

			BigDecimal uGood = BigDecimal.ONE.subtract(f1.divide(new BigDecimal(n), MathContext.DECIMAL128));

			if (uGood.compareTo(BigDecimal.ZERO) == 0)
				uGood = BigDecimal.ONE;

			return getTotal(histogram).divide(uGood, MathContext.DECIMAL128);
		}
	}

}
