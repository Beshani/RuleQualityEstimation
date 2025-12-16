package edu.rit.goal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jgrapht.graph.DirectedMultigraph;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;

import edu.rit.goal.estimator.BinomialEstimator;
import edu.rit.goal.estimator.EstimatorFactory;
import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;
import edu.rit.goal.estimator.EstimatorFactory.StatisticalEstimatorType;
import edu.rit.goal.estimator.EstimatorMemento;
import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.graph.GraphDatabase;
import edu.rit.goal.graph.LabeledEdge;
import edu.rit.goal.metric.RulePCAConfidence;
import edu.rit.goal.metric.RuleSupport;
import edu.rit.goal.visitor.EstimatedRuleMetricListener;
import edu.rit.goal.visitor.ExactRuleMetricListener;
import edu.rit.goal.visitor.GraphVisitor;
import edu.rit.goal.visitor.RuleMetricFactory;
import edu.rit.goal.visitor.RuleMetricListener;
import edu.rit.goal.visitor.confidence.EstimatedPCAConfidenceVisitor.SampleSelection;
import edu.rit.goal.visitor.confidence.PCAConfidenceVisitor;
import edu.rit.goal.visitor.support.SupportVisitor;

public class TestEstimates {

	public static void main(String[] args) throws FileNotFoundException {
		String dbFolder = "db/Codex_L/";

		double accuracy = .05, confidence = .01;
		int minSamples = 50, runs = 5;
		Integer split = 1;

		DatabaseManagementService dbService = new DatabaseManagementServiceBuilder(Path.of(dbFolder))
				.setConfig(GraphDatabaseSettings.keep_logical_logs, "false")
				.setConfig(GraphDatabaseSettings.preallocate_logical_logs, false).build();
		registerShutdownHook(dbService);

		GraphDatabase db = new GraphDatabase(dbService.database(GraphDatabaseSettings.DEFAULT_DATABASE_NAME), split);

		DirectedMultigraph<Integer, LabeledEdge> query = new DirectedMultigraph<>(LabeledEdge.class);

		// 33(?a ?b) <= 9(?c ?b) 34(?a ?d) 34(?c ?d)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("33", 0, false));
//		query.addEdge(2, 1, new LabeledEdge("9", 1, false));
//		query.addEdge(0, 3, new LabeledEdge("34", 2, false));
//		query.addEdge(2, 3, new LabeledEdge("34", 3, false));

		// 4(?a ?b) <= 4(?a ?c) 10(?d ?b) 10(?d ?c)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("4", 0, false));
//		query.addEdge(0, 2, new LabeledEdge("4", 1, false));
//		query.addEdge(3, 1, new LabeledEdge("10", 2, false));
//		query.addEdge(3, 2, new LabeledEdge("10", 3, false));

		// 33(?a,?h) 6(?h,?b) => 29(?a,?b) 0.057185629 0.001103587 0.130553657 191
		// 173072 1463 -1
		// 36(?a,?h) 6(?h,?b) => 18(?a,?b) 0.121495327 0.04887218 0.168242907 338 6916
		// 2009 -1
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // h
//		
//		query.addEdge(0, 1, new LabeledEdge("18", 0, false));
//		query.addEdge(0, 2, new LabeledEdge("36", 1, false));
//		query.addEdge(2, 1, new LabeledEdge("6", 2, false));

		// 33(?a,?b) => 9(?a,?b) 0.866419451 0.745141027 0.825023798 277340 372198
		// 336160 -1
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		
//		query.addEdge(0, 1, new LabeledEdge("9", 0, false));
//		query.addEdge(0, 1, new LabeledEdge("33", 1, false));

		// YAGO 14(?g,?b) 35(?g,?a) => 14(?a,?b) 0.036525425 0.851735016 0.855031668
		// 2430 2853 2842 -1 <== Is this empty?
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // g
//		
//		query.addEdge(0, 1, new LabeledEdge("14", 0));
//		query.addEdge(2, 0, new LabeledEdge("35", 1));
//		query.addEdge(2, 1, new LabeledEdge("14", 2));
//		
		// YAGO 13(?a,?h) 6(?h,?b) => 18(?a,?b) 0.080877067 0.00683662 0.161406026 225
		// 32911 1394 -1 <== Is this empty?
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // h
//		
//		query.addEdge(0, 1, new LabeledEdge("18", 0));
//		query.addEdge(0, 2, new LabeledEdge("13", 1));
//		query.addEdge(2, 1, new LabeledEdge("6", 2));

		// WN18 0(?a ?b) <= 9(?h ?a) 5(?h ?b) 0.097874888 0.726666667 0.153377111 68081
		// 1698 5869
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // h
//		
//		query.addEdge(0, 1, new LabeledEdge("0", 0, false));
//		query.addEdge(2, 0, new LabeledEdge("9", 1, false));
//		query.addEdge(2, 1, new LabeledEdge("5", 2, false));

//		// WN18 17(?a ?b) <= 2(?b ?c) 2(?a ?d) 10(?d ?c) 
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("17", 0, false));
//		query.addEdge(1, 2, new LabeledEdge("2", 1, false));
//		query.addEdge(0, 3, new LabeledEdge("2", 2, false));
//		query.addEdge(3, 2, new LabeledEdge("10", 3, false));
//		
//		// WN18 2(?a ?b) <= 10(?c ?b) 5(?b ?c) 5(?d ?a) 10(?a ?d) 
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("2", 0, false));
//		query.addEdge(2, 1, new LabeledEdge("10", 1, false));
//		query.addEdge(1, 2, new LabeledEdge("5", 2, false));
//		query.addEdge(3, 0, new LabeledEdge("5", 3, false));
//		query.addEdge(0, 3, new LabeledEdge("10", 4, false));

		// WN18 2(?23518 ?3212) <= 10(?25216 ?23518) 5(?23518 ?25216) 2(?3212 ?10285)
		// 2(?10285 ?3212)
		// WN18 2(?a ?b) <= 10(?c ?a) 5(?a ?c) 2(?b ?d) 2(?d ?b)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("2", 0, false));
