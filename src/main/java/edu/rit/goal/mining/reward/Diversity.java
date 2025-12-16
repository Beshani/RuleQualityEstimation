package edu.rit.goal.mining.reward;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import ch.obermuhlner.math.big.BigDecimalMath;

public class Diversity {
	private static final int FAILURE_RULEID = -1;

	// Keep a frequency count.
	Map<Integer, AtomicInteger> frequencies = new ConcurrentHashMap<>();

	public synchronized void reportFailure() {
		initialize(FAILURE_RULEID);
		frequencies.get(FAILURE_RULEID).incrementAndGet();
	}

	public synchronized void reportSuccess(int ruleId) {
		initialize(ruleId);
		frequencies.get(ruleId).incrementAndGet();
	}

	public synchronized void reportRepeated(int ruleId) {
		// Two options: the rule is there, then this was a success.
		if (frequencies.containsKey(ruleId))
			reportSuccess(ruleId);
		// If not there, the rule was pruned and we use it as a failure.
		else
			reportFailure();
	}

	private synchronized void initialize(int ruleId) {
		frequencies.putIfAbsent(ruleId, new AtomicInteger());
	}

	// This combines two diversity objects. Useful when computing the pooled sample.
	public void combine(Diversity other) {
		for (Entry<Integer, AtomicInteger> otherEntry : other.frequencies.entrySet()) {
			frequencies.putIfAbsent(otherEntry.getKey(), new AtomicInteger());
			frequencies.get(otherEntry.getKey()).addAndGet(otherEntry.getValue().intValue());
		}
	}

	private BigDecimal getNumberOfSpecies() {
		// Count the number of unique species found.
		return new BigDecimal(frequencies.size());
	}

	private Map<Integer, AtomicInteger> getAbundanceFrequencyCount() {
		// The number of species that appear exactly k times (key).
		Map<Integer, AtomicInteger> abundanceFreqCount = new HashMap<>();

		// Determine the unique frequencies.
		for (Entry<Integer, AtomicInteger> entry : frequencies.entrySet()) {
			AtomicInteger f = entry.getValue();
			
			abundanceFreqCount.putIfAbsent(f.intValue(), new AtomicInteger());
			abundanceFreqCount.get(f.intValue()).incrementAndGet();
		}

		return abundanceFreqCount;
	}

	// All these from here:
	// https://www.sciencedirect.com/science/article/pii/S0306437924001704
	public BigDecimal getDiversity() {
		BigDecimal estimation = null;

		BigDecimal s = getNumberOfSpecies();
		Map<Integer, AtomicInteger> abundanceFreq = getAbundanceFrequencyCount();

		BigDecimal f1 = new BigDecimal(abundanceFreq.getOrDefault(1, new AtomicInteger()).intValue()),
				f2 = new BigDecimal(abundanceFreq.getOrDefault(2, new AtomicInteger()).intValue());

		if (f2.compareTo(BigDecimal.ZERO) == 0)
			estimation = s
					.add(f1.multiply(f1.subtract(BigDecimal.ONE)).divide(new BigDecimal(2), MathContext.DECIMAL128));
		else
			estimation = s.add(f1.pow(2).divide(new BigDecimal(2).multiply(f2), MathContext.DECIMAL128));

		return estimation;
	}

	public BigDecimal getN() {
		BigDecimal n = BigDecimal.ZERO;

		for (AtomicInteger count : frequencies.values()) {
			BigDecimal cnt = new BigDecimal(count.get());

			n = n.add(cnt);
		}

		return n;
	}

