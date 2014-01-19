package databank;

import java.util.HashMap;
import java.util.HashSet;

public class SentenceWordRelationGraph {

	private HashSet<SentenceWordRelation> sentenceWordRelationSet;
	private int sourceID;
	private int sentenceID;
	private int maxWordPos;
	private int relationCount;

	public SentenceWordRelationGraph(int newSourceID, int newSentenceID, int newMaxWordPos) {
		sourceID = newSourceID;
		sentenceID = newSentenceID;
		maxWordPos = newMaxWordPos;
		sentenceWordRelationSet = new HashSet<SentenceWordRelation>();
		relationCount = 0;
	}

	public boolean add(SentenceWordRelation wordRelation) {
		if (wordRelation.status == 0) {
			if ((wordRelation.relationType == SentenceWordRelation.preposition)
					&& (wordRelation.word1Pos < wordRelation.word2Pos))
				return false;

			wordRelation.status = 1;
		}

		if (existWordRelation(wordRelation))
			return false;

		if (wordRelation.id == 0)
			wordRelation.id = ++relationCount;

		sentenceWordRelationSet.add(wordRelation);
		return true;
	}

	public void addAll(SentenceWordRelationGraph curWordRelationGraph) {
		HashMap<Integer, Integer> idMapping = new HashMap<Integer, Integer>();
		for (SentenceWordRelation wordRelation : curWordRelationGraph.getSet()) {
			if (wordRelation.id != 0) {
				if (idMapping.containsKey(wordRelation.id))
					wordRelation.id = idMapping.get(wordRelation.id);
				else {
					idMapping.put(wordRelation.id, ++relationCount);
					wordRelation.id = relationCount;
				}
			}

			if (wordRelation.depID != 0) {
				if (idMapping.containsKey(wordRelation.depID))
					wordRelation.depID = idMapping.get(wordRelation.depID);
				else {
					idMapping.put(wordRelation.depID, ++relationCount);
					wordRelation.depID = relationCount;
				}
			}

			sentenceWordRelationSet.add(wordRelation);
		}
	}

	public void remove(SentenceWordRelation wordRelation) {
		sentenceWordRelationSet.remove(wordRelation);
	}

	public HashSet<SentenceWordRelation> getSet() {
		return sentenceWordRelationSet;
	}