//		query.addEdge(2, 0, new LabeledEdge("10", 1, false));
//		query.addEdge(0, 2, new LabeledEdge("5", 2, false));
//		query.addEdge(1, 3, new LabeledEdge("2", 3, false));
//		query.addEdge(3, 1, new LabeledEdge("2", 4, false));

		// FB13 4(?41787 ?5318) <= 7(?41787 ?5318) 7(?5317 ?5318) 4(?5317 ?5318)
		// 4(?a ?b) <= 7(?a ?b) 7(?c ?b) 4(?c ?b)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("4", 0, false));
//		query.addEdge(0, 1, new LabeledEdge("7", 1, false));
//		query.addEdge(2, 1, new LabeledEdge("7", 2, false));
//		query.addEdge(2, 1, new LabeledEdge("4", 3, false));

		// FB13 10(?49062 ?11994) <= 11(?11994 ?41745) 9(?11994 ?49062) 11(?41745
		// ?11994)
		// 10(?a ?b) <= 11(?b ?c) 9(?b ?a) 11(?c ?b)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("10", 0, false));
//		query.addEdge(1, 2, new LabeledEdge("11", 1, false));
//		query.addEdge(1, 0, new LabeledEdge("9", 2, false));
//		query.addEdge(2, 1, new LabeledEdge("11", 3, false));

		// FB15K237
		// ([a, b, -1], [170(a : b)=(a), 40(a : b)=(-1,b)])
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//
//		query.addEdge(0, 1, new LabeledEdge("170", 0));
//		query.addEdge(0, 1, new LabeledEdge("40", 1));

		// WN18
		// Rule: ([0, 1], [13(0 : 1)=(0,1), 15(1 : 0)=(1,0)])
//		query.addVertex(0);
//		query.addVertex(1);
//		
//		query.addEdge(0, 1, new LabeledEdge("13", 1));
//		query.addEdge(1, 0, new LabeledEdge("15", 0));

		// WN18
		// Rule: 17(a, b) <= 17(b, a); PCAX=68, PCAY=48
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//
//		query.addEdge(0, 1, new LabeledEdge("17", 0));
//		query.addEdge(1, 0, new LabeledEdge("17", 1));

		// Rule: 15(a, b) <= 13(b, a); PCAX=3, PCAY=0
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//
//		query.addEdge(0, 1, new LabeledEdge("15", 0));
//		query.addEdge(1, 0, new LabeledEdge("13", 1));

		// Rule: 17(a, b) <= 10(c, d), 2(b, d), 2(c, a); Support: 103; PCAX=1555,
		// PCAY=2634.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//
