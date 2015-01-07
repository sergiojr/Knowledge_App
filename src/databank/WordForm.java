package databank;

public class WordForm {
	public int wordID;
	String wordForm;
	int postfix_id;
	EndingRule endingRule;
	float relationIndex;

	public WordForm(String wordForm, int wordID, EndingRule rule, int postfix_id) {
		this.wordForm = wordForm.intern();
		this.wordID = wordID;
		this.endingRule = rule;
		this.postfix_id = postfix_id;
	}

	public EndingRule getEndingRule() {
		return endingRule;
	}

	public int getRuleID() {
		if (endingRule == null)
			return 0;
		else
			return endingRule.rule_id;
	}

	public float getRelationIndex() {
		return relationIndex;
	}

	public void setRelationIndex(float relationIndex) {
		this.relationIndex = relationIndex;
	}

}
