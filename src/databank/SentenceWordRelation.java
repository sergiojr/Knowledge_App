package databank;

public class SentenceWordRelation {
	int id;
	int depID;
	int sentenceID;
	int word1Pos;
	int word1Type;
	int word1Case;
	int word1Gender;
	int word1Sing_Pl;
	int word2Pos;
	int word2Type;
	int word2Case;
	int word2Gender;
	int word2Sing_Pl;
	int status;
	int relationType;
	static int attribute = 1;
	static int adverbAttribute = 2;
	static int verbInfinitive = 11;
	static int verbSubstantive = 12;
	static int verbAdverb = 13;
	static int numeral = 21;
	static int genetivesubstantive = 31;
	static int conjunction = 100;
	static int negative = 101;
	static int preposition = 102;

	public SentenceWordRelation(int id, SentenceWordRelation wordRelation, int word2Pos, int word2Type,
			int word2Case, int word2Gender, int word2Sing_Pl, int relationType) {
		this.id = id;
		this.depID = wordRelation.id;
		this.sentenceID = wordRelation.sentenceID;
		this.word1Pos = wordRelation.word1Pos;
		this.word1Type = wordRelation.word1Type;
		this.word1Case = wordRelation.word1Case;
		this.word1Gender = wordRelation.word1Gender;
		this.word1Sing_Pl = wordRelation.word1Sing_Pl;
		this.word2Pos = word2Pos;
		this.word2Type = word2Type;
		this.word2Case = word2Case;
		this.word2Gender = word2Gender;
		this.word2Sing_Pl = word2Sing_Pl;
		this.relationType = relationType;
		this.status=1;
	}

	public SentenceWordRelation(int id, int depID, int sentenceID, int word1Pos, int word1Type, int word1Case,
			int word1Gender, int word1Sing_Pl, int word2Pos, int word2Type, int word2Case, int word2Gender,
			int word2Sing_Pl, int relationType) {
		this.id = id;
		this.depID = depID;
		this.sentenceID = sentenceID;
		this.word1Pos = word1Pos;
		this.word1Type = word1Type;
		this.word1Case = word1Case;
		this.word1Gender = word1Gender;
		this.word1Sing_Pl = word1Sing_Pl;
		this.word2Pos = word2Pos;
		this.word2Type = word2Type;
		this.word2Case = word2Case;
		this.word2Gender = word2Gender;
		this.word2Sing_Pl = word2Sing_Pl;
		this.relationType = relationType;
		this.status=1;
	}
}