//		query.addEdge(0, 1, new LabeledEdge("17", 0));
//		query.addEdge(2, 3, new LabeledEdge("10", 1));
//		query.addEdge(1, 3, new LabeledEdge("2", 2));
//		query.addEdge(2, 0, new LabeledEdge("2", 3));
//
		// NELL; Rule 293: 32(a, b) <= 157(b, c), 19(d, a), 26(d, c)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//
//		query.addEdge(0, 1, new LabeledEdge("32", 0));
//		query.addEdge(1, 2, new LabeledEdge("157", 1));
//		query.addEdge(3, 0, new LabeledEdge("19", 2));
//		query.addEdge(3, 2, new LabeledEdge("26", 3));

		// FB15K; Rule 169: 832(a, b) <= 957(a, b), 832(a, c), 973(c, a)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//
//		query.addEdge(0, 1, new LabeledEdge("832", 0));
//		query.addEdge(0, 1, new LabeledEdge("957", 1));
//		query.addEdge(0, 2, new LabeledEdge("832", 2));
//		query.addEdge(2, 0, new LabeledEdge("973", 3));

		// Wikidata5M; Rule 8: P175(a, b) <= P155(c, a), P175(c, b). PCAx (estimated):
		// 9940, PCAy (estimated): 7737.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("P175", 0));
//		query.addEdge(2, 0, new LabeledEdge("P155", 1));
//		query.addEdge(2, 1, new LabeledEdge("P175", 2));

		// Wikidata5M; Rule 10: P175(a, b) <= P175(c, b), P361(c, a). PCAx (exact):
		// 4477; PCAy (exact): 3243.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("P175", 0));
//		query.addEdge(2, 1, new LabeledEdge("P175", 1));
//		query.addEdge(2, 0, new LabeledEdge("P361", 2));

		// Wikidata5M; Rule 3: P264(a, b) <= P156(c, a), P264(c, b). PCAx (exact):
		// 26606; PCAy (exact): 26606.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("P264", 0));
//		query.addEdge(2, 0, new LabeledEdge("P156", 1));
//		query.addEdge(2, 1, new LabeledEdge("P264", 2));

		// Wikidata5M; Rule 26: P156(a, b) <= P155(c, b), P175(c, d), P175(a, d). ????
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("P156", 0));
//		query.addEdge(2, 1, new LabeledEdge("P155", 1));
//		query.addEdge(2, 3, new LabeledEdge("P175", 2));
//		query.addEdge(0, 3, new LabeledEdge("P175", 3));

		// BioKG; Rule 20: 12(a, b) <= 13(c, a), 12(c, b). Support: 38346; PCAx: 685329;
		// PCAy: 521166.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("12", 0));
//		query.addEdge(2, 0, new LabeledEdge("13", 1));
//		query.addEdge(2, 1, new LabeledEdge("12", 2));

		// BioKG; Rule 24: 13(a, b) <= 13(d, c), 13(d, b), 13(c, a). PCAx (est.):
		// 12997007; PCAy (est.): 10441141.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("13", 0));
//		query.addEdge(3, 2, new LabeledEdge("13", 1));
//		query.addEdge(3, 1, new LabeledEdge("13", 2));
//		query.addEdge(2, 0, new LabeledEdge("13", 3));

		// BioKG; Rule 35: 9(a, b) <= 9(c, b), 2(c, d), 2(e, d), 2(e, a); Support: 2470;
		// PCAx (estimated): 2377256; PCAy (estimated): 865472.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		query.addVertex(4); // e
//
//		query.addEdge(0, 1, new LabeledEdge("9", 0));
//		query.addEdge(2, 1, new LabeledEdge("9", 1));
//		query.addEdge(2, 3, new LabeledEdge("2", 2));
//		query.addEdge(4, 3, new LabeledEdge("2", 3));
//		query.addEdge(4, 0, new LabeledEdge("2", 4));

		// BioKG; Rule 1656: 11(a, b) <= 2(a, c), 10(c, b); Support: 1111; PCAx (exact):
		// 53557; PCAy (exact): 434333.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//
//		query.addEdge(0, 1, new LabeledEdge("11", 0));
//		query.addEdge(0, 2, new LabeledEdge("2", 1));
//		query.addEdge(2, 1, new LabeledEdge("10", 2));

		// Codex_L; Rule 3: P47(a, b) <= P47(c, a), P47(c, b), P47(d, b), P47(b, d);
		// Support: 5873; PCAx (estimated): 15178; PCAy (exact): 15126.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("P47", 0));
