package edu.rit.goal.estimator;

import java.util.Collection;
import java.util.List;

public class EstimatorFactory {
	public enum NonstatisticalEstimatorType {
		AnyBURL
	}

	public enum StatisticalEstimatorType {
		Binomial, Chao1, Chao2, Coverage, Hypergeometric, HansenHurwitz, HorvitzThompsonWith, HorvitzThompsonWithout,
		HajekWith, HajekWithout, Jackknife, Poisson
	}

	public enum EstimatorLimit {
		Chernoff, ConfidenceInterval, CentralLimitTheorem
	}

	public static NonStatisticalEstimator getNonstatisticalEstimator(NonstatisticalEstimatorType e,
			EstimatorMemento memento) {
		return switch (e) {
		case AnyBURL -> new AnyBURLEstimator(memento);
		default -> throw new IllegalArgumentException("Unexpected value: " + e);
		};
	}

	public static StatisticalEstimator getStatisticalEstimator(StatisticalEstimatorType e, double accuracy,
			double confidence, EstimatorLimit limit, int minSamples, EstimatorMemento memento) {
		return switch (e) {
		case Binomial -> new BinomialEstimator(memento, accuracy, confidence, limit, minSamples);
		case Chao1 -> new Chao1Estimator(memento, accuracy, confidence, limit, minSamples);
		case Chao2 -> new Chao2Estimator(memento, accuracy, confidence, limit, minSamples);
		case Coverage -> new CoverageEstimator(memento, accuracy, confidence, limit, minSamples);
		case Hypergeometric -> new HypergeometricEstimator(memento, accuracy, confidence, limit, minSamples);
		case HansenHurwitz -> new HansenHurwitzEstimator(memento, accuracy, confidence, limit, minSamples);
		case HorvitzThompsonWith ->
			new HorvitzThompsonEstimator(memento, accuracy, confidence, limit, minSamples, true);
		case HorvitzThompsonWithout ->
			new HorvitzThompsonEstimator(memento, accuracy, confidence, limit, minSamples, false);
		case HajekWith -> new HajekEstimator(memento, accuracy, confidence, limit, minSamples, true);
		case HajekWithout -> new HajekEstimator(memento, accuracy, confidence, limit, minSamples, false);
		case Jackknife -> new JackknifeEstimator(memento, accuracy, confidence, limit, minSamples);
		case Poisson -> new PoissonEstimator(memento, accuracy, confidence, limit, minSamples);
		default -> throw new IllegalArgumentException("Unexpected value: " + e);
		};
	}

	public static Collection<StatisticalEstimatorType> getEstimatorsReplacement(boolean withReplacement) {
		if (withReplacement)
			return List.of(StatisticalEstimatorType.Binomial, // StatisticalEstimatorType.Chao1,
					StatisticalEstimatorType.Chao2, StatisticalEstimatorType.Coverage,
					StatisticalEstimatorType.HansenHurwitz, StatisticalEstimatorType.HorvitzThompsonWith,
					// StatisticalEstimatorType.HajekWith, StatisticalEstimatorType.Jackknife,
					StatisticalEstimatorType.Poisson);
		else
			return List.of(StatisticalEstimatorType.Hypergeometric, StatisticalEstimatorType.HorvitzThompsonWithout
			// , StatisticalEstimatorType.HajekWithout
			);
	}
}
