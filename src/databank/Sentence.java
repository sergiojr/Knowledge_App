package databank;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;

import knowledge_app.WordProcessor;

public class Sentence {
	// rating_tolerance - max allowed difference between (100-rating) and (100-maxraring) for
	// wordform
	static double rating_tolerance = 1.5;
	private String sentence;
	private ArrayList<SentenceWord> sentenceWordList;
	private ArrayList<SentenceWordform> sentenceWordformList;
	ArrayList<SentenceWordRelation> wordRelationList;
	private SentenceWordFilter[] sentenceWordFilter;
	ArrayList<SentenceWordLink> wordLinkList;

	private int id;
	private int type;
	private int wordCount;
	private int relationCount;
	private ArrayList<ArrayList<Integer>> division;
	private DataBank databank;

	public Sentence(DataBank databank, int id, String sentence,
			ArrayList<SentenceWord> sentenceWordList) {
		this.databank = databank;
		this.id = id;
		this.sentence = sentence;
		this.sentenceWordList = sentenceWordList;
		this.relationCount = 0;
		this.wordCount = sentenceWordList.size();
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
		wordRelationList = new ArrayList<SentenceWordRelation>();
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
		parseAttributes();
		parseConjunctions();
		parseComplexPredicate();
		parseAdverbs();
		parseVerbControlledSubstantives();
		parseConjunctions();
		// parseGenetiveSubstantives();

		sentenceParts = generateSentenceParts(wordRelationList);
		databank.saveSentenceParts(sentenceParts);
		
		
		// get best wordform
		try {
			databank.FillBestMatch(id);
		} catch (SQLException e) {
			e.printStackTrace();
		}

		division = divideSentence(id);
		if (division == null)
			return;

		if (markAdverbialParticiple(division))
			division = divideSentence(id);

		// save "subsentence_id" and "internal" mark
		databank.updateSentenceWordList(sentenceWordList);

		conjunctions = getConjunctions("и");

		subsentenceIterator = division.iterator();
		while (subsentenceIterator.hasNext()) {
			curSubsentence = subsentenceIterator.next();
			sentenceParts = gather(parseSubsentence(conjunctions, curSubsentence));
			if (!sentenceParts.isEmpty())
				databank.saveSentenceParts(sentenceParts);
		}
		databank.saveSentenceWordLinkList(wordLinkList);
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
				Integer.toString(subsentenceID), 0, "2");
		if (!adverbialParticipleList.isEmpty()) {
			ArrayList<SentenceWordform> verbList = getVerbList(id, Integer.toString(subsentenceID),
					0, "1");
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
		SentenceWord substantive;

		SentenceWordRelation wordRelation;
		int relationType = SentenceWordRelation.preposition;

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить список предлогов
		prepositionList = getSentencePartList("", 0, "", "", 0, 0,
				String.valueOf(WordProcessor.preposition), "", 1);
		prepositionIterator = prepositionList.iterator();

		// для каждого предлога ищем следующие за ним прилагательные или существительные
		while (prepositionIterator.hasNext()) {
			prepositionWordform = prepositionIterator.next();
			// собираем существительные или прилагательные на позиции за предлогом не в именительном
			// падеже
			substantiveList = getSubstantiveList(id, prepositionWordform.wordPos + 1, ">1", "", 0,
					0);
			if (!substantiveList.isEmpty()) {
				substantiveIterator = substantiveList.iterator();
				if (substantiveIterator.hasNext()) {
					substantive = getSentenceWord(sentenceWordList,
							substantiveIterator.next().wordPos);
					substantive.preposition_id = prepositionWordform.word_id;
					wordRelation = new SentenceWordRelation(++relationCount, 0, id,
							substantive.wordPos, 0, 0, 0, 0, prepositionWordform.wordPos,
							prepositionWordform.type, prepositionWordform.wcase,
							prepositionWordform.gender, prepositionWordform.sing_pl, relationType);
					wordRelationList.add(wordRelation);
				}
			} else {
				// проверяем, есть ли другие подходящие словоформы кроме предлога
				prepAlternativeList = getSentencePartList("", prepositionWordform.wordPos, "", "",
						0, 0, "<>" + String.valueOf(WordProcessor.preposition), "", 1);
				prepAlternativeIterator = prepAlternativeList.iterator();
				if (prepAlternativeIterator.hasNext()) {
					prepAlternativeWordform = prepAlternativeIterator.next();
					wordRelation = new SentenceWordRelation(++relationCount, 0, id,
							prepAlternativeWordform.wordPos, prepAlternativeWordform.type,
							prepAlternativeWordform.wcase, prepAlternativeWordform.gender,
							prepAlternativeWordform.sing_pl, 0, 0, 0, 0, 0, relationType);
					wordRelationList.add(wordRelation);

				}
			}
		}
	}

