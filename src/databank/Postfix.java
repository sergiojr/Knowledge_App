package databank;

public class Postfix {
	int id;
	String postfix;
	int reflexive;
	int type;
	int tense;
	int rule_no;

	public Postfix(int id, String postfix, int reflexive, int type, int tense, int rule_no) {
		this.postfix = postfix;
		this.id = id;
		this.reflexive = reflexive;
		this.type = type;
		this.tense = tense;
		this.rule_no = rule_no;

	}

	public int getId() {
		return id;
	}

	public String getPostfix() {
		return postfix;
	}
}
