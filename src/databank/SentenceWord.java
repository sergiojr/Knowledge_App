package databank;

public class SentenceWord {
	public SentenceWord(int sentenceID, int subsentenceID, int wordPos, String word, int part,
			int dep_word_pos, int preposition_id, boolean isPunctuation, boolean isName,
			boolean internal, String word_type_filter, String wcase_filter, String gender_filter,
			String sing_pl_filter) {
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
	public boolean internal;
	public String word_type_filter;
	public String wcase_filter;
	public String gender_filter;
	public String sing_pl_filter;
	SentenceWordform sentenceWordform;

	private void addValuestoFilters(SentenceWordform sentenceWordform) {
		word_type_filter = addValueToFilter(sentenceWordform.type, word_type_filter,true);
		wcase_filter = addValueToFilter(sentenceWordform.wcase, wcase_filter,true);
		gender_filter = addValueToFilter(sentenceWordform.gender, gender_filter,false);
		sing_pl_filter = addValueToFilter(sentenceWordform.sing_pl, sing_pl_filter,false);
	}

	public void addValuestoFilters(SentenceWordRelation wordRelation, int wordSelector) {
		if (wordSelector == 1) {
			word_type_filter = addValueToFilter(wordRelation.word1Type, word_type_filter,true);
			wcase_filter = addValueToFilter(wordRelation.word1Case, wcase_filter,true);
			gender_filter = addValueToFilter(wordRelation.word1Gender, gender_filter,false);
			sing_pl_filter = addValueToFilter(wordRelation.word1Sing_Pl, sing_pl_filter,false);
		}
		if (wordSelector == 2) {
			word_type_filter = addValueToFilter(wordRelation.word2Type, word_type_filter,true);
			wcase_filter = addValueToFilter(wordRelation.word2Case, wcase_filter,true);
			gender_filter = addValueToFilter(wordRelation.word2Gender, gender_filter,false);
			sing_pl_filter = addValueToFilter(wordRelation.word2Sing_Pl, sing_pl_filter,false);
		}

	}

	public SentenceWordform getSentenceWordform() {
		return new SentenceWordform(sentenceID, subsentenceID, wordPos, 0, 0, 0, 0, 0, 0, 0, 0, 0,
				0, "", "", "", "", 0, 0);
	}

	private String addValueToFilter(int value, String filter, boolean strict) {
		String result = filter;

		if (!strict)
			if (value == 0)
				result = new String();

		if (value > 0) {
			if (result == null)
				result = String.valueOf(value);
			else if (!DataBank.checkFilter(value, result)) {
				result = result + '|' + String.valueOf(value);
			}
		}
		return result;
	}

	public boolean filterMatch(String externalFilter) {
		if (wcase_filter == null)
			return false;

		if (wcase_filter.isEmpty())
			return false;

		String[] splitFilter = wcase_filter.split("\\|");
		for (String wcaseString : splitFilter) {
			int wcase = Integer.valueOf(wcaseString);
			if (DataBank.checkFilter(wcase, externalFilter))
				return true;
		}
		return false;
	}
}
