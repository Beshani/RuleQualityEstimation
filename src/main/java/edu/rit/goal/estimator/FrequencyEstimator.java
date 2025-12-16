package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

public abstract class FrequencyEstimator extends StatisticalEstimator {
	public FrequencyEstimator(EstimatorMemento memento,
			double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public void newSuccess() {
		super.newSuccess();
	}

	Map<Integer, Long> getHistogram() {
		return getHistogram(memento.getPairs(this.successes));
	}
	
	public static Map<Integer, Long> getHistogram(Map<Entry<Integer, Integer>, AtomicInteger> pairs) {
		return pairs.values().stream().map(i -> i.get())
				.collect(Collectors.groupingBy(Function.identity(), Collectors.summingLong(v -> v)));
	}

	public static BigDecimal getTotal(Map<Integer, Long> histogram) {
		return new BigDecimal(histogram.values().stream().collect(Collectors.summingLong(v -> v)));
	}

	public static BigDecimal getF1(Map<Integer, Long> histogram) {
		return new BigDecimal(histogram.getOrDefault(1, 0l));
	}

	public static BigDecimal getF2(Map<Integer, Long> histogram) {
		return new BigDecimal(histogram.getOrDefault(2, 0l));
	}

	BigDecimal getFi(Map<Integer, Long> histogram, int i) {
		return new BigDecimal(histogram.getOrDefault(i, 0l));
	}

	@Override
	public Map<String, Object> getExtraInfo() {
		Map<String, Object> extra = super.getExtraInfo();

		extra.put("histogram", String.join("--", getHistogram().entrySet().stream()
				.map(entry -> entry.getKey() + "->" + entry.getValue()).collect(Collectors.toList())));

		return extra;
	}

	@Override
	public boolean requiresProbability() {
		return false;
	}

	@Override
	public boolean withReplacement() {
		return true;
	}

}
