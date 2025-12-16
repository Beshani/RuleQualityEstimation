package edu.rit.goal.metric;

import java.util.HashSet;
import java.util.Set;

import org.jgrapht.graph.DirectedMultigraph;

import edu.rit.goal.graph.LabeledEdge;

public class Rule {
	int ruleId;

	DirectedMultigraph<Integer, LabeledEdge> rule;
	LabeledEdge head;

	RuleSupport support;
	RulePCAConfidence confidenceX, confidenceY;
	public String confidenceXType, confidenceYType;

	boolean pruned;

	public Rule(int ruleId, LabeledEdge head, DirectedMultigraph<Integer, LabeledEdge> rule) {
		super();
		this.ruleId = ruleId;
		this.head = head;
		this.rule = rule;
	}

	public int getRuleId() {
		return ruleId;
	}

	public void setRuleId(int ruleId) {
		this.ruleId = ruleId;
	}

	public DirectedMultigraph<Integer, LabeledEdge> getRule() {
		return rule;
	}

	public LabeledEdge getHead() {
		return head;
	}
	
	public int getUniqueBodyPredicates() {
		Set<String> preds = new HashSet<>();
		
		for (LabeledEdge e : rule.edgeSet())
			if (e != head)
				preds.add(e.predicate);
		
		return preds.size();
	}
	
	public int getUniqueVariables() {
		return rule.vertexSet().size();
	}

	public int getRuleLength() {
		return rule.edgeSet().size();
	}

	public RuleSupport getSupport() {
		return support;
	}

	public RulePCAConfidence getConfidenceX() {
		return confidenceX;
	}

	public RulePCAConfidence getConfidenceY() {
		return confidenceY;
	}

	public boolean isPruned() {
		return pruned;
	}
	
	public String getConfidenceXType() {
		return confidenceXType;
	}

	public String getConfidenceYType() {
		return confidenceYType;
	}

	public void setSupport(RuleSupport support) {
		this.support = support;
	}

	public void setConfidenceX(RulePCAConfidence confidenceX) {
		this.confidenceX = confidenceX;
	}

	public void setConfidenceY(RulePCAConfidence confidenceY) {
		this.confidenceY = confidenceY;
	}

	public void setPruned(boolean pruned) {
		this.pruned = pruned;
	}

	public void setConfidenceXType(String confidenceXType) {
		this.confidenceXType = confidenceXType;
	}

	public void setConfidenceYType(String confidenceYType) {
		this.confidenceYType = confidenceYType;
	}

}
