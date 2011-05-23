package knowledge_app;

public class EndingRule {
	String ending;
	int rule_no;
	int type;
	int subtype;
	int rule_id;
	int person;
	int wcase;
	int gender;
	String allow_after;
	String deny_after;
	String e_before;
	String o_before;
	int min_length;

	public EndingRule(String ending, int rule_no, int type, int subtype, int rule_id,int wcase, int gender, int person,
			String allow_after, String deny_after, String e_before, String o_before,int min_length) {
		this.ending = ending;
		this.rule_no = rule_no;
		this.type = type;
		this.subtype = subtype;
		this.rule_id = rule_id;
		this.wcase = wcase;
		this.gender = gender;
		this.person = person;
		this.allow_after = allow_after;
		this.deny_after = deny_after;
		this.e_before=e_before;
		this.o_before=o_before;
		this.min_length=min_length;
	}
	
	public EndingRule(int rule_id,int wcase, int gender, int person, int type, int rule_no){
		this.rule_id=rule_id;
		this.type=type;
		this.rule_no=rule_no;
		this.wcase=wcase;
		this.gender=gender;
		this.person=person;
	}
}
