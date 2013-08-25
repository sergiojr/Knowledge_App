package databank;

public class SentenceWordFilter {
	int sentenceID;
	int wordPos;
	String typeFilter;
	String wcaseFilter;
	String genderFilter;
	String sing_plFilter;
	String animateFilter;

	public SentenceWordFilter(int sentenceID, int wordPos) {
		this.sentenceID = sentenceID;
		this.wordPos = wordPos;
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