//		query.addEdge(2, 0, new LabeledEdge("P47", 1));
//		query.addEdge(2, 1, new LabeledEdge("P47", 2));
//		query.addEdge(3, 1, new LabeledEdge("P47", 3));
//		query.addEdge(1, 3, new LabeledEdge("P47", 4));

		// Codex_L; Rule 11: P3373(a, b) <= P106(a, c), P106(b, c)
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		
//		query.addEdge(0, 1, new LabeledEdge("P3373", 0));
//		query.addEdge(0, 2, new LabeledEdge("P106", 1));
//		query.addEdge(1, 2, new LabeledEdge("P106", 2));

		// Codex_L; Rule 15: P131(a, b) <= P4552(a, c), P4552(d, c), P131(d, b); PCAx
		// (exact): 1373; PCAy (exact): 1675.
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		
//		query.addEdge(0, 1, new LabeledEdge("P131", 0));
//		query.addEdge(0, 2, new LabeledEdge("P4552", 1));
//		query.addEdge(3, 2, new LabeledEdge("P4552", 2));
//		query.addEdge(3, 1, new LabeledEdge("P131", 3));

		// FB15K237; Rule 50: 17(a, b) <= 30(d, c), 38(e, d), 17(e, b), 19(a, c).
//		query.addVertex(0); // a
//		query.addVertex(1); // b
//		query.addVertex(2); // c
//		query.addVertex(3); // d
//		query.addVertex(4); // e
//
//		query.addEdge(0, 1, new LabeledEdge("17", 0));
//		query.addEdge(3, 2, new LabeledEdge("30", 1));
//		query.addEdge(4, 3, new LabeledEdge("38", 2));
//		query.addEdge(4, 1, new LabeledEdge("17", 3));
//		query.addEdge(0, 2, new LabeledEdge("19", 4));

		// FB13: 5(0, 1) <= 2(0, 2), 5(3, 1), 7(3, 2)
//		query.addVertex(0);
//		query.addVertex(1);
//		query.addVertex(2);
//		query.addVertex(3);
//
//		query.addEdge(0, 1, new LabeledEdge("5", 0));
//		query.addEdge(0, 2, new LabeledEdge("2", 1));
//		query.addEdge(3, 1, new LabeledEdge("5", 2));
//		query.addEdge(3, 2, new LabeledEdge("7", 3));

		// CodexL: P17(a, b) <= P20(c, a), P27(c, b)
		// Strategy: approx.; PCA 0. Conf.: 4865.905197030268418046830382638492322000.
		// Strategy: approx.; PCA 1. Conf.: 2548.010508280982295830953740719588806488.
//		query.addVertex(0);
//		query.addVertex(1);
//		query.addVertex(2);
//
//		query.addEdge(0, 1, new LabeledEdge("P17", 0));
//		query.addEdge(2, 0, new LabeledEdge("P20", 1));
//		query.addEdge(2, 1, new LabeledEdge("P27", 2));

		// CodexL: P17(a, b) <= P551(c, a), P27(c, b)
		// Strategy: Exact PCA 0. Conf.: 1547.
		// Strategy: Exact PCA 1. Conf.: 745.
//		query.addVertex(0);
//		query.addVertex(1);
//		query.addVertex(2);
//
//		query.addEdge(0, 1, new LabeledEdge("P17", 0));
//		query.addEdge(2, 0, new LabeledEdge("P551", 1));
//		query.addEdge(2, 1, new LabeledEdge("P27", 2));

		// CodexL: P17(a, b) <= P19(c, a), P161(d, c), P495(d, b)
		// Strategy: Exact PCA 0. Conf.: 3193.
		// Strategy: Exact PCA 1. Conf.: 1823.
//		query.addVertex(0);
//		query.addVertex(1);
//		query.addVertex(2);
//		query.addVertex(3);
//
//		query.addEdge(0, 1, new LabeledEdge("P17", 0));
//		query.addEdge(2, 0, new LabeledEdge("P19", 1));
//		query.addEdge(3, 2, new LabeledEdge("P161", 2));
//		query.addEdge(3, 1, new LabeledEdge("P495", 3));

		// CodexL: P17(a, b) <= P740(c, a), P264(c, d), P17(d, b)
		// Strategy: Exact PCA 0. Conf.: 10.
		// Strategy: Exact PCA 1. Conf.: 8.
