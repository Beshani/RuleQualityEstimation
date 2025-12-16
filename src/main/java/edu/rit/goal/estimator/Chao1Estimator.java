package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// https://www.jstor.org/stable/2290733
public class Chao1Estimator extends FrequencyEstimator {

	public Chao1Estimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
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

			return getTotal(histogram).add(
					f1.pow(2).divide(f2.add(BigDecimal.ONE).multiply(BigDecimal.valueOf(2)), MathContext.DECIMAL128));
		}
	}

}
