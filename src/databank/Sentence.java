package databank;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import knowledge_app.Knowledge_App;
import knowledge_app.WordProcessor;

public class Sentence {
	// rating_tolerance - max allowed difference between (100-rating) and (100-maxraring) for
	// wordform
	static double rating_tolerance = 1.5;
	static int forward = 1;
	static int backward = -1;
	private String sentence;
	private ArrayList<SentenceWord> sentenceWordList;
	private ArrayList<SentenceWordform> sentenceWordformList;
	SentenceWordRelationGraph wordRelationGraph;
	private SentenceWordFilter[] sentenceWordFilter;
	ArrayList<SentenceWordLink> wordLinkList;

	private int sourceID;
	private int id;
	private int type;
	private ArrayList<ArrayList<Integer>> division;
	private int[] clustering;
	private DataBank databank;
	private Vocabulary vocabulary;

	public Sentence(DataBank databank, Vocabulary vocabulary, int sourceID, int id,
			ArrayList<SentenceWord> sentenceWordList) {
		this.databank = databank;
		this.vocabulary = vocabulary;
		this.sourceID = sourceID;
		this.id = id;
		// this.sentence = sentence;
		this.sentenceWordList = sentenceWordList;

		this.sentence = new String();
		for (SentenceWord sentenceWord : sentenceWordList) {
			if (!this.sentence.isEmpty())
				this.sentence += ' ' + sentenceWord.word;
			else
				this.sentence = sentenceWord.word;
		}
	}

