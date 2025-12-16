# RuleQualityEstimation
Source code of the paper: Statistics-based Quality Measurements for Horn Rules over Knowledge Graphs

# Overview

The code implements different estimators for approximating support and contradiction of Horn rules in knowledge graphs.
It includes implementations of:

  - Frequency-based estimators (Chao2, Poisson)
  - Distribution-based estimators (Binomial, Hypergeometric)
  - Probability-based estimators (Hansen–Hurwitz, Horvitz–Thompson)
  - Sampling designs with Chernoff-bound and confidence-interval stopping rules

# How to run

Use KGToNeo4j.java to load a dataset into Neo4j. You can find the datasets here: https://github.com/nari97/AugmentedKGE/tree/master/Datasets

Use RuleMiningEstimates.java to run the estimates and compute the ground truth (you can test the estimates using TestEstimates.java).

Use ExtractRMEstimatesResults.java to extract the results from the logs. Our results can be accessed here: https://drive.google.com/file/d/1df1Rv0Wg9wdrJk-fiptVR7z0kaRTUgCr/view?usp=sharing

Use PlotComparisonStats.py to process those results and plot hyperparemeter selection. Use PlotSupportPCAStats.py to process those results and plot charts for a given combination of hyperparameter valus for all datasets.









