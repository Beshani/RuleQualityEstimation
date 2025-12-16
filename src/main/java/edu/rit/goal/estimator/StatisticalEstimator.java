package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.TDistribution;

import ch.obermuhlner.math.big.BigDecimalMath;
import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

public abstract class StatisticalEstimator extends Estimator {
	EstimatorLimit limit;
	BigDecimal accuracy, confidence;
	BigInteger minSamples;

	public StatisticalEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento);
		
		this.accuracy = new BigDecimal("" + accuracy);
		this.confidence = new BigDecimal("" + confidence);
		this.limit = limit;
		this.minSamples = BigInteger.valueOf(minSamples);
	}

	@Override
	public boolean withinLimit() {
		return switch (limit) {
		case Chernoff -> withinChernoffBound();
		case ConfidenceInterval -> withinConfidenceInterval();
		case CentralLimitTheorem -> withinCLT();
		default -> throw new IllegalArgumentException("Unexpected value: " + limit);
		};
	}

	BigInteger cltBound = null;

	public BigInteger getCLTBound() {
		if (cltBound == null)
			// Assuming k/n = 0.25 (worst case for Bernoulli trials).
			cltBound = new BigDecimal(""
					+ Math.abs(new NormalDistribution(0, 1).inverseCumulativeProbability(confidence.doubleValue() / 2)))
					.pow(2, MathContext.DECIMAL128).multiply(new BigDecimal("0.25"))
					.divide(accuracy.pow(2, MathContext.DECIMAL128), MathContext.DECIMAL128)
					.setScale(0, RoundingMode.UP).toBigInteger();
		return cltBound;
	}

	public final boolean withinCLT() {
		if (n.compareTo(minSamples.min(total)) < 0)
			return false;
		else
			return n.compareTo(total) == 0 || n.compareTo(getCLTBound()) >= 0;
	}

	BigDecimal chernoffBound = null;

	public BigDecimal getChernoffBound() {
		if (chernoffBound == null)
			// https://www.cs.princeton.edu/courses/archive/fall09/cos521/Handouts/probabilityandcomputing.pdf
			chernoffBound = accuracy.add(BigDecimal.valueOf(2))
					.multiply(BigDecimalMath.log(new BigDecimal(2).divide(confidence, MathContext.DECIMAL128),
							MathContext.DECIMAL128))
					.divide(accuracy.pow(2, MathContext.DECIMAL128), MathContext.DECIMAL128);
		return chernoffBound;
	}

	public final boolean withinChernoffBound() {
		if (n.compareTo(minSamples.min(total)) < 0)
			return false;
		else
			return n.compareTo(total) == 0 || new BigDecimal(n).compareTo(getChernoffBound()) >= 0;
	}

	public final ConfidenceIntervalBigDecimal getConfidenceInterval() {
		BigDecimal estimate = getMean(), marginOfError = getMarginOfError();

		return new ConfidenceIntervalBigDecimal(estimate.subtract(marginOfError), estimate.add(marginOfError),
				confidence);
	}

	// We assume all our sampling consists of n Bernoulli trials (yes/no):
	// https://online.stat.psu.edu/stat506/Lesson02
	final BigDecimal getMean() {
		return new BigDecimal(successes).divide(new BigDecimal(n), MathContext.DECIMAL128);
	}

	final BigDecimal getVariance() {
		BigDecimal p = getMean(), N = new BigDecimal(total), NMinusOne = N.subtract(BigDecimal.ONE);
		BigDecimal nMinusOne = new BigDecimal(n.subtract(BigInteger.ONE));

		if (nMinusOne.compareTo(BigDecimal.ZERO) == 0 || NMinusOne.compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;
		else
			return p.multiply(BigDecimal.ONE.subtract(p)).divide(nMinusOne, MathContext.DECIMAL128)
					.multiply(N.subtract(new BigDecimal(n)).divide(NMinusOne, MathContext.DECIMAL128));
	}

	final BigDecimal getMarginOfError() {
		BigDecimal margin = BigDecimal.ZERO;

		if (n.compareTo(BigInteger.ONE) > 0) {
			BigDecimal t = new BigDecimal(Math.abs(
					new TDistribution(n.doubleValue() - 1).inverseCumulativeProbability(confidence.doubleValue() / 2)));
			margin = t.multiply(getVariance().sqrt(MathContext.DECIMAL128))
					.divide(new BigDecimal(n).sqrt(MathContext.DECIMAL128), MathContext.DECIMAL128);
		}

		return margin;
	}

	public boolean withinConfidenceInterval() {
		if (n.compareTo(minSamples.min(total)) < 0)
			return false;
		else
			return n.compareTo(total) == 0 || getMarginOfError().compareTo(getMean().multiply(accuracy)) <= 0;
	}

	public Map<String, Object> getExtraInfo() {
		Map<String, Object> extra = super.getExtraInfo();

		extra.put("accuracy", accuracy);
		extra.put("confidence", confidence);
		extra.put("mean", getMean());
		extra.put("variance", getVariance());
		extra.put("marginOfError", getMarginOfError());

		ConfidenceIntervalBigDecimal ci = getConfidenceInterval();

		extra.put("ciLower", ci.getLowerBound());
		extra.put("ciUpper", ci.getUpperBound());

		return extra;
	}
}
