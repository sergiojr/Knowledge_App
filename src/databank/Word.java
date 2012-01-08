package databank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


public class Word {
	public Word(DataBank databank, int id, String word, int type, int rule_no, int rule_variance,
			boolean complex, int word1ID, int word2ID, int rating) {
		this.databank = databank;
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
	}

	public String getWord() {
		return word;
	}

	DataBank databank;
	boolean fixed;
	int id;
	String word;
	int type;
	int rule_no;
	int rule_variance;
	boolean complex;
	int word1ID;
	int word2ID;
	HashSet<Integer> ruleVariance;

	public int getId() {
		return id;
	}

	int rating;
	private HashSet<WordForm> wordforms;

	public void checkTransormations() {
		Set<Transformation> transformations;
		Transformation transformation;
		Word newWord;
		int transformationType;
		boolean isNewWord;

		ArrayList<Word> transformedWordsList;
		Iterator<Word> transformedWordIterator;
		Word transformedWord;
		if (fixed)
			return;

		if (type == 0)
			return;

		WordWordRelation transformationRelation;
		transformations = databank.getTransformations();
		Iterator<Transformation> iterator = transformations.iterator();
		while (iterator.hasNext()) {
			transformation = iterator.next();
			// check for forward transformation
			newWord = transformation.forwardTransformation(this);
			if (newWord != null) {
				transformedWordsList = databank.getWords(newWord.word, newWord.type,
						newWord.rule_no, newWord.rule_variance);
				transformedWordIterator = transformedWordsList.iterator();
				while (transformedWordIterator.hasNext()) {
					isNewWord = false;
					transformedWord = transformedWordIterator.next();
					transformationType = transformation.type;
					if ((rule_no == transformedWord.rule_no) & (transformationType == 1)
							& (rule_variance != transformedWord.rule_variance))
						if (transformedWord.rule_variance == 0) {
							transformedWord.rule_variance = rule_variance;
							databank.updateWord(transformedWord);
						} else if (rule_variance == 0) {
							rule_variance = transformedWord.rule_variance;
							databank.updateWord(this);
						} else {
							transformedWord = databank.getWord(transformedWord.word,
									transformedWord.type, transformedWord.rule_no, rule_variance,
									transformedWord.complex, transformedWord.word1ID,
									transformedWord.word2ID, true);
							isNewWord = true;
						}
					if (!isNewWord) {
						transformationRelation = new WordWordRelation(id, transformedWord.id,
								transformationType, transformation.getId(),
								transformation.getLine());
						databank.saveWordWordRelation(transformationRelation);

						if (rule_no == transformedWord.rule_no)
							this.copyTransformWordforms(transformationRelation);
					}
				}
			}
			// check for backward transformation
			newWord = transformation.backwardTransformation(this);
			if (newWord != null) {
				transformedWordsList = databank.getWords(newWord.word, newWord.type,
						newWord.rule_no, newWord.rule_variance);
				transformedWordIterator = transformedWordsList.iterator();
				while (transformedWordIterator.hasNext()) {
					isNewWord = false;
					transformedWord = transformedWordIterator.next();
					transformationType = transformation.type;
					if ((rule_no == transformedWord.rule_no) & (transformationType == 1)
							& (rule_variance != transformedWord.rule_variance))
						if (transformedWord.rule_variance == 0) {
							transformedWord.rule_variance = rule_variance;
							databank.updateWord(transformedWord);
						} else if (rule_variance == 0) {
							rule_variance = transformedWord.rule_variance;
							databank.updateWord(this);
						} else {
							transformedWord = databank.getWord(transformedWord.word,
									transformedWord.type, transformedWord.rule_no, rule_variance,
									transformedWord.complex, transformedWord.word1ID,
									transformedWord.word2ID, true);
							isNewWord = true;
						}
					if (!isNewWord) {
						transformationRelation = new WordWordRelation(transformedWord.id, id,
								transformationType, transformation.getId(),
								transformation.getLine());
						databank.saveWordWordRelation(transformationRelation);
						if (rule_no == transformedWord.rule_no)
							transformedWord.copyTransformWordforms(transformationRelation);
					}
				}
			}
		}
	}

	public WordForm createWordform(String wordform, EndingRule endingrule, int postfix) {
		Iterator<WordForm> iterator;
		WordForm tempWordform;
		if (wordforms == null)
			wordforms = new HashSet<WordForm>();

		iterator = wordforms.iterator();
		while (iterator.hasNext()) {
			tempWordform = iterator.next();
			if ((tempWordform.getRuleID() == endingrule.rule_id) & (tempWordform.postfix_id == postfix))
				return tempWordform;
		}

		tempWordform = new WordForm(wordform, id, endingrule, postfix);
		wordforms.add(tempWordform);
		databank.saveWordForm(tempWordform);
		if (endingrule != null)
			if (endingrule.isZeroVarience() & (rule_variance > 0))
				for (Integer variance : ruleVariance) {
					Word word = databank.getWord(this.word, type, rule_no, variance, complex,
							word1ID, word2ID, false);
					if (word != null)
						if (word.rule_variance != rule_variance)
							word.createWordform(wordform, endingrule, postfix);
				}
		return tempWordform;
	}

