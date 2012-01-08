package databank;

public class WordForm {
	public int wordID;
	public String wordForm;
	public int postfix_id;
	EndingRule endingRule;

	public WordForm(String wordForm, int wordID, EndingRule rule, int postfix_id) {
		this.wordForm = wordForm;
		this.wordID = wordID;
		this.endingRule = rule;
		this.postfix_id = postfix_id;
	}

	private int getPerson(DataBank databank) {
		if (endingRule != null)
			return endingRule.person;
		return -1;
	}

	private int getCase(DataBank databank) {
		if (endingRule != null)
			return endingRule.wcase;
		return -1;
	}

	private int getGender(DataBank databank) {
		if (endingRule != null)
			return endingRule.gender;
		return -1;
	}

	public EndingRule getEndingRule() {
		return endingRule;
	}
	
	public int getRuleID(){
		if (endingRule == null)
			return 0;
		else
			return endingRule.rule_id;
		
	}

}
