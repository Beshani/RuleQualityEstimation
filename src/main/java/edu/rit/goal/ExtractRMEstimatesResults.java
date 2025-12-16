package edu.rit.goal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import edu.rit.goal.Experiments.Dataset;
import edu.rit.goal.Experiments.Experiment;

public class ExtractRMEstimatesResults {
	enum Selected {
		Value, Calls, CPU, PCAError
	};

	class Measurements {
		BigDecimal value, calls, cpu, pcaError;

		BigDecimal getSelected(Selected s) {
			return switch (s) {
			case Value -> value;
			case Calls -> calls;
			case CPU -> cpu.divide(new BigDecimal("1e9", MathContext.DECIMAL128));
			case PCAError -> pcaError;
			default -> throw new IllegalArgumentException("Unexpected value: " + s);
			};
		}
	}

	static List<Measurements> parse(Map<String, String> map) {
		List<Measurements> ret = new ArrayList<>();

		if (map.containsKey("Value")) {
			Measurements m = new ExtractRMEstimatesResults().new Measurements();

			m.value = new BigDecimal(map.get("Value"));
			m.calls = new BigDecimal(map.get("Calls"));
			m.cpu = new BigDecimal(map.get("CPU"));

			ret.add(m);
		}

		if (map.containsKey("Valuex")) {
			Measurements m = new ExtractRMEstimatesResults().new Measurements();

			m.value = new BigDecimal(map.get("Valuex"));
			m.calls = new BigDecimal(map.get("Callsx"));
			m.cpu = new BigDecimal(map.get("CPU"));
			m.pcaError = new BigDecimal(map.get("AbsoluteError"));

			ret.add(m);
		}

		if (map.containsKey("Valuey")) {
			Measurements m = new ExtractRMEstimatesResults().new Measurements();

			m.value = new BigDecimal(map.get("Valuey"));
			m.calls = new BigDecimal(map.get("Callsy"));
			m.cpu = new BigDecimal(map.get("CPU"));
			m.pcaError = new BigDecimal(map.get("AbsoluteError"));

			ret.add(m);
		}

		return ret;
	}

	private static String getFilename(String folder, String dataset, String agg, String suffix) {
		return folder + dataset + "_" + agg + "_" + suffix + ".txt";
	}

	private static void printResultToFile(String folder, String dataset, String agg, String suffix, String metric,
			String type, String technique, double[] values) throws Exception {

		// "Metric: {Support_Value, Support_CPU, PCA_Value, PCA_CPU} \n"
		// "Technique: Chao1: qe_1, qe_2, qe_3, ... \n"

		PrintWriter writer = new PrintWriter(
				new FileOutputStream(new File(getFilename(folder, dataset, agg, suffix)), true));

		writer.println("Metric: " + metric + "_" + type);
		writer.println("Technique: " + technique + ":"
				+ String.join(",", Arrays.stream(values).boxed().map(v -> Double.toString(v)).toList()));

		writer.close();
	}

