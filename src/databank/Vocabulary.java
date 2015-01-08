package databank;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class Vocabulary {
	private DataBank databank;
	private ArrayList<Word> words;
	private HashMap<String, ArrayList<Word>> wordsByBase;
	private HashMap<String, ArrayList<WordForm>> wordformsByWordformstring;
	private HashSet<Word> delayedSaveWords;
	private HashSet<WordForm> delayedSaveWordforms;
	private HashSet<WordWordRelation> delayedSaveWordWordRelations;
	private int lastWordID;
	private HashMap<String, ArrayList<EndingRule>> endingRulesByEnding;
	private HashMap<String, ArrayList<WordForm>> fixedWordformsByWordformstring;
	private ArrayList<String> fixedOnlyForms;
	private ArrayList<String> sentenceSeparatorList;
	private ArrayList<Transformation> transformations;
	private ArrayList<ArrayList<Transformation>> transformationsById;

	public Vocabulary(DataBank databank) {
		this.databank = databank;
		words = new ArrayList<Word>();
		wordsByBase = new HashMap<String, ArrayList<Word>>();

		for (Word word : this.databank.getWords("", 0))
			updateWordCache(word);

		for (WordForm wordform : this.databank.getWordforms(null))
			getWord(wordform.wordID).addWordform(wordform);
		
		for (WordWordRelation wordRelation : this.databank.getWordWordRelation(0, -1)) {
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
		ArrayList<Word> wordSet = wordsByBase.get(baseForm);
		if (wordSet != null) {
			for (Word word : wordSet)
				if ((word.type == type) & (word.rule_no == rule)
						& ((word.rule_variance == rule_variance) | (word.rule_variance == 0))
						& (word.complex == complex) & (word.word1ID == word1)
						& (word.word2ID == word2))
					return word.id;
		}
		return 0;
	}

	public Word getWord(int id) {
		if (id > words.size() - 1)
			return null;

		return words.get(id);
	}

	public Word getWord(String baseForm, int type, int rule_no, int rule_variance, boolean complex,
			int word1ID, int word2ID, boolean save) {
		// rule_variance <0 - exclude rule_variance
		if ((rule_variance < 0) & (save))
			return null;

		ArrayList<Word> wordSet = wordsByBase.get(baseForm);
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

	public ArrayList<Word> getWords(String baseForm, int type, int rule_no, int rule_variance) {
		ArrayList<Word> wordsList = new ArrayList<Word>();

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

		ArrayList<Word> tempWordsList = wordsByBase.get(baseForm);

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

	private ArrayList<Word> filterWordSet(int type, int rule_no, boolean complex, int word1ID,
			int word2ID, ArrayList<Word> wordSet) {
		ArrayList<Word> result = new ArrayList<Word>();
		if (wordSet != null)
			for (Word word : wordSet) {
				if ((word.type == type) && (word.rule_no == rule_no) && (word.complex == complex)
						&& (word.word1ID == word1ID) && (word.word2ID == word2ID))
					result.add(word);
			}
		return result;
	}

	private void updateWordCache(Word word) {
		while (word.id > words.size() - 1)
			words.add(null);
		words.set(word.id, word);

		ArrayList<Word> wordSet = wordsByBase.get(word.word);
		if (wordSet == null) {
			wordSet = new ArrayList<Word>();
			wordSet.add(word);
			wordsByBase.put(word.word, wordSet);
		} else if (!wordSet.contains(word))
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
			tempWordform = new WordForm(wordform.intern(), word.id, endingrule, postfix);
			word.addWordform(tempWordform);
			ArrayList<WordWordRelation> transformRelations = word.getWordRelations(1);
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
								createWordform(tempWord, wordform.intern(), endingrule, postfix);
					}
		}
		return tempWordform;
	}

	public void putWordformsByWordformstring(String wordform, ArrayList<WordForm> wordforms) {
		if (wordformsByWordformstring == null)
			wordformsByWordformstring = new HashMap<String, ArrayList<WordForm>>();

		wordformsByWordformstring.put(wordform.intern(), wordforms);
	}

	public ArrayList<WordForm> getWordformsByWordformstring(String wordform) {
		if (wordformsByWordformstring == null)
			wordformsByWordformstring = new HashMap<String, ArrayList<WordForm>>();

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
		ArrayList<Transformation> transformations;
		Transformation transformation;
		Word newWord;
		int transformationType;
		boolean isNewWord;

		ArrayList<Word> transformedWordsList;
		Iterator<Word> transformedWordIterator;
		Word transformedWord;
		if (word.fixed)
			return;

		if (word.type == 0)
			return;

		WordWordRelation transformationRelation;
		transformations = getTransformations();
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

		Transformation transformation = getTransformation(transformRelation.relationRefID,
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
		Word newWord = null;

		int to_word_id = 0;
		if (transformRelation.relationType != 1)
			return;
		if (transformRelation.relationRefID == 0)
			return;

		Transformation transformation = getTransformation(transformRelation.relationRefID,
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

		ArrayList<WordForm> sourceWordforms = sourceWord.getWordforms();
		if (sourceWordforms != null) {
			for (WordForm fromWordform : sourceWordforms) {
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
		// gather Words that have to update rating
		for (WordWordRelation wordRelation : delayedSaveWordWordRelations) {
			delayedSaveWords.add(getWord(wordRelation.wordID));
			delayedSaveWords.add(getWord(wordRelation.parentWordID));
		}
		for (WordForm wordform : delayedSaveWordforms)
			delayedSaveWords.add(getWord(wordform.wordID));

		delayedSaveWords.addAll(UpdateWordRating(updateWordformRelationIndex()));

		delayedSaveWords.addAll(UpdateWordRating(delayedSaveWords));

		// System.out.print("SaveWord...");
		databank.saveWord(delayedSaveWords);
		databank.saveWordWordRelation(delayedSaveWordWordRelations);
		// System.out.print("SaveWordform...");
		databank.saveWordforms(delayedSaveWordforms);
		delayedSaveWords.clear();
		delayedSaveWordforms.clear();
		delayedSaveWordWordRelations.clear();
	}

	private HashSet<Word> UpdateWordRating(HashSet<Word> wordSet) {
		HashSet<Word> updatedWordSet = new HashSet<Word>();
		if (wordSet.isEmpty())
			return updatedWordSet;
		float wordDiversity = 0;
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
				wordDiversity = word.getRelationIndex() + word.getWordRelationDiversity()
						+ word.getEndingDiversity();
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
				
				if (word.getDependentComplexWords()!=null)
					dependentWordSet.addAll(word.getDependentComplexWords());
			}
		}

		if (!dependentWordSet.isEmpty())
			updatedWordSet.addAll(UpdateWordRating(dependentWordSet));

		return updatedWordSet;
	}

	private HashSet<Word> updateWordformRelationIndex() {
		HashSet<Word> updatedWords = new HashSet<Word>();
		HashMap<String, ArrayList<EndingRuleStat>> wordformRelationStats = databank
				.getWordformRelationStats();
		boolean found;

		for (Word word : words)
			if (word != null)
				for (WordForm wordform : word.getWordforms()) {
					ArrayList<EndingRuleStat> endingRuleStats = wordformRelationStats
							.get(wordform.wordForm);
					if ((endingRuleStats != null) && !endingRuleStats.isEmpty()) {
						int totalCount = 0;
						for (EndingRuleStat endingRuleStat : endingRuleStats)
							totalCount += endingRuleStat.index;
						found = false;
						EndingRule endingrule = wordform.endingRule;
						for (EndingRuleStat endingRuleStat : endingRuleStats)
							if ((!found) && (endingrule.type == endingRuleStat.type)
									&& (endingrule.wcase == endingRuleStat.wcase)
									&& (endingrule.gender == endingRuleStat.gender)
									&& (endingrule.sing_pl == endingRuleStat.sing_pl)) {
								wordform.setRelationIndex(((float) endingRuleStat.index)
										/ totalCount);
								updatedWords.add(word);
								found = true;
							}

						if (!found) {
							// wordform.setRelationIndex(0);
							updatedWords.add(word);
						}
					} else {
						// wordform.setRelationIndex(0);
						updatedWords.add(word);
					}
				}
		return updatedWords;
	}

	public Set<EndingRule> getEndingRules(String ending, Postfix postfix, int complexWordIndex,
			ComplexWordTemplate complexWordTemplate) throws SQLException {
		if (endingRulesByEnding == null)
			endingRulesByEnding = new HashMap<String, ArrayList<EndingRule>>();

		ending = ending.intern();

		boolean isMatch;

		if (!endingRulesByEnding.containsKey(ending))
			endingRulesByEnding.put(ending, databank.getEndingRules(ending, null, 0, null));

		HashSet<EndingRule> matchedEndingRules = new HashSet<EndingRule>();

		for (EndingRule endingRule : endingRulesByEnding.get(ending)) {
			isMatch = true;
			if (postfix != null) {
				if (postfix.type > 0)
					isMatch = isMatch && (endingRule.type == postfix.type);
				if (postfix.tense > 0)
					isMatch = isMatch && (endingRule.tense == postfix.tense);
				if (postfix.rule_no > 0)
					isMatch = isMatch && (endingRule.rule_no == postfix.rule_no);
			}

			if (isMatch && (complexWordIndex == 1) && (complexWordTemplate != null)) {
				if (complexWordTemplate.word1_type > 0)
					isMatch = isMatch && (endingRule.type == complexWordTemplate.word1_type);
				if (complexWordTemplate.word1_subtype > 0)
					isMatch = isMatch && (endingRule.subtype == complexWordTemplate.word1_subtype);
				if (complexWordTemplate.word1_wcase > 0)
					isMatch = isMatch && (endingRule.wcase == complexWordTemplate.word1_wcase);
				if (complexWordTemplate.word1_sing_pl > 0)
					isMatch = isMatch && (endingRule.sing_pl == complexWordTemplate.word1_sing_pl);
			}

			if (isMatch && (complexWordIndex == 2) && (complexWordTemplate != null)) {
				if (complexWordTemplate.word2_type > 0)
					isMatch = isMatch && (endingRule.type == complexWordTemplate.word2_type);
				if (complexWordTemplate.word2_subtype > 0)
					isMatch = isMatch && (endingRule.subtype == complexWordTemplate.word2_subtype);
				if (complexWordTemplate.word2_wcase > 0)
					isMatch = isMatch && (endingRule.wcase == complexWordTemplate.word2_wcase);
				if (complexWordTemplate.word2_sing_pl > 0)
					isMatch = isMatch && (endingRule.sing_pl == complexWordTemplate.word2_sing_pl);
			}
			if (isMatch)
				matchedEndingRules.add(endingRule);
		}

		return matchedEndingRules;
	}

	public ArrayList<WordForm> getFixedWordForms(String wordformstring, Postfix postfix)
			throws SQLException {
		boolean isMatch;
		Word word;

		if (fixedWordformsByWordformstring == null)
			fixedWordformsByWordformstring = new HashMap<String, ArrayList<WordForm>>();

		wordformstring = wordformstring.intern();
		if (fixedWordformsByWordformstring.get(wordformstring) == null)
			fixedWordformsByWordformstring.put(wordformstring,
					databank.getFixedWordForms(this, wordformstring, databank.getPostfix(0)));

		ArrayList<WordForm> wordforms = new ArrayList<WordForm>();

		for (WordForm wordform : fixedWordformsByWordformstring.get(wordformstring)) {
			word = getWord(wordform.wordID);
			isMatch = true;

			if (postfix != null) {
				if (postfix.type > 0)
					isMatch = isMatch && (word.type == postfix.type);
				// if (postfix.tense > 0)
				// query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new
				// CustomSql(
				// "tense"), postfix.tense));
			}

			if (isMatch)
				wordforms.add(createWordform(word, wordformstring + postfix.postfix,
						wordform.endingRule, postfix.id));
		}
		return wordforms;
	}

	public boolean isOnlyFixedForm(String lcWord) {
		if (fixedOnlyForms == null)
			fixedOnlyForms = databank.getFixedOnlyForms();

		return fixedOnlyForms.contains(lcWord.intern());
	}
	
	public ArrayList<String> getSentenceSeparators() {
		if (sentenceSeparatorList == null)
			sentenceSeparatorList = databank.getFixedSentenceSeparators();

		return sentenceSeparatorList;
	}
	
	public boolean isSeparator(String word) {
		return getSentenceSeparators().contains(word.intern());
	}

	private ArrayList<Transformation> getTransformations() {
		ArrayList<Transformation> tempTransformations;
		if (transformations == null) {
			transformations = databank.getTransformations();
			transformationsById = new ArrayList<ArrayList<Transformation>>();
			for (Transformation transformation : transformations) {
				while (transformationsById.size() - 1 < transformation.id)
					transformationsById.add(null);

				tempTransformations = transformationsById.get(transformation.id);

				if (tempTransformations == null)
					tempTransformations = new ArrayList<Transformation>();
				tempTransformations.add(transformation);
				transformationsById.set(transformation.id, tempTransformations);
			}
		}
		return transformations;
	}

	private Transformation getTransformation(int relationRefID, int relationRefLine) {
		if (transformations == null)
			getTransformations();

		for (Transformation transformation : transformationsById.get(relationRefID)) {
			if (transformation.line == relationRefLine)
				return transformation;
		}
		return null;
	}
}