	public WordForm getWordform(int rule_id, int postfix_id) {
		Iterator<WordForm> iterator;
		WordForm tempWordform;

		if (wordforms == null)
			wordforms = new HashSet<WordForm>();

		iterator = wordforms.iterator();
		while (iterator.hasNext()) {
			tempWordform = iterator.next();
			if ((tempWordform.getRuleID() == rule_id) & (tempWordform.postfix_id == postfix_id))
				return tempWordform;
		}
		return null;
	}

	public void updateWordforms() {
		HashSet<WordForm> newWordforms = databank.getWordforms(id);
		Iterator<WordForm> iterator = newWordforms.iterator();
		WordForm tempWordform;
		while (iterator.hasNext()) {
			tempWordform = iterator.next();
			if (getWordform(tempWordform.getRuleID(), tempWordform.postfix_id) == null)
				wordforms.add(tempWordform);
		}
	}

	public void save() {
		if (fixed)
			rating = 100;
		if (id == 0)
			id = databank.getWordID(word, type, rule_no, rule_variance, complex, word1ID, word2ID);
		if (id == 0) {
			databank.saveWord(this);
			checkRuleVariance();
			checkTransormations();
		} else {
			databank.updateWord(this);
			checkTransormations();
		}
	}

	private void checkRuleVariance() {
		if (rule_variance == 0)
			return;
		Word fromWord = databank.getWord(word, type, rule_no, -rule_variance, complex, word1ID,
				word2ID, false);
		if (fromWord == null)
			return;
		for (WordForm fromWordform : fromWord.wordforms) {
			if (fromWordform.endingRule.isZeroVarience())
				createWordform(fromWordform.wordForm, fromWordform.endingRule, fromWordform.postfix_id);
		}
	}

	private void copyTransformWordforms(WordWordRelation transformRelation) {
		copyWordForms(transformRelation);
		databank.getWord(transformRelation.parentWordID).copyWordForms(transformRelation);
	}

	public void copyWordForm(WordWordRelation transformRelation, EndingRule endingRule, int postfix_id) {
		boolean forwardTransform = false;
		boolean backwardTransform = false;
		int rule_id;
		Word toWord;
		Word newWord = null;
		WordForm fromWordform;
		if (endingRule==null)
			rule_id = 0;
		else
			rule_id=endingRule.rule_id;
		int to_word_id = 0;
		if (transformRelation.relationType != 1)
			return;
		if (transformRelation.relationRefID == 0)
			return;

		Transformation transformation = databank.getTransformation(transformRelation.relationRefID,
				transformRelation.relationRefLine);
		if (transformation == null)
			return;

		if (id == transformRelation.parentWordID) {
			to_word_id = transformRelation.wordID;
			backwardTransform = true;
		}

		if (id == transformRelation.wordID) {
			to_word_id = transformRelation.parentWordID;
			forwardTransform = true;
		}

		if (to_word_id == 0)
			return;

		toWord = databank.getWord(to_word_id);
		if (toWord == null)
			return;

		if (toWord.getWordform(rule_id, postfix_id) != null)
			return;

		if (rule_no != toWord.rule_no)
			return;

		if ((rule_variance != toWord.rule_variance) & (toWord.rule_variance != 0))
			return;

		fromWordform = getWordform(rule_id, postfix_id);
		if (forwardTransform)
			newWord = transformation.forwardTransformation(new Word(null, 0, fromWordform.wordForm,
					type, rule_no, rule_variance, false, 0, 0, 0));
		if (backwardTransform)
			newWord = transformation.backwardTransformation(new Word(null, 0,
					fromWordform.wordForm, type, rule_no, rule_variance, false, 0, 0, 0));
		if (newWord != null)
			toWord.createWordform(newWord.word, endingRule, postfix_id);
	}

	public void copyWordForms(WordWordRelation transformRelation) {
		boolean forwardTransform = false;
		boolean backwardTransform = false;
		Word toWord;
		WordForm fromWordform;
		Word newWord = null;

		int to_word_id = 0;
		if (transformRelation.relationType != 1)
			return;
		if (transformRelation.relationRefID == 0)
			return;

		Transformation transformation = databank.getTransformation(transformRelation.relationRefID,
				transformRelation.relationRefLine);
		if (transformation == null)
			return;

		if (id == transformRelation.parentWordID) {
			to_word_id = transformRelation.wordID;
			backwardTransform = true;
		}

		if (id == transformRelation.wordID) {
			to_word_id = transformRelation.parentWordID;
			forwardTransform = true;
		}

		if (to_word_id == 0)
			return;

		toWord = databank.getWord(to_word_id);
		if (toWord == null)
			return;

		if (rule_no != toWord.rule_no)
			return;

		if ((rule_variance != toWord.rule_variance) & (toWord.rule_variance != 0))
			return;

		updateWordforms();
		if (wordforms != null) {
			Iterator<WordForm> iterator = wordforms.iterator();
			while (iterator.hasNext()) {
				fromWordform = iterator.next();
				if (toWord.getWordform(fromWordform.getRuleID(), fromWordform.postfix_id) == null) {
					if (forwardTransform)
						newWord = transformation
								.forwardTransformation(new Word(null, 0, fromWordform.wordForm,
										type, rule_no, rule_variance, false, 0, 0, 0));
					if (backwardTransform)
						newWord = transformation
								.backwardTransformation(new Word(null, 0, fromWordform.wordForm,
										type, rule_no, rule_variance, false, 0, 0, 0));

					if (newWord != null)
						toWord.createWordform(newWord.word, fromWordform.endingRule,
								fromWordform.postfix_id);
				}
			}
		}
	}
}