	public static void main(String[] args) throws Exception {
		String zipResults = "ZIP_FILE_WITH_RESULT",
				resultsFolder = "FOLDER_TO_WRITE_RESULTS";

		// This indicates how many runs per rule. Because we resume, there can be more
		// than x runs for the same rule. We make sure only the last x are considered.
//		int runs = 5;

		// We need this to present AnyBURL's results.
		Set<String> allPCAHyperparemeterConfigurations = new HashSet<>();

		BiFunction<String, Integer, String> getHyperparemeterConfig = (t, type) -> {
			String[] techSplit = t.split("\\_");

			if (techSplit.length < 4)
				return "";

			String ret = techSplit[techSplit.length - 3] + "_" + techSplit[techSplit.length - 2] + "_"
					+ techSplit[techSplit.length - 1];

			// PCA!
			if (type == 0)
				ret = techSplit[techSplit.length - 4] + "_" + ret;

			return ret;
		};

		for (int number = 0; number < 13; number++) {
			Map<String, Map<String, List<Measurements>>> supportResults = new HashMap<>(), pcaResults = new HashMap<>();

			Experiment exp = Experiments.resolveSingleRunExperiment(number);
			exp.run = 0;

			System.out.println(new Date() + " -- Dataset: " + exp.dataset.toString());

			ZipFile zip = new ZipFile(zipResults);
			ZipEntry entry = zip.getEntry(Experiments.getResultsFile(exp, "results", "/"));

			if (entry == null)
				continue;

			// We'll have two different results: support and confidence.
			Scanner sc = new Scanner(zip.getInputStream(entry));

			while (sc.hasNextLine()) {
				String line = sc.nextLine();

				// Skip, this is a rule.
				if (!line.startsWith("{"))
					continue;

				Map<String, String> lineAsMap = Arrays.stream(line.substring(1, line.length() - 1).split(", "))
						.map(s -> {
							String[] ret = s.split("=");
							if (ret.length == 1)
								ret = new String[] { ret[0], "" };
							return ret;
						}).collect(Collectors.toMap(x -> x[0], x -> x[1]));

				String technique = lineAsMap.get("Technique"), ruleId = lineAsMap.get("RuleId");

				Map<String, Map<String, List<Measurements>>> selectedResults = null;

				if (technique.startsWith("Support"))
					selectedResults = supportResults;
				else {
					selectedResults = pcaResults;
					allPCAHyperparemeterConfigurations.add(getHyperparemeterConfig.apply(technique, 0));
				}

				// Technique first.
				selectedResults.putIfAbsent(technique, new HashMap<>());

				// Rule id second.
				selectedResults.get(technique).putIfAbsent(ruleId, new ArrayList<>());

				selectedResults.get(technique).get(ruleId).addAll(parse(lineAsMap));
			}

			sc.close();

			zip.close();

			// Adjusted q-error.
			BiFunction<BigDecimal, BigDecimal, Double> qerror = (gt, est) -> {
				Double ret = null;

				if (gt != null && est != null) {
					BigDecimal sign = BigDecimal.ONE;

					if (est.compareTo(gt) < 0)
						sign = BigDecimal.valueOf(-1);

					double value = BigDecimal.ONE.max(gt).divide(BigDecimal.ONE.max(est), MathContext.DECIMAL128)
							.max(BigDecimal.ONE.max(est).divide(BigDecimal.ONE.max(gt), MathContext.DECIMAL128))
							// Q-error can never be zero. In the best case, it is equal to one. We force it
							// to be zero.
							.subtract(BigDecimal.ONE).multiply(sign).doubleValue();

					ret = value;
				}

				return ret;
			};

			AtomicInteger totalRules = new AtomicInteger();

			// For each dataset, summary statistics for each technique of the q-errors.
			for (Selected s : List.of(Selected.Value, Selected.CPU, Selected.PCAError)) {
				System.out.println("Measurement: " + s);

				Dataset dataset = exp.dataset;
				System.out.println("Dataset: " + dataset);
				
				// Total: PCAError is only valid for PCA.
				int totalOptions = 2;
				if (s.equals(Selected.PCAError))
					totalOptions = 1;

				for (int i = 0; i < totalOptions; i++) {
					Map<String, Map<String, List<Measurements>>> selected = null;
					String gtTechnique = null;

					if (i == 1) {
						selected = supportResults;
						gtTechnique = "SupportExact";
						System.out.println("Support results");
					}

					if (i == 0) {
						selected = pcaResults;
						gtTechnique = "PCAExact";
						System.out.println("PCA results");
					}

					Map<String, DescriptiveStatistics> techStats = new HashMap<>();

					for (String technique : selected.keySet()) {
						if (technique.equals(gtTechnique))
							continue;

						DescriptiveStatistics ss = new DescriptiveStatistics();

						techStats.put(technique, ss);

						if (!selected.containsKey(technique))
							continue;

						int tr = selected.get(technique).size();

						totalRules.set(Integer.max(totalRules.get(), tr));

						for (String r : selected.get(technique).keySet()) {
							// It should have only one measurement!
							BigDecimal gt = selected.get(gtTechnique).get(r).get(0).getSelected(s);

							// Use runs to get only the last computed.
							List<Measurements> listOfMeasurements = selected.get(technique).get(r);
//							if (listOfMeasurements.size() > runs)
//								listOfMeasurements = listOfMeasurements.subList(listOfMeasurements.size() - runs - 1,
//										listOfMeasurements.size());

							if (s.equals(Selected.CPU))
								// If CPU, we will get the summation of all the attempts.
//								ss.addValue(gt
//									.divide(listOfMeasurements.stream().map(m -> m.getSelected(s))
//											.reduce(BigDecimal.ZERO, BigDecimal::add), MathContext.DECIMAL128)
//									.doubleValue());

								// Using speedup: gt/s.
								for (Measurements m : listOfMeasurements)
									ss.addValue(gt.divide(m.getSelected(s), MathContext.DECIMAL128).doubleValue());
							else if (s.equals(Selected.Value))
								// Include all the q-errors.
								for (Measurements m : listOfMeasurements)
									ss.addValue(qerror.apply(gt, m.getSelected(s)).doubleValue());
							else if (s.equals(Selected.PCAError))
								// These are already w.r.t. the ground truth
								for (Measurements m : listOfMeasurements)
									ss.addValue(m.getSelected(s).doubleValue());
						}

						if (technique.contains("AnyBURL")) {
							// For AnyBURL, we will replicate the results finding the current selection,
							// e.g., PCA_AnyBURL__Minimum.
							String selection = technique.split("\\_")[3];

							for (String current : allPCAHyperparemeterConfigurations)
								// Adding _ to avoid mixing Corrupt and NonCorrupt.
								if (current.contains("_" + selection))
									printResultToFile(resultsFolder, dataset.toString(), "Data", current,
											gtTechnique.replace("Exact", ""), s.name(), technique, ss.getValues());
						} else {
							// For support, get last three. For instance, "_Chernoff_Acc0.15_Conf0.10".
							// For PCA, get last four. For instance, "_Chernoff_Minimum_Acc0.15_Conf0.10".
							String suffix = getHyperparemeterConfig.apply(technique, i);

							printResultToFile(resultsFolder, dataset.toString(), "Data", suffix,
									gtTechnique.replace("Exact", ""), s.name(), technique, ss.getValues());
						}
					}
				}

				System.out.println();

				System.out.println();
				System.out.println();
				System.out.println();

				System.out.println("Total rules: " + totalRules.get());

				System.out.println();
				System.out.println();

				System.out.println("Done!");
			}
		}
	}

}
