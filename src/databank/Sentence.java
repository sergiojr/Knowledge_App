package databank;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;

import knowledge_app.WordProcessor;

public class Sentence {
	// rating_tolerance - max allowed difference between (100-rating) and (100-maxraring) for
	// wordform
	static double rating_tolerance = 1.5;

	public Sentence(DataBank databank, int id, String sentence,
			ArrayList<SentenceWord> sentenceWordList) {
		this.databank = databank;
		this.id = id;
		this.sentence = sentence;
		this.sentenceWordList = sentenceWordList;
	}

	String sentence;
	ArrayList<SentenceWord> sentenceWordList;
	int id;
	int type;
	ArrayList<ArrayList<Integer>> division;
	DataBank databank;

	public void save() {
		try {
			id = databank.saveSentence(type, sentence, sentenceWordList);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void parse() {
		ArrayList<SentenceWord> sentenceParts;
		ArrayList<SentenceWord> conjunctions;
		Iterator<ArrayList<Integer>> subsentenceIterator;
		ArrayList<Integer> curSubsentence;
		if (databank == null)
			return;

		parsePrepositions();
		parseNumerals();
		parseNegative();
		parseConjunctions();
		parseAdverbsAttributes();
		parseAttributes();
		// parseGenetiveSubstantives();
		parseConjunctions();
		parseComplexPredicate();
		parseAdverbs();
		parseVerbControlledSubstantives();
		parseConjunctions();
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

		conjunctions = getConjunctions("и");

		subsentenceIterator = division.iterator();
		while (subsentenceIterator.hasNext()) {
			curSubsentence = subsentenceIterator.next();
			sentenceParts = gather(parseSubsentence(conjunctions, curSubsentence));
			if (!sentenceParts.isEmpty())
				databank.saveSentenceParts(sentenceParts);
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
		predicateList = databank.getPredicateList(id, subsentenceFilter, 0, 0, 0, 0,
				rating_tolerance);
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
			subjectList = databank.getSubjectList(id, subsentenceFilter, predicate.wordPos,
					personFilter, predicateWordform.gender, predicateWordform.sing_pl,
					rating_tolerance);
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
								// подлежащее2 И подлежащее1 сказемое
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
		SentenceWord preposition;
		ArrayList<SentenceWordform> prepAlternativeList;
		Iterator<SentenceWordform> prepAlternativeIterator;
		SentenceWordform prepAlternativeWordform;
		SentenceWord prepAlternative;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWord substantive;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// получить список предлогов
		prepositionList = databank.getSentencePartList(id, "", 0, "", "", 0, 0,
				String.valueOf(WordProcessor.preposition), "");
		prepositionIterator = prepositionList.iterator();

		// для каждого предлога ищем следующие за ним прилагательные или существительные
		while (prepositionIterator.hasNext()) {
			prepositionWordform = prepositionIterator.next();
			preposition = getSentenceWord(prepositionWordform.wordPos);
			// собираем существительные или прилагательные на позиции за предлогом не в именительном
			// падеже
			substantiveList = getSubstantiveList(id, prepositionWordform.wordPos + 1, ">1", "", 0,
					0);
			if (!substantiveList.isEmpty()) {
				substantiveIterator = substantiveList.iterator();
				if (substantiveIterator.hasNext()) {
					substantive = getSentenceWord(substantiveIterator.next().wordPos);
					substantive.preposition_id = prepositionWordform.word_id;
					sentenceParts.add(substantive);
					preposition.word_type_filter = String.valueOf(prepositionWordform.type);
					sentenceParts.add(preposition);
				}
			} else {
				// проверяем, есть ли другие подходящие словоформы кроме предлога
				prepAlternativeList = databank.getSentencePartList(id, "", preposition.wordPos, "",
						"", 0, 0, "<>" + String.valueOf(WordProcessor.preposition), "");
				prepAlternativeIterator = prepAlternativeList.iterator();
				if (prepAlternativeIterator.hasNext()) {
					prepAlternativeWordform = prepAlternativeIterator.next();
					prepAlternative = getSentenceWord(prepAlternativeWordform.wordPos);
					prepAlternative.word_type_filter = String.valueOf(prepAlternativeWordform.type);
					sentenceParts.add(prepAlternative);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseNumerals() {
		ArrayList<SentenceWordform> numeralList;
		Iterator<SentenceWordform> numeralIterator;
		SentenceWordform numeralPartWordform;
		Numeral numeral;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWord substantive;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		int wcase;
		int sing_pl;

		// получить список числительных
		numeralList = databank.getSentencePartList(id, "", 0, ">0", ">0", 0, 0,
				String.valueOf(WordProcessor.numeral), "");
		numeralIterator = numeralList.iterator();

		// для каждого числительного найти существительное, следующее за ним
		while (numeralIterator.hasNext()) {
			numeralPartWordform = numeralIterator.next();
			numeral = databank.getNumeralByWordID(numeralPartWordform.word_id);
			wcase = numeralPartWordform.wcase;
			sing_pl = numeralPartWordform.sing_pl;
			if (databank
					.isNumeralBaseForm(numeralPartWordform.word_id, numeralPartWordform.rule_id)) {
				wcase = numeral.getBaseWcase();
				sing_pl = numeral.getBaseSingPl();
			}
			substantiveList = getSubstantiveList(id, numeralPartWordform.wordPos + 1,
					String.valueOf(wcase), ">0", numeralPartWordform.gender, sing_pl);
			substantiveIterator = substantiveList.iterator();
			if (substantiveIterator.hasNext()) {
				substantive = getSentenceWord(substantiveIterator.next().wordPos);
				substantive.dep_word_pos = numeralPartWordform.wordPos;
				sentenceParts.add(substantive);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseGenetiveSubstantives() {
		ArrayList<SentenceWordform> genetiveSubstantiveList;
		Iterator<SentenceWordform> genetiveSubstantiveIterator;
		SentenceWord genetiveSubstantive;
		ArrayList<SentenceWordform> mainSubstantiveList;
		Iterator<SentenceWordform> mainSubstantiveIterator;
		SentenceWord mainSubstantive;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// получить существительные в родительном падеже
		genetiveSubstantiveList = getSubstantiveList(id, 0, "2", ">0", 0, 0);
		genetiveSubstantiveIterator = genetiveSubstantiveList.iterator();

		// для каждого существительного в родительном падеже получить предшествующее существительное
		while (genetiveSubstantiveIterator.hasNext()) {
			genetiveSubstantive = getSentenceWord(genetiveSubstantiveIterator.next().wordPos);
			if (genetiveSubstantive.wordPos > 1) {
				mainSubstantiveList = getSubstantiveList(id,
						databank.getPrevIndependentWordPos(id, genetiveSubstantive.wordPos), ">0",
						">0", 0, 0);
				mainSubstantiveIterator = mainSubstantiveList.iterator();
				if (mainSubstantiveIterator.hasNext()) {
					mainSubstantive = getSentenceWord(mainSubstantiveIterator.next().wordPos);
					genetiveSubstantive.dep_word_pos = mainSubstantive.wordPos;
					sentenceParts.add(genetiveSubstantive);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseComplexPredicate() {
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		SentenceWord verb;
		ArrayList<SentenceWordform> infinitiveList;
		Iterator<SentenceWordform> infinitiveIterator;
		SentenceWordform infinitiveWordform;
		SentenceWord infinitive;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить глаголы в инфинитиве
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			verb = getSentenceWord(verbWordform.wordPos);
			infinitiveList = copySentenceParts(verbList,
					databank.getNextIndependentWordPos(id, verb.wordPos), "0");
			infinitiveIterator = infinitiveList.iterator();
			if (infinitiveIterator.hasNext()) {
				infinitiveWordform = infinitiveIterator.next();
				infinitive = getSentenceWord(infinitiveWordform.wordPos);
				infinitive.dep_word_pos = verbWordform.wordPos;
				infinitive.word_type_filter = String.valueOf(infinitiveWordform.type);
				sentenceParts.add(infinitive);
				verb.word_type_filter = String.valueOf(verbWordform.type);
				sentenceParts.add(verb);
				markLinkedWords(infinitiveWordform, verbWordform.wordPos, sentenceParts);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseAdverbs() {
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		SentenceWord verb;
		ArrayList<SentenceWordform> adverbList;
		Iterator<SentenceWordform> adverbsIterator;
		SentenceWordform adverbWordform;
		SentenceWord adverb;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить наречия
		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			verb = getSentenceWord(verbWordform.wordPos);
			adverbList = getAdverbList(id, databank.getPrevIndependentWordPos(id, verb.wordPos));
			adverbList.addAll(getAdverbList(id,
					databank.getNextIndependentWordPos(id, verb.wordPos)));
			adverbsIterator = adverbList.iterator();
			if (adverbsIterator.hasNext()) {
				adverbWordform = adverbsIterator.next();
				adverb = getSentenceWord(adverbWordform.wordPos);
				adverb.dep_word_pos = verbWordform.wordPos;
				adverb.word_type_filter = String.valueOf(adverbWordform.type);
				sentenceParts.add(adverb);
				verb.word_type_filter = String.valueOf(verbWordform.type);
				sentenceParts.add(verb);
				markLinkedWords(adverbWordform, verbWordform.wordPos, sentenceParts);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseVerbControlledSubstantives() {
		int curWordPos;
		boolean hasPreposition;
		ArrayList<SentenceWordform> verbList;
		Iterator<SentenceWordform> verbIterator;
		SentenceWordform verbWordform;
		SentenceWord verb;
		ArrayList<SentenceWordform> nextWordformList;
		Iterator<SentenceWordform> nextWordformIterator;
		SentenceWordform nextWordform;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;
		SentenceWord substantive;
		String personFilter;

		ArrayList<SentenceWord> tempLinkedSubstantiveList;
		ArrayList<SentenceWord> maxTempLinkedSubstantiveList;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// получить глаголы в действительной форме или инфинитиве
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		while (verbIterator.hasNext()) {
			verbWordform = verbIterator.next();
			verb = getSentenceWord(verbWordform.wordPos);
			if (verbWordform.person == 0)
				personFilter = ">0";
			else
				personFilter = String.valueOf(verbWordform.person);
			// для каждого глагола в действительной форме получить существительные не в именительном
			// падеже
			curWordPos = verb.wordPos;
			nextWordformList = getNextWordforms(id, curWordPos);
			nextWordformIterator = nextWordformList.iterator();
			if (nextWordformIterator.hasNext()) {
				nextWordform = nextWordformIterator.next();
				// если следующее слово предлог, то пропускаем
				hasPreposition = (nextWordform.type == WordProcessor.preposition);
				if (hasPreposition)
					curWordPos += 1;
				curWordPos = databank.getNextIndependentWordPos(id, curWordPos);
				if (curWordPos > 0)
					if (hasPreposition
							| (getSubstantiveList(id, curWordPos, "1", personFilter,
									verbWordform.gender, verbWordform.sing_pl).isEmpty())) {
						substantiveList = getSubstantiveList(id, curWordPos, ">1", ">0", 0, 0);
						substantiveIterator = substantiveList.iterator();
						maxTempLinkedSubstantiveList = new ArrayList<SentenceWord>();
						while (substantiveIterator.hasNext()) {
							substantiveWordform = substantiveIterator.next();
							substantive = getSentenceWord(substantiveWordform.wordPos);
							tempLinkedSubstantiveList = new ArrayList<SentenceWord>();
							if (substantive.dep_word_pos == 0) {
								SentenceWord tempSubstantive = new SentenceWord(substantive);
								tempSubstantive.dep_word_pos = verb.wordPos;
								tempSubstantive.word_type_filter = String
										.valueOf(substantiveWordform.type);
								tempLinkedSubstantiveList.add(tempSubstantive);
								verb.word_type_filter = String.valueOf(verbWordform.type);
								markLinkedWords(substantiveWordform, verb.wordPos,
										tempLinkedSubstantiveList);
							}
							if (tempLinkedSubstantiveList.size() > maxTempLinkedSubstantiveList
									.size())
								maxTempLinkedSubstantiveList = tempLinkedSubstantiveList;
						}
						if (maxTempLinkedSubstantiveList.size() > 0) {
							sentenceParts.add(verb);
							copyFormTempSentenceWordList(sentenceParts,
									maxTempLinkedSubstantiveList);
							transferPrepositionId(verb, sentenceParts);
						}
					}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void copyFormTempSentenceWordList(ArrayList<SentenceWord> sentenceParts,
			ArrayList<SentenceWord> tempSentenceWordList) {
		for (SentenceWord tempSentenceWord : tempSentenceWordList) {
			SentenceWord sentenceWord = getSentenceWord(tempSentenceWord.wordPos);
			sentenceWord.part = tempSentenceWord.part;
			sentenceWord.dep_word_pos = tempSentenceWord.dep_word_pos;
			sentenceWord.preposition_id = tempSentenceWord.preposition_id;
			sentenceWord.isPunctuation = tempSentenceWord.isPunctuation;
			sentenceWord.isName = tempSentenceWord.isName;
			sentenceWord.internal = tempSentenceWord.internal;
			sentenceWord.word_type_filter = tempSentenceWord.word_type_filter;
			sentenceWord.wcase_filter = tempSentenceWord.wcase_filter;
			sentenceWord.gender_filter = tempSentenceWord.gender_filter;
			sentenceWord.sing_pl_filter = tempSentenceWord.sing_pl_filter;
			sentenceParts.add(sentenceWord);
		}
	}

	private void parseAdverbsAttributes() {
		ArrayList<SentenceWordform> attributeList;
		Iterator<SentenceWordform> attributeIterator;
		SentenceWordform attributeWordform;
		SentenceWord attribute;
		ArrayList<SentenceWordform> adverbList;
		Iterator<SentenceWordform> adverbsIterator;
		SentenceWordform adverbWordform;
		SentenceWord adverb;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// получить прилагательные и местоимения прилагательные
		attributeList = getAdjectiveList(id, 0, ">0", 0, 0);
		attributeIterator = attributeList.iterator();

		// для каждого прилагательного получить предшествующие наречия
		while (attributeIterator.hasNext()) {
			attributeWordform = attributeIterator.next();
			attribute = getSentenceWord(attributeWordform.wordPos);
			if (attributeWordform.type == WordProcessor.adjective) {
				adverbList = getAdverbList(id,
						databank.getPrevIndependentWordPos(id, attribute.wordPos));
				adverbsIterator = adverbList.iterator();
				if (adverbsIterator.hasNext()) {
					adverbWordform = adverbsIterator.next();
					adverb = getSentenceWord(adverbWordform.wordPos);
					adverb.dep_word_pos = attributeWordform.wordPos;
					adverb.word_type_filter = String.valueOf(adverbWordform.type);
					sentenceParts.add(adverb);
					attribute.word_type_filter = String.valueOf(attributeWordform.type);
					sentenceParts.add(attribute);
					markLinkedWords(adverbWordform, attributeWordform.wordPos, sentenceParts);
					transferPrepositionId(attribute, sentenceParts);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseAttributes() {
		ArrayList<SentenceWordform> adjectiveList;
		Iterator<SentenceWordform> adjectiveIterator;
		SentenceWordform adjectiveWordform;
		SentenceWord adjective;
		ArrayList<SentenceWordform> substantiveList;
		Iterator<SentenceWordform> substantiveIterator;
		SentenceWordform substantiveWordform;
		SentenceWord substantive;
		ArrayList<SentenceWordform> linkedAdjectiveList;
		Iterator<SentenceWordform> linkedAdjectiveIterator;
		SentenceWordform linkedAdjectiveWordform;
		SentenceWord linkedAdjective;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();
		ArrayList<SentenceWord> curSentenceParts = new ArrayList<SentenceWord>();

		boolean found;
		int curWordPos;

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
			substantive = getSentenceWord(substantiveWordform.wordPos);
			curSentenceParts = new ArrayList<SentenceWord>();
			if (substantive.wordPos > 1) {
				adjectiveList = getAdjectiveList(id, substantive.wordPos - 1,
						String.valueOf(substantiveWordform.wcase), substantiveWordform.gender,
						substantiveWordform.sing_pl);
				adjectiveIterator = adjectiveList.iterator();
				if (adjectiveIterator.hasNext()) {
					adjectiveWordform = adjectiveIterator.next();
					adjective = getSentenceWord(adjectiveWordform.wordPos);
					// mark, that adjective is dependent on substantive
					adjective.dep_word_pos = substantiveWordform.wordPos;
					adjective.addValuestoFilters(adjectiveWordform);
					substantive.addValuestoFilters(substantiveWordform);
					curSentenceParts.add(substantive);
					curSentenceParts.add(adjective);
					// mark any linked adjective
					markLinkedWords(adjectiveWordform, substantiveWordform.wordPos,
							curSentenceParts);

					// find leftmost dependent adjective
					curWordPos = adjectiveWordform.wordPos;
					for (SentenceWord sentencePart : curSentenceParts)
						if ((sentencePart.dep_word_pos == substantiveWordform.wordPos)
								& (sentencePart.filterMatch(">0") & (sentencePart.wordPos < curWordPos)))
							curWordPos = sentencePart.wordPos;

					// try to find adjectives with the same properties to the left
					found = true;
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
							linkedAdjective = getSentenceWord(linkedAdjectiveWordform.wordPos);
							linkedAdjective.dep_word_pos = substantiveWordform.wordPos;
							linkedAdjective.addValuestoFilters(linkedAdjectiveWordform);
							curSentenceParts.add(linkedAdjective);
						}
						curWordPos--;
					}
					transferPrepositionId(substantive, curSentenceParts);
					sentenceParts.addAll(curSentenceParts);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void markLinkedWords(SentenceWordform dependentWord, int wordPos,
			ArrayList<SentenceWord> sentenceParts) {
		ArrayList<SentenceWordform> linkedWordList;
		Iterator<SentenceWordform> linkedWordIterator;
		SentenceWordform linkedWordform;
		SentenceWord linkedWord;
		SentenceWord conjunction;
		linkedWordList = databank.getLinkedWordList(id, dependentWord.wordPos, dependentWord.type,
				0, dependentWord.wcase, dependentWord.person, dependentWord.gender,
				dependentWord.sing_pl);
		linkedWordIterator = linkedWordList.iterator();
		while (linkedWordIterator.hasNext()) {
			linkedWordform = linkedWordIterator.next();
			linkedWord = getSentenceWord(linkedWordform.wordPos);
			if ((!existsSentencePart(sentenceParts, linkedWord)) & (linkedWord.dep_word_pos == 0)) {
				linkedWord.dep_word_pos = wordPos;
				linkedWord.addValuestoFilters(linkedWordform);
				sentenceParts.add(linkedWord);
				conjunction = getSentenceWord(databank.getConjunction(id, dependentWord.wordPos,
						linkedWordform.wordPos).wordPos);
				conjunction.dep_word_pos = wordPos;
				sentenceParts.add(conjunction);
				markLinkedWords(linkedWordform, wordPos, sentenceParts);
			}
		}
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
						& (dependentSentencePart.filterMatch(">0")))
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
							databank.updateSentenceWordList(sentenceWordList);
							databank.saveSentenceWordLink(prevWordform, conjunction, nextWordform);
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
							databank.updateSentenceWordList(sentenceWordList);
							databank.saveSentenceWordLink(prevWordform, conjunction, nextWordform);
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
							databank.updateSentenceWordList(sentenceWordList);
							databank.saveSentenceWordLink(prevWordform, conjunction, nextWordform);
						}
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
		SentenceWord negative;
		ArrayList<SentenceWordform> nextWordforms;
		Iterator<SentenceWordform> nextWordformIterator;
		SentenceWordform nextWordform = null;

		ArrayList<SentenceWord> sentenceParts = new ArrayList<SentenceWord>();

		// find wordPos of negative
		negatives = databank.getNegatives(id);
		negativeIterator = negatives.iterator();
		while (negativeIterator.hasNext()) {
			found = false;
			empty = false;
			negativeWordform = negativeIterator.next();
			negative = getSentenceWord(negativeWordform.wordPos);
			curWordPos = negative.wordPos;
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
				negative.dep_word_pos = nextWordform.wordPos;
				sentenceParts.add(negative);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private ArrayList<SentenceWordform> getAdjectiveList(int sentence_id, int wordPos,
			String wcaseFilter, int gender, int sing_pl) {
		return databank.getSentencePartList(sentence_id, "", wordPos, wcaseFilter, "0", gender,
				sing_pl, "", "");
	}

	private ArrayList<SentenceWordform> getAdverbList(int sentence_id, int wordPos) {
		ArrayList<SentenceWordform> adverbList = new ArrayList<SentenceWordform>();
		adverbList.addAll(databank.getSentencePartList(sentence_id, "", wordPos, "", "", 0, 0, "3",
				"1"));
		adverbList.addAll(databank.getSentencePartList(sentence_id, "", wordPos, "", "", 0, 0,
				"98", ""));
		return adverbList;
	}

	private ArrayList<SentenceWordform> getSubstantiveList(int sentence_id, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl) {
		return databank.getSentencePartList(sentence_id, "", wordPos, wcaseFilter, personFilter,
				gender, sing_pl, "", "");
	}

	private ArrayList<SentenceWordform> getVerbList(int sentence_id, String subsentenceFilter,
			int wordPos, String subtypeFilter) {
		return databank.getSentencePartList(sentence_id, subsentenceFilter, wordPos, "", "", 0, 0,
				"2", subtypeFilter);
	}

	public ArrayList<SentenceWordform> getPrevWordforms(int sentence_id, int wordPos) {
		return databank.getSentencePartList(sentence_id, "",
				databank.getPrevIndependentWordPos(sentence_id, wordPos), "", "", 0, 0, "", "");
	}

	public ArrayList<SentenceWordform> getNextWordforms(int sentence_id, int wordPos) {
		return databank.getSentencePartList(sentence_id, "",
				databank.getNextIndependentWordPos(sentence_id, wordPos), "", "", 0, 0, "", "");
	}

	private ArrayList<SentenceWordform> copySentenceParts(
			ArrayList<SentenceWordform> sentencePartList, int wordPos, String subtypeFilter) {
		Iterator<SentenceWordform> iterator;
		SentenceWordform sentencePart;
		ArrayList<SentenceWordform> newSentencePartList = new ArrayList<SentenceWordform>();

		iterator = sentencePartList.iterator();
		while (iterator.hasNext()) {
			sentencePart = iterator.next();
			if ((sentencePart.wordPos == wordPos)
					& (databank.checkFilter(sentencePart.subtype, subtypeFilter)))
				newSentencePartList.add(sentencePart);
		}
		return newSentencePartList;
	}

	private boolean existsSentencePart(ArrayList<SentenceWord> sentenceParts,
			SentenceWord sentencePart) {
		Iterator<SentenceWord> iterator = sentenceParts.iterator();
		SentenceWord tempSentencePart;
		while (iterator.hasNext()) {
			tempSentencePart = iterator.next();
			if (tempSentencePart.wordPos == sentencePart.wordPos)
				return true;
		}
		return false;
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
		SentenceWord result = getSentenceWord(sentenceWordform.wordPos);
		result.sentenceWordform = sentenceWordform;
		return result;
	}

	private SentenceWord getSentenceWord(int wordPos) {
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

		if (result)
			databank.updateSentenceWordList(sentenceWordList);
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
		databank.updateSentenceWordList(sentenceWordList);
		databank.setSentenceType(sentence_id, 1);
		return division;
	}
}
