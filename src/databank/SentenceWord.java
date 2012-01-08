package databank;


public class SentenceWord {
	public SentenceWord(int sentenceID, int subsentenceID, int wordPos, String word, int part,
			int dep_word_pos, int preposition_id, boolean isPunctuation, boolean isName,
			String word_type_filter, String wcase_filter, String gender_filter,
			String sing_pl_filter) {
		this.sentenceID = sentenceID;
		this.subsentenceID = subsentenceID;
		this.wordPos = wordPos;
		this.word = word;
		this.part = part;
		this.dep_word_pos = dep_word_pos;
		this.preposition_id = preposition_id;
		this.isPunctuation = isPunctuation;
		this.isName=isName;
		this.word_type_filter = word_type_filter;
		this.wcase_filter = wcase_filter;
		this.gender_filter = gender_filter;
		this.sing_pl_filter = sing_pl_filter;
	}

	public int sentenceID;
	public int subsentenceID;
	public int wordPos;
	public String word;
	public int part;
	public static int subject = 1;
	public static int predicate = 2;
	public int dep_word_pos;
	public int preposition_id;
	public boolean isPunctuation;
	public boolean isName;
	public String word_type_filter;
	public String wcase_filter;
	public String gender_filter;
	public String sing_pl_filter;

	public void addValuestoFilters(SentenceWordform sentenceWordform) {
		word_type_filter = String.valueOf(sentenceWordform.type);
		wcase_filter = String.valueOf(sentenceWordform.wcase);
		gender_filter = String.valueOf(sentenceWordform.gender);
		sing_pl_filter = String.valueOf(sentenceWordform.sing_pl);
	}
}
