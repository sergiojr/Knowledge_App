package databank;

public class WordWordRelation {
	int wordID;
	int parentWordID;
	int relationType;
	int relationRefID;
	int relationRefLine;

	public WordWordRelation(int wordID, int parentWordID, int relationType, int relationRefID,
			int relationRefLine) {
		this.wordID = wordID;
		this.parentWordID = parentWordID;
		this.relationType = relationType;
		this.relationRefID = relationRefID;
		this.relationRefLine = relationRefLine;
	}
}
