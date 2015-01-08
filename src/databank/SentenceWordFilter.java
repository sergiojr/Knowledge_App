package databank;

public class SentenceWordFilter {
	int sentenceID;
	int wordPos;
	String typeFilter;
	String subtypeFilter;
	String wcaseFilter;
	String personFilter;
	String genderFilter;
	String sing_plFilter;
	String animateFilter;

	public SentenceWordFilter(int sentenceID, int wordPos) {
		this.sentenceID = sentenceID;
		this.wordPos = wordPos;
	}

	public SentenceWordFilter(int sentenceID, int wordPos, String wcaseFilter, String personFilter,
			int gender, int sing_pl, int animate, String typeFilter, String subtypeFilter) {
		this.sentenceID = sentenceID;
		this.wordPos = wordPos;
		this.wcaseFilter = wcaseFilter;
		this.personFilter = personFilter;
		if (gender == 0)
			this.genderFilter = "";
		else
			this.genderFilter = "0|" + String.valueOf(gender);
		if (sing_pl == 0)
			this.sing_plFilter = "";
		else
			this.sing_plFilter = "0|" + String.valueOf(sing_pl);
		if (animate == 0)
			this.animateFilter = "";
		else
			this.animateFilter = "0|" + String.valueOf(animate);
		this.typeFilter = typeFilter;
		this.subtypeFilter = subtypeFilter;
	}

	public void addValuestoFilters(SentenceWordRelation wordRelation, int wordSelector) {
		if (wordSelector == 1) {
			typeFilter = addValueToFilter(wordRelation.word1Type, typeFilter, true);
			wcaseFilter = addValueToFilter(wordRelation.word1Case, wcaseFilter, true);
			genderFilter = addValueToFilter(wordRelation.word1Gender, genderFilter, false);
			sing_plFilter = addValueToFilter(wordRelation.word1Sing_Pl, sing_plFilter, false);
			animateFilter = addValueToFilter(wordRelation.word1Animate, animateFilter, false);
		}
		if (wordSelector == 2) {
			typeFilter = addValueToFilter(wordRelation.word2Type, typeFilter, true);
			wcaseFilter = addValueToFilter(wordRelation.word2Case, wcaseFilter, true);
			genderFilter = addValueToFilter(wordRelation.word2Gender, genderFilter, false);
			sing_plFilter = addValueToFilter(wordRelation.word2Sing_Pl, sing_plFilter, false);
			animateFilter = addValueToFilter(wordRelation.word2Animate, animateFilter, false);
		}
	}

	public boolean check(SentenceWordform sentenceWordform) {
		return SentenceWordFilter.checkFilter(sentenceWordform.wcase, wcaseFilter)
				&& SentenceWordFilter.checkFilter(sentenceWordform.person, personFilter)
				&& SentenceWordFilter.checkFilter(sentenceWordform.gender, genderFilter)
				&& SentenceWordFilter.checkFilter(sentenceWordform.sing_pl, sing_plFilter)
				&& SentenceWordFilter.checkFilter(sentenceWordform.animate, animateFilter)
				&& SentenceWordFilter.checkFilter(sentenceWordform.type, typeFilter)
				&& SentenceWordFilter.checkFilter(sentenceWordform.subtype, subtypeFilter);
	}

	private String addValueToFilter(int value, String filter, boolean strict) {
		String result = filter;

		if (!strict)
			if (value == 0)
				result = new String();

		if (value > 0) {
			if (result == null)
				result = String.valueOf(value);
			else if (!checkFilter(value, result)) {
				result = result + '|' + String.valueOf(value);
			}
		}
		return result;
	}

	static public boolean checkFilter(int value, String filter) {
		boolean result = false;
		if (filter == null)
			return true;
		if (filter.isEmpty())
			return true;
		String[] splitFilter = filter.split("\\|");
		for (int i = 0; i < splitFilter.length; i++) {
			result = result | checkBinaryFilter(value, splitFilter[i]);
		}
		return result;
	}

	static private boolean checkBinaryFilter(int value, String filter) {
		String part = filter;
		if (filter.startsWith("<>")) {
			part = filter.substring(2);
			return (value != Integer.valueOf(part));
		}
		if (filter.startsWith("<")) {
			part = filter.substring(1);
			return (value < Integer.valueOf(part));
		}
		if (filter.startsWith(">")) {
			part = filter.substring(1);
			return (value > Integer.valueOf(part));
		}

		// if (Integer.valueOf(part)==0)
		// return true;

		return (value == Integer.valueOf(part));
	}

}