	private void parseNegative() {
		boolean found = false;
		boolean empty = true;
		int curWordPos;
		ArrayList<SentenceWordform> negatives;
		Iterator<SentenceWordform> negativeIterator;
		SentenceWordform negativeWordform;
		ArrayList<SentenceWordform> nextWordforms;
		Iterator<SentenceWordform> nextWordformIterator;
		SentenceWordform nextWordform = null;

		int relationType = SentenceWordRelation.negative;
		SentenceWordRelation wordRelation;

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// find wordPos of negative
		negatives = databank.getNegatives(id);
		negativeIterator = negatives.iterator();
		while (negativeIterator.hasNext()) {
			found = false;
			empty = false;
			negativeWordform = negativeIterator.next();
			curWordPos = negativeWordform.wordPos;
			while (!found && !empty) {
				empty = true;
				nextWordforms = getNextWordforms(id, curWordPos);
				nextWordformIterator = nextWordforms.iterator();
				while (!found && (nextWordformIterator.hasNext())) {
					empty = false;
					nextWordform = nextWordformIterator.next();
					found = (nextWordform.type != WordProcessor.preposition);
				}
				curWordPos++;
			}
			if (!empty && found && (nextWordform != null)) {
				wordRelation = new SentenceWordRelation(++relationCount, 0, id,
						nextWordform.wordPos, 0, 0, 0, 0, negativeWordform.wordPos,
						negativeWordform.type, negativeWordform.wcase, negativeWordform.gender,
						negativeWordform.sing_pl, relationType);
				wordRelationList.add(wordRelation);

			}
		}
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

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить список числительных
		numeralList = getSentencePartList("", 0, ">0", ">0", 0, 0,
				String.valueOf(WordProcessor.numeral), "", 1);
		numeralIterator = numeralList.iterator();

		// для каждого числительного найти существительное, следующее за ним
		while (numeralIterator.hasNext()) {
			numeralPartWordform = numeralIterator.next();
			if (!existWord1Relation(wordRelationList, numeralPartWordform.wordPos,
					numeralPartWordform.type, relationType)) {
				numeral = databank.getNumeralByWordID(numeralPartWordform.word_id);
				wcase = numeralPartWordform.wcase;
				sing_pl = numeralPartWordform.sing_pl;
				if (databank.isNumeralBaseForm(numeralPartWordform.word_id,
						numeralPartWordform.rule_id)) {
					wcase = numeral.getBaseWcase();
					sing_pl = numeral.getBaseSingPl();
				}
				substantiveList = getSubstantiveList(id, numeralPartWordform.wordPos + 1,
						String.valueOf(wcase), ">0", numeralPartWordform.gender, sing_pl);
				substantiveIterator = substantiveList.iterator();
				while (substantiveIterator.hasNext()) {
					substantiveWordform = substantiveIterator.next();
					if (!existWordRelation(wordRelationList, numeralPartWordform,
							substantiveWordform, relationType, 0)) {
						wordRelation = new SentenceWordRelation(++relationCount, 0, id,
								numeralPartWordform.wordPos, numeralPartWordform.type,
								numeralPartWordform.wcase, numeralPartWordform.gender,
								numeralPartWordform.sing_pl, substantiveWordform.wordPos,
								substantiveWordform.type, substantiveWordform.wcase,
								substantiveWordform.gender, substantiveWordform.sing_pl,
								relationType);
						wordRelationList.add(wordRelation);
					}
				}
			}
		}
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

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить существительные
		mainSubstantiveList = getSubstantiveList(id, 0, ">0", ">0", 0, 0);
		mainSubstantiveIterator = mainSubstantiveList.iterator();

		// для каждого существительного в родительном падеже получить предшествующее существительное
		while (mainSubstantiveIterator.hasNext()) {
			mainSubstantiveWordform = mainSubstantiveIterator.next();
			if (!existWord1Relation(wordRelationList, mainSubstantiveWordform.wordPos,
					mainSubstantiveWordform.type, relationType)) {
				genetiveSubstantiveList = getSubstantiveList(id,
						getNextIndependentWordPos(mainSubstantiveWordform.wordPos), "2", ">0", 0, 0);
				genetiveSubstantiveIterator = genetiveSubstantiveList.iterator();
				if (genetiveSubstantiveIterator.hasNext()) {
					genetiveSubstantiveWordform = genetiveSubstantiveIterator.next();
					if (!existWordRelation(wordRelationList, mainSubstantiveWordform,
							genetiveSubstantiveWordform, relationType, 0)) {
						wordRelation = new SentenceWordRelation(++relationCount, 0, id,
								mainSubstantiveWordform.wordPos, mainSubstantiveWordform.type,
								mainSubstantiveWordform.wcase, mainSubstantiveWordform.gender,
								mainSubstantiveWordform.sing_pl,
								genetiveSubstantiveWordform.wordPos,
								genetiveSubstantiveWordform.type,
								genetiveSubstantiveWordform.wcase,
								genetiveSubstantiveWordform.gender,
								genetiveSubstantiveWordform.sing_pl, relationType);
						wordRelationList.add(wordRelation);
					}
				}
			}
		}
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

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить глаголы в инфинитиве
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			if (!existWord1Relation(wordRelationList, verbWordform.wordPos, verbWordform.type,
					relationType)) {
				infinitiveList = getVerbList(id, "",
						getNextIndependentWordPos(verbWordform.wordPos), "0");
				infinitiveIterator = infinitiveList.iterator();
				if (infinitiveIterator.hasNext()) {
					infinitiveWordform = infinitiveIterator.next();
					if (!existWordRelation(wordRelationList, verbWordform, infinitiveWordform,
							relationType, 0)) {
						wordRelation = new SentenceWordRelation(++relationCount, 0, id,
								verbWordform.wordPos, verbWordform.type, verbWordform.wcase,
								verbWordform.gender, verbWordform.sing_pl,
								infinitiveWordform.wordPos, infinitiveWordform.type, 0, 0, 0,
								relationType);
						wordRelationList.add(wordRelation);
						markLinkedWords(wordRelationList, wordRelation, infinitiveWordform,
								verbWordform.wordPos);
					}
				}
			}
		}
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

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить наречия
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			if (!existWord1Relation(wordRelationList, verbWordform.wordPos, verbWordform.type,
					relationType)) {
				adverbList = getAdverbList(id, getPrevIndependentWordPos(verbWordform.wordPos));
				// если есть связанный инфинитив, то относим наречия к нему
				if (!existDependence(verbWordform.wordPos, SentenceWordRelation.verbInfinitive))
					adverbList.addAll(getAdverbList(id,
							getNextIndependentWordPos(verbWordform.wordPos)));
				adverbsIterator = adverbList.iterator();
				if (adverbsIterator.hasNext()) {
					adverbWordform = adverbsIterator.next();
					if (!existWordRelation(wordRelationList, verbWordform, adverbWordform,
							relationType, 0)) {
						wordRelation = new SentenceWordRelation(++relationCount, 0, id,
								verbWordform.wordPos, verbWordform.type, 0, 0, 0,
								adverbWordform.wordPos, adverbWordform.type, 0, 0, 0, relationType);
						wordRelationList.add(wordRelation);
						markLinkedWords(wordRelationList, wordRelation, adverbWordform,
								verbWordform.wordPos);
					}
				}
			}
		}
		cleanWordRelationList(wordRelationList, relationType);
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

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить глаголы в действительной форме или инфинитиве
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			if (!existWord1Relation(wordRelationList, verbWordform.wordPos, verbWordform.type,
					relationType)
					&& !existDependence(verbWordform.wordPos, SentenceWordRelation.verbInfinitive)) {
				mainVerbRelation = getMainVerbRelation(verbWordform.wordPos);
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
				curWordPos = getNextIndependentWordPos(verbWordform.wordPos);
				if (curWordPos > 0)
					if (existDependence(curWordPos, SentenceWordRelation.preposition)
							| (getSubstantiveList(id, curWordPos, "1", personFilter, mainGender,
									mainSing_Pl).isEmpty())) {
						substantiveList = getSubstantiveList(id, curWordPos, ">1", ">0", 0, 0);
						substantiveIterator = substantiveList.iterator();
						while (substantiveIterator.hasNext()) {
							substantiveWordform = substantiveIterator.next();
							if ((!existWordRelation(wordRelationList, verbWordform,
									substantiveWordform, relationType, 0))) {
								wordRelation = new SentenceWordRelation(++relationCount, 0, id,
										verbWordform.wordPos, verbWordform.type, 0, 0, 0,
										substantiveWordform.wordPos, substantiveWordform.type,
										substantiveWordform.wcase, substantiveWordform.gender,
										substantiveWordform.sing_pl, relationType);
								wordRelationList.add(wordRelation);
								markLinkedWords(wordRelationList, wordRelation,
										substantiveWordform, verbWordform.wordPos);
							}
						}
					}
			}
		}
		cleanWordRelationList(wordRelationList, relationType);
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

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// получить прилагательные и местоимения прилагательные
		attributeList = getAdjectiveList(id, 0, ">0", 0, 0);
		attributeIterator = attributeList.iterator();

		// для каждого прилагательного получить предшествующие наречия
		while (attributeIterator.hasNext()) {
			attributeWordform = attributeIterator.next();
			if ((attributeWordform.type == WordProcessor.adjective)
					&& (!existWord1Relation(wordRelationList, attributeWordform.wordPos,
							attributeWordform.type, relationType))
					&& !existDependence(attributeWordform.wordPos, SentenceWordRelation.preposition)) {
				adverbList = getAdverbList(id, getPrevIndependentWordPos(attributeWordform.wordPos));
				adverbsIterator = adverbList.iterator();
				if (adverbsIterator.hasNext()) {
					adverbWordform = adverbsIterator.next();
					if (!existWordRelation(wordRelationList, attributeWordform, adverbWordform,
							relationType, 0)) {
						wordRelation = new SentenceWordRelation(++relationCount, 0, id,
								attributeWordform.wordPos, attributeWordform.type, 0, 0, 0,
								adverbWordform.wordPos, adverbWordform.type, 0, 0, 0, relationType);
						wordRelationList.add(wordRelation);
						markLinkedWords(wordRelationList, wordRelation, adverbWordform,
								attributeWordform.wordPos);
					}
				}
			}
		}
		cleanWordRelationList(wordRelationList, relationType);
	}

	private void parseAttributes() {
		ArrayList<SentenceWordform> adjectiveList;
		Iterator<SentenceWordform> adjectiveIterator;
		SentenceWordform adjectiveWordform;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;
		ArrayList<SentenceWordform> linkedAdjectiveList;
		Iterator<SentenceWordform> linkedAdjectiveIterator;
		SentenceWordform linkedAdjectiveWordform;

		int relationType = SentenceWordRelation.attribute;
		SentenceWordRelation wordRelation;
		SentenceWordRelation curWordRelation;
		SentenceWordRelation linkedWordRelation;

		boolean found;
		int curWordPos;

		sentenceWordFilter = generateSentenceWordFilter(wordRelationList);

		// find possible substantives
		// possible substantive: wordform that has an substantive as a word
		// with maximal rating
		substantiveList = getSubstantiveList(id, 0, ">0", ">0", 0, 0);
		substantiveIterator = substantiveList.iterator();

		// find possible adjectives that have common form with substantive
		// possible adjective: wordform that has an adjective as a word with
		// maximal rating
		while (substantiveIterator.hasNext()) {
			substantiveWordform = substantiveIterator.next();
			if ((substantiveWordform.wordPos > 1)
					&& (!existWord1Relation(wordRelationList, substantiveWordform.wordPos,
							substantiveWordform.type, relationType))) {
				adjectiveList = getAdjectiveList(id, substantiveWordform.wordPos - 1,
						String.valueOf(substantiveWordform.wcase), substantiveWordform.gender,
						substantiveWordform.sing_pl);
				adjectiveIterator = adjectiveList.iterator();
				if (adjectiveIterator.hasNext()) {
					adjectiveWordform = adjectiveIterator.next();
					// mark, that adjective is dependent on substantive
					if (!existWordRelation(wordRelationList, substantiveWordform,
							adjectiveWordform, relationType, 0)) {
						wordRelation = new SentenceWordRelation(++relationCount, 0, id,
								substantiveWordform.wordPos, substantiveWordform.type,
								substantiveWordform.wcase, substantiveWordform.gender,
								substantiveWordform.sing_pl, adjectiveWordform.wordPos,
								adjectiveWordform.type, adjectiveWordform.wcase,
								adjectiveWordform.gender, adjectiveWordform.sing_pl, relationType);
						wordRelationList.add(wordRelation);
						movePrepositionRelation(wordRelation);

						// mark any linked adjective
						markLinkedWords(wordRelationList, wordRelation, adjectiveWordform,
								substantiveWordform.wordPos);

						// find leftmost dependent adjective
						curWordRelation = getLastWordRelation(wordRelationList, wordRelation);
						curWordPos = curWordRelation.word2Pos;

						// start search only if leftmost dependent adjective is before substantive
						found = curWordPos < wordRelation.word1Pos;

						// try to find adjectives with the same properties to the left
						curWordPos--;
						while (found && (curWordPos > 0)) {
							found = false;
							linkedAdjectiveList = getAdjectiveList(id, curWordPos,
									String.valueOf(substantiveWordform.wcase),
									substantiveWordform.gender, substantiveWordform.sing_pl);
							linkedAdjectiveIterator = linkedAdjectiveList.iterator();
							if (linkedAdjectiveIterator.hasNext()) {
								found = true;
								linkedAdjectiveWordform = linkedAdjectiveIterator.next();
								linkedWordRelation = new SentenceWordRelation(++relationCount,
										curWordRelation, linkedAdjectiveWordform.wordPos,
										linkedAdjectiveWordform.type,
										linkedAdjectiveWordform.wcase,
										linkedAdjectiveWordform.gender,
										linkedAdjectiveWordform.sing_pl,
										curWordRelation.relationType);
								wordRelationList.add(linkedWordRelation);
								movePrepositionRelation(linkedWordRelation);
							}
							curWordPos--;
						}
					}
				}
			}
		}
		cleanWordRelationList(wordRelationList, relationType);
	}

	private void movePrepositionRelation(SentenceWordRelation wordRelation) {
		int relationType = SentenceWordRelation.preposition;
		for (SentenceWordRelation prepWordRelation : wordRelationList)
			if ((prepWordRelation.relationType == relationType)
					&& (wordRelation.sentenceID == prepWordRelation.sentenceID)
					&& (wordRelation.word2Pos == prepWordRelation.word1Pos))
				prepWordRelation.word1Pos = wordRelation.word1Pos;
	}

	private void markLinkedWords(ArrayList<SentenceWordRelation> wordRelationList,
			SentenceWordRelation wordRelation, SentenceWordform dependentWord, int wordPos) {
		ArrayList<SentenceWordform> linkedWordList;
		Iterator<SentenceWordform> linkedWordIterator;
		SentenceWordform linkedWordform;
		SentenceWordform conjunctionWordform;
		SentenceWordRelation linkedWordRelation;
		SentenceWordRelation conjunctionWordRelation;
		linkedWordList = getLinkedWordList(dependentWord.wordPos, dependentWord.type,
				dependentWord.subtype, dependentWord.wcase, dependentWord.person,
				dependentWord.gender, dependentWord.sing_pl);
		linkedWordIterator = linkedWordList.iterator();
		while (linkedWordIterator.hasNext()) {
			linkedWordform = linkedWordIterator.next();
			if (!existWordRelation(wordRelationList, wordRelation, linkedWordform,
					wordRelation.relationType)) {
				if (linkedWordform.wcase > 0)
					linkedWordRelation = new SentenceWordRelation(++relationCount, wordRelation,
							linkedWordform.wordPos, linkedWordform.type, linkedWordform.wcase,
							linkedWordform.gender, linkedWordform.sing_pl,
							wordRelation.relationType);
				else
					linkedWordRelation = new SentenceWordRelation(++relationCount, wordRelation,
							linkedWordform.wordPos, linkedWordform.type, 0, 0, 0,
							wordRelation.relationType);
				wordRelationList.add(linkedWordRelation);
				conjunctionWordform = getConjunction(dependentWord.wordPos, linkedWordform.wordPos);
				conjunctionWordRelation = new SentenceWordRelation(++relationCount, wordRelation,
						conjunctionWordform.wordPos, conjunctionWordform.type, 0, 0, 0,
						SentenceWordRelation.conjunction);
				wordRelationList.add(conjunctionWordRelation);
				markLinkedWords(wordRelationList, linkedWordRelation, linkedWordform, wordPos);
			}
		}
	}

	private ArrayList<SentenceWord> generateSentenceParts(
			ArrayList<SentenceWordRelation> wordRelationList) {
		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();
		ArrayList<SentenceWord> mainSentenceParts = new ArrayList<SentenceWord>();
		SentenceWord sentencePart;
		for (SentenceWordRelation wordRelation : wordRelationList) {
			sentencePart = getSentenceWord(sentenceWordList, wordRelation.word1Pos);
			sentencePart.addValuestoFilters(wordRelation, 1);
			sentenceParts.add(sentencePart);
			mainSentenceParts.add(sentencePart);
			if (wordRelation.word2Pos != 0) {
				sentencePart = getSentenceWord(sentenceWordList, wordRelation.word2Pos);
				sentencePart.addValuestoFilters(wordRelation, 2);
				sentencePart.dep_word_pos = wordRelation.word1Pos;
				sentenceParts.add(sentencePart);
			}
		}

		for (SentenceWord mainSentencePart : mainSentenceParts)
			transferPrepositionId(mainSentencePart, sentenceParts);
		return sentenceParts;
	}

	private SentenceWordFilter[] generateSentenceWordFilter(
			ArrayList<SentenceWordRelation> wordRelationList) {
		SentenceWordFilter[] sentenceWordFilter = new SentenceWordFilter[wordCount + 1];
		for (SentenceWordRelation wordRelation : wordRelationList) {
			if (sentenceWordFilter[wordRelation.word1Pos] == null)
				sentenceWordFilter[wordRelation.word1Pos] = new SentenceWordFilter(id,
						wordRelation.word1Pos);
			sentenceWordFilter[wordRelation.word1Pos].addValuestoFilters(wordRelation, 1);
			if (wordRelation.word2Pos != 0) {
				if (sentenceWordFilter[wordRelation.word2Pos] == null)
					sentenceWordFilter[wordRelation.word2Pos] = new SentenceWordFilter(id,
							wordRelation.word2Pos);
				sentenceWordFilter[wordRelation.word2Pos].addValuestoFilters(wordRelation, 2);
			}
		}
		return sentenceWordFilter;
	}

	/**
	 * Delete chains of dependent words, that are not the longest for each word position
	 * 
	 * @param wordRelationList
	 * @param relationType
	 */
	private void cleanWordRelationList(ArrayList<SentenceWordRelation> wordRelationList,
			int relationType) {
		ArrayList<SentenceWordRelation> removeWordRelationList = new ArrayList<SentenceWordRelation>();
		int size = sentenceWordList.get(sentenceWordList.size() - 1).wordPos;
		int[] maxChainLengths = new int[size + 1];
		int curLength;
		// find max chain legth for each word position
		for (SentenceWordRelation wordRelation : wordRelationList)
			if ((wordRelation.depID == 0) && (wordRelation.relationType == relationType)) {
				curLength = calcDependencyLength(wordRelationList, wordRelation);
				if (curLength > maxChainLengths[wordRelation.word1Pos])
					maxChainLengths[wordRelation.word1Pos] = curLength;
			}

		// for each word position find chains that are below maximum length
		for (SentenceWordRelation wordRelation : wordRelationList)
			if ((wordRelation.depID == 0) && (wordRelation.relationType == relationType)) {
				if (calcDependencyLength(wordRelationList, wordRelation) < maxChainLengths[wordRelation.word1Pos])
					removeWordRelationList.add(wordRelation);
			}

		for (SentenceWordRelation removeWordRelation : removeWordRelationList)
			removeWordRelation(wordRelationList, removeWordRelation);
	}

	/**
	 * Remove word relation and all dependent word relations from list
	 * 
	 * @param wordRelationList
	 * @param wordRelation
	 */
	private void removeWordRelation(ArrayList<SentenceWordRelation> wordRelationList,
			SentenceWordRelation wordRelation) {
		SentenceWordRelation result = wordRelation;
		ArrayList<SentenceWordRelation> markedForDeletionWordRelationList = new ArrayList<SentenceWordRelation>();
		markedForDeletionWordRelationList.add(result);
		if (wordRelation.id == 0)
			return;
		boolean found = true;
		while (found) {
			found = false;
			for (SentenceWordRelation curWordRelation : wordRelationList)
				if (result.id == curWordRelation.depID) {
					found = true;
					result = curWordRelation;
					markedForDeletionWordRelationList.add(result);
				}
		}
		for (SentenceWordRelation curWordRelation : markedForDeletionWordRelationList)
			wordRelationList.remove(curWordRelation);
	}

	private int calcDependencyLength(ArrayList<SentenceWordRelation> wordRelationList,
			SentenceWordRelation wordRelation) {
		SentenceWordRelation resultWordRelation = wordRelation;
		int result = 0;
		if (wordRelation.id == 0)
			return 0;
		boolean found = true;
		while (found) {
			found = false;
			for (SentenceWordRelation curWordRelation : wordRelationList)
				if (resultWordRelation.id == curWordRelation.depID) {
					result++;
					found = true;
					resultWordRelation = curWordRelation;
				}
		}
		return result;
	}

	private SentenceWordRelation getLastWordRelation(
			ArrayList<SentenceWordRelation> wordRelationList, SentenceWordRelation wordRelation) {
		SentenceWordRelation result = wordRelation;
		if (wordRelation.id == 0)
			return null;
		boolean found = true;
		while (found) {
			found = false;
			for (SentenceWordRelation curWordRelation : wordRelationList)
				if (result.id == curWordRelation.depID) {
					found = true;
					result = curWordRelation;
				}
		}
		return result;
	}

	private SentenceWordRelation getMainVerbRelation(int wordPos) {
		for (SentenceWordRelation wordRelation : wordRelationList)
			if ((wordRelation.sentenceID == id) && (wordRelation.word2Pos == wordPos)
					&& (wordRelation.relationType == SentenceWordRelation.verbInfinitive)) {
				SentenceWordRelation tempWordRelation = getMainVerbRelation(wordRelation.word1Pos);
				if (tempWordRelation == null)
					return wordRelation;
				else
					return tempWordRelation;
			}
		return null;
	}

	private boolean existDependence(int wordPos, int relationType) {
		for (SentenceWordRelation wordRelation : wordRelationList)
			if ((wordRelation.sentenceID == id) && (wordRelation.word1Pos == wordPos)
					&& (wordRelation.relationType == relationType))
				return true;
		return false;
	}

	private boolean existWord1Relation(ArrayList<SentenceWordRelation> wordRelationList,
			int wordPos, int type, int relationType) {
		for (SentenceWordRelation wordRelation : wordRelationList) {
			// if exist WordRelation where Word1 has different type and type is not zero
			if ((wordRelation.sentenceID == id) && (wordRelation.word1Pos == wordPos)
					&& (wordRelation.word1Type != type) && (wordRelation.word1Type != 0))
				return true;
			if ((wordRelation.sentenceID == id) && (wordRelation.word2Pos == wordPos)
					&& (wordRelation.word2Type != type) && (wordRelation.word2Type != 0))
				return true;
		}
		return false;
	}

	private boolean existWordRelation(ArrayList<SentenceWordRelation> wordRelationList,
			SentenceWordform word1Form, SentenceWordform word2Form, int relationType,
			int depRelationID) {
		for (SentenceWordRelation wordRelation : wordRelationList) {
			// if exist WordRelation where Word2 has different type and type is not zero
			if ((wordRelation.sentenceID == id) && (wordRelation.word1Pos == word2Form.wordPos)
					&& (wordRelation.word1Type != word2Form.type) && (wordRelation.word1Type != 0))
				return true;
			if ((wordRelation.sentenceID == id) && (wordRelation.word2Pos == word2Form.wordPos)
					&& (wordRelation.word2Type != word2Form.type) && (wordRelation.word2Type != 0))
				return true;
			// if exist wordRelation where Word1 is related to different Word2 and has the same
			// relationType and dependent Relation
			if ((wordRelation.sentenceID == id) && (wordRelation.word1Pos == word1Form.wordPos)
					&& (wordRelation.word2Pos != word2Form.wordPos)
					&& (wordRelation.relationType == relationType)
					&& (wordRelation.depID == depRelationID))
				return true;
			// if exist wordRelation where Word2 is related to different Word1
			if ((wordRelation.sentenceID == id) && (wordRelation.word2Pos == word2Form.wordPos)
					&& (wordRelation.word1Pos != word1Form.wordPos))
				return true;
			// if exist wordRelation with the same Word1 and Word2
			if ((wordRelation.sentenceID == id)
					&& (wordRelation.word1Pos == word1Form.wordPos)
					&& (wordRelation.word1Type == word1Form.type)
					&& (wordRelation.relationType == relationType)
					&& ((word1Form.wcase == 0) | ((wordRelation.word1Case == word1Form.wcase)
							&& (wordRelation.word1Gender == word1Form.gender) && (wordRelation.word1Sing_Pl == word1Form.sing_pl)))
					&& (wordRelation.word2Pos == word2Form.wordPos)
					&& (wordRelation.word2Type == word2Form.type)
					&& (wordRelation.relationType == relationType)
					&& ((word2Form.wcase == 0) | ((wordRelation.word2Case == word2Form.wcase)
							&& (wordRelation.word2Gender == word2Form.gender) && (wordRelation.word2Sing_Pl == word2Form.sing_pl))))
				return true;
		}
		return false;
	}

	private boolean existWordRelation(ArrayList<SentenceWordRelation> wordRelationList,
			SentenceWordRelation mainWordRelation, SentenceWordform word2Form, int relationType) {
		for (SentenceWordRelation wordRelation : wordRelationList) {
			// if exist WordRelation where Word2 has different type
			if ((wordRelation.sentenceID == id) && (wordRelation.word1Pos == word2Form.wordPos)
					&& (wordRelation.word1Type != word2Form.type) && (wordRelation.word1Type != 0))
				return true;
			if ((wordRelation.sentenceID == id) && (wordRelation.word2Pos == word2Form.wordPos)
					&& (wordRelation.word2Type != word2Form.type) && (wordRelation.word2Type != 0))
				return true;
			// if exist wordRelation where Word1 is related to different Word2 and has the same
			// relationType and dependent Relation
			if ((wordRelation.sentenceID == id)
					&& (wordRelation.word1Pos == mainWordRelation.word1Pos)
					&& (wordRelation.word2Pos != word2Form.wordPos)
					&& (wordRelation.relationType == relationType)
					&& (wordRelation.depID == mainWordRelation.id))
				return true;
			// if exist wordRelation where Word2 is related to different Word1
			if ((wordRelation.sentenceID == id) && (wordRelation.word2Pos == word2Form.wordPos)
					&& (wordRelation.word1Pos != mainWordRelation.word1Pos))
				return true;
			// if exist wordRelation with the same Word1 and Word2
			if ((wordRelation.sentenceID == id)
					&& (wordRelation.relationType == relationType)
					&& (wordRelation.word1Pos == mainWordRelation.word1Pos)
					&& (wordRelation.word1Type == mainWordRelation.word1Type)
					&& ((mainWordRelation.word1Case == 0) | ((wordRelation.word1Case == mainWordRelation.word1Case)
							&& (wordRelation.word1Gender == mainWordRelation.word1Gender) && (wordRelation.word1Sing_Pl == mainWordRelation.word1Sing_Pl)))
					&& (wordRelation.word2Pos == word2Form.wordPos)
					&& (wordRelation.word2Type == word2Form.type)
					&& ((word2Form.wcase == 0) | ((wordRelation.word2Case == word2Form.wcase)
							&& (wordRelation.word2Gender == word2Form.gender) && (wordRelation.word2Sing_Pl == word2Form.sing_pl))))
				return true;
		}
		return false;
	}

	private void transferPrepositionId(SentenceWord mainSentencePart,
			ArrayList<SentenceWord> sentenceParts) {
		int prepositionId = 0;
		Iterator<SentenceWord> sentencePartIterator;
		SentenceWord dependentSentencePart;

		// ищем предлог перед любым из прилагательных, связанных с данным существительным
		sentencePartIterator = sentenceParts.iterator();
		while (sentencePartIterator.hasNext()) {
			dependentSentencePart = sentencePartIterator.next();
			if ((dependentSentencePart.dep_word_pos == mainSentencePart.wordPos)
					& (dependentSentencePart.preposition_id > 0))
				prepositionId = dependentSentencePart.preposition_id;
		}

		// заполняем предлог в существительное и во все связанные прилагательные
		if (prepositionId > 0) {
			if (mainSentencePart.filterMatch(">0"))
				mainSentencePart.preposition_id = prepositionId;
			sentencePartIterator = sentenceParts.iterator();
			while (sentencePartIterator.hasNext()) {
				dependentSentencePart = sentencePartIterator.next();
				if ((dependentSentencePart.dep_word_pos == mainSentencePart.wordPos)
						& (dependentSentencePart.filterMatch(">0"))
						& (dependentSentencePart.preposition_id == 0))
					dependentSentencePart.preposition_id = prepositionId;
			}
		}
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
			prevWordforms = getPrevWordforms(id, conjunction.wordPos);
			nextWordforms = getNextWordforms(id, conjunction.wordPos);
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

	private ArrayList<SentenceWordform> getAdjectiveList(int sentence_id, int wordPos,
			String wcaseFilter, int gender, int sing_pl) {
		return getSentencePartList("", wordPos, wcaseFilter, "0", gender, sing_pl, "", "", 1);
	}

	private ArrayList<SentenceWordform> getAdverbList(int sentence_id, int wordPos) {
		ArrayList<SentenceWordform> adverbList = new ArrayList<SentenceWordform>();
		adverbList.addAll(getSentencePartList("", wordPos, "", "", 0, 0, "3", "1", 1));
		adverbList.addAll(getSentencePartList("", wordPos, "", "", 0, 0, "98", "", 1));
		return adverbList;
	}

	private ArrayList<SentenceWordform> getSubstantiveList(int sentence_id, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl) {
		return getSentencePartList("", wordPos, wcaseFilter, personFilter, gender, sing_pl, "", "",
				1);
	}

	private ArrayList<SentenceWordform> getVerbList(int sentence_id, String subsentenceFilter,
			int wordPos, String subtypeFilter) {
		return getSentencePartList(subsentenceFilter, wordPos, "", "", 0, 0, "2", subtypeFilter, 1);
	}

	private ArrayList<SentenceWordform> getPrevWordforms(int sentence_id, int wordPos) {
		return getSentencePartList("", getPrevIndependentWordPos(wordPos), "", "", 0, 0, "", "", 1);
	}

	private ArrayList<SentenceWordform> getNextWordforms(int sentence_id, int wordPos) {
		return getSentencePartList("", getNextIndependentWordPos(wordPos), "", "", 0, 0, "", "", 1);
	}

	private int getPrevIndependentWordPos(int wordPos) {
		SentenceWordRelation depWordRelation;
		int curWordPos = wordPos - 1;
		while (curWordPos > 0) {
			depWordRelation = getDependentWordRelation(curWordPos, wordRelationList);
			if (depWordRelation == null)
				return curWordPos;
			else if ((depWordRelation.relationType == SentenceWordRelation.conjunction)
					&& existIndirectDependence(depWordRelation.word1Pos, wordPos, wordRelationList))
				return -1;
			curWordPos--;
		}
		return -1;
	}

	private int getNextIndependentWordPos(int wordPos) {
		SentenceWordRelation depWordRelation;
		int curWordPos = wordPos + 1;
		int maxWordPos = sentenceWordList.size();
		while (curWordPos < maxWordPos) {
			depWordRelation = getDependentWordRelation(curWordPos, wordRelationList);
			if (depWordRelation == null)
				return curWordPos;
			else if ((depWordRelation.relationType == SentenceWordRelation.conjunction)
					&& existIndirectDependence(depWordRelation.word1Pos, wordPos, wordRelationList))
				return -1;
			curWordPos++;
		}
		return -1;
	}

	private ArrayList<SentenceWordform> getLinkedWordList(int wordPos, int type, int subtype,
			int wcase, int person, int gender, int sing_pl) {
		int tempWordPos;
		ArrayList<SentenceWordform> linkedWords = new ArrayList<SentenceWordform>();
		for (SentenceWordLink wordLink : wordLinkList)
			if ((id == wordLink.sentenceID)
					&& ((wordPos == wordLink.wordPos) | (wordPos == wordLink.linkWordPos))) {
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

	private ArrayList<SentenceWordform> getSentencePartList(String subsentenceFilter, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl, String typeFilter,
			String subtypeFilter, double rating_tolerance) {
		ArrayList<SentenceWordform> result = new ArrayList<SentenceWordform>();
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
					&& DataBank.checkFilter(sentenceWordform.subtype, subtypeFilter)
					&& DataBank
							.checkFilter(
									getSentenceWord(sentenceWordList, sentenceWordform.wordPos).subsentenceID,
									subsentenceFilter))
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
				if ((sentenceWord.preposition_id == 0) && (sentenceWord.dep_word_pos == 0)
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
				if ((sentenceWord.preposition_id == 0)
						&& DataBank.checkFilter(sentenceWord.subsentenceID, subsentenceFilter))
					result.add(sentenceWordform);
			}
		return result;
	}

	private boolean existIndirectDependence(int word1Pos, int word2Pos,
			ArrayList<SentenceWordRelation> wordRelationList) {
		SentenceWordRelation wordRelation;
		int depWordPos = word2Pos;
		do {
			wordRelation = getDependentWordRelation(depWordPos, wordRelationList);
			if (wordRelation == null)
				return false;
			else if (wordRelation.word1Pos == word1Pos)
				return true;
			depWordPos = wordRelation.word1Pos;
		} while (wordRelation != null);
		return false;
	}

	private SentenceWordRelation getDependentWordRelation(int wordPos,
			ArrayList<SentenceWordRelation> wordRelationList) {
		for (SentenceWordRelation wordRelation : wordRelationList)
			if (wordRelation.word2Pos == wordPos)
				return wordRelation;
		return null;
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

	private ArrayList<SentenceWord> getConjunctions(String conjunction) {
		ArrayList<SentenceWord> conjunctions = new ArrayList<SentenceWord>();
		for (SentenceWord sentenceWord : sentenceWordList)
			if (sentenceWord.word.equals(conjunction))
				conjunctions.add(sentenceWord);
		return conjunctions;
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

	private ArrayList<ArrayList<Integer>> divideSentence(int sentence_id) {
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
		databank.setSentenceType(sentence_id, 1);
		return division;
	}
}
