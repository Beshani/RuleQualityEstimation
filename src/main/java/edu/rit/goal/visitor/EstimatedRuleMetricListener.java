package edu.rit.goal.visitor;

import java.math.BigInteger;
import java.util.Map;
import java.util.stream.Collectors;

import edu.rit.goal.estimator.Estimator;
import edu.rit.goal.metric.RuleMetric;
import edu.rit.goal.metric.RulePCAConfidence;
import edu.rit.goal.metric.RuleSupport;

public class EstimatedRuleMetricListener extends RuleMetricListener {
	private Estimator estimator;

	public EstimatedRuleMetricListener(RuleMetric metric, Estimator estimator) {
		super(metric);

		this.estimator = estimator;
	}

	public Estimator getEstimator() {
		return estimator;
	}

	public void resetEstimator(BigInteger total) {
		estimator.reset(total);
	}

	public void newSuccess() {
		if (!stopCollection)
			estimator.newSuccess();
	}

	public void newFailure() {
		if (!stopCollection)
			estimator.newFailure();
	}

	public boolean stop() {
		if (!stopCollection)
			stopCollection = estimator.withinLimit();
		return stopCollection;
	}

	public void setMetric(String suffix) {
		if (metric instanceof RuleSupport) {
			RuleSupport s = ((RuleSupport) metric);
			s.support = estimator.getEstimation();
		}

		if (metric instanceof RulePCAConfidence) {
			RulePCAConfidence p = ((RulePCAConfidence) metric);
			p.pcaPrime = estimator.getEstimation();
		}

		if (suffix != null)
			metric.addExtraStuff(estimator.getExtraInfo().entrySet().stream()
					.collect(Collectors.toMap(entry -> entry.getKey() + suffix.toString(), Map.Entry::getValue)));
	}
}
