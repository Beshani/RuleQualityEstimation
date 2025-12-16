package edu.rit.goal;

import java.io.File;
import java.util.List;
import java.util.Set;

public class Experiments {
	public enum DatasetFormat {
		OPENKE, WIKI
	}

	public enum Dataset {
		BioKG, Codex_L, FB13, FB15K, FB15K237, Hetionet, NELL_995, SNOMED, Wikidata5M, WN18, WN18RR, YAGO3_10, WN11
	}

	public enum Algorithm {
		AnyBURL/* , ERM */, AMIE
	}

	public enum Policy {
		Maximum, Probability, StaticProbability
	}

	public enum MetricComputation {
		AnyBURL, HyperPlusHH
	}

	public enum Reward {
		Support, SupportConf, SupportConfLength, ConfLengthPredSize, F1, F1Length, Effort
	}

	public enum Profile {
		Length, Head, LengthHead
	}

	private static final Set<Dataset> wikiDatasets = Set.of(Dataset.Codex_L, Dataset.Wikidata5M);
	private static final Set<Dataset> openKEDatasets = Set.of(Dataset.BioKG, Dataset.FB13, Dataset.FB15K,
			Dataset.FB15K237, Dataset.Hetionet, Dataset.NELL_995, Dataset.SNOMED, Dataset.WN18, Dataset.WN18RR,
			Dataset.YAGO3_10, Dataset.WN11);

	private static final Set<Dataset> tabDatasets = Set.of(Dataset.BioKG, Dataset.Codex_L, Dataset.FB13,
			Dataset.Hetionet, Dataset.SNOMED, Dataset.Wikidata5M, Dataset.WN11);

	public static Dataset resolveDataset(int i) {
		return Dataset.values()[i];
	}

	public static DatasetFormat resolveFormat(Dataset dataset) {
		DatasetFormat format = null;

		if (wikiDatasets.contains(dataset))
			format = DatasetFormat.WIKI;
		else if (openKEDatasets.contains(dataset))
			format = DatasetFormat.OPENKE;

		return format;
	}

	public static String resolveSeparator(Dataset dataset) {
		String sep = " ";

		if (tabDatasets.contains(dataset))
			sep = "\t";

		return sep;
	}

	private static final int[] singleRunBudgets = new int[] { 1000 };

	public static Experiment resolveSingleRunExperiment(int i) {
		int current = 0;
		for (Algorithm algo : List.of(Algorithm.AnyBURL))
			for (Dataset dataset : Dataset.values())
				for (int budget : singleRunBudgets)
					if (current == i) {
						Experiment exp = new Experiments().new Experiment();

						exp.algo = algo;
						exp.budget = budget;
						exp.dataset = dataset;

						return exp;
					} else
						current++;
		return null;
	}

	private static final double[] epsilonValues = new double[] { .05, .1, .25, .50, 1.0 };

	private static final int[] windowSizes = new int[] { 5, 10, 15 };

	public static void configureReward(Experiment e) {
		switch (e.reward) {
		case Effort -> {
			e.useRwEffort = true;
		}
		case F1 -> {
			e.useRwF1 = true;
		}
		case F1Length -> {
			e.useRwF1 = true;
			e.useRwLength = true;
		}
		case Support -> {
			e.useRwSupport = true;
		}
		case SupportConf -> {
			e.useRwSupport = true;
			e.useRwConf = true;
		}
		case SupportConfLength -> {
			e.useRwSupport = true;
			e.useRwConf = true;
			e.useRwLength = true;
		}
		case ConfLengthPredSize -> {
			e.useRwConf = true;
			e.useRwLength = true;
			e.useRwPredSize = true;
		}
		default -> throw new IllegalArgumentException("Unexpected value: " + e);
		}
	}

	public static void configureProfile(Experiment e) {
		switch (e.profile) {
		case Head -> {
			e.usePrfHead = true;
		}
		case Length -> {
			e.usePrfLength = true;
		}
		case LengthHead -> {
			e.usePrfLength = true;
			e.usePrfHead = true;
		}
		default -> throw new IllegalArgumentException("Unexpected value: " + e);
		}
	}

	public static Experiment getEmptyExperiment(Dataset dataset, int budget) {
		Experiment exp = new Experiments().new Experiment();

		exp.algo = Algorithm.AnyBURL;
		exp.dataset = dataset;

		exp.budget = budget;

		return exp;
	}

