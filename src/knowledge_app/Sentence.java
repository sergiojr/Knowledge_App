package knowledge_app;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;

import databank.DataBank;
import databank.Numeral;

public class Sentence {
	// rating_tolerance - max allowed difference between (100-rating) and (100-maxraring) for
	// wordform
	static double rating_tolerance = 1.5;

	public Sentence(String sentence, ArrayList<WordProcessor> wordlist) {
		this.sentence = sentence;
		this.wordList = wordlist;
		this.type = 0;
	}

	public Sentence() {
		sentence = new String();
		wordList = new ArrayList<WordProcessor>();
		type = 0;
	}

	public Sentence(DataBank databank, int id, String sentence) {
		this.databank = databank;
		this.id = id;
		this.sentence = sentence;
	}

	String sentence;
	ArrayList<WordProcessor> wordList;
	ArrayList<WordForm> subjectList;
	int id;
	int type;
	ArrayList<ArrayList<Integer>> division;
	DataBank databank;

	public void save(DataBank databank) {
		this.databank = databank;
		try {
			id = databank.saveSentence(type, sentence, wordList);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void parse() {
		ArrayList<SentencePart> sentenceParts;
		ArrayList<SentencePart> conjunctions;
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

		division = databank.divideSentence(id);
		if (division == null)
			return;

		if (markAdverbialParticiple(division))
			division = databank.divideSentence(id);

		conjunctions = databank.getConjunctions(id, "и");

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
		ArrayList<SentencePart> adverbialParticipleList = getVerbList(id,
				Integer.toString(subsentenceID), 0, "2");
		if (!adverbialParticipleList.isEmpty()) {
			ArrayList<SentencePart> verbList = getVerbList(id, Integer.toString(subsentenceID), 0,
					"1");
			if (verbList.isEmpty())
				return databank.markAsInternal(id,
						adverbialParticipleList.iterator().next().wordPos, ",");
		}
		return false;
	}

	private ArrayList<ArrayList<SentencePart>> parseSubsentence(
			ArrayList<SentencePart> conjunctions, ArrayList<Integer> curSubsentence) {
		String subsentenceFilter;
		ArrayList<Integer> subsentencePart;
		ArrayList<ArrayList<SentencePart>> curSentenceParts;
		ArrayList<ArrayList<SentencePart>> sentenceParts = null;
		int size = curSubsentence.size();
		int curRating;
		int maxRating = -100 * size;
		int[] subsentenceDivisionMask = new int[size]; // new array of zeroes
		Iterator<ArrayList<Integer>> iterator;
		ArrayList<ArrayList<Integer>> subsentenceDivision = new ArrayList<ArrayList<Integer>>();
		do {
			curSentenceParts = new ArrayList<ArrayList<SentencePart>>();
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

	private int calculateSubsenteneRating(ArrayList<ArrayList<SentencePart>> curSentenceParts) {
		int result = 0;
		for (ArrayList<SentencePart> subsentence : curSentenceParts) {
			// bonus rating for each subsentence
			if (subsentence.size() > 0)
				result += 10;
			int maxSubjectRating = 0;
			int maxPredicateRating = 0;
			int rating = 0;
			SentencePart subject = null;
			SentencePart predicate = null;
			for (SentencePart sentencePart : subsentence) {
				if ((sentencePart.part == SentencePart.subject)
						& (sentencePart.rating > maxSubjectRating)) {
					maxSubjectRating = sentencePart.rating;
					subject = sentencePart;
				}
				if ((sentencePart.part == SentencePart.predicate)
						& (sentencePart.rating > maxPredicateRating)) {
					maxPredicateRating = sentencePart.rating;
					predicate = sentencePart;
				}
			}
			if (subject != null) {
				rating += subject.rating - subject.maxrating;
			}
			if (predicate != null) {
				rating += predicate.rating - predicate.maxrating;
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

	private ArrayList<SentencePart> findSubjectPredicate(ArrayList<SentencePart> conjunctions,
			String subsentenceFilter) {
		ArrayList<SentencePart> subjectList;
		Iterator<SentencePart> subjectIterator;
		SentencePart subject;
		ArrayList<SentencePart> predicateList;
		Iterator<SentencePart> predicateIterator;
		SentencePart predicate;
		ArrayList<SentencePart> sentenceParts;
		Iterator<SentencePart> conjunctionIterator;
		SentencePart conjunction;
		SentencePart subject2;
		boolean success = false;
		boolean conjunctionFound;
		String personFilter;
		sentenceParts = new ArrayList<SentencePart>();
		// получить потенциальные сказуемые, отсортированные по рейтингу
		predicateList = databank.getPredicateList(id, subsentenceFilter, 0, 0, 0, 0,
				rating_tolerance);
		predicateIterator = predicateList.iterator();
		while ((predicateIterator.hasNext()) & !success) {
			predicate = predicateIterator.next();
			// получить для каждого сказуемого, потенциальные подлежащие,
			// отсортированные по рейтингу
			if (predicate.person > 0)
				personFilter = String.valueOf(predicate.person);
			else
				personFilter = ">0";
			subjectList = databank.getSubjectList(id, subsentenceFilter, predicate.wordPos,
					personFilter, predicate.gender, predicate.sing_pl, rating_tolerance);
			subjectIterator = subjectList.iterator();
			// выбрать первую пару
			if (subjectIterator.hasNext()) {
				subject = subjectIterator.next();
				sentenceParts.add(subject);
				sentenceParts.add(predicate);

				// поиск подлежащего из двух слов, связанных союзом И
				if (!conjunctions.isEmpty()) {
					while (subjectIterator.hasNext()) {
						subject2 = subjectIterator.next();
						conjunctionIterator = conjunctions.iterator();
						conjunctionFound = false;
						while (conjunctionIterator.hasNext()) {
							conjunction = conjunctionIterator.next();
							// варианты взаимного расположения
							// подлежащее1 И подлежащее2 сказуемое
							if ((subject.wordPos < conjunction.wordPos)
									& (subject2.wordPos > conjunction.wordPos)
									& (subject2.wordPos < predicate.wordPos)) {
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
				success = true;
			}
		}
		return sentenceParts;
	}

	private void parsePrepositions() {
		ArrayList<SentencePart> prepositionList;
		Iterator<SentencePart> prepositionIterator;
		SentencePart preposition;
		ArrayList<SentencePart> prepAlternativeList;
		Iterator<SentencePart> prepAlternativeIterator;
		SentencePart prepAlternative;
		ArrayList<SentencePart> substantiveList;
		Iterator<SentencePart> substantiveIterator;
		SentencePart substantive;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// получить список предлогов
		prepositionList = databank.getSentencePartList(id, "", 0, "", "", 0, 0,
				String.valueOf(WordProcessor.preposition), "");
		prepositionIterator = prepositionList.iterator();

		// для каждого предлога ищем следующие за ним прилагательные или существительные
		while (prepositionIterator.hasNext()) {
			preposition = prepositionIterator.next();
			// собираем существительные или прилагательные на позиции за предлогом не в именительном
			// падеже
			substantiveList = getSubstantiveList(id, preposition.wordPos + 1, ">1", "", 0, 0);
			if (!substantiveList.isEmpty()) {
				substantiveIterator = substantiveList.iterator();
				if (substantiveIterator.hasNext()) {
					substantive = substantiveIterator.next();
					substantive.preposition_id = preposition.word_id;
					sentenceParts.add(substantive);
					preposition.word_type_filter = String.valueOf(preposition.type);
					sentenceParts.add(preposition);
				}
			} else {
				// проверяем, есть ли другие подходящие словоформы кроме предлога
				prepAlternativeList = databank.getSentencePartList(id, "", preposition.wordPos, "",
						"", 0, 0, "<>" + String.valueOf(WordProcessor.preposition), "");
				prepAlternativeIterator = prepAlternativeList.iterator();
				if (prepAlternativeIterator.hasNext()) {
					prepAlternative = prepAlternativeIterator.next();
					prepAlternative.word_type_filter = String.valueOf(prepAlternative.type);
					sentenceParts.add(prepAlternative);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseNumerals() {
		ArrayList<SentencePart> numeralList;
		Iterator<SentencePart> numeralIterator;
		SentencePart numeralPart;
		Numeral numeral;
		ArrayList<SentencePart> substantiveList;
		Iterator<SentencePart> substantiveIterator;
		SentencePart substantive;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		int wcase;
		int sing_pl;

		// получить список числительных
		numeralList = databank.getSentencePartList(id, "", 0, ">0", ">0", 0, 0,
				String.valueOf(WordProcessor.numeral), "");
		numeralIterator = numeralList.iterator();

		// для каждого числительного найти существительное, следующее за ним
		while (numeralIterator.hasNext()) {
			numeralPart = numeralIterator.next();
			numeral = databank.getNumeralByWordID(numeralPart.word_id);
			wcase = numeralPart.wcase;
			sing_pl = numeralPart.sing_pl;
			if (databank.isNumeralBaseForm(numeralPart.word_id, numeralPart.rule_id)) {
				wcase = numeral.getBaseWcase();
				sing_pl = numeral.getBaseSingPl();
			}
			substantiveList = getSubstantiveList(id, numeralPart.wordPos + 1,
					String.valueOf(wcase), ">0", numeralPart.gender, sing_pl);
			substantiveIterator = substantiveList.iterator();
			if (substantiveIterator.hasNext()) {
				substantive = substantiveIterator.next();
				substantive.dep_word_pos = numeralPart.wordPos;
				sentenceParts.add(substantive);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseGenetiveSubstantives() {
		ArrayList<SentencePart> genetiveSubstantiveList;
		Iterator<SentencePart> genetiveSubstantiveIterator;
		SentencePart genetiveSubstantive;
		ArrayList<SentencePart> mainSubstantiveList;
		Iterator<SentencePart> mainSubstantiveIterator;
		SentencePart mainSubstantive;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// получить существительные в родительном падеже
		genetiveSubstantiveList = getSubstantiveList(id, 0, "2", ">0", 0, 0);
		genetiveSubstantiveIterator = genetiveSubstantiveList.iterator();

		// для каждого существительного в родительном падеже получить предшествующее существительное
		while (genetiveSubstantiveIterator.hasNext()) {
			genetiveSubstantive = genetiveSubstantiveIterator.next();
			if (genetiveSubstantive.wordPos > 1) {
				mainSubstantiveList = getSubstantiveList(id,
						databank.getPrevIndependentWordPos(id, genetiveSubstantive.wordPos), ">0",
						">0", 0, 0);
				mainSubstantiveIterator = mainSubstantiveList.iterator();
				if (mainSubstantiveIterator.hasNext()) {
					mainSubstantive = mainSubstantiveIterator.next();
					genetiveSubstantive.dep_word_pos = mainSubstantive.wordPos;
					sentenceParts.add(genetiveSubstantive);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseComplexPredicate() {
		ArrayList<SentencePart> verbList;
		Iterator<SentencePart> verbIterator;
		SentencePart verb;
		ArrayList<SentencePart> infinitiveList;
		Iterator<SentencePart> infinitiveIterator;
		SentencePart infinitive;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить глаголы в инфинитиве
		while (verbIterator.hasNext()) {
			verb = verbIterator.next();
			infinitiveList = copySentenceParts(verbList,
					databank.getNextIndependentWordPos(id, verb.wordPos), "0");
			infinitiveIterator = infinitiveList.iterator();
			if (infinitiveIterator.hasNext()) {
				infinitive = infinitiveIterator.next();
				infinitive.dep_word_pos = verb.wordPos;
				infinitive.word_type_filter = String.valueOf(infinitive.type);
				sentenceParts.add(infinitive);
				verb.word_type_filter = String.valueOf(verb.type);
				sentenceParts.add(verb);
				markLinkedWords(infinitive, verb.wordPos, sentenceParts);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseAdverbs() {
		ArrayList<SentencePart> verbList;
		Iterator<SentencePart> verbIterator;
		SentencePart verb;
		ArrayList<SentencePart> adverbList;
		Iterator<SentencePart> adverbsIterator;
		SentencePart adverb;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// получить глаголы в действительной форме
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		// для каждого глагола в действительной форме получить наречия
		while (verbIterator.hasNext()) {
			verb = verbIterator.next();
			adverbList = getAdverbList(id, databank.getPrevIndependentWordPos(id, verb.wordPos));
			adverbList.addAll(getAdverbList(id,
					databank.getNextIndependentWordPos(id, verb.wordPos)));
			adverbsIterator = adverbList.iterator();
			if (adverbsIterator.hasNext()) {
				adverb = adverbsIterator.next();
				adverb.dep_word_pos = verb.wordPos;
				adverb.word_type_filter = String.valueOf(adverb.type);
				sentenceParts.add(adverb);
				verb.word_type_filter = String.valueOf(verb.type);
				sentenceParts.add(verb);
				markLinkedWords(adverb, verb.wordPos, sentenceParts);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseVerbControlledSubstantives() {
		int curWordPos;
		boolean hasPreposition;
		ArrayList<SentencePart> verbList;
		Iterator<SentencePart> verbIterator;
		SentencePart verb;
		ArrayList<SentencePart> nextWordformList;
		Iterator<SentencePart> nextWordformIterator;
		SentencePart nextWordform;
		ArrayList<SentencePart> substantiveList;
		Iterator<SentencePart> substantiveIterator;
		SentencePart substantive;
		String personFilter;

		ArrayList<SentencePart> linkedSubstantiveList;
		ArrayList<SentencePart> maxLinkedSubstantiveList;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// получить глаголы в действительной форме или инфинитиве
		verbList = getVerbList(id, "", 0, "");
		verbIterator = verbList.iterator();

		while (verbIterator.hasNext()) {
			verb = verbIterator.next();
			if (verb.person == 0)
				personFilter = ">0";
			else
				personFilter = String.valueOf(verb.person);
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
							| (getSubstantiveList(id, curWordPos, "1", personFilter, verb.gender,
									verb.sing_pl).isEmpty())) {
						substantiveList = getSubstantiveList(id, curWordPos, ">1", ">0", 0, 0);
						substantiveIterator = substantiveList.iterator();
						maxLinkedSubstantiveList = new ArrayList<SentencePart>();
						while (substantiveIterator.hasNext()) {
							substantive = substantiveIterator.next();
							linkedSubstantiveList = new ArrayList<SentencePart>();
							if (substantive.dep_word_pos == 0) {
								substantive.dep_word_pos = verb.wordPos;
								substantive.word_type_filter = String.valueOf(substantive.type);
								linkedSubstantiveList.add(substantive);
								verb.word_type_filter = String.valueOf(verb.type);
								markLinkedWords(substantive, verb.wordPos, linkedSubstantiveList);
							}
							if (linkedSubstantiveList.size() > maxLinkedSubstantiveList.size())
								maxLinkedSubstantiveList = linkedSubstantiveList;
						}
						if (maxLinkedSubstantiveList.size() > 0) {
							sentenceParts.add(verb);
							sentenceParts.addAll(maxLinkedSubstantiveList);
							transferPrepositionId(verb, sentenceParts);
						}
					}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseAdverbsAttributes() {
		ArrayList<SentencePart> attributeList;
		Iterator<SentencePart> attributeIterator;
		SentencePart attribute;
		ArrayList<SentencePart> adverbList;
		Iterator<SentencePart> adverbsIterator;
		SentencePart adverb;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// получить прилагательные и местоимения прилагательные
		attributeList = getAdjectiveList(id, 0, ">0", 0, 0);
		attributeIterator = attributeList.iterator();

		// для каждого прилагательного получить предшествующие наречия
		while (attributeIterator.hasNext()) {
			attribute = attributeIterator.next();
			if (attribute.type == WordProcessor.adjective) {
				adverbList = getAdverbList(id,
						databank.getPrevIndependentWordPos(id, attribute.wordPos));
				adverbsIterator = adverbList.iterator();
				if (adverbsIterator.hasNext()) {
					adverb = adverbsIterator.next();
					adverb.dep_word_pos = attribute.wordPos;
					adverb.word_type_filter = String.valueOf(adverb.type);
					sentenceParts.add(adverb);
					attribute.word_type_filter = String.valueOf(attribute.type);
					sentenceParts.add(attribute);
					markLinkedWords(adverb, attribute.wordPos, sentenceParts);
					transferPrepositionId(attribute, sentenceParts);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseAttributes() {
		ArrayList<SentencePart> adjectiveList;
		Iterator<SentencePart> adjectiveIterator;
		SentencePart adjective;
		ArrayList<SentencePart> substantiveList;
		Iterator<SentencePart> substantiveIterator;
		SentencePart substantive;
		ArrayList<SentencePart> linkedAdjectiveList;
		Iterator<SentencePart> linkedAdjectiveIterator;
		SentencePart linkedAdjective;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();
		ArrayList<SentencePart> curSentenceParts = new ArrayList<SentencePart>();

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
			substantive = substantiveIterator.next();
			curSentenceParts = new ArrayList<SentencePart>();
			if (substantive.wordPos > 1) {
				adjectiveList = getAdjectiveList(id, substantive.wordPos - 1,
						String.valueOf(substantive.wcase), substantive.gender, substantive.sing_pl);
				adjectiveIterator = adjectiveList.iterator();
				if (adjectiveIterator.hasNext()) {
					adjective = adjectiveIterator.next();
					// mark, that adjective is dependent on substantive
					adjective.dep_word_pos = substantive.wordPos;
					adjective.addValuestoFilters();
					substantive.addValuestoFilters();
					curSentenceParts.add(substantive);
					curSentenceParts.add(adjective);
					// mark any linked adjective
					markLinkedWords(adjective, substantive.wordPos, curSentenceParts);

					// find leftmost dependent adjective
					curWordPos = adjective.wordPos;
					for (SentencePart sentencePart : curSentenceParts) 
						if ((sentencePart.dep_word_pos == substantive.wordPos)
								& (sentencePart.wcase > 0) & (sentencePart.wordPos < curWordPos))
							curWordPos = sentencePart.wordPos;

					// try to find adjectives with the same properties to the left
					found = true;
					curWordPos--;
					while (found && (curWordPos > 0)) {
						found = false;
						linkedAdjectiveList = getAdjectiveList(id, curWordPos,
								String.valueOf(substantive.wcase), substantive.gender,
								substantive.sing_pl);
						linkedAdjectiveIterator = linkedAdjectiveList.iterator();
						if (linkedAdjectiveIterator.hasNext()) {
							found = true;
							linkedAdjective = linkedAdjectiveIterator.next();
							linkedAdjective.dep_word_pos = substantive.wordPos;
							linkedAdjective.addValuestoFilters();
							curSentenceParts.add(linkedAdjective);
						}
						curWordPos--;
					}
					transferPrepositionId(substantive, curSentenceParts);
					addToSentenceParts(sentenceParts,curSentenceParts);
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void addToSentenceParts(ArrayList<SentencePart> sentenceParts,
			ArrayList<SentencePart> curSentenceParts) {
		if (curSentenceParts == null)
			return;
		if (curSentenceParts.isEmpty())
			return;
		for (SentencePart sentencePart: curSentenceParts)
			addToSentenceParts(sentenceParts, sentencePart);
	}

	private void addToSentenceParts(ArrayList<SentencePart> sentenceParts,
			SentencePart newSentencePart) {
		boolean found = false;
		Iterator<SentencePart> iterator = sentenceParts.iterator();
		SentencePart sentencePart;
		while (iterator.hasNext()) {
			sentencePart = iterator.next();
			if ((sentencePart.wordPos == newSentencePart.wordPos)
					& (sentencePart.dep_word_pos == newSentencePart.dep_word_pos)) {
				found = true;
				sentencePart.wcase_filter = addValueToFilter(newSentencePart.wcase,
						sentencePart.wcase_filter);
				sentencePart.gender_filter = addValueToFilter(newSentencePart.gender,
						sentencePart.gender_filter);
				sentencePart.sing_pl_filter = addValueToFilter(newSentencePart.sing_pl,
						sentencePart.sing_pl_filter);
			}
		}
		if (!found)
			sentenceParts.add(newSentencePart);
	}

	private String addValueToFilter(int value, String filter) {
		String result = filter;
		if (value > 0) {
			if (result.isEmpty())
				result = String.valueOf(value);
			else if (!databank.checkFilter(value, result)) {
				result = result + '|' + String.valueOf(value);
			}
		}
		return result;
	}

	private void markLinkedWords(SentencePart dependentWord, int wordPos,
			ArrayList<SentencePart> sentenceParts) {
		ArrayList<SentencePart> linkedWordList;
		Iterator<SentencePart> linkedWordIterator;
		SentencePart linkedWord;
		SentencePart conjunction;
		linkedWordList = databank.getLinkedWordList(id, dependentWord.wordPos, dependentWord.type,
				0, dependentWord.wcase, dependentWord.person, dependentWord.gender,
				dependentWord.sing_pl);
		linkedWordIterator = linkedWordList.iterator();
		while (linkedWordIterator.hasNext()) {
			linkedWord = linkedWordIterator.next();
			if ((!existsSentencePart(sentenceParts, linkedWord)) & (linkedWord.dep_word_pos == 0)) {
				linkedWord.dep_word_pos = wordPos;
				linkedWord.addValuestoFilters();
				sentenceParts.add(linkedWord);
				conjunction = databank
						.getConjunction(id, dependentWord.wordPos, linkedWord.wordPos);
				conjunction.dep_word_pos = wordPos;
				sentenceParts.add(conjunction);
				markLinkedWords(linkedWord, wordPos, sentenceParts);
			}
		}
	}

	private void transferPrepositionId(SentencePart mainSentencePart,
			ArrayList<SentencePart> sentenceParts) {
		int prepositionId = 0;
		Iterator<SentencePart> sentencePartIterator;
		SentencePart dependentSentencePart;

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
			if (mainSentencePart.wcase > 0)
				mainSentencePart.preposition_id = prepositionId;
			sentencePartIterator = sentenceParts.iterator();
			while (sentencePartIterator.hasNext()) {
				dependentSentencePart = sentencePartIterator.next();
				if ((dependentSentencePart.dep_word_pos == mainSentencePart.wordPos)
						& (dependentSentencePart.wcase > 0))
					dependentSentencePart.preposition_id = prepositionId;
			}
		}
	}

	private void parseConjunctions() {
		ArrayList<SentencePart> conjunctions;
		Iterator<SentencePart> conjunctionIterator;
		SentencePart conjunction;
		ArrayList<SentencePart> prevWordforms;
		Iterator<SentencePart> prevWordformIterator;
		SentencePart prevWordform;
		ArrayList<SentencePart> nextWordforms;
		Iterator<SentencePart> nextWordformIterator;
		SentencePart nextWordform;
		// find wordPos with conjunction
		conjunctions = databank.getConjunctions(id, "и");
		conjunctions.addAll(databank.getConjunctions(id, "или"));
		conjunctions.addAll(databank.getConjunctions(id, ","));
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
						if ((prevWordform.wcase == nextWordform.wcase) & (nextWordform.person > 0))
							databank.saveSentenceWordLink(prevWordform, conjunction, nextWordform);

					// прилагательные и местоимения прилагательные
					if ((prevWordform.wcase > 0) & (prevWordform.person == 0))
						if ((nextWordform.person == 0)
								& (prevWordform.wcase == nextWordform.wcase)
								& ((prevWordform.gender == nextWordform.gender)
										| (prevWordform.gender == 0) | (nextWordform.gender == 0))
								& ((prevWordform.sing_pl == nextWordform.sing_pl)
										| (prevWordform.sing_pl == 0) | (nextWordform.sing_pl == 0)))
							databank.saveSentenceWordLink(prevWordform, conjunction, nextWordform);
					// прочие
					if (prevWordform.wcase == 0)
						if ((nextWordform.person == prevWordform.person)
								& (prevWordform.wcase == nextWordform.wcase)
								& (prevWordform.gender == nextWordform.gender)
								& (prevWordform.sing_pl == nextWordform.sing_pl)
								& (prevWordform.type == nextWordform.type)
								& (prevWordform.subtype == nextWordform.subtype))
							databank.saveSentenceWordLink(prevWordform, conjunction, nextWordform);
				}
			}
		}
	}

	private void parseNegative() {
		boolean found = false;
		boolean empty = true;
		int curWordPos;
		ArrayList<SentencePart> negatives;
		Iterator<SentencePart> negativeIterator;
		SentencePart negative;
		ArrayList<SentencePart> nextWordforms;
		Iterator<SentencePart> nextWordformIterator;
		SentencePart nextWordform = null;

		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();

		// find wordPos of negative
		negatives = databank.getNegatives(id);
		negativeIterator = negatives.iterator();
		while (negativeIterator.hasNext()) {
			found = false;
			empty = false;
			negative = negativeIterator.next();
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

	private ArrayList<SentencePart> getAdjectiveList(int sentence_id, int wordPos,
			String wcaseFilter, int gender, int sing_pl) {
		return databank.getSentencePartList(sentence_id, "", wordPos, wcaseFilter, "0", gender,
				sing_pl, "", "");
	}

	private ArrayList<SentencePart> getAdverbList(int sentence_id, int wordPos) {
		ArrayList<SentencePart> adverbList = new ArrayList<SentencePart>();
		adverbList.addAll(databank.getSentencePartList(sentence_id, "", wordPos, "", "", 0, 0, "3",
				"1"));
		adverbList.addAll(databank.getSentencePartList(sentence_id, "", wordPos, "", "", 0, 0,
				"98", ""));
		return adverbList;
	}

	private ArrayList<SentencePart> getSubstantiveList(int sentence_id, int wordPos,
			String wcaseFilter, String personFilter, int gender, int sing_pl) {
		return databank.getSentencePartList(sentence_id, "", wordPos, wcaseFilter, personFilter,
				gender, sing_pl, "", "");
	}

	private ArrayList<SentencePart> getVerbList(int sentence_id, String subsentenceFilter,
			int wordPos, String subtypeFilter) {
		return databank.getSentencePartList(sentence_id, subsentenceFilter, wordPos, "", "", 0, 0,
				"2", subtypeFilter);
	}

	public ArrayList<SentencePart> getPrevWordforms(int sentence_id, int wordPos) {
		return databank.getSentencePartList(sentence_id, "",
				databank.getPrevIndependentWordPos(sentence_id, wordPos), "", "", 0, 0, "", "");
	}

	public ArrayList<SentencePart> getNextWordforms(int sentence_id, int wordPos) {
		return databank.getSentencePartList(sentence_id, "",
				databank.getNextIndependentWordPos(sentence_id, wordPos), "", "", 0, 0, "", "");
	}

	private ArrayList<SentencePart> copySentenceParts(ArrayList<SentencePart> sentencePartList,
			int wordPos, String subtypeFilter) {
		Iterator<SentencePart> iterator;
		SentencePart sentencePart;
		ArrayList<SentencePart> newSentencePartList = new ArrayList<SentencePart>();

		iterator = sentencePartList.iterator();
		while (iterator.hasNext()) {
			sentencePart = iterator.next();
			if ((sentencePart.wordPos == wordPos)
					& (databank.checkFilter(sentencePart.subtype, subtypeFilter)))
				newSentencePartList.add(sentencePart);
		}
		return newSentencePartList;
	}

	private boolean existsSentencePart(ArrayList<SentencePart> sentenceParts,
			SentencePart sentencePart) {
		Iterator<SentencePart> iterator = sentenceParts.iterator();
		SentencePart tempSentencePart;
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

	private ArrayList<SentencePart> gather(ArrayList<ArrayList<SentencePart>> arraylist) {
		ArrayList<SentencePart> result = new ArrayList<SentencePart>();
		Iterator<ArrayList<SentencePart>> iterator;
		iterator = arraylist.iterator();
		while (iterator.hasNext())
			result.addAll(iterator.next());
		return result;
	}
}
