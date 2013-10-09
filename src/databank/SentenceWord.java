package databank;

public class SentenceWord {
	public SentenceWord(int sourceID, int sentenceID, int subsentenceID, int wordPos, String word,
			int part, int dep_word_pos, int preposition_id, boolean isPunctuation, boolean isName,
			boolean internal, String word_type_filter, String wcase_filter, String gender_filter,
			String sing_pl_filter, String animate_filter) {
		this.sourceID = sourceID;
		this.sentenceID = sentenceID;
		this.subsentenceID = subsentenceID;
		this.wordPos = wordPos;
		this.word = word;
		this.part = part;
		this.dep_word_pos = dep_word_pos;
		this.preposition_id = preposition_id;
		this.isPunctuation = isPunctuation;
		this.isName = isName;
		this.internal = internal;
		this.word_type_filter = word_type_filter;
		this.wcase_filter = wcase_filter;
		this.gender_filter = gender_filter;
		this.sing_pl_filter = sing_pl_filter;
		this.animate_filter = animate_filter;
	}

	public int sourceID;
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
	public boolean internal;
	String word_type_filter;
	String wcase_filter;
	String gender_filter;
	String sing_pl_filter;
	String animate_filter;
	SentenceWordform sentenceWordform;

	public void setFilters(SentenceWordFilter wordFilter) {
		if (wordFilter != null) {
			word_type_filter = wordFilter.typeFilter;
			wcase_filter = wordFilter.wcaseFilter;
			gender_filter = wordFilter.genderFilter;
			sing_pl_filter = wordFilter.sing_plFilter;
			animate_filter = wordFilter.animateFilter;
		}
	}
}
