package databank;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import knowledge_app.WordProcessor;

public class Sentence {
	// rating_tolerance - max allowed difference between (100-rating) and (100-maxraring) for
	// wordform
	static double rating_tolerance = 1.5;
	private String sentence;
	private ArrayList<SentenceWord> sentenceWordList;
	private ArrayList<SentenceWordform> sentenceWordformList;
	SentenceWordRelationGraph wordRelationGraph;
	private SentenceWordFilter[] sentenceWordFilter;
	ArrayList<SentenceWordLink> wordLinkList;

	private int id;
	private int type;
	private int relationCount;
	private ArrayList<ArrayList<Integer>> division;
	private DataBank databank;
	private Vocabulary vocabulary;

	public Sentence(DataBank databank, Vocabulary vocabulary, int id, String sentence,
			ArrayList<SentenceWord> sentenceWordList) {
		this.databank = databank;
		this.vocabulary = vocabulary;
		this.id = id;
		this.sentence = sentence;
		this.sentenceWordList = sentenceWordList;
		this.relationCount = 0;
	}

	public void save() {
		try {
			id = databank.saveSentence(type, sentence, sentenceWordList);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void parse() {
		// System.out.println(id);
		wordRelationGraph = new SentenceWordRelationGraph(id, sentenceWordList.size());
		wordLinkList = new ArrayList<SentenceWordLink>();
		ArrayList<SentenceWord> sentenceParts;
		ArrayList<SentenceWord> conjunctions;
		Iterator<ArrayList<Integer>> subsentenceIterator;
		ArrayList<Integer> curSubsentence;
		if (databank == null)
			return;

		sentenceWordformList = databank.getSentencePartList(id, "", 0, "", "", 0, 0, "", "",
				rating_tolerance);

		parsePrepositions();
		parseNumerals();
		parseNegative();
		parseConjunctions();
		parseAdverbsAttributes();
		parsePrepositions();
		parseAttributes();
		parseConjunctions();
		parseComplexPredicate();
		parseAdverbs();
		parseVerbControlledSubstantives();
		parseConjunctions();
		// parseGenetiveSubstantives();

		// find best wordform for each SentenceWord in sentenceWordList
		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();
		for (SentenceWord sentenceWord : sentenceWordList) {
			ArrayList<SentenceWordform> tempWordformList = getSentencePartList("",
					sentenceWord.wordPos, "", "", 0, 0, "", "", rating_tolerance);
			Iterator<SentenceWordform> iterator = tempWordformList.iterator();
			if (iterator.hasNext()) {
				sentenceWord.sentenceWordform = iterator.next();
			}
		}

		// update filter values and "preposition_id" in sentenceWordList
		generateSentenceParts(wordRelationGraph);

		division = divideSentence();
		if (division != null)
			if (markAdverbialParticiple(division))
				division = divideSentence();

		databank.saveSentenceParts(sentenceWordList);

		if (division != null) {
			conjunctions = getConjunctions("и");

			subsentenceIterator = division.iterator();
			while (subsentenceIterator.hasNext()) {
				curSubsentence = subsentenceIterator.next();
				sentenceParts = gather(parseSubsentence(conjunctions, curSubsentence));
				if (!sentenceParts.isEmpty())
					databank.saveSentenceParts(sentenceParts);
			}
		}
		databank.saveSentenceWordLinkList(wordLinkList);
		databank.saveSentenceWordRelationList(wordRelationGraph.getSet());
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
				Integer.toString(subsentenceID), 0,
				String.valueOf(WordProcessor.verb_adverbial_participle));
		if (!adverbialParticipleList.isEmpty()) {
			ArrayList<SentenceWordform> verbList = getVerbList(id, Integer.toString(subsentenceID),
					0, String.valueOf(WordProcessor.verb_active));
			if (verbList.isEmpty())
				return markAsInternal(id, adverbialParticipleList.iterator().next().wordPos, ",");
		}
		return false;
	}

	private ArrayList<ArrayList<SentenceWord>> parseSubsentence(
			ArrayList<SentenceWord> conjunctions, ArrayList<Integer> curSubsentence) {
		String subsentenceFilter;
		ArrayList<Integer> subsentencePart;
		ArrayList<ArrayList<SentenceWord>> curSentenceParts;
		ArrayList<ArrayList<SentenceWord>> sentenceParts = null;
		int size = curSubsentence.size();
		int curRating;
		int maxRating = -100 * size;
		int[] subsentenceDivisionMask = new int[size]; // new array of zeroes
		Iterator<ArrayList<Integer>> iterator;
		ArrayList<ArrayList<Integer>> subsentenceDivision = new ArrayList<ArrayList<Integer>>();
		do {
			curSentenceParts = new ArrayList<ArrayList<SentenceWord>>();
			subsentenceDivision = makeSubsentenceDivision(curSubsentence, subsentenceDivisionMask);
			iterator = subsentenceDivision.iterator();
			while (iterator.hasNext()) {
				subsentencePart = iterator.next();
				subsentenceFilter = convertArrayToFilter(subsentencePart);
				curSentenceParts.add(findSubjectPredicate(conjunctions, subsentenceFilter));
			}
			curRating = calculateSubsenteneRating(curSentenceParts);
			if (curRating > maxRating) {
				sentenceParts = curSentenceParts;
				maxRating = curRating;
			}
			subsentenceDivisionMask = getNextSubsentenceDivisionMask(subsentenceDivisionMask);
		} while (subsentenceDivisionMask != null);
		return sentenceParts;
	}

	private int calculateSubsenteneRating(ArrayList<ArrayList<SentenceWord>> curSentenceParts) {
		int result = 0;
		for (ArrayList<SentenceWord> subsentence : curSentenceParts) {
			// bonus rating for each subsentence
			if (subsentence.size() > 0)
				result += 10;
			int maxSubjectRating = 0;
			int maxPredicateRating = 0;
			int rating = 0;
			SentenceWord subject = null;
			SentenceWord predicate = null;
			for (SentenceWord sentencePart : subsentence) {
				if ((sentencePart.part == SentenceWord.subject)
						& (sentencePart.sentenceWordform.rating > maxSubjectRating)) {
					maxSubjectRating = sentencePart.sentenceWordform.rating;
					subject = sentencePart;
				}
				if ((sentencePart.part == SentenceWord.predicate)
						& (sentencePart.sentenceWordform.rating > maxPredicateRating)) {
					maxPredicateRating = sentencePart.sentenceWordform.rating;
					predicate = sentencePart;
				}
			}
			if (subject != null) {
				rating += subject.sentenceWordform.rating - subject.sentenceWordform.maxrating;
			}
			if (predicate != null) {
				rating += predicate.sentenceWordform.rating - predicate.sentenceWordform.maxrating;
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

	private ArrayList<SentenceWord> findSubjectPredicate(ArrayList<SentenceWord> conjunctions,
			String subsentenceFilter) {
		ArrayList<SentenceWordform> subjectList;
		Iterator<SentenceWordform> subjectIterator;
		SentenceWordform subjectWordform;
		SentenceWord subject;
		ArrayList<SentenceWordform> predicateList;
		Iterator<SentenceWordform> predicateIterator;
		SentenceWordform predicateWordform;
		SentenceWord predicate;
		ArrayList<SentenceWord> sentenceParts;
		Iterator<SentenceWord> conjunctionIterator;
		SentenceWord conjunction;
		SentenceWordform subject2Wordform;
		SentenceWord subject2;
		boolean success = false;
		boolean conjunctionFound;
		String personFilter;
		sentenceParts = new ArrayList<SentenceWord>();
		// получить потенциальные сказуемые, отсортированные по рейтингу
		predicateList = getPredicateList(subsentenceFilter, 0, "", 0, 0, rating_tolerance);
		predicateIterator = predicateList.iterator();
		while ((predicateIterator.hasNext()) & !success) {
			predicateWordform = predicateIterator.next();
			predicate = getSentenceWord(predicateWordform);
			predicate.part = SentenceWord.predicate;
			// получить для каждого сказуемого, потенциальные подлежащие,
			// отсортированные по рейтингу
			if (predicateWordform.person > 0)
				personFilter = String.valueOf(predicateWordform.person);
			else
				personFilter = ">0";
			subjectList = getSubjectList(subsentenceFilter, predicate.wordPos, personFilter,
					predicateWordform.gender, predicateWordform.sing_pl, rating_tolerance);
			subjectIterator = subjectList.iterator();
			// выбрать первую пару
			if (subjectIterator.hasNext()) {
				subjectWordform = subjectIterator.next();
				subject = getSentenceWord(subjectWordform);
				subject.part = SentenceWord.subject;
				sentenceParts.add(subject);
				sentenceParts.add(predicate);

				// поиск подлежащего из двух слов, связанных союзом И
				if (!conjunctions.isEmpty()) {
					while (subjectIterator.hasNext()) {
						subject2Wordform = subjectIterator.next();
						if (subject2Wordform.wordPos != subject.wordPos) {
							subject2 = getSentenceWord(subject2Wordform);
							subject2.part = SentenceWord.subject;
							conjunctionIterator = conjunctions.iterator();
							conjunctionFound = false;
							while (conjunctionIterator.hasNext()) {
								conjunction = conjunctionIterator.next();
								// варианты взаимного расположения
								// подлежащее1 И подлежащее2 сказуемое
								if ((subject.wordPos < conjunction.wordPos)
										& (subject2.wordPos > conjunction.wordPos)
										& (subject2.wordPos < predicateWordform.wordPos)) {
									conjunctionFound = true;
									break;
								}
								// подлежащее2 И подлежащее1 сказуемое
								if ((subject2.wordPos < conjunction.wordPos)
										& (subject.wordPos > conjunction.wordPos)
										& (subject.wordPos < predicate.wordPos)) {
									conjunctionFound = true;
									break;
								}
								// сказуемое подлежащее1 И подлежащее2
								if ((subject.wordPos < conjunction.wordPos)
										& (subject2.wordPos > conjunction.wordPos)
										& (subject.wordPos > predicate.wordPos)) {
									conjunctionFound = true;
									break;
								}
								// сказуемое подлежащее2 И подлежащее1
								if ((subject2.wordPos < conjunction.wordPos)
										& (subject.wordPos > conjunction.wordPos)
										& (subject2.wordPos > predicate.wordPos)) {
									conjunctionFound = true;
									break;
								}
							}
							if (conjunctionFound) {
								sentenceParts.add(subject2);
								break;
							}
						}
					}
				}
				success = true;
			}
		}
		return sentenceParts;
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

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить список предлогов
		prepositionList = getSentencePartList("", 0, "", "", 0, 0,
				String.valueOf(WordProcessor.preposition), "", 1);
		prepositionIterator = prepositionList.iterator();

		// для каждого предлога ищем следующие за ним прилагательные или существительные
		while (prepositionIterator.hasNext()) {
			prepositionWordform = prepositionIterator.next();
			// собираем существительные или прилагательные на позиции за предлогом не в именительном
			// падеже
			substantiveList = getSubstantiveList(id,
					wordRelationGraph.getNextIndependentWordPos(prepositionWordform.wordPos), ">1",
					"", 0, 0, rating_tolerance);
			if (!substantiveList.isEmpty()) {
				substantiveIterator = substantiveList.iterator();
				while (substantiveIterator.hasNext()) {
					substantiveWordform = substantiveIterator.next();
					wordRelation = new SentenceWordRelation(relationCount + 1, 0,
							substantiveWordform, prepositionWordform, relationType);
					if (!wordRelationGraph.existWordRelation(wordRelation)) {
						wordRelationGraph.add(wordRelation);
						relationCount++;
					}
				}
			} else {
				// проверяем, есть ли другие подходящие словоформы кроме предлога
				prepAlternativeList = getSentencePartList("", prepositionWordform.wordPos, "", "",
						0, 0, "<>" + String.valueOf(WordProcessor.preposition), "", 1);
				prepAlternativeIterator = prepAlternativeList.iterator();
				while (prepAlternativeIterator.hasNext()) {
					prepAlternativeWordform = prepAlternativeIterator.next();
					wordRelation = new SentenceWordRelation(++relationCount, 0,
							prepAlternativeWordform, relationType);
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

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// find wordPos of negative
		negatives = getNegatives();
		negativeIterator = negatives.iterator();
		while (negativeIterator.hasNext()) {
			negativeWordform = negativeIterator.next();
			nextWordforms = getNextWordforms(id, negativeWordform.wordPos, rating_tolerance);
			nextWordformIterator = nextWordforms.iterator();
			while (nextWordformIterator.hasNext()) {
				nextWordform = nextWordformIterator.next();
				wordRelation = new SentenceWordRelation(relationCount + 1, 0, nextWordform,
						negativeWordform, relationType);
				if (!wordRelationGraph.existWordRelation(wordRelation)) {
					wordRelationGraph.add(wordRelation);
					relationCount++;
				}
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

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить список числительных
		numeralList = getSentencePartList("", 0, ">0", ">0", 0, 0,
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
					String.valueOf(wcase), ">0", numeralPartWordform.gender, sing_pl,
					rating_tolerance);
			substantiveIterator = substantiveList.iterator();
			while (substantiveIterator.hasNext()) {
				substantiveWordform = substantiveIterator.next();
				wordRelation = new SentenceWordRelation(relationCount + 1, 0, numeralPartWordform,
						substantiveWordform, relationType);
				if (!wordRelationGraph.existWordRelation(wordRelation)) {
					wordRelationGraph.add(wordRelation);
					relationCount++;
				}
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

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить существительные
		mainSubstantiveList = getSubstantiveList(id, 0, ">0", ">0", 0, 0, 1);
		mainSubstantiveIterator = mainSubstantiveList.iterator();

		// для каждого существительного в родительном падеже получить предшествующее существительное
		while (mainSubstantiveIterator.hasNext()) {
			mainSubstantiveWordform = mainSubstantiveIterator.next();
			genetiveSubstantiveList = getSubstantiveList(id,
					wordRelationGraph.getNextIndependentWordPos(mainSubstantiveWordform.wordPos),
					"2", ">0", 0, 0, 1);
			genetiveSubstantiveIterator = genetiveSubstantiveList.iterator();
			if (genetiveSubstantiveIterator.hasNext()) {
				genetiveSubstantiveWordform = genetiveSubstantiveIterator.next();
				wordRelation = new SentenceWordRelation(relationCount + 1, 0,
						mainSubstantiveWordform, genetiveSubstantiveWordform, relationType);
				if (!wordRelationGraph.existWordRelation(wordRelation)) {
					wordRelationGraph.add(wordRelation);
					relationCount++;
				}
			}
		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseComplexPredicate() {
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		ArrayList<SentenceWordform> infinitiveList;
		Iterator<SentenceWordform> infinitiveIterator;
		SentenceWordform infinitiveWordform;

		int relationType = SentenceWordRelation.verbInfinitive;
		SentenceWordRelation wordRelation;

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить глаголы в инфинитиве
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			infinitiveList = getVerbList(id, "",
					wordRelationGraph.getNextIndependentWordPos(verbWordform.wordPos),
					String.valueOf(WordProcessor.verb_infinitive));
			infinitiveIterator = infinitiveList.iterator();
			while (infinitiveIterator.hasNext()) {
				infinitiveWordform = infinitiveIterator.next();
				wordRelation = new SentenceWordRelation(++relationCount, 0, verbWordform,
						infinitiveWordform, relationType);
				if (!wordRelationGraph.existWordRelation(wordRelation)) {
					wordRelationGraph.add(wordRelation);
					relationCount++;
					markLinkedWords(wordRelationGraph, wordRelation, infinitiveWordform,
							verbWordform.wordPos);
				}
			}
		}
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseAdverbs() {
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		ArrayList<SentenceWordform> adverbList;
		Iterator<SentenceWordform> adverbsIterator;
		SentenceWordform adverbWordform;

		int relationType = SentenceWordRelation.verbAdverb;
		SentenceWordRelation wordRelation;

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить наречия
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			adverbList = getAdverbList(id,
					wordRelationGraph.getPrevIndependentWordPos(verbWordform.wordPos));
			// если есть связанный инфинитив, то относим наречия к нему
			if (!wordRelationGraph.existDependence(verbWordform.wordPos,
					SentenceWordRelation.verbInfinitive))
				adverbList.addAll(getAdverbList(id,
						wordRelationGraph.getNextIndependentWordPos(verbWordform.wordPos)));
			adverbsIterator = adverbList.iterator();
			while (adverbsIterator.hasNext()) {
				adverbWordform = adverbsIterator.next();
				wordRelation = new SentenceWordRelation(relationCount + 1, 0, verbWordform,
						adverbWordform, relationType);
				if (!wordRelationGraph.existWordRelation(wordRelation)) {
					wordRelationGraph.add(wordRelation);
					relationCount++;
					markLinkedWords(wordRelationGraph, wordRelation, adverbWordform,
							verbWordform.wordPos);
				}
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

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить глаголы в действительной форме или инфинитиве
		verbList = getVerbList(id, "", 0, "");
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
				// именительном
				// падеже
				curWordPos = wordRelationGraph.getNextIndependentWordPos(verbWordform.wordPos);
				if (curWordPos > 0)
					if (wordRelationGraph.existDependence(curWordPos,
							SentenceWordRelation.preposition)
							| (getSubstantiveList(id, curWordPos, "1", personFilter, mainGender,
									mainSing_Pl, 1).isEmpty())) {
						substantiveList = getSubstantiveList(id, curWordPos, ">1", ">0", 0, 0, 1);
						substantiveIterator = substantiveList.iterator();
						while (substantiveIterator.hasNext()) {
							substantiveWordform = substantiveIterator.next();
							wordRelation = new SentenceWordRelation(relationCount + 1, 0,
									verbWordform, substantiveWordform, relationType);
							if ((!wordRelationGraph.existWordRelation(wordRelation))) {
								wordRelationGraph.add(wordRelation);
								relationCount++;
								markLinkedWords(wordRelationGraph, wordRelation,
										substantiveWordform, verbWordform.wordPos);
							}
						}
					}
			}
		}
		wordRelationGraph.cleanWordRelationList(relationType);
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

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// получить прилагательные и местоимения прилагательные
		attributeList = getAdjectiveList(id, 0, ">0", 0, 0, 1);
		attributeIterator = attributeList.iterator();

		// для каждого прилагательного получить предшествующие наречия
		while (attributeIterator.hasNext()) {
			attributeWordform = attributeIterator.next();
			if ((attributeWordform.type == WordProcessor.adjective)
					&& !wordRelationGraph.existDependence(attributeWordform.wordPos,
							SentenceWordRelation.preposition)) {
				adverbList = getAdverbList(id,
						wordRelationGraph.getPrevIndependentWordPos(attributeWordform.wordPos));
				adverbsIterator = adverbList.iterator();
				if (adverbsIterator.hasNext()) {
					adverbWordform = adverbsIterator.next();
					wordRelation = new SentenceWordRelation(relationCount + 1, 0,
							attributeWordform, adverbWordform, relationType);
					if (!wordRelationGraph.existWordRelation(wordRelation)) {
						wordRelationGraph.add(wordRelation);
						relationCount++;
						markLinkedWords(wordRelationGraph, wordRelation, adverbWordform,
								attributeWordform.wordPos);
					}
				}
			}
		}
		wordRelationGraph.cleanWordRelationList(relationType);
		wordRelationGraph.changeWordRelationStatus(relationType);
	}

	private void parseAttributes() {
		ArrayList<SentenceWordform> adjectiveList;
		Iterator<SentenceWordform> adjectiveIterator;
		SentenceWordform adjectiveWordform;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;

		int relationType = SentenceWordRelation.attribute;
		SentenceWordRelation wordRelation;
		SentenceWordRelation curWordRelation;
		SentenceWordRelationGraph curWordRelationGraph = new SentenceWordRelationGraph(id,
				sentenceWordList.size());

		boolean found;
		int curWordPos;
		int curWordRelationId;
		int curPrepositionPos;

		sentenceWordFilter = wordRelationGraph.generateSentenceWordFilter();

		// find possible substantives
		// possible substantive: wordform that has an substantive as a word
		// with maximal rating
		substantiveList = getSubstantiveList(id, 0, ">0", ">0", 0, 0, rating_tolerance);
		substantiveIterator = substantiveList.iterator();

		// find possible adjectives that have common form with substantive
		// possible adjective: wordform that has an adjective as a word with
		// maximal rating
		while (substantiveIterator.hasNext()) {
			substantiveWordform = substantiveIterator.next();
			curPrepositionPos = wordRelationGraph
					.getPrepositionWordPos(substantiveWordform.wordPos);
			if (substantiveWordform.wordPos > 1) {
				// start from substantive position
				curWordPos = substantiveWordform.wordPos;
				curWordRelationId = 0;
				found = true;
				// try to find adjectives with the same properties to the left
				while (found && (curWordPos > 1)) {
					found = false;
					// curWordPos--;
					curWordPos = wordRelationGraph.getPrevIndependentWordPos(curWordPos);
					if ((curPrepositionPos == 0)
							| (curPrepositionPos == wordRelationGraph
									.getPrepositionWordPos(curWordPos))) {
						curPrepositionPos = wordRelationGraph.getPrepositionWordPos(curWordPos);
						adjectiveList = getAdjectiveList(id, curWordPos,
								String.valueOf(substantiveWordform.wcase),
								substantiveWordform.gender, substantiveWordform.sing_pl,
								rating_tolerance);
						adjectiveIterator = adjectiveList.iterator();
						if (adjectiveIterator.hasNext()) {
							found = true;
							adjectiveWordform = adjectiveIterator.next();
							// mark, that adjective is dependent on substantive
							wordRelation = new SentenceWordRelation(relationCount + 1,
									curWordRelationId, substantiveWordform, adjectiveWordform,
									relationType);
							if (!wordRelationGraph.existWordRelation(wordRelation)
									&& !curWordRelationGraph.existWordRelation(wordRelation)) {
								curWordRelationGraph.add(wordRelation);
								relationCount++;
								// mark any linked adjective
								markLinkedWords(curWordRelationGraph, wordRelation,
										adjectiveWordform, substantiveWordform.wordPos);

								// find leftmost dependent adjective
								curWordRelation = curWordRelationGraph
										.getLastWordRelation(wordRelation);
								curWordRelationId = curWordRelation.id;
								curWordPos = curWordRelation.word2Pos;

								// start search only if leftmost dependent adjective is before
								// substantive
								found = curWordPos < wordRelation.word1Pos;
							}
						}
					}
				}
			}
		}
		curWordRelationGraph.cleanWordRelationList(relationType);
		relationCount = wordRelationGraph.movePrepositionRelations(curWordRelationGraph,
				relationCount);
		wordRelationGraph.addAll(curWordRelationGraph);
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
				nextWordformIterator = nextWordforms.iterator();
				while (nextWordformIterator.hasNext()) {
					nextWordform = nextWordformIterator.next();
					// существительные и местоимения существительные
					if ((prevWordform.wcase > 0) & (prevWordform.person > 0))
						if ((prevWordform.wcase == nextWordform.wcase) & (nextWordform.person > 0)) {
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
										| (prevWordform.sing_pl == 0) | (nextWordform.sing_pl == 0))) {
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
								& (prevWordform.subtype == nextWordform.subtype)) {
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
			SentenceWordRelation wordRelation, SentenceWordform dependentWord, int wordPos) {
		ArrayList<SentenceWordform> linkedWordList;
		Iterator<SentenceWordform> linkedWordIterator;
		SentenceWordform linkedWordform;
		SentenceWordform conjunctionWordform;
		SentenceWordRelation linkedWordRelation;
		SentenceWordRelation conjunctionWordRelation;
		boolean createConjunctionRelation;
		int curRelationCount;
		HashMap<Integer, SentenceWordRelation> conjunctionWordRelationbyWordPos = new HashMap<Integer, SentenceWordRelation>();
		linkedWordList = getLinkedWordList(dependentWord.wordPos, dependentWord.type,
				dependentWord.subtype, dependentWord.wcase, dependentWord.person,
				dependentWord.gender, dependentWord.sing_pl);
		linkedWordIterator = linkedWordList.iterator();
		while (linkedWordIterator.hasNext()) {
			linkedWordform = linkedWordIterator.next();
			curRelationCount = relationCount;
			if (wordRelation.word1Pos != linkedWordform.wordPos) {
				createConjunctionRelation = !conjunctionWordRelationbyWordPos
						.containsKey(linkedWordform.wordPos);
				if (createConjunctionRelation) {
					conjunctionWordform = getConjunction(dependentWord.wordPos,
							linkedWordform.wordPos);
					conjunctionWordRelation = new SentenceWordRelation(++curRelationCount,
							wordRelation, conjunctionWordform, SentenceWordRelation.conjunction);
					// temporary add conjunctionWordRelation to create dependency chain for
					// linkedWordRelation
					wordRelationGraph.add(conjunctionWordRelation);
				} else
					conjunctionWordRelation = conjunctionWordRelationbyWordPos
							.get(linkedWordform.wordPos);

				linkedWordRelation = new SentenceWordRelation(++curRelationCount,
						conjunctionWordRelation, linkedWordform, wordRelation.relationType);

				if (!wordRelationGraph.existWordRelation(linkedWordRelation)) {
					if (createConjunctionRelation)
						conjunctionWordRelationbyWordPos.put(linkedWordform.wordPos,
								conjunctionWordRelation);
					wordRelationGraph.add(linkedWordRelation);
					relationCount = curRelationCount;
					markLinkedWords(wordRelationGraph, linkedWordRelation, linkedWordform, wordPos);
				} else if (createConjunctionRelation)
					wordRelationGraph.remove(conjunctionWordRelation);
			}
		}
	}

	private ArrayList<SentenceWord> generateSentenceParts(
			SentenceWordRelationGraph wordRelationGraph) {
		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();
		SentenceWord sentencePart1;
		SentenceWord sentencePart2;
		for (SentenceWordRelation wordRelation : wordRelationGraph.getSet()) {
			sentencePart1 = getSentenceWord(sentenceWordList, wordRelation.word1Pos);
			sentencePart1.addValuestoFilters(wordRelation, 1);
			if (!sentenceParts.contains(sentencePart1))
				sentenceParts.add(sentencePart1);
			if (wordRelation.word2Pos != 0) {
				sentencePart2 = getSentenceWord(sentenceWordList, wordRelation.word2Pos);
				sentencePart2.addValuestoFilters(wordRelation, 2);
				sentencePart2.dep_word_pos = wordRelation.word1Pos;
				if (wordRelation.relationType == SentenceWordRelation.preposition)
					sentencePart1.preposition_id = sentencePart2.sentenceWordform.word_id;
				if (!sentenceParts.contains(sentencePart2))
					sentenceParts.add(sentencePart2);
			}
		}

		return sentenceParts;
	}

	private ArrayList<SentenceWordform> getAdjectiveList(int sentence_id, int wordPos,
			String wcaseFilter, int gender, int sing_pl, double rating_tolerance) {
		return getSentencePartList("", wordPos, wcaseFilter, "0", gender, sing_pl, "", "",
				rating_tolerance);
	}

	private ArrayList<SentenceWordform> getAdverbList(int sentence_id, int wordPos) {
		ArrayList<SentenceWordform> adverbList = new ArrayList<SentenceWordform>();
		adverbList.addAll(getSentencePartList("", wordPos, "", "", 0, 0,
				String.valueOf(WordProcessor.adjective),
				String.valueOf(WordProcessor.adjective_adverb), 1));
		adverbList.addAll(getSentencePartList("", wordPos, "", "", 0, 0,
				String.valueOf(WordProcessor.adverb), "", 1));
		return adverbList;
	}

	private ArrayList<SentenceWordform> getSubstantiveList(int sentence_id, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl,
			double rating_tolerance) {
		return getSentencePartList("", wordPos, wcaseFilter, personFilter, gender, sing_pl, "", "",
				rating_tolerance);
	}

	private ArrayList<SentenceWordform> getVerbList(int sentence_id, String subsentenceFilter,
			int wordPos, String subtypeFilter) {
		return getSentencePartList(subsentenceFilter, wordPos, "", "", 0, 0,
				String.valueOf(WordProcessor.verb), subtypeFilter, 1);
	}

	private ArrayList<SentenceWordform> getPrevWordforms(int sentence_id, int wordPos,
			double rating_tolerance) {
		return getSentencePartList("", wordRelationGraph.getPrevIndependentWordPos(wordPos), "",
				"", 0, 0, "", "", rating_tolerance);
	}

	private ArrayList<SentenceWordform> getNextWordforms(int sentence_id, int wordPos,
			double rating_tolerance) {
		return getSentencePartList("", wordRelationGraph.getNextIndependentWordPos(wordPos), "",
				"", 0, 0, "", "", rating_tolerance);
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
					linkedWords.addAll(getSentencePartList("", tempWordPos, String.valueOf(wcase),
							">0", 0, 0, "", "", 1));
				// прилагательные и местоимения прилагательные
				if ((wcase > 0) & (person == 0))
					linkedWords.addAll(getSentencePartList("", tempWordPos, String.valueOf(wcase),
							String.valueOf(person), gender, sing_pl, "", "", 1));
				// прочие
				if ((wcase == 0) && (type == wordLink.type) && (subtype == wordLink.subtype))
					linkedWords.addAll(getSentencePartList("", tempWordPos, String.valueOf(wcase),
							String.valueOf(person), gender, sing_pl, String.valueOf(type),
							String.valueOf(subtype), 1));
			}

		return linkedWords;
	}

	private SentenceWordform getConjunction(int wordPos, int linkWordPos) {
		for (SentenceWordLink wordLink : wordLinkList)
			if ((id == wordLink.sentenceID)
					&& ((wordPos == wordLink.wordPos) && (linkWordPos == wordLink.linkWordPos))
					| ((wordPos == wordLink.linkWordPos) && (linkWordPos == wordLink.wordPos)))
				return new SentenceWordform(id, 0, wordLink.conjunctionWordPos,
						WordProcessor.conjunction, 0, 0, 0, 0, 0, 0, 0, 0, 0, "", "", "", "", 0, 0);
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
				result.addAll(getSentencePartList("", sentenceWord.wordPos, "", "", 0, 0,
						String.valueOf(WordProcessor.particle), "", 1));
		return result;
	}

	private ArrayList<SentenceWordform> getSentencePartList(String subsentenceFilter, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl, String typeFilter,
			String subtypeFilter, double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();

		if (wordPos < 0)
			return result;

		SentenceWord sentenceWord;
		for (SentenceWordform sentenceWordform : sentenceWordformList)
			if ((wordPos == 0 | sentenceWordform.wordPos == wordPos)
					&& ((100 - sentenceWordform.rating) <= (100 - sentenceWordform.maxrating)
							* rating_tolerance)
					&& (sentenceWordform.rating * rating_tolerance >= sentenceWordform.maxrating)
					&& DataBank.checkFilter(sentenceWordform.wcase, wcaseFilter)
					&& DataBank.checkFilter(sentenceWordform.person, personFilter)
					&& (gender == 0 | sentenceWordform.gender == 0 | sentenceWordform.gender == gender)
					&& (sing_pl == 0 | sentenceWordform.sing_pl == 0 | sentenceWordform.sing_pl == sing_pl)
					&& DataBank.checkFilter(sentenceWordform.type, typeFilter)
					&& DataBank.checkFilter(sentenceWordform.subtype, subtypeFilter)) {
				sentenceWord = getSentenceWord(sentenceWordList, sentenceWordform.wordPos);
				// if (((sentenceWordform.wcase != 1) | ((sentenceWordform.wcase == 1) &
				// (sentenceWord.preposition_id == 0)))
				// && DataBank.checkFilter(sentenceWord.subsentenceID, subsentenceFilter))
				if (DataBank.checkFilter(sentenceWord.subsentenceID, subsentenceFilter))
					if (sentenceWordFilter[sentenceWordform.wordPos] == null)
						result.add(sentenceWordform);
					else if (DataBank.checkFilter(sentenceWordform.type,
							sentenceWordFilter[sentenceWordform.wordPos].typeFilter)
							&& DataBank.checkFilter(sentenceWordform.wcase,
									sentenceWordFilter[sentenceWordform.wordPos].wcaseFilter)
							&& DataBank.checkFilter(sentenceWordform.gender,
									sentenceWordFilter[sentenceWordform.wordPos].genderFilter)
							&& DataBank.checkFilter(sentenceWordform.sing_pl,
									sentenceWordFilter[sentenceWordform.wordPos].sing_plFilter))
						result.add(sentenceWordform);
			}
		return result;
	}

	private ArrayList<SentenceWordform> getSubjectList(String subsentenceFilter, int predicatePos,
			String personFilter, int gender, int sing_pl, double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();
		SentenceWord sentenceWord;
		for (SentenceWordform sentenceWordform : sentenceWordformList)
			if ((sentenceWordform.wordPos != predicatePos)
					&& ((100 - sentenceWordform.rating) <= (100 - sentenceWordform.maxrating)
							* rating_tolerance)
					&& (sentenceWordform.rating * rating_tolerance >= sentenceWordform.maxrating)
					&& (sentenceWordform.wcase == 1)
					&& DataBank.checkFilter(sentenceWordform.person, personFilter)
					&& (gender == 0 | sentenceWordform.gender == 0 | sentenceWordform.gender == gender)
					&& (sing_pl == 0 | sentenceWordform.sing_pl == 0 | sentenceWordform.sing_pl == sing_pl)) {
				sentenceWord = getSentenceWord(sentenceWordList, sentenceWordform.wordPos);
				if ((!wordRelationGraph.existDependence(sentenceWordform.wordPos,
						SentenceWordRelation.preposition))
						&& (sentenceWord.dep_word_pos == 0)
						&& DataBank.checkFilter(sentenceWord.subsentenceID, subsentenceFilter))
					result.add(sentenceWordform);
			}
		return result;
	}

	private ArrayList<SentenceWordform> getPredicateList(String subsentenceFilter, int subjectPos,
			String personFilter, int gender, int sing_pl, double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();
		SentenceWord sentenceWord;
		for (SentenceWordform sentenceWordform : sentenceWordformList)
			if ((sentenceWordform.wordPos != subjectPos)
					&& ((100 - sentenceWordform.rating) <= (100 - sentenceWordform.maxrating)
							* rating_tolerance)
					&& (sentenceWordform.rating * rating_tolerance >= sentenceWordform.maxrating)
					&& (sentenceWordform.wcase == 0)
					&& (sentenceWordform.type == 2)
					&& (sentenceWordform.subtype == 1)
					&& DataBank.checkFilter(sentenceWordform.person, personFilter)
					&& (gender == 0 | sentenceWordform.gender == 0 | sentenceWordform.gender == gender)
					&& (sing_pl == 0 | sentenceWordform.sing_pl == 0 | sentenceWordform.sing_pl == sing_pl)) {
				sentenceWord = getSentenceWord(sentenceWordList, sentenceWordform.wordPos);
				if (!wordRelationGraph.existDependence(sentenceWordform.wordPos,
						SentenceWordRelation.preposition)
				// if ((sentenceWord.preposition_id == 0)
						&& DataBank.checkFilter(sentenceWord.subsentenceID, subsentenceFilter))
					result.add(sentenceWordform);
			}
		return result;
	}

	private SentenceWord getSentenceWord(SentenceWordform sentenceWordform) {
		SentenceWord result = getSentenceWord(sentenceWordList, sentenceWordform.wordPos);
		result.sentenceWordform = sentenceWordform;
		return result;
	}

	private SentenceWord getSentenceWord(ArrayList<SentenceWord> sentenceWordList, int wordPos) {
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.wordPos == wordPos)
				return sentenceWord;
		return null;
	}

	private boolean markAsInternal(int sentence_id, int wordPos, String punctuation) {
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
			if ((!leftPunctuation.internal) && (leftPunctuation.word.equals(punctuation))) {
				leftPunctuation.internal = true;
				result = true;
			}

		if (rightPunctuation != null)
			if ((!rightPunctuation.internal) && (rightPunctuation.word.equals(punctuation))) {
				rightPunctuation.internal = true;
				result = true;
			}
		return result;
	}

	private ArrayList<ArrayList<Integer>> divideSentence() {
		ArrayList<ArrayList<Integer>> division = new ArrayList<ArrayList<Integer>>();
		ArrayList<Integer> curSubsentence = new ArrayList<Integer>();
		int subsentence_id;
		char[] canNotParseMarks;
		canNotParseMarks = databank.getPunctuationMarksNotReady().toCharArray();
		subsentence_id = 1;
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.isPunctuation) {
				for (int i = 0; i < canNotParseMarks.length; i++)
					if (sentenceWord.word.indexOf(canNotParseMarks[i]) >= 0)
						return null;
				curSubsentence.add(new Integer(subsentence_id));
				if (!sentenceWord.internal) {
					division.add(curSubsentence);
					curSubsentence = new ArrayList<Integer>();
				}
				subsentence_id++;
			} else
				sentenceWord.subsentenceID = subsentence_id;
		databank.setSentenceType(id, 1);
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

	private ArrayList<SentenceWord> gather(ArrayList<ArrayList<SentenceWord>> arraylist) {
		ArrayList<SentenceWord> result = new ArrayList<SentenceWord>();
		Iterator<ArrayList<SentenceWord>> iterator;
		iterator = arraylist.iterator();
		while (iterator.hasNext())
			result.addAll(iterator.next());
		return result;
	}
}
