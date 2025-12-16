package edu.rit.goal.graph.signature;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections4.iterators.PermutationIterator;
import org.jgrapht.graph.DirectedMultigraph;

import edu.rit.goal.graph.LabeledEdge;

public class GraphSignature {
	public class SignatureResult {
		// This is the actual signature.
		public SignatureAtom[] signature;
		// This is the mapping between the ids of the input graph and the signature.
		public Map<Integer, Integer> mapping;
	}
	
	public static SignatureResult getSignature(DirectedMultigraph<Integer, LabeledEdge> graph, LabeledEdge head) {
		SignatureResult ret = new GraphSignature().new SignatureResult();

		// We aim to find the mapping with the minimum signature.

		// The head variables are always 0 and 1. Let's find out all the possible
		// combinations.
		int x = graph.getEdgeSource(head), y = graph.getEdgeTarget(head);

		// Create mapping.
		Map<Integer, Integer> mapping = new HashMap<>();
		mapping.put(x, 0);
		mapping.put(y, 1);

		Set<Integer> variables = new HashSet<>(graph.vertexSet());
		variables.remove(x);
		variables.remove(y);

		// There are no more variables!
		if (variables.isEmpty()) {
			SignatureAtom[] signature = getSignature(graph, mapping);
			ret.signature = signature;
			ret.mapping = mapping;
		} else {
			// Get all the permutations of the variables.
			PermutationIterator<Integer> permutIt = new PermutationIterator<>(variables);
			while (permutIt.hasNext()) {
				List<Integer> permut = permutIt.next();

				// Add the rest (2, 3, 4, ...).
				for (int i = 0; i < permut.size(); i++)
					mapping.put(permut.get(i), i + 2);

				SignatureAtom[] signature = getSignature(graph, mapping);

				// If this signature is less than the current one, update.
				if (ret.signature == null || cmpSignature(signature, ret.signature) < 0) {
					ret.signature = signature;
					ret.mapping = new HashMap<>(mapping);
				}
			}
		}

		return ret;
	}

	private static SignatureAtom[] getSignature(DirectedMultigraph<Integer, LabeledEdge> graph,
			Map<Integer, Integer> mapping) {
		SignatureAtom[] signature = new SignatureAtom[graph.edgeSet().size()];

		int i = 0;
		for (LabeledEdge e : graph.edgeSet()) {
			int s = graph.getEdgeSource(e), o = graph.getEdgeTarget(e);

			SignatureAtom sa = new SignatureAtom();
			sa.s = mapping.get(s);
			sa.o = mapping.get(o);
			sa.p = e.predicate;

			signature[i++] = sa;
		}

		Arrays.sort(signature);

		return signature;
	}

	public static int cmpSignature(SignatureAtom[] a, SignatureAtom[] b) {
		int ret = 0;

		for (int i = 0; ret == 0 && i < a.length; i++)
			ret = a[i].compareTo(b[i]);

		return ret;
	}
}