	public void save() {
		try {
			id = databank.saveSentence(sourceID, type, sentence, sentenceWordList);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void parse() {
		// System.out.println(id);
		wordRelationGraph = new SentenceWordRelationGraph(sourceID, id, sentenceWordList.size());
		wordLinkList = new ArrayList<SentenceWordLink>();
		if (databank == null)
			return;

		ArrayList<SentenceWordform> tempSentenceWordformList = databank.getSentencePartList(
				sourceID, id, "", 0, "", "", 0, 0, "", "");
		tempSentenceWordformList = sortByUniqueWordPos(tempSentenceWordformList);
		sentenceWordformList = tempSentenceWordformList;
		clustering = new int[sentenceWordList.size() + 1];

		parsePrepositions();
		parseNumerals();
		parseNegative();
		parseConjunctions();
		parseAdverbsAttributes();
		parsePrepositions();
		parseAttributes(backward);
		parseAttributes(forward);
		parseConjunctions();
		parseVerbQualifier();
		parseComplexPredicate();
		parseSubjectPredicate();
		fillClustering();
		parseConjunctions();
		parseVerbControlledSubstantives();
		parseConjunctions();
		// parseGenetiveSubstantives();

		// find best wordform for each SentenceWord in sentenceWordList
		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();
		for (SentenceWord sentenceWord : sentenceWordList) {
			findBestWordform(sentenceWord);
		}

		// fill "part", "dep_id" and "preposition_id" in sentenceWordList
		generateSentenceParts();

		databank.saveSentenceParts(sentenceWordList);
		databank.saveSentenceWordLinkList(wordLinkList);
		databank.saveSentenceWordRelationList(wordRelationGraph.getSet());
		databank.setSentenceProcessed(sourceID, id);
	}

	private void fillClustering() {
		int clusterID = 0;
		for (SentenceWordRelation subjectPredicateRelation : wordRelationGraph
				.getSet(SentenceWordRelation.subjectPredicate)) {
			clusterID++;
			ArrayList<SentenceWordRelation> relationTree = new ArrayList<SentenceWordRelation>();

			relationTree.addAll(wordRelationGraph
					.getRelationTree(subjectPredicateRelation.word1Pos));

			for (SentenceWord sentenceWord : sentenceWordList)
				if (sentenceWord.subsentenceID == getSentenceWord(sentenceWordList,
						subjectPredicateRelation.word1Pos).subsentenceID)
					clustering[sentenceWord.wordPos] = clusterID;

			for (SentenceWordRelation sentenceWordRelation : relationTree) {
				for (SentenceWord sentenceWord : sentenceWordList)
					if (sentenceWord.subsentenceID == getSentenceWord(sentenceWordList,
							sentenceWordRelation.word2Pos).subsentenceID)
						clustering[sentenceWord.wordPos] = clusterID;
			}
		}
	}

	private ArrayList<SentenceWordform> sortByUniqueWordPos(
			ArrayList<SentenceWordform> sentenceWordformList) {
		int[] posStats = new int[sentenceWordList.size() + 1];
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();
		ArrayList<SentenceWordform> tempSentenceWordformList = new ArrayList<SentenceWordform>();
		int curRating = -1;
		for (SentenceWordform sentenceWordform : sentenceWordformList) {
			if (curRating != sentenceWordform.rating) {
				// 1. Copy unique pos
				for (SentenceWordform curSentenceWordform : tempSentenceWordformList)
					if (posStats[curSentenceWordform.wordPos] == 1)
						result.add(curSentenceWordform);
				// 2. Copy non-unique pos
				for (SentenceWordform curSentenceWordform : tempSentenceWordformList)
					if (posStats[curSentenceWordform.wordPos] != 1)
						result.add(curSentenceWordform);
				// 3. Clear stats for next rating
				tempSentenceWordformList = new ArrayList<SentenceWordform>();
				posStats = new int[sentenceWordList.size() + 1];
				curRating = sentenceWordform.rating;
			}
			tempSentenceWordformList.add(sentenceWordform);
			posStats[sentenceWordform.wordPos] += 1;
		}
		// 1. Copy unique pos
		for (SentenceWordform curSentenceWordform : tempSentenceWordformList)
			if (posStats[curSentenceWordform.wordPos] == 1)
				result.add(curSentenceWordform);
		// 2. Copy non-unique pos
		for (SentenceWordform curSentenceWordform : tempSentenceWordformList)
			if (posStats[curSentenceWordform.wordPos] != 1)
				result.add(curSentenceWordform);
		return result;
	}

	private void findBestWordform(SentenceWord sentenceWord) {
		sentenceWord.setFilters(sentenceWordFilter[sentenceWord.wordPos]);
		ArrayList<SentenceWordform> tempWordformList = getSentencePartList("", "",
				sentenceWord.wordPos, "", "", 0, 0, 0, "", "", rating_tolerance);
		Iterator<SentenceWordform> iterator = tempWordformList.iterator();
		if (iterator.hasNext()) {
			sentenceWord.sentenceWordform = iterator.next();
		}
	}

	private boolean markAdverbialParticiple(ArrayList<ArrayList<Integer>> division) {
		int first;
		int last;
		boolean result = false;
		Iterator<Integer> iterator;
		for (ArrayList<Integer> subsentence : division) {
			first = 0;
			last = 0;
			iterator = subsentence.iterator();
			if (iterator.hasNext()) {
				first = iterator.next();
				while (iterator.hasNext())
					last = iterator.next();
			}
			result = result | markAdverbialParticiple(first);
			if (last != first)
				result = result | markAdverbialParticiple(last);
		}
		return result;
	}

	private boolean markAdverbialParticiple(int subsentenceID) {
		ArrayList<SentenceWordform> adverbialParticipleList = getVerbList(id,
				Integer.toString(subsentenceID), "", 0,
				String.valueOf(WordProcessor.verb_adverbial_participle));
		if (!adverbialParticipleList.isEmpty()) {
			ArrayList<SentenceWordform> verbList = getVerbList(id, Integer.toString(subsentenceID),
					"", 0, String.valueOf(WordProcessor.verb_active));
			if (verbList.isEmpty())
				return markAsInternal(id, adverbialParticipleList.iterator().next().wordPos, ",", 1);
		}
		return false;
	}

	/**
	 * Generate possible subsentence combinations that have best subject-predicate pairs
	 * 
	 * @param conjunctions
	 *            - list of conjuntions that can be between subjects
	 * @param curSubsentence
	 *            - list of subsentence IDs in current sentence
	 * @return ArrayList of primitive sentences that are detected in sentence, each primitive
	 *         sentence represented by ArrayList of its subjects and predicates
	 */
	private ArrayList<ArrayList<SentenceWordRelation>> parseSubsentence(
			ArrayList<SentenceWord> conjunctions, ArrayList<Integer> curSubsentence) {
		String subsentenceFilter;
		ArrayList<Integer> subsentencePart;
		ArrayList<ArrayList<SentenceWordRelation>> sentenceBaseRelations = null;
		ArrayList<ArrayList<SentenceWordRelation>> curSentenceBaseRelations;
		int size = curSubsentence.size();
		int curRating;
		int maxRating = -100 * size;
		int[] subsentenceDivisionMask = new int[size]; // new array of zeroes
		Iterator<ArrayList<Integer>> iterator;
		ArrayList<ArrayList<Integer>> subsentenceDivision = new ArrayList<ArrayList<Integer>>();
		do {
			curSentenceBaseRelations = new ArrayList<ArrayList<SentenceWordRelation>>();
			subsentenceDivision = makeSubsentenceDivision(curSubsentence, subsentenceDivisionMask);
			iterator = subsentenceDivision.iterator();
			while (iterator.hasNext()) {
				subsentencePart = iterator.next();
				subsentenceFilter = convertArrayToFilter(subsentencePart);
				curSentenceBaseRelations.add(findSubjectPredicate(conjunctions, subsentenceFilter));
			}
			curRating = calculateSubsentenceRating(curSentenceBaseRelations);
			if (curRating > maxRating) {
				sentenceBaseRelations = curSentenceBaseRelations;
				maxRating = curRating;
			}
			subsentenceDivisionMask = getNextSubsentenceDivisionMask(subsentenceDivisionMask);
		} while (subsentenceDivisionMask != null);
		return sentenceBaseRelations;
	}

	private int calculateSubsentenceRating(
			ArrayList<ArrayList<SentenceWordRelation>> curSentenceBaseRelations) {
		int result = 0;
		for (ArrayList<SentenceWordRelation> subsentence : curSentenceBaseRelations) {
			// bonus rating for each subsentence
			if (subsentence.size() > 0)
				result += 10;
			int maxSubjectRating = 0;
			int maxPredicateRating = 0;
			int rating = 0;
			ArrayList<SentenceWordform> sentenceWordformList = null;
			SentenceWordform subjectWordform = null;
			SentenceWordform predicateWordform = null;
			for (SentenceWordRelation sentenceBaseRelation : subsentence) {
				sentenceWordformList = getSentencePartList("", "", sentenceBaseRelation.word1Pos,
						String.valueOf(sentenceBaseRelation.word1Case), "",
						sentenceBaseRelation.word1Gender, sentenceBaseRelation.word1Sing_Pl, 0,
						String.valueOf(sentenceBaseRelation.word1Type),
						String.valueOf(sentenceBaseRelation.word1SubType), rating_tolerance);
				if ((sentenceWordformList != null) && (sentenceWordformList.size() > 0)) {
					SentenceWordform sentenceWordform = sentenceWordformList.get(0);
					if (sentenceWordform.rating > maxPredicateRating) {
						maxPredicateRating = sentenceWordform.rating;
						predicateWordform = sentenceWordform;
					}
				}

				sentenceWordformList = getSentencePartList("", "", sentenceBaseRelation.word2Pos,
						String.valueOf(sentenceBaseRelation.word2Case), "",
						sentenceBaseRelation.word2Gender, sentenceBaseRelation.word2Sing_Pl, 0,
						String.valueOf(sentenceBaseRelation.word2Type),
						String.valueOf(sentenceBaseRelation.word2SubType), rating_tolerance);
				if ((sentenceWordformList != null) && (sentenceWordformList.size() > 0)) {
					SentenceWordform sentenceWordform = sentenceWordformList.get(0);
					if (sentenceWordform.rating > maxSubjectRating) {
						maxSubjectRating = sentenceWordform.rating;
						subjectWordform = sentenceWordform;
					}
				}
			}
			if (subjectWordform != null) {
				rating += subjectWordform.rating - subjectWordform.maxrating;
			}
			if (predicateWordform != null) {
				rating += predicateWordform.rating - predicateWordform.maxrating;
			}
			result += rating;
		}
		return result;
	}

	private ArrayList<ArrayList<Integer>> makeSubsentenceDivision(
			ArrayList<Integer> curSubsentence, int[] mask) {
		ArrayList<ArrayList<Integer>> result = new ArrayList<ArrayList<Integer>>();
		ArrayList<Integer> subsentence;
		int size = mask.length;
		for (int i = 0; i < size; i++) {
			subsentence = new ArrayList<Integer>();
			for (int j = 0; j < size; j++) {
				if (mask[j] == i)
					subsentence.add(curSubsentence.get(j));
			}
			if (!subsentence.isEmpty())
				result.add(subsentence);
		}
		return result;
	}

	private int[] getNextSubsentenceDivisionMask(int[] mask) {
		boolean success = false;
		int size = mask.length;
		int i;
		i = size - 1;
		while ((i > 0) & (!success)) {
			while ((mask[i] < size) & (!success)) {
				mask[i]++;
				if (mask[i] < size)
					success = isMaskSensible(mask);
			}
			if (mask[i] == size) {
				mask[i] = 0;
				i--;
				success = false;
			}
		}
		if (success)
			return mask;
		else
			return null;
	}

	private boolean isMaskSensible(int[] mask) {
		boolean valid = false;
		int size = mask.length;
		int[] checkField = new int[size]; // new zero
		for (int place : mask) {
			checkField[place] = 1;
		}
		for (int i = size - 1; i >= 0; i--) {
			if (checkField[i] == 1)
				valid = true;
			if ((checkField[i] == 0) & valid) {
				valid = false;
				break;
			}
		}
		return valid;
	}

	private ArrayList<SentenceWordRelation> findSubjectPredicate(
			ArrayList<SentenceWord> conjunctions, String subsentenceFilter) {
		ArrayList<SentenceWordform> subjectList;
		Iterator<SentenceWordform> subjectIterator;
		SentenceWordform subjectWordform;
		ArrayList<SentenceWordform> predicateList;
		Iterator<SentenceWordform> predicateIterator;
		SentenceWordform predicateWordform;
		ArrayList<SentenceWordRelation> subjectPredicateRelations;
		Iterator<SentenceWord> conjunctionIterator;
		SentenceWord conjunction;
		SentenceWordform subject2Wordform;
		boolean success = false;
		boolean conjunctionFound;
		String personFilter;
		subjectPredicateRelations = new ArrayList<SentenceWordRelation>();
		// получить потенциальные сказуемые, отсортированные по рейтингу
		predicateList = getSentencePartList(subsentenceFilter, "", 0, "0", "", 0, 0, 0, "2", "1",
				rating_tolerance);
		predicateIterator = predicateList.iterator();
		while ((predicateIterator.hasNext()) && !success) {
			predicateWordform = predicateIterator.next();
			// получить для каждого сказуемого, потенциальные подлежащие,
			// отсортированные по рейтингу
			if (predicateWordform.person > 0)
				personFilter = String.valueOf(predicateWordform.person);
			else
				personFilter = ">0";

			subjectList = getSentencePartList(subsentenceFilter,
					String.valueOf(predicateWordform.elevation), 0, "1", personFilter,
					predicateWordform.gender, predicateWordform.sing_pl, 0, "", "",
					rating_tolerance);
			subjectIterator = subjectList.iterator();
			// выбрать первую пару
			while ((subjectIterator.hasNext()) && !success) {
				subjectWordform = subjectIterator.next();
				SentenceWordRelation subjectPredicateRelation = new SentenceWordRelation(0, 0,
						predicateWordform, subjectWordform, SentenceWordRelation.subjectPredicate);
				if ((subjectWordform.wordPos != predicateWordform.wordPos)
						&& !wordRelationGraph.existWordRelation(subjectPredicateRelation)
				// && !wordRelationGraph.existDependence(subjectWordform.wordPos,
				// SentenceWordRelation.preposition)
				) {
					subjectPredicateRelations.add(subjectPredicateRelation);

					// поиск подлежащего из двух слов, связанных союзом И
					if (!conjunctions.isEmpty()) {
						while (subjectIterator.hasNext()) {
							subject2Wordform = subjectIterator.next();
							if (subject2Wordform.wordPos != subjectWordform.wordPos) {
								conjunctionIterator = conjunctions.iterator();
								conjunctionFound = false;
								while (conjunctionIterator.hasNext()) {
									conjunction = conjunctionIterator.next();
									// варианты взаимного расположения
									// подлежащее1 И подлежащее2 сказуемое
									if ((subjectWordform.wordPos < conjunction.wordPos)
											& (subject2Wordform.wordPos > conjunction.wordPos)
											& (subject2Wordform.wordPos < predicateWordform.wordPos)) {
										conjunctionFound = true;
										break;
									}
									// подлежащее2 И подлежащее1 сказуемое
									if ((subject2Wordform.wordPos < conjunction.wordPos)
											& (subjectWordform.wordPos > conjunction.wordPos)
											& (subjectWordform.wordPos < predicateWordform.wordPos)) {
										conjunctionFound = true;
										break;
									}
									// сказуемое подлежащее1 И подлежащее2
									if ((subjectWordform.wordPos < conjunction.wordPos)
											& (subject2Wordform.wordPos > conjunction.wordPos)
											& (subjectWordform.wordPos > predicateWordform.wordPos)) {
										conjunctionFound = true;
										break;
									}
									// сказуемое подлежащее2 И подлежащее1
									if ((subject2Wordform.wordPos < conjunction.wordPos)
											& (subjectWordform.wordPos > conjunction.wordPos)
											& (subject2Wordform.wordPos > predicateWordform.wordPos)) {
										conjunctionFound = true;
										break;
									}
								}
								if (conjunctionFound) {
									SentenceWordRelation subjectPredicateRelation2 = new SentenceWordRelation(
											0, 0, predicateWordform, subject2Wordform,
											SentenceWordRelation.subjectPredicate);
									if (!wordRelationGraph
											.existWordRelation(subjectPredicateRelation2)) {
										subjectPredicateRelations.add(subjectPredicateRelation2);
										break;
									}
								}
							}
						}
					}
					success = true;
				}
			}
		}
		return subjectPredicateRelations;
	}

	private void parseSubjectPredicate() {
		ArrayList<SentenceWord> conjunctions;
		division = divideSentence();
		if (division != null)
			if (markAdverbialParticiple(division))
				division = divideSentence();

		if (division != null) {
			conjunctions = getConjunctions("и");
			for (ArrayList<Integer> curSubsentence : division) {
				int depID = 0;
				ArrayList<SentenceWordRelation> subjectPredicateRelations = gather(parseSubsentence(
						conjunctions, curSubsentence));
				for (SentenceWordRelation sentenceWordRelation : subjectPredicateRelations) {
					sentenceWordRelation.depID = depID;
					wordRelationGraph.add(sentenceWordRelation);
					depID = sentenceWordRelation.id;
				}
			}
			wordRelationGraph.changeWordRelationStatus(SentenceWordRelation.subjectPredicate);
		}
	}

	private void parsePrepositions() {
		ArrayList<SentenceWordform> prepositionList;
		Iterator<SentenceWordform> prepositionIterator;
		SentenceWordform prepositionWordform;
		ArrayList<SentenceWordform> prepAlternativeList;
		Iterator<SentenceWordform> prepAlternativeIterator;
		SentenceWordform prepAlternativeWordform;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;

		SentenceWordRelation wordRelation;
		int relationType = SentenceWordRelation.preposition;

		// получить список предлогов
		prepositionList = getSentencePartList("", "", 0, "", "", 0, 0, 0,
				String.valueOf(WordProcessor.preposition), "", 1);
		prepositionIterator = prepositionList.iterator();

		// для каждого предлога ищем следующие за ним прилагательные или существительные
		while (prepositionIterator.hasNext()) {
			prepositionWordform = prepositionIterator.next();
			// собираем существительные или прилагательные на позиции за предлогом не в именительном
			// падеже
			substantiveList = getSubstantiveList(id,
					wordRelationGraph.getNextIndependentWordPos(prepositionWordform.wordPos), ">1",
					"", 0, 0, 0, rating_tolerance);
			if (!substantiveList.isEmpty()) {
				substantiveIterator = substantiveList.iterator();
				while (substantiveIterator.hasNext()) {
					substantiveWordform = substantiveIterator.next();
					wordRelation = new SentenceWordRelation(0, 0, substantiveWordform,
							prepositionWordform, relationType);
					wordRelationGraph.add(wordRelation);
				}
			} else {
				// проверяем, есть ли другие подходящие словоформы кроме предлога
				prepAlternativeList = getSentencePartList("", "", prepositionWordform.wordPos, "",
						"", 0, 0, 0, "<>" + String.valueOf(WordProcessor.preposition), "", 1);
				prepAlternativeIterator = prepAlternativeList.iterator();
				while (prepAlternativeIterator.hasNext()) {
					prepAlternativeWordform = prepAlternativeIterator.next();
					wordRelation = new SentenceWordRelation(0, 0, prepAlternativeWordform,
							relationType);
					wordRelationGraph.add(wordRelation);
				}
			}
		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseNegative() {
		ArrayList<SentenceWordform> negatives;
		Iterator<SentenceWordform> negativeIterator;
		SentenceWordform negativeWordform;
		ArrayList<SentenceWordform> nextWordforms;
		Iterator<SentenceWordform> nextWordformIterator;
		SentenceWordform nextWordform = null;

		int relationType = SentenceWordRelation.negative;
		SentenceWordRelation wordRelation;

		// find wordPos of negative
		negatives = getNegatives();
		negativeIterator = negatives.iterator();
		while (negativeIterator.hasNext()) {
			negativeWordform = negativeIterator.next();
			nextWordforms = getNextWordforms(id, negativeWordform.wordPos, rating_tolerance);
			nextWordformIterator = nextWordforms.iterator();
			while (nextWordformIterator.hasNext()) {
				nextWordform = nextWordformIterator.next();
				wordRelation = new SentenceWordRelation(0, 0, nextWordform, negativeWordform,
						relationType);
				wordRelationGraph.add(wordRelation);
			}
		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseNumerals() {
		ArrayList<SentenceWordform> numeralList;
		Iterator<SentenceWordform> numeralIterator;
		SentenceWordform numeralPartWordform;
		Numeral numeral;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;

		int relationType = SentenceWordRelation.numeral;
		SentenceWordRelation wordRelation;

		int wcase;
		int sing_pl;

		// получить список числительных
		numeralList = getSentencePartList("", "", 0, ">0", ">0", 0, 0, 0,
				String.valueOf(WordProcessor.numeral), "", rating_tolerance);
		numeralIterator = numeralList.iterator();

		// для каждого числительного найти существительное, следующее за ним
		while (numeralIterator.hasNext()) {
			numeralPartWordform = numeralIterator.next();
			numeral = databank.getNumeralByWordID(vocabulary, numeralPartWordform.word_id);
			wcase = numeralPartWordform.wcase;
			sing_pl = numeralPartWordform.sing_pl;
			if (databank.isNumeralBaseForm(vocabulary, numeralPartWordform.word_id,
					numeralPartWordform.rule_id)) {
				wcase = numeral.getBaseWcase();
				sing_pl = numeral.getBaseSingPl();
			}
			substantiveList = getSubstantiveList(id, numeralPartWordform.wordPos + 1,
					String.valueOf(wcase), ">0", numeralPartWordform.gender, sing_pl, 0,
					rating_tolerance);
			substantiveIterator = substantiveList.iterator();
			while (substantiveIterator.hasNext()) {
				substantiveWordform = substantiveIterator.next();
				wordRelation = new SentenceWordRelation(0, 0, numeralPartWordform,
						substantiveWordform, relationType);
				wordRelationGraph.add(wordRelation);
			}
		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseGenetiveSubstantives() {
		ArrayList<SentenceWordform> genetiveSubstantiveList;
		Iterator<SentenceWordform> genetiveSubstantiveIterator;
		SentenceWordform genetiveSubstantiveWordform;
		ArrayList<SentenceWordform> mainSubstantiveList;
		Iterator<SentenceWordform> mainSubstantiveIterator;
		SentenceWordform mainSubstantiveWordform;

		int relationType = SentenceWordRelation.genetivesubstantive;
		SentenceWordRelation wordRelation;

		// получить существительные
		mainSubstantiveList = getSubstantiveList(id, 0, ">0", ">0", 0, 0, 0, 1);
		mainSubstantiveIterator = mainSubstantiveList.iterator();

		// для каждого существительного в родительном падеже получить предшествующее существительное
		while (mainSubstantiveIterator.hasNext()) {
			mainSubstantiveWordform = mainSubstantiveIterator.next();
			genetiveSubstantiveList = getSubstantiveList(id,
					wordRelationGraph.getNextIndependentWordPos(mainSubstantiveWordform.wordPos),
					"2", ">0", 0, 0, 0, 1);
			genetiveSubstantiveIterator = genetiveSubstantiveList.iterator();
			if (genetiveSubstantiveIterator.hasNext()) {
				genetiveSubstantiveWordform = genetiveSubstantiveIterator.next();
				wordRelation = new SentenceWordRelation(0, 0, mainSubstantiveWordform,
						genetiveSubstantiveWordform, relationType);
				wordRelationGraph.add(wordRelation);
			}
		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseComplexPredicate() {
		int relationType = SentenceWordRelation.verbInfinitive;
		int relationType2 = SentenceWordRelation.verbShortAdjective;

		// получить глаголы в действительной форме
		ArrayList<SentenceWordform> verbList = getVerbList(id, "", "", 0, "");
		// verbList.addAll(getShortAdjectiveList(id, 0, 0, 0));

		for (SentenceWordform verbWordform : verbList) {
			int nextIndependentWordPos = wordRelationGraph
					.getNextIndependentWordPos(verbWordform.wordPos);
			// для каждого глагола в действительной форме получить глаголы в инфинитиве и краткие
			// прилагательные
			for (SentenceWordform infinitiveWordform : getVerbList2(id, "", "",
					nextIndependentWordPos, String.valueOf(WordProcessor.verb_infinitive),
					verbWordform.gender, verbWordform.sing_pl)) {
				SentenceWordRelation wordRelation;
				if (infinitiveWordform.type == WordProcessor.verb)
					wordRelation = new SentenceWordRelation(0, 0, verbWordform, infinitiveWordform,
							relationType);
				else
					wordRelation = new SentenceWordRelation(0, 0, verbWordform, infinitiveWordform,
							relationType2);

				if (wordRelationGraph.add(wordRelation))
					markLinkedWords(wordRelationGraph, wordRelation, infinitiveWordform);
			}

		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	/**
	 * разбираем слова, определяющие харакатер действия глагола: наречия, сравнительные
	 * прилагательные
	 */
	private void parseVerbQualifier() {
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		ArrayList<SentenceWordform> adverbList;
		Iterator<SentenceWordform> adverbsIterator;
		SentenceWordform adverbWordform;

		int relationType = SentenceWordRelation.verbAdverb;
		SentenceWordRelation wordRelation;

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить наречия
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			adverbList = getAdverbList(
					id,
					wordRelationGraph.getPrevIndependentWordPos(verbWordform.wordPos),
					"",
					String.valueOf(WordProcessor.adjective_adverb) + "|"
							+ String.valueOf(WordProcessor.adjective_comparative));
			// если есть связанный инфинитив, то относим наречия к нему
			if (!wordRelationGraph.existDependence(verbWordform.wordPos,
					SentenceWordRelation.verbInfinitive)) {
				adverbList.addAll(getAdverbList(
						id,
						wordRelationGraph.getNextIndependentWordPos(verbWordform.wordPos),
						"",
						String.valueOf(WordProcessor.adjective_adverb) + "|"
								+ String.valueOf(WordProcessor.adjective_comparative)));
			}
			adverbsIterator = adverbList.iterator();
			while (adverbsIterator.hasNext()) {
				adverbWordform = adverbsIterator.next();
				if ((adverbWordform.type == WordProcessor.adjective)
						&& (adverbWordform.subtype == WordProcessor.adjective_comparative))
					relationType = SentenceWordRelation.verbRelativeAdjective;
				else
					relationType = SentenceWordRelation.verbAdverb;
				wordRelation = new SentenceWordRelation(0, 0, verbWordform, adverbWordform,
						relationType);
				if (wordRelationGraph.add(wordRelation))
					markLinkedWords(wordRelationGraph, wordRelation, adverbWordform);
			}
		}
		wordRelationGraph.cleanWordRelationList(relationType);
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseVerbControlledSubstantives() {
		int curWordPos;
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;
		String personFilter;
		int mainGender;
		int mainSing_Pl;

		int relationType = SentenceWordRelation.verbSubstantive;
		SentenceWordRelation wordRelation;
		SentenceWordRelation mainVerbRelation;

		// получить глаголы в действительной форме или инфинитиве
		verbList = getVerbList(id, "", "", 0, "");
		//verbList = getVerbList2(id, "", "", 0, "", 0, 0);		
		verbIterator = verbList.iterator();

		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			if (!wordRelationGraph.existDependence(verbWordform.wordPos,
					SentenceWordRelation.verbInfinitive)) {
				mainVerbRelation = wordRelationGraph.getMainVerbRelation(verbWordform.wordPos);
				if (mainVerbRelation == null) {
					mainGender = verbWordform.gender;
					mainSing_Pl = verbWordform.sing_pl;
				} else {
					mainGender = mainVerbRelation.word1Gender;
					mainSing_Pl = mainVerbRelation.word1Sing_Pl;
				}
				if (verbWordform.person == 0)
					personFilter = ">0";
				else
					personFilter = String.valueOf(verbWordform.person);

				// для каждого глагола в действительной форме получить существительные не в
				// именительном падеже
				curWordPos = wordRelationGraph.getNextIndependentWordPos(verbWordform.wordPos);
				if ((curWordPos > 0)
				// && getSubstantiveList(id, curWordPos, "1", personFilter, mainGender,
				// mainSing_Pl, 0, 1).isEmpty()
				) {
					substantiveList = getSubstantiveList(id, curWordPos, ">1", ">0", 0, 0, 0, 1);
					substantiveIterator = substantiveList.iterator();
					while (substantiveIterator.hasNext()) {
						substantiveWordform = substantiveIterator.next();
						wordRelation = new SentenceWordRelation(0, 0, verbWordform,
								substantiveWordform, relationType);
						if (wordRelationGraph.add(wordRelation))
							markLinkedWords(wordRelationGraph, wordRelation, substantiveWordform);
					}
				}
			}
		}
		wordRelationGraph.cleanWordRelationList(relationType);
		wordRelationGraph.movePrepositionRelations(wordRelationGraph);
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseAdverbsAttributes() {
		ArrayList<SentenceWordform> attributeList;
		Iterator<SentenceWordform> attributeIterator;
		SentenceWordform attributeWordform;
		ArrayList<SentenceWordform> adverbList;
		Iterator<SentenceWordform> adverbsIterator;
		SentenceWordform adverbWordform;

		int relationType = SentenceWordRelation.adverbAttribute;
		SentenceWordRelation wordRelation;

		// получить прилагательные и местоимения прилагательные
		// attributeList = getAdjectiveList(id, 0, ">0", 0, 0, 0, 1);
		attributeList = getAdjectiveList(id, 0, "", 0, 0, 0, 1);
		attributeIterator = attributeList.iterator();

		// для каждого прилагательного получить предшествующие наречия
		while (attributeIterator.hasNext()) {
			attributeWordform = attributeIterator.next();
			if ((attributeWordform.type == WordProcessor.adjective)
					&& !wordRelationGraph.existDependence(attributeWordform.wordPos,
							SentenceWordRelation.preposition)) {
				adverbList = getAdverbList(id,
						wordRelationGraph.getPrevIndependentWordPos(attributeWordform.wordPos),
						String.valueOf(WordProcessor.adverb_attribute),
						String.valueOf(WordProcessor.adjective_adverb));
				adverbsIterator = adverbList.iterator();
				if (adverbsIterator.hasNext()) {
					adverbWordform = adverbsIterator.next();
					wordRelation = new SentenceWordRelation(0, 0, attributeWordform,
							adverbWordform, relationType);
					if (wordRelationGraph.add(wordRelation))
						markLinkedWords(wordRelationGraph, wordRelation, adverbWordform);
				}
			}
		}
		wordRelationGraph.cleanWordRelationList(relationType);
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseAttributes(int direction) {
		ArrayList<SentenceWordform> adjectiveList;
		Iterator<SentenceWordform> adjectiveIterator;
		SentenceWordform adjectiveWordform;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;

		int relationType = SentenceWordRelation.attribute;
		SentenceWordRelation wordRelation;
		SentenceWordRelation curWordRelation;
		SentenceWordRelationGraph curWordRelationGraph = new SentenceWordRelationGraph(sourceID,
				id, sentenceWordList.size());

		boolean found;
		int curWordPos;
		int curWordRelationId;
		int curPrepositionPos;
		int adjPrepositionPos;

		// find possible substantives
		// possible substantive: wordform that has an substantive as a word with maximal rating
		substantiveList = getSubstantiveList(id, 0, ">0", ">0", 0, 0, 0, rating_tolerance);
		substantiveIterator = substantiveList.iterator();

		// find possible adjectives that have common form with substantive
		// possible adjective: wordform that has an adjective as a word with maximal rating
		while (substantiveIterator.hasNext()) {
			substantiveWordform = substantiveIterator.next();
			curPrepositionPos = wordRelationGraph
					.getPrepositionWordPos(substantiveWordform.wordPos);
			// start from substantive position
			curWordPos = substantiveWordform.wordPos;
			curWordRelationId = 0;
			found = true;
			// try to find adjectives with the same properties to the left
			while (found && (curWordPos + direction > 0)
					&& (curWordPos + direction <= sentenceWordList.size())) {
				found = false;
				// curWordPos--;
				if (direction == forward)
					curWordPos = wordRelationGraph.getNextIndependentWordPos(curWordPos);
				else if (direction == backward)
					curWordPos = wordRelationGraph.getPrevIndependentWordPos(curWordPos);
				adjPrepositionPos = wordRelationGraph.getPrepositionWordPos(curWordPos);
				if (((curPrepositionPos == 0) && (direction == backward))
						| ((adjPrepositionPos == 0) && (direction == forward))
						| (curPrepositionPos == adjPrepositionPos)) {
					curPrepositionPos = adjPrepositionPos;
					adjectiveList = getAdjectiveList(id, curWordPos,
							String.valueOf(substantiveWordform.wcase), substantiveWordform.gender,
							substantiveWordform.sing_pl, substantiveWordform.animate,
							rating_tolerance);
					adjectiveIterator = adjectiveList.iterator();
					if (adjectiveIterator.hasNext()) {
						found = true;
						adjectiveWordform = adjectiveIterator.next();
						// mark, that adjective is dependent on substantive
						wordRelation = new SentenceWordRelation(0, curWordRelationId,
								substantiveWordform, adjectiveWordform, relationType);
						if (!wordRelationGraph.existWordRelation(wordRelation)
								&& curWordRelationGraph.add(wordRelation)) {
							// mark any linked adjective
							markLinkedWords(curWordRelationGraph, wordRelation, adjectiveWordform);

							// find leftmost dependent adjective
							curWordRelation = curWordRelationGraph
									.getLastWordRelation(wordRelation);
							curWordRelationId = curWordRelation.id;
							curWordPos = curWordRelation.word2Pos;

							// start search only if leftmost dependent adjective is before
							// substantive
							found = (curWordPos - wordRelation.word1Pos) * direction > 0;
						}
					}
				}
			}
		}
		curWordRelationGraph.cleanWordRelationList(relationType);
		wordRelationGraph.addAll(curWordRelationGraph);
		wordRelationGraph.movePrepositionRelations(curWordRelationGraph);
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseConjunctions() {
		ArrayList<SentenceWord> conjunctions;
		Iterator<SentenceWord> conjunctionIterator;
		SentenceWord conjunction;
		ArrayList<SentenceWordform> prevWordforms;
		Iterator<SentenceWordform> prevWordformIterator;
		SentenceWordform prevWordform;
		ArrayList<SentenceWordform> nextWordforms;
		Iterator<SentenceWordform> nextWordformIterator;
		SentenceWordform nextWordform;

		SentenceWordLink wordLink;

		int prepositionPrevWordPos;
		int prepositionNextWordPos;

		// find wordPos with conjunction
		conjunctions = getConjunctions("и");
		conjunctions.addAll(getConjunctions("или"));
		conjunctions.addAll(getConjunctions(","));
		conjunctionIterator = conjunctions.iterator();
		while (conjunctionIterator.hasNext()) {
			conjunction = conjunctionIterator.next();
			// get wordforms with maxrating at previous and next position
			prevWordforms = getPrevWordforms(id, conjunction.wordPos, 1.00);
			nextWordforms = getNextWordforms(id, conjunction.wordPos, 1.00);
			// find and save pairs with same rules
			prevWordformIterator = prevWordforms.iterator();
			while (prevWordformIterator.hasNext()) {
				prevWordform = prevWordformIterator.next();
				prepositionPrevWordPos = wordRelationGraph
						.getPrepositionWordPos(prevWordform.wordPos);
				nextWordformIterator = nextWordforms.iterator();
				while (nextWordformIterator.hasNext()) {
					nextWordform = nextWordformIterator.next();
					prepositionNextWordPos = wordRelationGraph
							.getPrepositionWordPos(nextWordform.wordPos);
					// существительные и местоимения существительные
					if ((prevWordform.wcase > 0) & (prevWordform.person > 0))
						if ((prevWordform.wcase == nextWordform.wcase)
								& (nextWordform.person > 0)
								& ((clustering[prevWordform.wordPos] == clustering[nextWordform.wordPos])
										| (clustering[prevWordform.wordPos] == 0) | (clustering[nextWordform.wordPos] == 0))) {
							conjunction.internal = true;
							wordLink = new SentenceWordLink(prevWordform, conjunction, nextWordform);
							if (!wordLink.exists(wordLinkList))
								wordLinkList.add(wordLink);
						}

					// прилагательные и местоимения прилагательные
					if ((prevWordform.wcase > 0) & (prevWordform.person == 0))
						if ((nextWordform.person == 0)
								& (prevWordform.wcase == nextWordform.wcase)
								& ((prevWordform.gender == nextWordform.gender)
										| (prevWordform.gender == 0) | (nextWordform.gender == 0))
								& ((prevWordform.sing_pl == nextWordform.sing_pl)
										| (prevWordform.sing_pl == 0) | (nextWordform.sing_pl == 0))
								& ((clustering[prevWordform.wordPos] == clustering[nextWordform.wordPos])
										| (clustering[prevWordform.wordPos] == 0) | (clustering[nextWordform.wordPos] == 0))
								& ((prepositionNextWordPos == prepositionPrevWordPos) | (prepositionNextWordPos == 0))) {
							conjunction.internal = true;
							wordLink = new SentenceWordLink(prevWordform, conjunction, nextWordform);
							if (!wordLink.exists(wordLinkList))
								wordLinkList.add(wordLink);
						}
					// прочие
					if (prevWordform.wcase == 0)
						if ((nextWordform.person == prevWordform.person)
								& (prevWordform.wcase == nextWordform.wcase)
								& (prevWordform.gender == nextWordform.gender)
								& (prevWordform.sing_pl == nextWordform.sing_pl)
								& (prevWordform.type == nextWordform.type)
								& (prevWordform.subtype == nextWordform.subtype)
								& ((clustering[prevWordform.wordPos] == clustering[nextWordform.wordPos])
										| (clustering[prevWordform.wordPos] == 0) | (clustering[nextWordform.wordPos] == 0))) {
							conjunction.internal = true;
							wordLink = new SentenceWordLink(prevWordform, conjunction, nextWordform);
							if (!wordLink.exists(wordLinkList))
								wordLinkList.add(wordLink);
						}
				}
			}
		}
	}

	private void markLinkedWords(SentenceWordRelationGraph wordRelationGraph,
			SentenceWordRelation wordRelation, SentenceWordform dependentWord) {
		ArrayList<SentenceWordform> linkedWordList;
		Iterator<SentenceWordform> linkedWordIterator;
		SentenceWordform linkedWordform;
		SentenceWordform conjunctionWordform;
		SentenceWordRelation linkedWordRelation;
		SentenceWordRelation conjunctionWordRelation;
		boolean createConjunctionRelation;
		HashMap<Integer, SentenceWordRelation> conjunctionWordRelationbyWordPos = new HashMap<Integer, SentenceWordRelation>();
		linkedWordList = getLinkedWordList(dependentWord.wordPos, dependentWord.type,
				dependentWord.subtype, dependentWord.wcase, dependentWord.person,
				dependentWord.gender, dependentWord.sing_pl);
		linkedWordIterator = linkedWordList.iterator();
		while (linkedWordIterator.hasNext()) {
			linkedWordform = linkedWordIterator.next();
			if (wordRelation.word1Pos != linkedWordform.wordPos) {
				createConjunctionRelation = !conjunctionWordRelationbyWordPos
						.containsKey(linkedWordform.wordPos);
				if (createConjunctionRelation) {
					conjunctionWordform = getConjunction(dependentWord.wordPos,
							linkedWordform.wordPos);
					conjunctionWordRelation = new SentenceWordRelation(0, wordRelation,
							conjunctionWordform, SentenceWordRelation.conjunction);
					// temporary add conjunctionWordRelation to create dependency chain for
					// linkedWordRelation
					wordRelationGraph.add(conjunctionWordRelation);
				} else
					conjunctionWordRelation = conjunctionWordRelationbyWordPos
							.get(linkedWordform.wordPos);

				linkedWordRelation = new SentenceWordRelation(0, conjunctionWordRelation,
						linkedWordform, wordRelation.relationType);

				if (wordRelationGraph.add(linkedWordRelation)) {
					if (createConjunctionRelation)
						conjunctionWordRelationbyWordPos.put(linkedWordform.wordPos,
								conjunctionWordRelation);
					markLinkedWords(wordRelationGraph, linkedWordRelation, linkedWordform);
				} else if (createConjunctionRelation)
					wordRelationGraph.remove(conjunctionWordRelation);
			}
		}
	}

	private ArrayList<SentenceWord> generateSentenceParts() {
		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();
		SentenceWord sentencePart1;
		SentenceWord sentencePart2;
		for (SentenceWordRelation wordRelation : wordRelationGraph.getSet()) {
			if (wordRelation.word2Pos != 0) {
				sentencePart1 = getSentenceWord(sentenceWordList, wordRelation.word1Pos);
				sentencePart2 = getSentenceWord(sentenceWordList, wordRelation.word2Pos);
				sentencePart2.dep_word_pos = wordRelation.word1Pos;
				if (wordRelation.relationType == SentenceWordRelation.preposition) {
					sentencePart1.preposition_id = sentencePart2.sentenceWordform.word_id;
					if (!sentenceParts.contains(sentencePart1))
						sentenceParts.add(sentencePart1);
				}
				if (wordRelation.relationType == SentenceWordRelation.subjectPredicate) {
					sentencePart1.part = SentenceWord.predicate;
					sentencePart2.part = SentenceWord.subject;
					if (!sentenceParts.contains(sentencePart1))
						sentenceParts.add(sentencePart1);
				}
				if (!sentenceParts.contains(sentencePart2))
					sentenceParts.add(sentencePart2);
			}

		}
		return sentenceParts;
	}

	private ArrayList<SentenceWordform> getAdjectiveList(int sentence_id, int wordPos,
			String wcaseFilter, int gender, int sing_pl, int animate, double rating_tolerance) {
		return getSentencePartList("", "", wordPos, wcaseFilter, "0", gender, sing_pl, animate, "",
				"", rating_tolerance);
	}

	private ArrayList<SentenceWordform> getAdverbList(int sentence_id, int wordPos,
			String adverbSubtypeFilter, String adjectiveSubtypeFilter) {
		ArrayList<SentenceWordFilter> sentenceWordFilterList = new ArrayList<SentenceWordFilter>();
		sentenceWordFilterList.add(new SentenceWordFilter(sentence_id, wordPos, "", "", 0, 0, 0,
				String.valueOf(WordProcessor.adjective), adjectiveSubtypeFilter));
		sentenceWordFilterList.add(new SentenceWordFilter(sentence_id, wordPos, "", "", 0, 0, 0,
				String.valueOf(WordProcessor.adverb), adverbSubtypeFilter));

		return getSentencePartList("", "", wordPos, sentenceWordFilterList, 1);
	}

	private ArrayList<SentenceWordform> getSubstantiveList(int sentence_id, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl, int animate,
			double rating_tolerance) {
		return getSentencePartList("", "", wordPos, wcaseFilter, personFilter, gender, sing_pl,
				animate, "", "", rating_tolerance);
	}

	private ArrayList<SentenceWordform> getVerbList(int sentence_id, String subsentenceFilter,
			String elevationFilter, int wordPos, String subtypeFilter) {
		return getSentencePartList(subsentenceFilter, elevationFilter, wordPos, "", "", 0, 0, 0,
				String.valueOf(WordProcessor.verb), subtypeFilter, 1);
	}

	private ArrayList<SentenceWordform> getShortAdjectiveList(int sentence_id, int wordPos,
			int gender, int sing_pl) {
		return getSentencePartList("", "", wordPos, "", "", gender, sing_pl, 0,
				String.valueOf(WordProcessor.adjective),
				String.valueOf(WordProcessor.adjective_short), 1);
	}

	private ArrayList<SentenceWordform> getVerbList2(int sentence_id, String subsentenceFilter,
			String elevationFilter, int wordPos, String verbSubtypeFilter,
			int shortAdjectiveGender, int shortAdjectiveSing_Pl) {
		ArrayList<SentenceWordFilter> sentenceWordFilterList = new ArrayList<SentenceWordFilter>();
		sentenceWordFilterList.add(new SentenceWordFilter(id, wordPos, "", "", 0, 0, 0, String
				.valueOf(WordProcessor.verb), verbSubtypeFilter));
		sentenceWordFilterList.add(new SentenceWordFilter(id, wordPos, "", "",
				shortAdjectiveGender, shortAdjectiveSing_Pl, 0, String
						.valueOf(WordProcessor.adjective), String
						.valueOf(WordProcessor.adjective_short)));
		return getSentencePartList(subsentenceFilter, elevationFilter, wordPos,
				sentenceWordFilterList, 1);
	}

	private ArrayList<SentenceWordform> getPrevWordforms(int sentence_id, int wordPos,
			double rating_tolerance) {
		return getSentencePartList("", "", wordRelationGraph.getPrevIndependentWordPos(wordPos),
				"", "", 0, 0, 0, "", "", rating_tolerance);
	}

	private ArrayList<SentenceWordform> getNextWordforms(int sentence_id, int wordPos,
			double rating_tolerance) {
		return getSentencePartList("", "", wordRelationGraph.getNextIndependentWordPos(wordPos),
				"", "", 0, 0, 0, "", "", rating_tolerance);
	}

	private ArrayList<SentenceWordform> getLinkedWordList(int wordPos, int type, int subtype,
			int wcase, int person, int gender, int sing_pl) {
		int tempWordPos;
		ArrayList<SentenceWordform> linkedWords = new ArrayList<SentenceWordform>();
		for (SentenceWordLink wordLink : wordLinkList)
			if ((id == wordLink.sentenceID)
					&& ((wordPos == wordLink.wordPos) | (wordPos == wordLink.linkWordPos))
					&& (wcase == wordLink.wcase)) {
				if (wordPos == wordLink.wordPos)
					tempWordPos = wordLink.linkWordPos;
				else
					tempWordPos = wordLink.wordPos;
				// существительные и местоимения существительные
				if ((wcase > 0) & (person > 0))
					linkedWords.addAll(getSentencePartList("", "", tempWordPos,
							String.valueOf(wcase), ">0", 0, 0, 0, "", "", 1));
				// прилагательные и местоимения прилагательные
				if ((wcase > 0) & (person == 0))
					linkedWords.addAll(getSentencePartList("", "", tempWordPos,
							String.valueOf(wcase), String.valueOf(person), gender, sing_pl, 0, "",
							"", 1));
				// прочие
				if ((wcase == 0) && (type == wordLink.type) && (subtype == wordLink.subtype))
					linkedWords.addAll(getSentencePartList("", "", tempWordPos,
							String.valueOf(wcase), String.valueOf(person), gender, sing_pl, 0,
							String.valueOf(type), String.valueOf(subtype), 1));
			}

		return linkedWords;
	}

	private SentenceWordform getConjunction(int wordPos, int linkWordPos) {
		for (SentenceWordLink wordLink : wordLinkList)
			if ((id == wordLink.sentenceID)
					&& ((wordPos == wordLink.wordPos) && (linkWordPos == wordLink.linkWordPos))
					| ((wordPos == wordLink.linkWordPos) && (linkWordPos == wordLink.wordPos)))
				return new SentenceWordform(sourceID, id, 0, 0, wordLink.conjunctionWordPos,
						WordProcessor.conjunction, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", "", "", "", 0,
						0);
		return null;
	}

	private ArrayList<SentenceWord> getConjunctions(String conjunction) {
		ArrayList<SentenceWord> conjunctions = new ArrayList<SentenceWord>();
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.word.equals(conjunction))
				conjunctions.add(sentenceWord);
		return conjunctions;
	}

	private ArrayList<SentenceWordform> getNegatives() {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();
		String negative = databank.getSetup().getNegative();
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.word.equals(negative))
				result.addAll(getSentencePartList("", "", sentenceWord.wordPos, "", "", 0, 0, 0,
						String.valueOf(WordProcessor.particle), "", 1));
		return result;
	}

	private ArrayList<SentenceWordform> getSentencePartList(String subsentenceFilter,
			String elevationFilter, int wordPos, String wcaseFilter, String personFilter,
			int gender, int sing_pl, int animate, String typeFilter, String subtypeFilter,
			double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();

		if (wordPos < 0)
			return result;

		for (SentenceWordform sentenceWordform : sentenceWordformList)
			if ((wordPos == 0 | sentenceWordform.wordPos == wordPos)
					&& ((100 - sentenceWordform.rating) <= (100 - sentenceWordform.maxrating)
							* rating_tolerance)
					&& (sentenceWordform.rating * rating_tolerance >= sentenceWordform.maxrating)
					&& SentenceWordFilter.checkFilter(sentenceWordform.wcase, wcaseFilter)
					&& SentenceWordFilter.checkFilter(sentenceWordform.person, personFilter)
					&& (gender == 0 | sentenceWordform.gender == 0 | sentenceWordform.gender == gender)
					&& (sing_pl == 0 | sentenceWordform.sing_pl == 0 | sentenceWordform.sing_pl == sing_pl)
					&& (animate == 0 | sentenceWordform.animate == 0 | sentenceWordform.animate == animate)
					&& SentenceWordFilter.checkFilter(sentenceWordform.type, typeFilter)
					&& SentenceWordFilter.checkFilter(sentenceWordform.subtype, subtypeFilter)
					&& SentenceWordFilter.checkFilter(sentenceWordform.elevation, elevationFilter)
					&& SentenceWordFilter.checkFilter(sentenceWordform.subsentenceID,
							subsentenceFilter)
					&& wordRelationGraph.checkRelationCompatability(sentenceWordform))
				result.add(sentenceWordform);
		return result;
	}

	private ArrayList<SentenceWordform> getSentencePartList(String subsentenceFilter,
			String elevationFilter, int wordPos, SentenceWordFilter sentenceWordFilter,
			double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();

		if (wordPos < 0)
			return result;

		for (SentenceWordform sentenceWordform : sentenceWordformList)
			if ((wordPos == 0 | sentenceWordform.wordPos == wordPos)
					&& ((100 - sentenceWordform.rating) <= (100 - sentenceWordform.maxrating)
							* rating_tolerance)
					&& (sentenceWordform.rating * rating_tolerance >= sentenceWordform.maxrating)
					&& sentenceWordFilter.checkFilter(sentenceWordform)
					&& SentenceWordFilter.checkFilter(sentenceWordform.elevation, elevationFilter)
					&& SentenceWordFilter.checkFilter(sentenceWordform.subsentenceID,
							subsentenceFilter)
					&& wordRelationGraph.checkRelationCompatability(sentenceWordform))
				result.add(sentenceWordform);
		return result;
	}

	private ArrayList<SentenceWordform> getSentencePartList(String subsentenceFilter,
			String elevationFilter, int wordPos,
			ArrayList<SentenceWordFilter> sentenceWordFilterList, double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();

		if (wordPos < 0)
			return result;

		for (SentenceWordform sentenceWordform : sentenceWordformList)
			if ((wordPos == 0 | sentenceWordform.wordPos == wordPos)
					&& ((100 - sentenceWordform.rating) <= (100 - sentenceWordform.maxrating)
							* rating_tolerance)
					&& (sentenceWordform.rating * rating_tolerance >= sentenceWordform.maxrating)
					&& SentenceWordFilter.checkFilter(sentenceWordform.elevation, elevationFilter)
					&& SentenceWordFilter.checkFilter(sentenceWordform.subsentenceID,
							subsentenceFilter)
					&& wordRelationGraph.checkRelationCompatability(sentenceWordform))
				for (SentenceWordFilter sentenceWordFilter : sentenceWordFilterList)
					if (sentenceWordFilter.checkFilter(sentenceWordform)) {
						result.add(sentenceWordform);
						break;
					}
		return result;
	}

	private SentenceWord getSentenceWord(ArrayList<SentenceWord> sentenceWordList, int wordPos) {
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.wordPos == wordPos)
				return sentenceWord;
		return null;
	}

	private boolean markAsInternal(int sentence_id, int wordPos, String punctuation,
			int elevation_dif) {
		boolean result = false;
		SentenceWord leftPunctuation = null;
		SentenceWord rightPunctuation = null;
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.isPunctuation) {
				if (sentenceWord.wordPos < wordPos)
					leftPunctuation = sentenceWord;
				if ((rightPunctuation == null) && (sentenceWord.wordPos > wordPos))
					rightPunctuation = sentenceWord;
			}

		if (leftPunctuation != null)
			if (/* (!leftPunctuation.internal) && */(leftPunctuation.word.equals(punctuation))) {
				leftPunctuation.internal = true;
				leftPunctuation.elevation_dif += elevation_dif;
				result = true;
			}

		if (rightPunctuation != null)
			if (/* (!rightPunctuation.internal) && */(rightPunctuation.word.equals(punctuation))) {
				rightPunctuation.internal = true;
				rightPunctuation.elevation_dif -= elevation_dif;
				result = true;
			}
		return result;
	}

	/**
	 * Divide sentence into linear parts (subsentences) between adjacent punctuations
	 * 
	 * @return ArrayList of subsentence IDs
	 */
	private ArrayList<ArrayList<Integer>> divideSentence() {
		ArrayList<ArrayList<Integer>> division = new ArrayList<ArrayList<Integer>>();
		ArrayList<Integer> curSubsentence = new ArrayList<Integer>();
		int subsentence_id = 1;
		int elevation = 0;
		char[] canNotParseMarks;
		canNotParseMarks = databank.getPunctuationMarksNotReady().toCharArray();
		for (SentenceWord sentenceWord : sentenceWordList) {
			if (sentenceWord.isPunctuation) {
				for (int i = 0; i < canNotParseMarks.length; i++)
					if (sentenceWord.word.indexOf(canNotParseMarks[i]) >= 0)
						return null;
				if (sentenceWord.elevation_dif != 0)
					sentenceWord.internal = true;
				curSubsentence.add(new Integer(subsentence_id));
				if (!sentenceWord.internal) {
					division.add(curSubsentence);
					curSubsentence = new ArrayList<Integer>();
				}
				subsentence_id++;
			} else {
				sentenceWord.subsentenceID = subsentence_id;
			}
			elevation += sentenceWord.elevation_dif;
			if (sentenceWord.elevation_dif < 0)
				sentenceWord.elevation = elevation - sentenceWord.elevation_dif;
			else
				sentenceWord.elevation = elevation;
		}

		for (SentenceWordform sentenceWordform : sentenceWordformList) {
			SentenceWord sentenceWord = getSentenceWord(sentenceWordList, sentenceWordform.wordPos);
			if (sentenceWord != null) {
				sentenceWordform.subsentenceID = sentenceWord.subsentenceID;
				sentenceWordform.elevation = sentenceWord.elevation;
			}
		}

		databank.setSentenceType(sourceID, id, 1);
		return division;
	}

	private String convertArrayToFilter(ArrayList<Integer> array) {
		Iterator<Integer> iterator;
		Integer curID;
		String filter;
		filter = "-1";
		iterator = array.iterator();
		if (iterator.hasNext()) {
			curID = iterator.next();
			filter = curID.toString();
			while (iterator.hasNext()) {
				curID = iterator.next();
				filter = filter + '|' + curID.toString();
			}
		}
		return filter;
	}

	/**
	 * Gather Collection of ArrayLists to a single ArrayList
	 * 
	 * @param arraylist
	 *            - Collection of ArrayLists
	 * @return ArrayList that contains all the elements of ArrayList in input Collection
	 */
	private ArrayList<SentenceWordRelation> gather(
			ArrayList<ArrayList<SentenceWordRelation>> arraylist) {
		ArrayList<SentenceWordRelation> result = new ArrayList<SentenceWordRelation>();
		for (ArrayList<SentenceWordRelation> curlist : arraylist) {
			result.addAll(curlist);
		}
		return result;
	}
}