	public SentenceWordFilter[] generateSentenceWordFilter() {
		SentenceWordFilter[] sentenceWordFilter = new SentenceWordFilter[maxWordPos + 1];
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet) {
			if (sentenceWordFilter[wordRelation.word1Pos] == null)
				sentenceWordFilter[wordRelation.word1Pos] = new SentenceWordFilter(sentenceID,
						wordRelation.word1Pos);
			sentenceWordFilter[wordRelation.word1Pos].addValuestoFilters(wordRelation, 1);
			if (wordRelation.word2Pos != 0) {
				if (sentenceWordFilter[wordRelation.word2Pos] == null)
					sentenceWordFilter[wordRelation.word2Pos] = new SentenceWordFilter(sentenceID,
							wordRelation.word2Pos);
				sentenceWordFilter[wordRelation.word2Pos].addValuestoFilters(wordRelation, 2);
			}
		}
		return sentenceWordFilter;
	}

	public boolean existWordRelation(SentenceWordRelation wordRelation) {
		for (SentenceWordRelation curWordRelation : sentenceWordRelationSet) {
			// if exist wordRelation where Word1 is related to different Word2 and has the same
			// relationType and dependent Relation
			if ((curWordRelation.sourceID == wordRelation.sourceID)
					&& (curWordRelation.sentenceID == wordRelation.sentenceID)
					&& (curWordRelation.word1Pos == wordRelation.word1Pos)
					&& (curWordRelation.word1Case == wordRelation.word1Case)
					&& (curWordRelation.word1Gender == wordRelation.word1Gender)
					&& (curWordRelation.word1Sing_Pl == wordRelation.word1Sing_Pl)
					&& (curWordRelation.word1Animate == wordRelation.word1Animate)
					&& (curWordRelation.word2Pos != wordRelation.word2Pos)
					&& (curWordRelation.relationType == wordRelation.relationType)
					&& (curWordRelation.depID == wordRelation.depID))
				return true;
			
			// Word1 can have only one preposition relation
			if ((curWordRelation.sourceID == wordRelation.sourceID)
					&& (curWordRelation.sentenceID == wordRelation.sentenceID)
					&& (curWordRelation.word1Pos == wordRelation.word1Pos)
					&& (curWordRelation.word1Case == wordRelation.word1Case)
					&& (curWordRelation.word1Gender == wordRelation.word1Gender)
					&& (curWordRelation.word1Sing_Pl == wordRelation.word1Sing_Pl)
					&& (curWordRelation.word1Animate == wordRelation.word1Animate)
					&& (curWordRelation.word2Pos != wordRelation.word2Pos)
					&& (curWordRelation.relationType == wordRelation.relationType)
					&& (curWordRelation.relationType == SentenceWordRelation.preposition))
				return true;

			// if exist wordRelation where Word1 is dependent to different word and has the same
			// relationType and dependent Relation
			if ((curWordRelation.status == 2)
					&& (curWordRelation.sourceID == wordRelation.sourceID)
					&& (curWordRelation.sentenceID == wordRelation.sentenceID)
					&& (curWordRelation.word2Pos == wordRelation.word1Pos)
					&& (curWordRelation.relationType == wordRelation.relationType)
					&& (curWordRelation.depID == wordRelation.depID))
				return true;

			// if exist wordRelation where Word2 is related to different Word1
			if ((curWordRelation.status == 2)
					&& (curWordRelation.sourceID == wordRelation.sourceID)
					&& (curWordRelation.sentenceID == wordRelation.sentenceID)
					&& (curWordRelation.word2Pos == wordRelation.word2Pos)
					&& (curWordRelation.word1Pos != wordRelation.word1Pos)
					&& ((curWordRelation.relationType != SentenceWordRelation.preposition) | (curWordRelation.relationType != wordRelation.relationType)))
				return true;

			// Break cycles:if exist wordRelation in WordRelation dependency chain with the same
			// Word1 and Word2
			if ((curWordRelation.sourceID == wordRelation.sourceID)
					&& (curWordRelation.sentenceID == wordRelation.sentenceID)
					&& (curWordRelation.word1Pos == wordRelation.word1Pos)
					&& (curWordRelation.word2Pos == wordRelation.word2Pos)
					&& (isDependentRelation(curWordRelation, wordRelation.depID)))
				return true;

			// if exist wordRelation with the same Word1 and Word2
			if ((curWordRelation.sourceID == wordRelation.sourceID)
					&& (curWordRelation.sentenceID == wordRelation.sentenceID)
					&& (curWordRelation.relationType == wordRelation.relationType)
					&& (curWordRelation.word1Pos == wordRelation.word1Pos)
					&& (curWordRelation.word1Type == wordRelation.word1Type)
					&& (curWordRelation.word1Case == wordRelation.word1Case)
					&& (curWordRelation.word1Gender == wordRelation.word1Gender)
					&& (curWordRelation.word1Sing_Pl == wordRelation.word1Sing_Pl)
					&& (curWordRelation.word1Animate == wordRelation.word1Animate)
					&& (curWordRelation.word2Pos == wordRelation.word2Pos)
					&& (curWordRelation.word2Type == wordRelation.word2Type)
					&& (curWordRelation.word2Case == wordRelation.word2Case)
					&& (curWordRelation.word2Gender == wordRelation.word2Gender)
					&& (curWordRelation.word2Sing_Pl == wordRelation.word2Sing_Pl)
					&& (curWordRelation.word2Animate == wordRelation.word2Animate)
					&& ((curWordRelation.depID == 0) | (wordRelation.depID == 0) | (curWordRelation.depID == wordRelation.depID)))
				return true;
		}
		return false;
	}

	/**
	 * Delete chains of dependent words, that are not the longest for each word position
	 * 
	 * @param sentenceWordRelationSet
	 * @param relationType
	 */
	public void cleanWordRelationList(int relationType) {
		HashSet<SentenceWordRelation> removeWordRelationList = new HashSet<SentenceWordRelation>();
		HashSet<SentenceWordRelation> chainRelationList;
		int[] maxChainLengths = new int[maxWordPos + 1];
		int curLength;
		// find max chain length for each word position
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if ((wordRelation.depID == 0) && (wordRelation.relationType == relationType)) {
				// curLength = calcDependencyLength(wordRelationList, wordRelation);
				chainRelationList = getDependencyChain(wordRelation);
				curLength = chainRelationList.size();
				if (curLength > maxChainLengths[wordRelation.word1Pos]) {
					// mark all chain members with max length
					maxChainLengths[wordRelation.word1Pos] = curLength;
					for (SentenceWordRelation curWordRelation : chainRelationList)
						if (curLength > maxChainLengths[curWordRelation.word2Pos])
							maxChainLengths[curWordRelation.word2Pos] = curLength;
				}
			}

		// for each word position find chains that are below maximum length
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if ((wordRelation.depID == 0) && (wordRelation.relationType == relationType)) {
				if (calcDependencyLength(sentenceWordRelationSet, wordRelation) < maxChainLengths[wordRelation.word1Pos])
					removeWordRelationList.add(wordRelation);
			}

		for (SentenceWordRelation removeWordRelation : removeWordRelationList)
			removeWordRelation(removeWordRelation);
	}

	public SentenceWordRelation getLastWordRelation(SentenceWordRelation wordRelation) {
		SentenceWordRelation result = wordRelation;
		if (wordRelation.id == 0)
			return null;
		boolean found = true;
		while (found) {
			found = false;
			for (SentenceWordRelation curWordRelation : sentenceWordRelationSet)
				if (result.id == curWordRelation.depID) {
					found = true;
					result = curWordRelation;
				}
		}
		return result;
	}

	public SentenceWordRelation getFirstWordRelation(SentenceWordRelation wordRelation) {
		SentenceWordRelation result = wordRelation;

		boolean found = true;
		while (found && result.depID != 0) {
			found = false;
			for (SentenceWordRelation curWordRelation : sentenceWordRelationSet)
				if (result.depID == curWordRelation.id) {
					found = true;
					result = curWordRelation;
				}
		}
		return result;
	}

	public void movePrepositionRelations(SentenceWordRelationGraph curWordRelations) {
		// Preposition relation was originally created for every possible wordform that is on the
		// next position in sentence. So there are several preposition relations for different
		// properties for type, wcase, gender, sing_pl. On the other hand attribute relations can
		// impose stricter rules. So we search for attribute relation for word that has preposition
		// relation and if we find any we delete preposition relations that doesn't have similar
		// properties.

		int relationType = SentenceWordRelation.preposition;
		boolean foundPos;
		boolean foundMatch;
		HashSet<SentenceWordRelation> prepositionRelations = new HashSet<SentenceWordRelation>();
		HashSet<SentenceWordRelation> newWordRelations = new HashSet<SentenceWordRelation>();
		HashSet<SentenceWordRelation> removeWordRelations = new HashSet<SentenceWordRelation>();
		SentenceWordRelation mainAttributeWordRelation;
		SentenceWordRelation dependentWordRelation;

		for (SentenceWordRelation prepWordRelation : sentenceWordRelationSet)
			if (prepWordRelation.relationType == relationType)
				prepositionRelations.add(prepWordRelation);

		for (SentenceWordRelation curWordRelation : curWordRelations.getSet())
			if (curWordRelation.status == 1)
				newWordRelations.add(curWordRelation);

		for (SentenceWordRelation prepWordRelation : prepositionRelations) {
			foundPos = false;
			foundMatch = false;
			for (SentenceWordRelation curWordRelation : newWordRelations)
				if ((curWordRelation.sourceID == prepWordRelation.sourceID)
						&& (curWordRelation.sentenceID == prepWordRelation.sentenceID)
						&& (curWordRelation.word2Pos == prepWordRelation.word1Pos)) {
					foundPos = true;
					if ((curWordRelation.word2Case == prepWordRelation.word1Case)
							&& (curWordRelation.word2Type == prepWordRelation.word1Type)
							// && (attributeWordRelation.word2Gender ==
							// prepWordRelation.word1Gender)
							&& (curWordRelation.word2Sing_Pl == prepWordRelation.word1Sing_Pl)) {
						foundMatch = true;
						// If we found match we need to propogate preposition relations along
						// the attribute relation chain
						mainAttributeWordRelation = curWordRelations
								.getFirstWordRelation(curWordRelation);
						// create preposition relation for main Substantive
						if (mainAttributeWordRelation.word1Case > 0) {
							dependentWordRelation = new SentenceWordRelation(0,
									mainAttributeWordRelation.id,
									mainAttributeWordRelation.sourceID,
									mainAttributeWordRelation.sentenceID,
									mainAttributeWordRelation.word1Pos,
									mainAttributeWordRelation.word1Type,
									mainAttributeWordRelation.word1Case,
									mainAttributeWordRelation.word1Gender,
									mainAttributeWordRelation.word1Sing_Pl,
									mainAttributeWordRelation.word1Animate,
									prepWordRelation.word2Pos, prepWordRelation.word2Type,
									prepWordRelation.word2Case, prepWordRelation.word2Gender,
									prepWordRelation.word2Sing_Pl, prepWordRelation.word2Animate,
									relationType);
							add(dependentWordRelation);
						}
						// create preposition relation for each part of dependency chain
						for (SentenceWordRelation dependentAttributeWordRelation : curWordRelations
								.getDependencyChain(mainAttributeWordRelation)) {
							if (dependentAttributeWordRelation.word2Case > 0) {
								dependentWordRelation = new SentenceWordRelation(0,
										dependentAttributeWordRelation.id,
										dependentAttributeWordRelation.sourceID,
										dependentAttributeWordRelation.sentenceID,
										dependentAttributeWordRelation.word2Pos,
										dependentAttributeWordRelation.word2Type,
										dependentAttributeWordRelation.word2Case,
										dependentAttributeWordRelation.word2Gender,
										dependentAttributeWordRelation.word2Sing_Pl,
										dependentAttributeWordRelation.word2Animate,
										prepWordRelation.word2Pos, prepWordRelation.word2Type,
										prepWordRelation.word2Case, prepWordRelation.word2Gender,
										prepWordRelation.word2Sing_Pl,
										prepWordRelation.word2Animate, relationType);

								add(dependentWordRelation);
							}
						}
					}
				}
			if (foundPos && !foundMatch)
				removeWordRelations.add(prepWordRelation);
		}

		for (SentenceWordRelation removeWordRelation : removeWordRelations)
			sentenceWordRelationSet.remove(removeWordRelation);
	}

	public void changeWordRelationStatus(int relationType) {
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if ((wordRelation.status == 1) /* && (wordRelation.relationType == relationType) */)
				wordRelation.status = 2;
	}

	public boolean existDependence(int wordPos, int relationType) {
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if ((wordRelation.sourceID == sourceID) && (wordRelation.sentenceID == sentenceID)
					&& (wordRelation.word1Pos == wordPos)
					&& (wordRelation.word2Pos>0)
					&& (wordRelation.relationType == relationType))
				return true;
		return false;
	}

	public int getPrepositionWordPos(int wordPos) {
		int relationType = SentenceWordRelation.preposition;
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if ((wordRelation.sourceID == sourceID) && (wordRelation.sentenceID == sentenceID)
					&& (wordRelation.word1Pos == wordPos)
					&& (wordRelation.relationType == relationType))
				return wordRelation.word2Pos;
		return 0;
	}

	public SentenceWordRelation getMainVerbRelation(int wordPos) {
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if ((wordRelation.sourceID == sourceID) && (wordRelation.sentenceID == sentenceID)
					&& (wordRelation.word2Pos == wordPos)
					&& (wordRelation.relationType == SentenceWordRelation.verbInfinitive)) {
				SentenceWordRelation tempWordRelation = getMainVerbRelation(wordRelation.word1Pos);
				if (tempWordRelation == null)
					return wordRelation;
				else
					return tempWordRelation;
			}
		return null;
	}

	private boolean existIndirectDependence(int word1Pos, int word2Pos) {
		SentenceWordRelation wordRelation;
		int depWordPos = word2Pos;
		do {
			wordRelation = getDependentWordRelation(depWordPos);
			if (wordRelation == null)
				return false;
			else if (wordRelation.word1Pos == word1Pos)
				return true;
			depWordPos = wordRelation.word1Pos;
		} while (wordRelation != null);
		return false;
	}

	private SentenceWordRelation getDependentWordRelation(int wordPos) {
		for (SentenceWordRelation wordRelation : sentenceWordRelationSet)
			if (wordRelation.word2Pos == wordPos)
				return wordRelation;
		return null;
	}

	public int getPrevIndependentWordPos(int wordPos) {
		SentenceWordRelation depWordRelation;
		int curWordPos = wordPos - 1;
		while (curWordPos > 0) {
			depWordRelation = getDependentWordRelation(curWordPos);
			if (depWordRelation == null)
				return curWordPos;
			else if ((depWordRelation.relationType == SentenceWordRelation.conjunction)
					&& existIndirectDependence(depWordRelation.word1Pos, wordPos))
				return -1;
			curWordPos--;
		}
		return -1;
	}

	public int getNextIndependentWordPos(int wordPos) {
		SentenceWordRelation depWordRelation;
		int curWordPos = wordPos + 1;
		while (curWordPos <= maxWordPos) {
			depWordRelation = getDependentWordRelation(curWordPos);
			if (depWordRelation == null)
				return curWordPos;
			else if ((depWordRelation.relationType == SentenceWordRelation.conjunction)
					&& existIndirectDependence(depWordRelation.word1Pos, wordPos))
				return -1;
			curWordPos++;
		}
		return -1;
	}

	/**
	 * Remove word relation and all dependent word relations from list
	 * 
	 * @param sentenceWordRelationSet
	 * @param wordRelation
	 */
	private void removeWordRelation(SentenceWordRelation wordRelation) {
		for (SentenceWordRelation curWordRelation : getDependencyChain(wordRelation))
			sentenceWordRelationSet.remove(curWordRelation);
	}

	private HashSet<SentenceWordRelation> getDependencyChain(SentenceWordRelation wordRelation) {
		HashSet<SentenceWordRelation> result = new HashSet<SentenceWordRelation>();
		if (wordRelation.id == 0)
			return result;

		HashSet<SentenceWordRelation> foundWordRelations = new HashSet<SentenceWordRelation>();
		HashSet<SentenceWordRelation> curFoundWordRelations;
		foundWordRelations.add(wordRelation);
		while (!foundWordRelations.isEmpty()) {
			result.addAll(foundWordRelations);
			curFoundWordRelations = new HashSet<SentenceWordRelation>();
			for (SentenceWordRelation foundWordRelation : foundWordRelations)
				for (SentenceWordRelation curWordRelation : sentenceWordRelationSet)
					if (foundWordRelation.id == curWordRelation.depID)
						curFoundWordRelations.add(curWordRelation);

			foundWordRelations = curFoundWordRelations;
		}
		return result;
	}

	private int calcDependencyLength(HashSet<SentenceWordRelation> wordRelationList,
			SentenceWordRelation wordRelation) {
		return getDependencyChain(wordRelation).size();
	}

	/**
	 * Check if depID has wordRelation as indirect parent
	 * 
	 * @param wordRelation
	 *            - parent SentenceWordRelation to check
	 * @param depID
	 *            - id of dependent SentenceWordRelation
	 * @return "true" if there is chain of relations between depID and wordRelation
	 */
	private boolean isDependentRelation(SentenceWordRelation wordRelation, int depID) {
		if (depID == 0 | wordRelation.id == 0)
			return false;
		int curDepID = depID;
		boolean found = true;
		while (found) {
			if (curDepID == wordRelation.id)
				return true;
			found = false;
			for (SentenceWordRelation curWordRelation : sentenceWordRelationSet)
				if ((!found) && (curWordRelation.id == curDepID)) {
					found = true;
					curDepID = curWordRelation.depID;
					if (curDepID == 0)
						return false;
				}
		}
		return false;
	}
}
