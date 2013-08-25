package databank;

public class SentenceWordform {
	public SentenceWordform(int sourceID, int sentenceID, int subsentenceID, int wordPos, int type,
			int subtype, int wcase, int gender, int person, int sing_pl, int animate, int word_id,
			int rule_id, int dep_word_pos, int preposition_id, String word_type_filter,
			String wcase_filter, String gender_filter, String sing_pl_filter, int rating,
			int maxrating) {
		this.sourceID = sourceID;
		this.sentenceID = sentenceID;
		this.subsentenceID = subsentenceID;
		this.wordPos = wordPos;
		this.type = type;
		this.subtype = subtype;
		this.wcase = wcase;
		this.gender = gender;
		this.person = person;
		this.sing_pl = sing_pl;
		this.animate = animate;
		this.word_id = word_id;
		this.rule_id = rule_id;
		this.dep_word_pos = dep_word_pos;
		this.preposition_id = preposition_id;
		this.word_type_filter = word_type_filter;
		this.wcase_filter = wcase_filter;
		this.gender_filter = gender_filter;
		this.sing_pl_filter = sing_pl_filter;
		this.rating = rating;
		this.maxrating = maxrating;
	}

	public int sourceID;
	public int sentenceID;
	public int subsentenceID;
	public int wordPos;
	public int type;
	public int subtype;
	public int wcase;
	public int gender;
	public int person;
	public int sing_pl;
	public int animate;
	public int word_id;
	public int rule_id;
	public int dep_word_pos;
	public int preposition_id;
	public int rating;
	public int maxrating;
	public String word_type_filter;
	public String wcase_filter;
	public String gender_filter;
	public String sing_pl_filter;

}