	public BigDecimal getDominance() {
		BigDecimal n = BigDecimal.ZERO, denominator = BigDecimal.ZERO;

		for (AtomicInteger count : frequencies.values()) {
			BigDecimal cnt = new BigDecimal(count.get());

			n = n.add(cnt);

			if (count.get() > 1)
				denominator = denominator.add(cnt.multiply(cnt.subtract(BigDecimal.ONE)));
		}

		if (denominator.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;

		return n.multiply(n.subtract(BigDecimal.ONE)).divide(denominator, MathContext.DECIMAL128);
	}

	// Let's say completeness is 80%. We've likely captured 80% of all species. This
	// gives a measure of how "complete" the species list is.
	public BigDecimal getCompleteness() {
		BigDecimal s = getNumberOfSpecies(), diversity = getDiversity();

		if (diversity.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;

		return s.divide(diversity, MathContext.DECIMAL128);
	}

	// Let's say coverage is 90%. This means that 90% of the total incidence
	// probability (likelihood of observing a species) is already represented in
	// your data. 10% is thus missing.
	public BigDecimal getCoverage() {
		Map<Integer, AtomicInteger> abundanceFreq = getAbundanceFrequencyCount();

		BigDecimal f1 = new BigDecimal(abundanceFreq.getOrDefault(1, new AtomicInteger()).intValue()),
				f2 = new BigDecimal(abundanceFreq.getOrDefault(2, new AtomicInteger()).intValue()), n = getN();

		BigDecimal denominator = n.subtract(BigDecimal.ONE).multiply(f1).add(new BigDecimal(2).multiply(f2));

		if (denominator.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;

		BigDecimal bigParenthesis = n.subtract(BigDecimal.ONE).multiply(f1).divide(denominator, MathContext.DECIMAL128),
				bigTerm = f1.divide(n, MathContext.DECIMAL128).multiply(bigParenthesis);

		return BigDecimal.ONE.subtract(bigTerm);
	}

	// https://esajournals.onlinelibrary.wiley.com/doi/10.1890/07-2147.1
	// g is the fraction of the diversity estimation that is desired (1.0 is 100%).
	// In the experiment section of the paper, g=.90 or g=.95 is recommended (for
	// g=1.0, the effort estimates "vary considerably").
	public BigDecimal getSamplingEffort(double g) {
		// Eq. 12 uses f0, which is diversity estimation minus number of species.
		BigDecimal s = getNumberOfSpecies(), diversity = getDiversity(), n = getN(), f0Hat = diversity.subtract(s);
		Map<Integer, AtomicInteger> abundanceFreq = getAbundanceFrequencyCount();

		BigDecimal f1 = new BigDecimal(abundanceFreq.getOrDefault(1, new AtomicInteger()).intValue()),
				f2 = new BigDecimal(abundanceFreq.getOrDefault(2, new AtomicInteger()).intValue());

		if (n.compareTo(BigDecimal.ZERO) == 0 || f2.compareTo(BigDecimal.ZERO) == 0
				|| s.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;

		BigDecimal toLog = f0Hat.divide(BigDecimal.ONE.subtract(new BigDecimal(g)).multiply(diversity),
				MathContext.DECIMAL128);

		if (toLog.compareTo(BigDecimal.ONE) < 0)
			return BigDecimal.ZERO;

		return n.multiply(f1).divide(new BigDecimal(2).multiply(f2), MathContext.DECIMAL128)
				.multiply(BigDecimalMath.log(toLog, MathContext.DECIMAL128));
	}

	public DiversityReport getReport(double g) {
		DiversityReport ret = new DiversityReport();

		ret.g = g;

		synchronized (this) {
			ret.completeness = getCompleteness();
			ret.coverage = getCoverage();
			ret.diversity = getDiversity();
			ret.dominance = getDominance();
			ret.effort = getSamplingEffort(g);
			ret.n = getN();
			ret.s = getNumberOfSpecies();	
		}

		return ret;
	}

	public class DiversityReport {
		public BigDecimal diversity, n, s;

		public BigDecimal dominance, completeness, coverage, effort;

		public double g;

		@Override
		public String toString() {
			return "diversity=" + diversity + ", n=" + n + ", s=" + s + ", dominance=" + dominance + ", completeness="
					+ completeness + ", coverage=" + coverage + ", effort (" + g + ")=" + effort;
		}

	}

	// TODO 1D (similarity of species in terms of probability) is complicated!
}
