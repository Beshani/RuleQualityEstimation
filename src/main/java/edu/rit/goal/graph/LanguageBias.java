package edu.rit.goal.graph;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DirectedMultigraph;

public class LanguageBias {
	public static boolean check(DirectedMultigraph<Integer, LabeledEdge> g,
			List<Predicate<DirectedMultigraph<Integer, LabeledEdge>>> bias) {
		return bias.stream().allMatch(p -> p.test(g));
	}

	// The language bias is implemented as a number of predicates to fulfill.
	public static Predicate<DirectedMultigraph<Integer, LabeledEdge>> getIsClosed() {
		return graph -> {
			return !graph.vertexSet().stream().anyMatch(v -> graph.degreeOf(v) == 1);
		};
	}

	public static Predicate<DirectedMultigraph<Integer, LabeledEdge>> getIsConnected() {
		return graph -> {
			return !graph.vertexSet().stream().anyMatch(v -> graph.degreeOf(v) == 0);
		};
	}

	public static Predicate<DirectedMultigraph<Integer, LabeledEdge>> getAtMostOneDangling() {
		return graph -> {
			return graph.vertexSet().stream().filter(v -> graph.degreeOf(v) == 1).count() <= 1;
		};
	}

	public static BiPredicate<DirectedMultigraph<Integer, LabeledEdge>, LabeledEdge> getNoDanglingPCAConf() {
		// The resulting PCA query should have a single connected component!
		return (graph, head) -> {
			DirectedMultigraph<Integer, LabeledEdge> clonedExceptHead = cloneSubgraph(graph, head);

			ConnectivityInspector<Integer, LabeledEdge> inspector = new ConnectivityInspector<>(clonedExceptHead);
			return inspector.isConnected();
		};
	}

	public static DirectedMultigraph<Integer, LabeledEdge> cloneSubgraph(
			DirectedMultigraph<Integer, LabeledEdge> subgraph, LabeledEdge... exclude) {
		DirectedMultigraph<Integer, LabeledEdge> ret = new DirectedMultigraph<>(LabeledEdge.class);

		cloneSubgraph(ret, subgraph, exclude);

		return ret;
	}

	public static void cloneSubgraph(DirectedMultigraph<Integer, LabeledEdge> newGraph,
			DirectedMultigraph<Integer, LabeledEdge> subgraph, LabeledEdge... exclude) {
		for (LabeledEdge e : subgraph.edgeSet())
			if (!Arrays.stream(exclude).anyMatch(x -> e == x)) {
				int v = subgraph.getEdgeSource(e), vp = subgraph.getEdgeTarget(e);

				newGraph.addVertex(v);
				newGraph.addVertex(vp);

				newGraph.addEdge(v, vp, new LabeledEdge(e.predicate, e.pid));
			}
	}

	public static Set<Integer> getNonClosedVertices(DirectedMultigraph<Integer, LabeledEdge> g) {
		return g.vertexSet().stream().filter(v -> g.degreeOf(v) == 1).collect(Collectors.toSet());
	}

}