//		query.addVertex(0);
//		query.addVertex(1);
//		query.addVertex(2);
//		query.addVertex(3);
//
//		query.addEdge(0, 1, new LabeledEdge("P17", 0));
//		query.addEdge(2, 0, new LabeledEdge("P740", 1));
//		query.addEdge(3, 2, new LabeledEdge("P264", 2));
//		query.addEdge(3, 1, new LabeledEdge("P17", 3));

		// CodexL: P161(a, b) <= P106(c, a), P27(c, d), P27(b, d)
		// Strategy: CLT PCA 0. Conf.: 2693658.2809822958309537407195888063917375.
		// Strategy: CLT PCA 1. Conf.: 13396.9999999999999999999999999999983077.
		query.addVertex(0);
		query.addVertex(1);
		query.addVertex(2);
		query.addVertex(3);

		query.addEdge(0, 1, new LabeledEdge("P161", 0));
		query.addEdge(2, 0, new LabeledEdge("P106", 1));
		query.addEdge(2, 3, new LabeledEdge("P27", 2));
		query.addEdge(1, 3, new LabeledEdge("P27", 3));

		// Wikidata5M: P1075(a, b) <= P108(c, a), P31(c, d), P31(b, d)
//		query.addVertex(0);
//		query.addVertex(1);
//		query.addVertex(2);
//		query.addVertex(3);
//
//		query.addEdge(0, 1, new LabeledEdge("P1075", 0));
//		query.addEdge(2, 0, new LabeledEdge("P108", 1));
//		query.addEdge(2, 3, new LabeledEdge("P31", 2));
//		query.addEdge(1, 3, new LabeledEdge("P31", 3));

		/*************/
		/*************/
		/*************/
		/*************/
		/*************/

		// Start here!
		LabeledEdge head = query.edgeSet().stream().filter(e -> e.pid == 0).findFirst().get();
		GraphVisitor queryVisitor = new GraphVisitor(db, query, head, split);

		RuleMetricListener exactSupportListener = new ExactRuleMetricListener(
				new RuleSupport(queryVisitor.getHeadSize()));
		Collection<RuleMetricListener> supportListeners = List.of(exactSupportListener);

		SupportVisitor exactSupportVisitor = RuleMetricFactory.getExactSupport(queryVisitor);
		exactSupportVisitor.compute(supportListeners);

		RuleSupport exactSupport = (RuleSupport) exactSupportListener.getMetric();

		System.out.println("Strategy: Exact Support. Support: " + exactSupport.support + ". Total CPU: "
				+ exactSupportListener.getTimeElapsed().intValue() / 1e9);

		PrintWriter toFile = new PrintWriter(new File("estimate.txt"));

		SupportVisitor supportVisitor = null;

		EstimatorMemento memento = new EstimatorMemento();
		Collection<RuleMetricListener> supportListenersWithoutReplacement = List.of(
				new EstimatedRuleMetricListener(new RuleSupport(queryVisitor.getHeadSize()),
						EstimatorFactory.getStatisticalEstimator(StatisticalEstimatorType.Hypergeometric, accuracy,
								confidence, EstimatorLimit.ConfidenceInterval, minSamples, memento)),
				new EstimatedRuleMetricListener(new RuleSupport(queryVisitor.getHeadSize()),
						EstimatorFactory.getStatisticalEstimator(StatisticalEstimatorType.Hypergeometric, accuracy,
								confidence, EstimatorLimit.Chernoff, minSamples, memento)));

		for (int i = 0; i < runs; i++) {
			supportVisitor = RuleMetricFactory.getEstimatedSupport(queryVisitor, memento,
					new Sampling<>(true, true, null));
			supportVisitor.compute(supportListenersWithoutReplacement);

			for (RuleMetricListener l : supportListenersWithoutReplacement)
				toFile.println("Strategy: " + ((EstimatedRuleMetricListener) l).getEstimator().toString()
						+ ". Support: " + ((RuleSupport) l.getMetric()).support + ". Total CPU: "
						+ l.getTimeElapsed().intValue() / 1e9 + ". Extra: " + l.getMetric().getExtraStuff() + ".");
		}

		memento = new EstimatorMemento();
		Collection<RuleMetricListener> supportListenersWithReplacement = List.of(
				new EstimatedRuleMetricListener(new RuleSupport(queryVisitor.getHeadSize()),
						EstimatorFactory.getStatisticalEstimator(StatisticalEstimatorType.Chao2, accuracy, confidence,
								EstimatorLimit.ConfidenceInterval, minSamples, memento)),
				new EstimatedRuleMetricListener(new RuleSupport(queryVisitor.getHeadSize()),
						EstimatorFactory.getStatisticalEstimator(StatisticalEstimatorType.Chao2, accuracy, confidence,
								EstimatorLimit.Chernoff, minSamples, memento)));

		for (int i = 0; i < runs; i++) {
			supportVisitor = RuleMetricFactory.getEstimatedSupport(queryVisitor, memento,
					new Sampling<>(true, true, null));
			supportVisitor.compute(supportListenersWithReplacement);

			for (RuleMetricListener l : supportListenersWithReplacement)
				toFile.println("Strategy: " + ((EstimatedRuleMetricListener) l).getEstimator().toString()
						+ ". Support: " + ((RuleSupport) l.getMetric()).support + ". Total CPU: "
						+ l.getTimeElapsed().intValue() / 1e9 + ". Extra: " + l.getMetric().getExtraStuff() + ".");
		}

