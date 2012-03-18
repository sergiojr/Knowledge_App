package databank;

public class SentenceWordFilter {
	int sentenceID;
	int wordPos;
	String typeFilter;
	String wcaseFilter;
	String genderFilter;
	String sing_plFilter;
	
	public SentenceWordFilter(int sentenceID, int wordPos) {	
		this.sentenceID = sentenceID;
		this.wordPos = wordPos;
	}

	public void addValuestoFilters(SentenceWordRelation wordRelation, int wordSelector) {
		if (wordSelector == 1) {
			typeFilter = addValueToFilter(wordRelation.word1Type, typeFilter);
			wcaseFilter = addValueToFilter(wordRelation.word1Case, wcaseFilter);
			genderFilter = addValueToFilter(wordRelation.word1Gender, genderFilter);
			sing_plFilter = addValueToFilter(wordRelation.word1Sing_Pl, sing_plFilter);
		}
		if (wordSelector == 2) {
			typeFilter = addValueToFilter(wordRelation.word2Type, typeFilter);
			wcaseFilter = addValueToFilter(wordRelation.word2Case, wcaseFilter);
			genderFilter = addValueToFilter(wordRelation.word2Gender, genderFilter);
			sing_plFilter = addValueToFilter(wordRelation.word2Sing_Pl, sing_plFilter);
		}
	}

	private String addValueToFilter(int value, String filter) {
		String result = filter;
		if (value > 0) {
			if (result == null)
				result = new String();
			if (result.isEmpty())
				result = String.valueOf(value);
			else if (!DataBank.checkFilter(value, result)) {
				result = result + '|' + String.valueOf(value);
			}
		}
		return result;
	}

}