	// This is the experiment; we only focus on those we have the ground truth.
	public static Experiment resolveRuleMiningGTExperiment(int i, Dataset dataset, int budget) {
		int current = 0;

		Experiment exp = getEmptyExperiment(dataset, budget);

		for (boolean closeWalk : List.of(false, true))
			for (double epsilon : epsilonValues) {
				Policy[] policyValues = Policy.values();

				// Only max when epsilon=1.0
				if (epsilon == 1.0)
					policyValues = new Policy[] { Policy.Maximum };

				for (Policy pcy : policyValues)
					for (MetricComputation m : MetricComputation.values())
						for (Reward r : Reward.values()) {
							int[] sizes = windowSizes;

							// No window size for this one.
							if (r.equals(Reward.Effort))
								sizes = new int[] { 0 };

							for (int ws : sizes) {
								// We will not try basic!
								for (Profile prf : List.of(Profile.Length, Profile.Head, Profile.LengthHead))
									if (current == i) {
										exp.epsilon = epsilon;

										exp.forceClosingRW = closeWalk;

										exp.metricComputation = m;

										exp.policy = pcy;

										exp.run = 0;

										exp.reward = r;
										configureReward(exp);

										exp.windowSize = ws;

										exp.profile = prf;
										configureProfile(exp);

										return exp;
									} else
										current++;
							}
						}
			}

		return null;
	}

	public static String getLatticeNumberFolder(Experiment e, int i, String latticeFolder) {
		return latticeFolder + File.separator + e.dataset + "_RW_" + i + "_Lattice/";
	}

	public static String getResultsNumberFile(Experiment e, int i, String resultsFolder) {
		return resultsFolder + File.separator + e.dataset + "_RW_" + i + "_Results.txt";
	}

	public static String getResultsNumberFile(Experiment e, int i, int budget, int run, String resultsFolder) {
		return resultsFolder + File.separator + e.dataset + "_RW_" + i + "_" + budget + "_" + run + "_Results.txt";
	}

	public static String getDBCopyNumberFolder(Experiment e, int i, String dbFolder) {
		return dbFolder + File.separator + e.dataset + "_RW_" + i + "_Copy/";
	}

	public static String getLatticeFolder(Experiment e, String latticeFolder) {
		return latticeFolder + File.separator + e.dataset + "_" + e.algo + "_" + e.budget + "_" + e.run + "_Lattice/";
	}

	public static String getResultsFile(Experiment e, String resultsFolder, String separator) {
		return resultsFolder + separator + e.dataset + "_" + e.algo + "_" + e.budget + "_" + e.run + "_Results.txt";
	}

	class Experiment {
		Dataset dataset;
		Algorithm algo;
		int budget, run;

		// Policy
		boolean forceClosingRW;

		Policy policy; // max or probability
		double epsilon; // .05, .1, .25, .50; 1.0 is for pure random.

		// Metric
		MetricComputation metricComputation; // AnyBURL or Hyper+HT

		// Reward
		Reward reward;
		boolean useRwSupport, useRwConf, useRwLength, useRwPredSize, useRwF1, useRwEffort;
		int windowSize;

		// Profile
		Profile profile;
		boolean usePrfLength, usePrfHead, usePrfNumberOfVars, usePrfNumberOfPreds;

		// Others
		boolean onlyHead;

		@Override
		public String toString() {
			return "Experiment [dataset=" + dataset + ", algo=" + algo + ", budget=" + budget + ", run=" + run
					+ ", forceClosingRW=" + forceClosingRW + ", policy=" + policy + ", epsilon=" + epsilon
					+ ", metricComputation=" + metricComputation + ", useRwSupport=" + useRwSupport + ", useRwConf="
					+ useRwConf + ", useRwLength=" + useRwLength + ", useRwPredSize=" + useRwPredSize + ", useRwF1="
					+ useRwF1 + ", useRwEffort=" + useRwEffort + ", windowSize=" + windowSize + ", usePrfLength="
					+ usePrfLength + ", usePrfHead=" + usePrfHead + ", usePrfNumberOfVars=" + usePrfNumberOfVars
					+ ", usePrfNumberOfPreds=" + usePrfNumberOfPreds + ", onlyHead=" + onlyHead + "]";
		}

	}
}
