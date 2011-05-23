package knowledge_app;

import databank.DataBank;


public class WordForm {
	public int wordID;
	public String wordForm;
	public int rule;
	int rule_no;
	public int postfix_id;
	EndingRule endingRule;

	public WordForm(String wordForm, int wordID,
			EndingRule rule, int postfix_id) {
		this.wordForm = wordForm;
		this.wordID = wordID;
		this.endingRule = rule;
		if (rule == null){
			this.rule = 0;
			this.rule_no = 0;
		}else{
			this.rule = rule.rule_id;
			this.rule_no = rule.rule_no;
		}
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

}
