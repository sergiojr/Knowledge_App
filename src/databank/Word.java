package databank;

import java.util.ArrayList;
import java.util.HashSet;

public class Word {
	boolean fixed;
	int id;
	String word;
	int type;
	int rule_no;
	int rule_variance;
	boolean complex;
	int word1ID;
	int word2ID;
	ArrayList<Integer> ruleVariance;
	int rating;
	private ArrayList<WordForm> wordforms;
	private ArrayList<WordWordRelation> wordRelations;
	private ArrayList<Word> dependentComplexWords;

	public Word(DataBank databank, int id, String word, int type, int rule_no, int rule_variance,
			boolean complex, int word1ID, int word2ID, int rating) {
		this.id = id;
		this.word = word;
		this.type = type;
		this.rule_no = rule_no;
		this.rule_variance = rule_variance;
		this.complex = complex;
		this.word1ID = word1ID;
		this.word2ID = word2ID;
		this.rating = rating;
		this.fixed = (this.rule_no < 0);
		if (databank != null)
			this.ruleVariance = databank.getRuleVariance(rule_no);
		this.wordforms = new ArrayList<WordForm>();
		this.wordRelations = null;
		this.dependentComplexWords = null;
	}

	public int getId() {
		return id;
	}

	public String getWord() {
		return word;
	}

	public WordForm getWordform(int rule_id, int postfix_id) {
		for (WordForm wordform : wordforms)
			if ((wordform.getRuleID() == rule_id) & (wordform.postfix_id == postfix_id))
				return wordform;
		return null;
	}

	public void addWordform(WordForm wordform) {
		if ((id == wordform.wordID) && (!wordforms.contains(wordform)))
			wordforms.add(wordform);
	}

	public ArrayList<WordForm> getWordforms() {
		return wordforms;
	}

	public void addWordWordRelation(WordWordRelation wordRelation) {
		if (wordRelations==null)
			wordRelations = new ArrayList<WordWordRelation>();
		
		if ((id == wordRelation.parentWordID | id == wordRelation.wordID)
				&& (!wordRelations.contains(wordRelation)))
			wordRelations.add(wordRelation);
	}

	public HashSet<WordWordRelation> getWordRelations(int relationType) {
		HashSet<WordWordRelation> result = new HashSet<WordWordRelation>();
		
		if (wordRelations==null)
			return result;
		
		for (WordWordRelation wordRelation : wordRelations)
			if (relationType < 0 | relationType == wordRelation.relationType)
				result.add(wordRelation);
		return result;
	}

	public void addDependentComplexWord(Word word) {
		if (dependentComplexWords == null)
			dependentComplexWords = new ArrayList<Word>();

		if (!dependentComplexWords.contains(word))
			dependentComplexWords.add(word);
	}

	public ArrayList<Word> getDependentComplexWords() {
		if (dependentComplexWords==null)
			return new ArrayList<Word>();
		
		return dependentComplexWords;
	}

	public int getWordRelationDiversity() {
		if (wordRelations==null)
			return 0;
		
		HashSet<Integer> wordRelationRefIDs = new HashSet<Integer>();		
		
		for (WordWordRelation wordRelation : wordRelations)
			if (wordRelation.relationType == 2)
				wordRelationRefIDs.add(wordRelation.relationRefID);
		return wordRelationRefIDs.size();
	}

	public int getEndingDiversity() {
		HashSet<String> endings = new HashSet<String>();
		for (WordForm wordform : wordforms)
			if (wordform.endingRule != null)
				if (wordform.endingRule.ending == null)
					endings.add(null);
				else
					endings.add(wordform.endingRule.ending.intern());
		return endings.size();
	}

	public float getRelationIndex() {
		float result = 0.0f;
		for (WordForm wordform : wordforms)
			result += wordform.relationIndex;
		return result;
	}
}
