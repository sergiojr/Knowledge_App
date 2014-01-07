package databank;

public class SentenceWordRelation {
	int id;
	int depID;
	int sourceID;
	int sentenceID;
	int word1Pos;
	int word1Type;
	int word1Case;
	int word1Gender;
	int word1Sing_Pl;
	int word1Animate;
	int word2Pos;
	int word2Type;
	int word2Case;
	int word2Gender;
	int word2Sing_Pl;
	int word2Animate;
	int status;
	int relationType;
	static int attribute = 1;
	static int adverbAttribute = 2;
	static int verbInfinitive = 11;
	static int verbSubstantive = 12;
	static int verbAdverb = 13;
	static int verbRelativeAdjective = 14;
	static int numeral = 21;
	static int genetivesubstantive = 31;
	static int conjunction = 100;
	static int negative = 101;
	static int preposition = 102;
	static int subjectPredicate = 1001;

	public SentenceWordRelation(int id, int depID, int sourceID, int sentenceID, int word1Pos,
			int word1Type, int word1Case, int word1Gender, int word1Sing_Pl, int word1Animate,
			int word2Pos, int word2Type, int word2Case, int word2Gender, int word2Sing_Pl,
			int word2Animate, int relationType) {
		this.id = id;
		this.depID = depID;
		this.sourceID = sourceID;
		this.sentenceID = sentenceID;
		this.word1Pos = word1Pos;
		this.word1Type = word1Type;
		this.word2Pos = word2Pos;
		this.word2Type = word2Type;

		if (relationType == verbAdverb) {
			this.word1Case = 0;
			this.word1Gender = 0;
			this.word1Sing_Pl = 0;
			this.word1Animate = 0;
			this.word2Case = 0;
			this.word2Gender = 0;
			this.word2Sing_Pl = 0;
			this.word2Animate = 0;
		} else if (relationType == verbRelativeAdjective) {
			this.word1Case = 0;
			this.word1Gender = 0;
			this.word1Sing_Pl = 0;
			this.word1Animate = 0;
			this.word2Case = 0;
			this.word2Gender = 0;
			this.word2Sing_Pl = 0;
			this.word2Animate = 0;
		} else if (relationType == adverbAttribute) {
			this.word1Case = 0;
			this.word1Gender = 0;
			this.word1Sing_Pl = 0;
			this.word1Animate = 0;
			this.word2Case = 0;
			this.word2Gender = 0;
			this.word2Sing_Pl = 0;
			this.word2Animate = 0;
		} else if (relationType == verbSubstantive) {
			this.word1Case = 0;
			this.word1Gender = 0;
			this.word1Sing_Pl = 0;
			this.word1Animate = 0;
			this.word2Case = word2Case;
			this.word2Gender = 0;
			this.word2Sing_Pl = word2Sing_Pl;
			this.word2Animate = word2Animate;
		} else if (relationType == verbInfinitive) {
			this.word1Case = word1Case;
			this.word1Gender = word1Gender;
			this.word1Sing_Pl = word1Sing_Pl;
			this.word1Animate = word1Animate;
			this.word2Case = 0;
			this.word2Gender = 0;
			this.word2Sing_Pl = 0;
			this.word2Animate = 0;
		} else if (relationType == preposition) {
			this.word1Case = word1Case;
			this.word1Gender = 0;
			this.word1Sing_Pl = word1Sing_Pl;
			this.word1Animate = word1Animate;
			this.word2Case = 0;
			this.word2Gender = 0;
			this.word2Sing_Pl = 0;
			this.word2Animate = 0;
		} else if (relationType == negative) {
			this.word1Case = 0;
			this.word1Gender = 0;
			this.word1Sing_Pl = 0;
			this.word1Animate = 0;
			this.word2Case = word2Case;
			this.word2Gender = word2Gender;
			this.word2Sing_Pl = word2Sing_Pl;
			this.word2Animate = word2Animate;
		} else {
			this.word1Case = word1Case;
			this.word1Gender = word1Gender;
			this.word1Sing_Pl = word1Sing_Pl;
			this.word1Animate = word1Animate;
			this.word2Case = word2Case;
			this.word2Gender = word2Gender;
			this.word2Sing_Pl = word2Sing_Pl;
			this.word2Animate = word2Animate;
		}

		this.relationType = relationType;
		this.status = 0;
	}

	public SentenceWordRelation(int id, SentenceWordRelation wordRelation,
			SentenceWordform wordform2, int relationType) {
		this(id, wordRelation.id, wordRelation.sourceID, wordRelation.sentenceID,
				wordRelation.word1Pos, wordRelation.word1Type, wordRelation.word1Case,
				wordRelation.word1Gender, wordRelation.word1Sing_Pl, wordRelation.word1Animate,
				wordform2.wordPos, wordform2.type, wordform2.wcase, wordform2.gender,
				wordform2.sing_pl, wordform2.animate, relationType);
	}

	public SentenceWordRelation(int id, int depID, SentenceWordform wordform1,
			SentenceWordform wordform2, int relationType) {
		this(id, depID, wordform1.sourceID, wordform1.sentenceID, wordform1.wordPos,
				wordform1.type, wordform1.wcase, wordform1.gender, wordform1.sing_pl,
				wordform1.animate, wordform2.wordPos, wordform2.type, wordform2.wcase,
				wordform2.gender, wordform2.sing_pl, wordform2.animate, relationType);
	}

	public SentenceWordRelation(int id, int depID, SentenceWordform wordform1, int relationType) {
		this(id, depID, wordform1.sourceID, wordform1.sentenceID, wordform1.wordPos,
				wordform1.type, wordform1.wcase, wordform1.gender, wordform1.sing_pl,
				wordform1.animate, 0, 0, 0, 0, 0, 0, relationType);
	}

}