//		memento = new EstimatorMemento();
//		supportListenersWithReplacement = List.of(
//				new EstimatedRuleMetricListener(new RuleSupport(queryVisitor.getHeadSize()),
//						EstimatorFactory.getStatisticalEstimator(StatisticalEstimatorType.HansenHurwitz, accuracy, confidence,
//								EstimatorLimit.ConfidenceInterval, minSamples, memento)),
//				new EstimatedRuleMetricListener(new RuleSupport(queryVisitor.getHeadSize()),
//						EstimatorFactory.getStatisticalEstimator(StatisticalEstimatorType.HansenHurwitz, accuracy, confidence,
//								EstimatorLimit.Chernoff, minSamples, memento)));
//
//		for (int i = 0; i < runs; i++) {
//			supportVisitor = RuleMetricFactory.getEstimatedSupport(queryVisitor, memento,
//					new Sampling<>(true, true, null));
//			supportVisitor.compute(supportListenersWithReplacement);
//
//			for (RuleMetricListener l : supportListenersWithReplacement)
//				toFile.println("Strategy: " + ((EstimatedRuleMetricListener) l).getEstimator().toString()
//						+ ". Support: " + ((RuleSupport) l.getMetric()).support + ". Total CPU: "
//						+ l.getTimeElapsed().intValue() / 1e9 + ". Extra: " + l.getMetric().getExtraStuff() + ".");
//		}

		queryVisitor = null;

		int x = query.getEdgeSource(head), y = query.getEdgeTarget(head);

		for (int corrupt : List.of(x, y)) {
			GraphVisitor pcaQueryVisitor = GraphVisitor.getPCAConfVisitor(db, query, head, corrupt, split);

			Collection<RuleMetricListener> listeners = new ArrayList<>();
			Map<RuleMetricListener, String> listenerNames = new HashMap<>();

			// TODO Uncomment!
//			// Choices: sample selection and with/without replacement.
//			for (SampleSelection choice : List.of(SampleSelection.NonCorrupt))
//				for (boolean withReplacement :
//				// new boolean[] { false, true }
//				new boolean[] { true }) {
//					memento = new EstimatorMemento();
//
//					EstimatedPCAConfidenceVisitor pcaVisitorBeam = RuleMetricFactory.getEstimatedPCAConfidence(
//							pcaQueryVisitor, memento, x, y, corrupt, choice,
//							new Sampling<>(true, withReplacement, null), new Sampling<>(true, withReplacement, null),
//							true);
//
//					// AnyBURL can be understood as with replacement.
//					if (withReplacement) {
//						EstimatedRuleMetricListener listener = new EstimatedRuleMetricListener(
//								new RulePCAConfidence(corrupt), EstimatorFactory
//										.getNonstatisticalEstimator(NonstatisticalEstimatorType.AnyBURL, memento));
//
//						Supplier<String> getTechnique = () -> {
//							return "PCA" + corrupt + "_AnyBURL_" + "_" + choice;
//						};
//
//						listeners.add(listener);
//						listenerNames.put(listener, getTechnique.get());
//					}
//
//					for (StatisticalEstimatorType estimatorType :
////							EstimatorFactory.getEstimatorsReplacement(withReplacement)
//					List.of(StatisticalEstimatorType.HorvitzThompsonWith))
//						for (EstimatorLimit limitType : List.of(EstimatorLimit.Chernoff
////								, EstimatorLimit.ConfidenceInterval
//						))
//							for (double accuracyBatch : new double[] { .1, .075, .05/* , .025 */ })
//								for (double confidenceBatch : new double[] { .05, .01, .001/* , .0001 */ }) {
//									EstimatedRuleMetricListener listener = new EstimatedRuleMetricListener(
//											new RulePCAConfidence(corrupt),
//											EstimatorFactory.getStatisticalEstimator(estimatorType, accuracyBatch,
//													confidenceBatch, limitType, minSamples, memento));
//
//									Supplier<String> getTechnique = () -> {
//										return "PCA" + corrupt + "_" + estimatorType + "_" + limitType + "_" + choice
//												+ "_Acc" + accuracyBatch + "_Conf" + confidenceBatch;
//									};
//
//									listeners.add(listener);
//									listenerNames.put(listener, getTechnique.get());
//								}
//
//					System.out.println("\tComputing estimated PCA conf using sample choice " + choice
//							+ " and replacement " + withReplacement + " -- " + new Date());
//
//					for (int i = 0; i < runs; i++) {
//						pcaVisitorBeam.compute(listeners);
//
//						for (RuleMetricListener l : listeners)
//							toFile.println("Strategy: " + listenerNames.get(l) + ". Conf.: "
//									+ ((RulePCAConfidence) l.getMetric()).pcaPrime + ". Total CPU: "
//									+ l.getTimeElapsed().intValue() / 1e9 + ". Extra: "
//									+ ((RulePCAConfidence) l.getMetric()).getExtraStuff() + ".");
//					}
//
//					listeners.clear();
//					listenerNames.clear();
//				}

			// TODO Comment out!
			System.out.println("\tComputing estimated PCA conf using exact/CLT -- " + new Date());

//			BigInteger total = BigInteger.valueOf(pcaQueryVisitor.getVariableCandidates(x).size())
//					.multiply(BigInteger.valueOf(pcaQueryVisitor.getVariableCandidates(y).size()));

			// We will use Binomial+CLT with accuracy=.0025 and confidence=.0025.
			BinomialEstimator binEstimator = new BinomialEstimator(new EstimatorMemento(), .005, .0001,
					EstimatorLimit.CentralLimitTheorem, 50);

			RuleMetricListener exactPCAListener;
			PCAConfidenceVisitor exactPCAVisitor;

			RulePCAConfidence conf = new RulePCAConfidence(corrupt);

			String type = null;

//			if (total.compareTo(binEstimator.getCLTBound()) >= 0) {
			exactPCAListener = new EstimatedRuleMetricListener(conf, binEstimator);

			EstimatorMemento cltMemento = new EstimatorMemento();
			exactPCAVisitor = RuleMetricFactory.getEstimatedPCAConfidence(pcaQueryVisitor, cltMemento, x, y, corrupt,
					SampleSelection.Random, new Sampling<>(true, true, null),
					// Using beam search!
					new Sampling<>(true, true, null), true);

			type = "approx.";
//			} else {
//				exactPCAListener = new ExactRuleMetricListener(conf);
//				exactPCAVisitor = RuleMetricFactory.getExactPCAConfidence(pcaQueryVisitor, x, y, corrupt);
//
//				type = "exact";
//			}

			listeners.add(exactPCAListener);

			exactPCAVisitor.compute(listeners);

			pcaQueryVisitor = null;

			System.out.println("Strategy: " + type + "; PCA " + corrupt + ". Conf.: " + conf.pcaPrime + ". "
					+ "Total CPU: " + exactPCAListener.getTimeElapsed().intValue() / 1e9);

			for (RuleMetricListener l : listeners)
				toFile.println(
						"Strategy: " + listenerNames.get(l) + ". Conf.: " + ((RulePCAConfidence) l.getMetric()).pcaPrime
								+ ". Total CPU: " + l.getTimeElapsed().intValue() / 1e9 + ". Extra: "
								+ ((RulePCAConfidence) l.getMetric()).getExtraStuff() + ".");

			toFile.println();
			toFile.println();

			System.out.println("\tDone! -- " + new Date());
		}

		toFile.close();

		// We are done!
		dbService.shutdown();
	}

	private static void registerShutdownHook(final DatabaseManagementService service) {
		// Registers a shutdown hook for the Neo4j instance so that it
		// shuts down nicely when the VM exits (even if you "Ctrl-C" the
		// running application).
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				service.shutdown();
			}
		});
	}
}
