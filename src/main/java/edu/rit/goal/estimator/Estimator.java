package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Estimator {
	public BigInteger n, total, successes, failures;
	EstimatorMemento memento;
	
	public Estimator(EstimatorMemento memento) {
		super();
		
		this.memento = memento;
	}
	
	public Map<Entry<Integer, Integer>, AtomicInteger> getPairs() {
		return memento.getPairs(this.successes);
	}
	
	// We found a solution!
	public void newSuccess() {
		successes = successes.add(BigInteger.ONE);
		n = n.add(BigInteger.ONE);
	}
	
	// Pair not found.
	public void newFailure() {
		failures = failures.add(BigInteger.ONE);
		n = n.add(BigInteger.ONE);
	}

	public abstract boolean withinLimit();

	public abstract BigDecimal getEstimation();
	
	// Indicates whether it is with (true) or without (false) replacement.
	public abstract boolean withReplacement();
	
	// Reset the estimator!
	public void reset(BigInteger total) {
		n = BigInteger.ZERO;
		this.total = total;
		
		successes = BigInteger.ZERO;
		failures = BigInteger.ZERO;
	}
	
	public Map<String, Object> getExtraInfo() {
		Map<String, Object> extra = new HashMap<>();
		
		extra.put("estimation", getEstimation());
		extra.put("n", n);
		extra.put("successes", successes);
		extra.put("failures", failures);
		extra.put("total", total);
		
		return extra;
	}
	
	// We need to know whether to compute probability or not. It is expensive!
	public abstract boolean requiresProbability();
}
