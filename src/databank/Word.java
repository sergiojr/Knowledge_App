package databank;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import knowledge_app.EndingRule;
import knowledge_app.WordForm;

public class Word {
	public Word(DataBank databank, int id, String word, int type, int rule_no, boolean complex,
			int word1ID, int word2ID, int rating) {
		this.databank = databank;
		this.id = id;
		this.word = word;
		this.type = type;
		this.rule_no = rule_no;
		this.complex = complex;
		this.word1ID = word1ID;
		this.word2ID = word2ID;
		this.rating = rating;
		this.fixed = (this.rule_no < 0);
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
	boolean complex;
	int word1ID;
	int word2ID;

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
						newWord.rule_no);
				transformedWordIterator = transformedWordsList.iterator();
				while (transformedWordIterator.hasNext()) {
					transformedWord = transformedWordIterator.next();
					transformationType = transformation.type;
					// if (databank.checkSimilarWordforms(id,transformedWord.id))
					// transformationType=0;
					transformationRelation = new WordWordRelation(id, transformedWord.id,
							transformationType, transformation.getId(), transformation.getLine());
					databank.saveWordWordRelation(transformationRelation);
					if (rule_no == transformedWord.rule_no)
						this.copyTransformWordforms(transformationRelation);
				}
			}
			// check for backward transformation
			newWord = transformation.backwardTransformation(this);
			if (newWord != null) {
				transformedWordsList = databank.getWords(newWord.word, newWord.type,
						newWord.rule_no);
				transformedWordIterator = transformedWordsList.iterator();
				while (transformedWordIterator.hasNext()) {
					transformedWord = transformedWordIterator.next();
					transformationType = transformation.type;
					// if (databank.checkSimilarWordforms(id,transformedWord.id))
					// transformationType=0;
					transformationRelation = new WordWordRelation(transformedWord.id, id,
							transformationType, transformation.getId(), transformation.getLine());
					databank.saveWordWordRelation(transformationRelation);
					if (rule_no == transformedWord.rule_no)
						transformedWord.copyTransformWordforms(transformationRelation);
				}
			}
		}
	}

	public WordForm createWordform(String wordform, int rule_id, int postfix) {
		Iterator<WordForm> iterator;
		WordForm tempWordform;
		EndingRule endingrule;

		if (wordforms == null)
			wordforms = new HashSet<WordForm>();

		iterator = wordforms.iterator();
		while (iterator.hasNext()) {
			tempWordform = iterator.next();
			if ((tempWordform.rule == rule_id) & (tempWordform.postfix_id == postfix))
				return tempWordform;
		}

		endingrule = databank.getEndingRule(fixed, rule_id);
		tempWordform = new WordForm(wordform, id, endingrule, postfix);
		wordforms.add(tempWordform);
		databank.saveWordForm(tempWordform);
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
			if ((tempWordform.rule == rule_id) & (tempWordform.postfix_id == postfix_id))
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
			if (getWordform(tempWordform.rule, tempWordform.postfix_id) == null)
				wordforms.add(tempWordform);
		}
	}

	public void save() {
		if (fixed)
			rating = 100;
		if (id == 0)
			id = databank.getWordID(word, type, rule_no, complex, word1ID, word2ID);
		if (id == 0) {
			databank.saveWord(this);
			checkTransormations();
		}
	}

	private void copyTransformWordforms(WordWordRelation transformRelation) {
		copyWordForms(transformRelation);
		databank.getWord(transformRelation.parentWordID).copyWordForms(transformRelation);
	}

	public void copyWordForm(WordWordRelation transformRelation, int rule_id, int postfix_id) {
		boolean forwardTransform = false;
		boolean backwardTransform = false;
		Word toWord;
		Word newWord = null;
		WordForm fromWordform;
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

		fromWordform = getWordform(rule_id, postfix_id);
		if (forwardTransform)
			newWord = transformation.forwardTransformation(new Word(null, 0, fromWordform.wordForm,
					type, rule_no, false, 0, 0, 0));
		if (backwardTransform)
			newWord = transformation.backwardTransformation(new Word(null, 0,
					fromWordform.wordForm, type, rule_no, false, 0, 0, 0));
		if (newWord != null)
			toWord.createWordform(newWord.word, rule_id, postfix_id);
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

		updateWordforms();
		if (wordforms != null) {
			Iterator<WordForm> iterator = wordforms.iterator();
			while (iterator.hasNext()) {
				fromWordform = iterator.next();
				if (toWord.getWordform(fromWordform.rule, fromWordform.postfix_id) == null) {
					if (forwardTransform)
						newWord = transformation.forwardTransformation(new Word(null, 0,
								fromWordform.wordForm, type, rule_no, false, 0, 0, 0));
					if (backwardTransform)
						newWord = transformation.backwardTransformation(new Word(null, 0,
								fromWordform.wordForm, type, rule_no, false, 0, 0, 0));

					if (newWord != null)
						toWord.createWordform(newWord.word, fromWordform.rule,
								fromWordform.postfix_id);
				}
			}
		}
	}
}
