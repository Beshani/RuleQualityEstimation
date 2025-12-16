package edu.rit.goal.visitor;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;

import edu.rit.goal.metric.RuleMetric;
import edu.rit.goal.metric.RulePCAConfidence;
import edu.rit.goal.metric.RuleSupport;

public abstract class RuleMetricListener {
	// Total time elapsed.
	private BigInteger timeElapsed = BigInteger.ZERO;
	
	// This indicates whether the collection should stop.
	protected boolean stopCollection;

	protected RuleMetric metric;

	public RuleMetricListener(RuleMetric metric) {
		super();
		this.metric = metric;
	}
	
	public RuleMetric getMetric() {
		return metric;
	}

	public void reset() {
		timeElapsed = BigInteger.ZERO;
		stopCollection = false;
		metric.reset();
	}
	
	public void addToTimeElapsed(AtomicLong starting, AtomicLong ending) {
		if (!stopCollection)
			timeElapsed = timeElapsed.add(BigInteger.valueOf(ending.get() - starting.get()));
	}

	public BigInteger getTimeElapsed() {
		return timeElapsed;
	}
	
	public void incrementMetric(BigDecimal increment) {
		if (stopCollection)
			return;

		if (metric instanceof RuleSupport) {
			RuleSupport s = ((RuleSupport) metric);
			s.support = s.support.add(increment);
		}

		if (metric instanceof RulePCAConfidence) {
			RulePCAConfidence p = ((RulePCAConfidence) metric);
			p.pcaPrime = p.pcaPrime.add(increment);
		}
	}

	public void updateCalls(int calls) {
		if (stopCollection)
			return;

		if (metric instanceof RuleSupport) {
			RuleSupport s = ((RuleSupport) metric);
			s.supportMatchingCalls = s.supportMatchingCalls.add(BigInteger.valueOf(calls));
		}

		if (metric instanceof RulePCAConfidence) {
			RulePCAConfidence p = ((RulePCAConfidence) metric);
			p.pcaPrimeMatchingCalls = p.pcaPrimeMatchingCalls.add(BigInteger.valueOf(calls));
		}
	}

}
