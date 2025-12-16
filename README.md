# RuleQualityEstimation
Source code of the VLDB journal paper: Statistics-based Quality Measurements for Horn Rules over Knowledge Graphs

# Overview

The code implements different estimators for approximating support and contradiction of Horn rules in knowledge graphs.
It includes implementations of:

  - Frequency-based estimators (Chao2, Poisson)
  - Distribution-based estimators (Binomial, Hypergeometric)
  - Probability-based estimators (Hansen–Hurwitz, Horvitz–Thompson)
  - Sampling designs with Chernoff-bound and confidence-interval stopping rules

# Data

The datasets used in our experiments are publicly available benchmark knowledge graphs commonly used for evaluating rule mining and link prediction methods provided by OpenKE: https://github.com/thunlp/OpenKE/tree/OpenKE-PyTorch/benchmarks







