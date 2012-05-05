package databank;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Vocabulary {
	private DataBank databank;
	private HashSet<Word> words;
	private HashMap<Integer, Word> wordsById;
	private HashMap<String, HashSet<Word>> wordsByBase;
	private HashMap<String, HashSet<WordForm>> wordformsByWordformstring;
	private HashSet<Word> delayedSaveWords;
	private HashSet<WordForm> delayedSaveWordforms;
	private HashSet<WordWordRelation> delayedSaveWordWordRelations;
	private int lastWordID;

	public Vocabulary(DataBank databank) {
		this.databank = databank;
		words = this.databank.getWords("", 0);
		wordsById = new HashMap<Integer, Word>();
		wordsByBase = new HashMap<String, HashSet<Word>>();
		for (Word word : words)
			updateWordCache(word);
		HashSet<WordForm> wordforms = this.databank.getWordforms(null);
		for (WordForm wordform : wordforms)
			getWord(wordform.wordID).addWordform(wordform);
		HashSet<WordWordRelation> wordWordRelations = this.databank.getWordWordRelation(0, -1);
		for (WordWordRelation wordRelation : wordWordRelations) {
			getWord(wordRelation.wordID).addWordWordRelation(wordRelation);
			getWord(wordRelation.parentWordID).addWordWordRelation(wordRelation);
		}

		delayedSaveWords = new HashSet<Word>();
		delayedSaveWordforms = new HashSet<WordForm>();
		delayedSaveWordWordRelations = new HashSet<WordWordRelation>();

		lastWordID = databank.getMaxWordID();
	}

	public void saveWord(Word word) {
		if (word.fixed)
			word.rating = 100;
		if (word.id == 0)
			word.id = getWordID(word.word, word.type, word.rule_no, word.rule_variance,
					word.complex, word.word1ID, word.word2ID);
		if (word.id == 0) {
			word.id = ++lastWordID;
			updateWordCache(word);
			checkRuleVariance(word);
			checkTransormations(word);
			if (word.complex) {
				getWord(word.word1ID).addDependentComplexWord(word);
				getWord(word.word2ID).addDependentComplexWord(word);
			}
		}
		delayedSaveWords.add(word);
	}

	public int getWordID(String baseForm, int type, int rule, int rule_variance, boolean complex,
			int word1, int word2) {
		HashSet<Word> wordSet = wordsByBase.get(baseForm);
		if (wordSet != null) {
			Iterator<Word> iterator = wordSet.iterator();
			Word word;
			while (iterator.hasNext()) {
				word = iterator.next();
				if ((word.type == type) & (word.rule_no == rule)
						& ((word.rule_variance == rule_variance) | (word.rule_variance == 0))
						& (word.complex == complex) & (word.word1ID == word1)
						& (word.word2ID == word2))
					return word.id;
			}
		}
		return 0;
	}

	public Word getWord(int id) {
		Word word = wordsById.get(new Integer(id));
		return word;
	}

	public Word getWord(String baseForm, int type, int rule_no, int rule_variance, boolean complex,
			int word1ID, int word2ID, boolean save) {
		// rule_variance <0 - exclude rule_variance
		if ((rule_variance < 0) & (save))
			return null;

		HashSet<Word> wordSet = wordsByBase.get(baseForm);
		wordSet = filterWordSet(type, rule_no, complex, word1ID, word2ID, wordSet);
		for (Word tempWord : wordSet) {
			if (rule_variance >= 0)
				if ((tempWord.rule_variance == rule_variance) | (tempWord.rule_variance == 0)
						| (rule_variance == 0)) {
					if ((rule_variance > 0) & (tempWord.rule_variance == 0) & save) {
						tempWord.rule_variance = rule_variance;
						saveWord(tempWord);
					}
					return tempWord;
				}
			if (rule_variance < 0)
				if (-rule_variance != tempWord.rule_variance)
					return tempWord;
		}

		if (save) {
			Word word = new Word(databank, 0, baseForm, type, rule_no, rule_variance, complex,
					word1ID, word2ID, 0);
			saveWord(word);
			updateWordCache(word);
			return word;
		}
		return null;
	}

	public Word getWord(String baseForm, EndingRule endingRule, boolean complex, int word1ID,
			int word2ID, boolean save) {
		return getWord(baseForm, endingRule.type, endingRule.rule_no, endingRule.rule_variance,
				complex, word1ID, word2ID, save);
	}

	public HashSet<Word> getWords(String baseForm, int type, int rule_no, int rule_variance) {
		HashSet<Word> wordsList = new HashSet<Word>();

		if (baseForm == null)
			return wordsList;

		if (type > 0 && rule_no > 0) {
			Word word = getWord(baseForm, type, rule_no, rule_variance, false, 0, 0, false);
			// если не нашли с текущим rule_variance или нулем, то пытаемся найти с любым другим
			if ((word == null) && (rule_variance > 0))
				word = getWord(baseForm, type, rule_no, 0, false, 0, 0, false);
			if (word != null)
				wordsList.add(word);
			return wordsList;
		}

		HashSet<Word> tempWordsList = wordsByBase.get(baseForm);

		if (tempWordsList != null) {
			if (type == 0 && rule_no == 0) {
				return tempWordsList;
			}

			if (type > 0 | rule_no > 0) {
				for (Word word : tempWordsList)
					if ((word.type == type | type == 0) && (word.rule_no == rule_no | rule_no == 0))
						wordsList.add(word);
			}
		}
		return wordsList;
	}

	private HashSet<Word> filterWordSet(int type, int rule_no, boolean complex, int word1ID,
			int word2ID, HashSet<Word> wordSet) {
		HashSet<Word> result = new HashSet<Word>();
		if (wordSet != null)
			for (Word word : wordSet) {
				if ((word.type == type) && (word.rule_no == rule_no) && (word.complex == complex)
						&& (word.word1ID == word1ID) && (word.word2ID == word2ID))
					result.add(word);
			}
		return result;
	}

	private void updateWordCache(Word word) {
		HashSet<Word> wordSet;
		words.add(word);
		wordsById.put(new Integer(word.id), word);
		wordSet = wordsByBase.get(word.word);
		if (wordSet == null) {
			wordSet = new HashSet<Word>();
			wordSet.add(word);
			wordsByBase.put(word.word, wordSet);
		} else
			wordSet.add(word);
	}

	public WordForm createWordform(Word word, String wordform, EndingRule endingrule, int postfix) {
		int rule_id;
		if (endingrule == null)
			rule_id = 0;
		else
			rule_id = endingrule.rule_id;

		WordForm tempWordform = word.getWordform(rule_id, postfix);

		if (tempWordform == null) {
			tempWordform = new WordForm(wordform, word.id, endingrule, postfix);
			word.addWordform(tempWordform);
			HashSet<WordWordRelation> transformRelations = word.getWordRelations(1);
			for (WordWordRelation transformRelation : transformRelations)
				copyWordForm(word, transformRelation, endingrule, postfix);
			delayedSaveWordforms.add(tempWordform);

			if (endingrule != null)
				if (endingrule.isZeroVarience() & (word.rule_variance > 0))
					for (Integer variance : word.ruleVariance) {
						Word tempWord = getWord(word.word, word.type, word.rule_no, variance,
								word.complex, word.word1ID, word.word2ID, false);
						if (tempWord != null)
							if (word.rule_variance != tempWord.rule_variance)
								createWordform(tempWord, wordform, endingrule, postfix);
					}
		}
		return tempWordform;
	}

	public void putWordformsByWordformstring(String wordform, HashSet<WordForm> wordforms) {
		if (wordformsByWordformstring == null)
			wordformsByWordformstring = new HashMap<String, HashSet<WordForm>>();

		wordformsByWordformstring.put(wordform.intern(), wordforms);
	}

	public HashSet<WordForm> getWordformsByWordformstring(String wordform) {
		if (wordformsByWordformstring == null)
			wordformsByWordformstring = new HashMap<String, HashSet<WordForm>>();

		return wordformsByWordformstring.get(wordform.intern());
	}

	private void checkRuleVariance(Word word) {
		if (word.rule_variance == 0)
			return;
		Word fromWord = getWord(word.word, word.type, word.rule_no, -word.rule_variance,
				word.complex, word.word1ID, word.word2ID, false);
		if (fromWord == null)
			return;
		for (WordForm fromWordform : fromWord.getWordforms()) {
			if (fromWordform.endingRule.isZeroVarience())
				createWordform(word, fromWordform.wordForm, fromWordform.endingRule,
						fromWordform.postfix_id);
		}
	}

	private void checkTransormations(Word word) {
		Set<Transformation> transformations;
		Transformation transformation;
		Word newWord;
		int transformationType;
		boolean isNewWord;

		HashSet<Word> transformedWordsList;
		Iterator<Word> transformedWordIterator;
		Word transformedWord;
		if (word.fixed)
			return;

		if (word.type == 0)
			return;

		WordWordRelation transformationRelation;
		transformations = databank.getTransformations();
		Iterator<Transformation> iterator = transformations.iterator();
		while (iterator.hasNext()) {
			transformation = iterator.next();
			// check for forward transformation
			newWord = transformation.forwardTransformation(word);
			if (newWord != null) {
				transformedWordsList = getWords(newWord.word, newWord.type, newWord.rule_no,
						newWord.rule_variance);
				transformedWordIterator = transformedWordsList.iterator();
				while (transformedWordIterator.hasNext()) {
					isNewWord = false;
					transformedWord = transformedWordIterator.next();
					transformationType = transformation.type;
					if ((word.rule_no == transformedWord.rule_no) & (transformationType == 1)
							& (word.rule_variance != transformedWord.rule_variance))
						if (transformedWord.rule_variance == 0) {
							transformedWord.rule_variance = word.rule_variance;
							saveWord(transformedWord);
						} else if (word.rule_variance == 0) {
							word.rule_variance = transformedWord.rule_variance;
							saveWord(word);
						} else {
							transformedWord = getWord(transformedWord.word, transformedWord.type,
									transformedWord.rule_no, word.rule_variance,
									transformedWord.complex, transformedWord.word1ID,
									transformedWord.word2ID, true);
							isNewWord = true;
						}
					if (!isNewWord) {
						transformationRelation = new WordWordRelation(word.id, transformedWord.id,
								transformationType, transformation.getId(),
								transformation.getLine());
						saveWordWordRelation(word, transformedWord, transformationRelation);

						if (word.rule_no == transformedWord.rule_no)
							copyTransformWordforms(word, transformationRelation);
					}
				}
			}
			// check for backward transformation
			newWord = transformation.backwardTransformation(word);
			if (newWord != null) {
				transformedWordsList = getWords(newWord.word, newWord.type, newWord.rule_no,
						newWord.rule_variance);
				transformedWordIterator = transformedWordsList.iterator();
				while (transformedWordIterator.hasNext()) {
					isNewWord = false;
					transformedWord = transformedWordIterator.next();
					transformationType = transformation.type;
					if ((word.rule_no == transformedWord.rule_no) & (transformationType == 1)
							& (word.rule_variance != transformedWord.rule_variance))
						if (transformedWord.rule_variance == 0) {
							transformedWord.rule_variance = word.rule_variance;
							saveWord(transformedWord);
						} else if (word.rule_variance == 0) {
							word.rule_variance = transformedWord.rule_variance;
							saveWord(word);
						} else {
							transformedWord = getWord(transformedWord.word, transformedWord.type,
									transformedWord.rule_no, word.rule_variance,
									transformedWord.complex, transformedWord.word1ID,
									transformedWord.word2ID, true);
							isNewWord = true;
						}
					if (!isNewWord) {
						transformationRelation = new WordWordRelation(transformedWord.id, word.id,
								transformationType, transformation.getId(),
								transformation.getLine());
						saveWordWordRelation(word, transformedWord, transformationRelation);
						if (word.rule_no == transformedWord.rule_no)
							copyTransformWordforms(transformedWord, transformationRelation);
					}
				}
			}
		}
	}

	private void saveWordWordRelation(Word word, Word transformedWord,
			WordWordRelation transformationRelation) {
		word.addWordWordRelation(transformationRelation);
		transformedWord.addWordWordRelation(transformationRelation);
		delayedSaveWordWordRelations.add(transformationRelation);
	}

	private void copyTransformWordforms(Word sourceWord, WordWordRelation transformRelation) {
		copyWordForms(sourceWord, transformRelation);
		copyWordForms(getWord(transformRelation.parentWordID), transformRelation);
	}

	private void copyWordForm(Word sourceWord, WordWordRelation transformRelation,
			EndingRule endingRule, int postfix_id) {
		boolean forwardTransform = false;
		boolean backwardTransform = false;
		int rule_id;
		Word toWord;
		Word newWord = null;
		WordForm fromWordform;
		if (endingRule == null)
			rule_id = 0;
		else
			rule_id = endingRule.rule_id;
		int to_word_id = 0;
		if (transformRelation.relationType != 1)
			return;
		if (transformRelation.relationRefID == 0)
			return;

		Transformation transformation = databank.getTransformation(transformRelation.relationRefID,
				transformRelation.relationRefLine);
		if (transformation == null)
			return;

		if (sourceWord.id == transformRelation.parentWordID) {
			to_word_id = transformRelation.wordID;
			backwardTransform = true;
		}

		if (sourceWord.id == transformRelation.wordID) {
			to_word_id = transformRelation.parentWordID;
			forwardTransform = true;
		}

		if (to_word_id == 0)
			return;

		toWord = getWord(to_word_id);
		if (toWord == null)
			return;

		if (toWord.getWordform(rule_id, postfix_id) != null)
			return;

		if (sourceWord.rule_no != toWord.rule_no)
			return;

		if ((sourceWord.rule_variance != toWord.rule_variance) & (toWord.rule_variance != 0))
			return;

		fromWordform = sourceWord.getWordform(rule_id, postfix_id);
		if (forwardTransform)
			newWord = transformation.forwardTransformation(new Word(null, 0, fromWordform.wordForm,
					sourceWord.type, sourceWord.rule_no, sourceWord.rule_variance, false, 0, 0, 0));
		if (backwardTransform)
			newWord = transformation.backwardTransformation(new Word(null, 0,
					fromWordform.wordForm, sourceWord.type, sourceWord.rule_no,
					sourceWord.rule_variance, false, 0, 0, 0));
		if (newWord != null)
			createWordform(toWord, newWord.word, endingRule, postfix_id);
	}

	private void copyWordForms(Word sourceWord, WordWordRelation transformRelation) {
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

		if (sourceWord.id == transformRelation.parentWordID) {
			to_word_id = transformRelation.wordID;
			backwardTransform = true;
		}

		if (sourceWord.id == transformRelation.wordID) {
			to_word_id = transformRelation.parentWordID;
			forwardTransform = true;
		}

		if (to_word_id == 0)
			return;

		toWord = getWord(to_word_id);
		if (toWord == null)
			return;

		if (sourceWord.rule_no != toWord.rule_no)
			return;

		if ((sourceWord.rule_variance != toWord.rule_variance) & (toWord.rule_variance != 0))
			return;

		HashSet<WordForm> sourceWordforms = sourceWord.getWordforms();
		if (sourceWordforms != null) {
			Iterator<WordForm> iterator = sourceWordforms.iterator();
			while (iterator.hasNext()) {
				fromWordform = iterator.next();
				if (toWord.getWordform(fromWordform.getRuleID(), fromWordform.postfix_id) == null) {
					if (forwardTransform)
						newWord = transformation.forwardTransformation(new Word(null, 0,
								fromWordform.wordForm, sourceWord.type, sourceWord.rule_no,
								sourceWord.rule_variance, false, 0, 0, 0));
					if (backwardTransform)
						newWord = transformation.backwardTransformation(new Word(null, 0,
								fromWordform.wordForm, sourceWord.type, sourceWord.rule_no,
								sourceWord.rule_variance, false, 0, 0, 0));

					if (newWord != null)
						createWordform(toWord, newWord.word, fromWordform.endingRule,
								fromWordform.postfix_id);
				}
			}
		}
	}

	public void save() {
		//gather Words that have to update rating
		for (WordWordRelation wordRelation : delayedSaveWordWordRelations) {
			delayedSaveWords.add(getWord(wordRelation.wordID));
			delayedSaveWords.add(getWord(wordRelation.parentWordID));
		}
		for (WordForm wordform : delayedSaveWordforms)
			delayedSaveWords.add(getWord(wordform.wordID));
		
		delayedSaveWords.addAll(UpdateWordRating(delayedSaveWords));

		databank.saveWord(delayedSaveWords);
		databank.saveWordWordRelation(delayedSaveWordWordRelations);
		databank.saveWordforms(delayedSaveWordforms);
		delayedSaveWords.clear();
		delayedSaveWordforms.clear();
		delayedSaveWordWordRelations.clear();
	}

	private HashSet<Word> UpdateWordRating(HashSet<Word> wordSet) {
		HashSet<Word> updatedWordSet = new HashSet<Word>();
		if (wordSet.isEmpty())
			return updatedWordSet;
		int wordDiversity = 0;
		int ruleDiversity;
		int newrating = 0;
		boolean isChanged = false;
		HashSet<Word> dependentWordSet = new HashSet<Word>();
		Iterator<Word> iterator = wordSet.iterator();
		Word word;
		while (iterator.hasNext()) {
			newrating = 0;
			isChanged = false;
			word = iterator.next();
			if (word.complex) {
				Word word1;
				Word word2;
				word1 = getWord(word.word1ID);
				word2 = getWord(word.word2ID);
				newrating = Math.round((float) Math.sqrt(word1.rating * word2.rating));
				if (newrating != word.rating)
					isChanged = true;
				word.rating = newrating;
			}
			if (!word.complex) {
				wordDiversity = word.getWordRelationDiversity() + word.getEndingDiversity();
				if (wordDiversity > 0) {
					ruleDiversity = databank.getRuleDiversity(word.rule_no);
					if (wordDiversity > ruleDiversity)
						wordDiversity = ruleDiversity;
					if (word.rule_no > 0)
						newrating = Math.round(100.0f * wordDiversity / ruleDiversity);
					if (word.rule_no < 0)
						newrating = 100;
					if (newrating != word.rating)
						isChanged = true;

					word.rating = newrating;
				}
			}
			if (isChanged) {
				if (word.rating != 0)
					updatedWordSet.add(word);
				dependentWordSet.addAll(word.getDependentComplexWords());
			}
		}

		if (!dependentWordSet.isEmpty())
			updatedWordSet.addAll(UpdateWordRating(dependentWordSet));
		
		return updatedWordSet;
	}

}
