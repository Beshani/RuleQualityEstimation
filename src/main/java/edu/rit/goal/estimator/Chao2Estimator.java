package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// DOI: 10.1111/2041-210X.13979
public class Chao2Estimator extends FrequencyEstimator {

	public Chao2Estimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public BigDecimal getEstimation() {
		if (this.successes.intValue() == 0)
			return BigDecimal.ZERO;
		else
			return getEstimationHistogram(getHistogram(), n);
	}
	
	public static BigDecimal getEstimationPairs(Map<Entry<Integer, Integer>, AtomicInteger> pairs, BigInteger n) {
		Map<Integer, Long> histogram = getHistogram(pairs);

		return getEstimationHistogram(histogram, n);
	}
	
	public static BigDecimal getEstimationHistogram(Map<Integer, Long> histogram, BigInteger n) {
		BigDecimal f1 = getF1(histogram), f2 = getF2(histogram);

		return getTotal(histogram).add(new BigDecimal(n.subtract(BigInteger.ONE))
				.divide(new BigDecimal(n), MathContext.DECIMAL128).multiply(f1.pow(2)
						.divide(f2.add(BigDecimal.ONE).multiply(BigDecimal.valueOf(2)), MathContext.DECIMAL128)));
	}

}
