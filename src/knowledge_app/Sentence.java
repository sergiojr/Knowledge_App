package knowledge_app;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;

import databank.DataBank;
import databank.Numeral;

public class Sentence {
	// rating_tolerance - max allowed difference between (100-rating) and (100-maxraring) for
	// wordform
	static double rating_tolerance = 2.0;

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
		ArrayList<SentencePart> subjectList;
		Iterator<SentencePart> subjectIterator;
		SentencePart subject;
		ArrayList<SentencePart> predicateList;
		Iterator<SentencePart> predicateIterator;
		SentencePart predicate;
		ArrayList<SentencePart> sentenceParts;
		boolean success = false;
		char[] canNotParseMarks;
		if (databank == null)
			return;

		// get best wordform to fill maxrating
		try {
			databank.FillBestMatch(id);
		} catch (SQLException e) {
			e.printStackTrace();
		}

		parseNumerals();		
		parseNegative();
		parseConjunctions();
		parseAttributes();
//		parseGenetiveSubstantives();
		parseComplexPredicate();
		
		try {
			canNotParseMarks = databank.getPunctuationMarksNotReady().toCharArray();
			for (int i = 0; i < canNotParseMarks.length; i++)
				if (sentence.indexOf(canNotParseMarks[i]) >= 0)
					return;
			databank.setSentenceType(id, 1);

		} catch (SQLException e) {
			e.printStackTrace();
		}

		// получить потенциальные сказуемые, отсортированные по рейтингу
		predicateList = databank.getPredicateList(id,0,0,0,0, rating_tolerance);
		predicateIterator = predicateList.iterator();

		// получить для каждого сказуемого, потенциальные подлежащие,
		// отсортированные по рейтингу
		while ((predicateIterator.hasNext()) & !success) {
			predicate = predicateIterator.next();		
			subjectList = databank.getSubjectList(id, predicate.wordPos, predicate.person,
					predicate.gender, predicate.sing_pl,rating_tolerance);
			subjectIterator = subjectList.iterator();
			// выбрать первую пару
			if (subjectIterator.hasNext()) {
				subject = subjectIterator.next();
				sentenceParts = new ArrayList<SentencePart>();
				sentenceParts.add(subject);
				sentenceParts.add(predicate);
				databank.saveSentenceParts(sentenceParts);
				success = true;
			}
		}
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
		
		//получить список числительных
		numeralList = databank.getNumeralList(id);
		numeralIterator = numeralList.iterator();
		
		//для каждого числительного найти существительное, следующее за ним
		while(numeralIterator.hasNext()){
			numeralPart = numeralIterator.next();
			numeral = databank.getNumeralByWordID(numeralPart.word_id);
			wcase = numeralPart.wcase;
			sing_pl = numeralPart.sing_pl;
			if (databank.isNumeralBaseForm(numeralPart.word_id,numeralPart.rule_id)){
				wcase = numeral.getBaseWcase();
				sing_pl = numeral.getBaseSingPl();
			}
			substantiveList = databank.getSubstantiveList(id, numeralPart.wordPos+1, wcase, 0, numeralPart.gender, sing_pl);
			substantiveIterator = substantiveList.iterator();
			if(substantiveIterator.hasNext()){
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

		//получить существительные в родительном падеже
		genetiveSubstantiveList = databank.getSubstantiveList(id, 0, 2, 0, 0, 0);
		genetiveSubstantiveIterator=genetiveSubstantiveList.iterator();
		
		//для каждого существительного в родительном падеже получить предшествующее существительное
		while(genetiveSubstantiveIterator.hasNext()){
			genetiveSubstantive=genetiveSubstantiveIterator.next();
			if (genetiveSubstantive.wordPos>1) {
				mainSubstantiveList = databank.getSubstantiveList(id,
						genetiveSubstantive.wordPos - 1, 0, 0, 0, 0);
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

		//получить глаголы в действительной форме
		verbList = databank.getVerbList(id,1,0);
		verbIterator=verbList.iterator();
		
		//для каждого глагола в действительной форме получить глаголы в инфинитиве
		while(verbIterator.hasNext()){
			verb=verbIterator.next();
			infinitiveList=databank.getVerbList(id, 0, verb.wordPos+1);
			infinitiveIterator=infinitiveList.iterator();
			if(infinitiveIterator.hasNext()){
				infinitive=infinitiveIterator.next();
				infinitive.dep_word_pos=verb.wordPos;
				sentenceParts.add(infinitive);
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}

	private void parseAttributes(){
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

		boolean found;
		int curWordPos;
		
		// find possible substantives
		// possible substantive: wordform that has an substantive as a word
		// with maximal rating
		substantiveList = databank.getSubstantiveList(id, 0, 0, 0, 0, 0);
		substantiveIterator = substantiveList.iterator();
		
		// find possible adjectives that have common form with substantive
		// possible adjective: wordform that has an adjective as a word with
		// maximal rating
		while (substantiveIterator.hasNext()) {
			substantive = substantiveIterator.next();
			if (substantive.wordPos>1) {
				adjectiveList = databank.getAdjectiveList(id, substantive.wordPos - 1,
						substantive.wcase, substantive.gender, substantive.sing_pl);
				adjectiveIterator = adjectiveList.iterator();
				if (adjectiveIterator.hasNext()) {
					adjective = adjectiveIterator.next();
					// mark, that adjective is dependent on substantive
					adjective.dep_word_pos = substantive.wordPos;
					transferPrepositionId(adjective.preposition_id, substantive, sentenceParts);
					sentenceParts.add(adjective);
					// mark any linked adjective
					linkedAdjectiveList = databank.getLinkedWordList(id, adjective.wordPos,
							WordProcessor.adjective, 0, adjective.wcase, adjective.person,
							adjective.gender, adjective.sing_pl);
					linkedAdjectiveIterator = linkedAdjectiveList.iterator();
					if(linkedAdjectiveIterator.hasNext())
						while (linkedAdjectiveIterator.hasNext()) {
							linkedAdjective = linkedAdjectiveIterator.next();
							linkedAdjective.dep_word_pos = substantive.wordPos;
							transferPrepositionId(linkedAdjective.preposition_id, substantive,
									sentenceParts);
							sentenceParts.add(linkedAdjective);
						}
					else{
						found=true;
						curWordPos=adjective.wordPos-1;
						while(found&&(curWordPos>0)){
							found=false;
							linkedAdjectiveList = databank.getAdjectiveList(id, curWordPos,
									substantive.wcase, substantive.gender, substantive.sing_pl);
							linkedAdjectiveIterator = linkedAdjectiveList.iterator();
							if (linkedAdjectiveIterator.hasNext()) {
								found=true;
								linkedAdjective = linkedAdjectiveIterator.next();
								linkedAdjective.dep_word_pos = substantive.wordPos;
								transferPrepositionId(linkedAdjective.preposition_id, substantive,
										sentenceParts);
								sentenceParts.add(linkedAdjective);
							}
							curWordPos--;
						}
						
					}
				}
			}
		}
		databank.saveSentenceParts(sentenceParts);
	}	
	
	private void transferPrepositionId(int prepositionId, SentencePart substantive,
			ArrayList<SentencePart> sentenceParts) {
		Iterator<SentencePart> sentencePartIterator;
		SentencePart sentencePart;
		if (prepositionId > 0) {
			substantive.preposition_id = prepositionId;
			sentencePartIterator=sentenceParts.iterator();
			while(sentencePartIterator.hasNext()){
				sentencePart=sentencePartIterator.next();
				if(sentencePart.dep_word_pos==substantive.wordPos)
					sentencePart.preposition_id=prepositionId;
			}
			if(!sentenceParts.contains(substantive))
				sentenceParts.add(substantive);			
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
		conjunctions = databank.getConjunctions(id);
		conjunctionIterator = conjunctions.iterator();
		while (conjunctionIterator.hasNext()) {
			conjunction = conjunctionIterator.next();
			// get wordforms with maxrating at previous and next position
			prevWordforms = databank.getPreviousWordforms(id, conjunction.wordPos);
			nextWordforms = databank.getNextWordforms(id, conjunction.wordPos);
			// find and save pairs with same rules
			prevWordformIterator = prevWordforms.iterator();
			while (prevWordformIterator.hasNext()) {
				prevWordform = prevWordformIterator.next();
				nextWordformIterator = nextWordforms.iterator();
				while (nextWordformIterator.hasNext()) {
					nextWordform = nextWordformIterator.next();
					if ((prevWordform.type == nextWordform.type)
							& (prevWordform.wcase == nextWordform.wcase)
							& (prevWordform.subtype == nextWordform.subtype))
						databank.saveSentenceWordLink(prevWordform, nextWordform);
				}
			}
		}
	}

	private void parseNegative() {
		boolean found=false;
		boolean empty=true;
		int curWordPos;
		ArrayList<SentencePart> negatives;
		Iterator<SentencePart> negativeIterator;
		SentencePart negative;
		ArrayList<SentencePart> nextWordforms;
		Iterator<SentencePart> nextWordformIterator;
		SentencePart nextWordform=null;
		
		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();
		
		//find wordPos of negative
		negatives = databank.getNegatives(id);
		negativeIterator=negatives.iterator();
		while(negativeIterator.hasNext()){
			found=false;
			empty=false;
			negative= negativeIterator.next();
			curWordPos=negative.wordPos;
			while (!found&&!empty) {
				empty=true;
				nextWordforms = databank.getNextWordforms(id, curWordPos);
				nextWordformIterator = nextWordforms.iterator();
				while (!found && (nextWordformIterator.hasNext())) {
					empty=false;
					nextWordform = nextWordformIterator.next();
					found = (nextWordform.type != WordProcessor.preposition);
				}
				curWordPos++;				
			}
			if (!empty&&found&&(nextWordform!=null)){
				negative.dep_word_pos=nextWordform.wordPos;
				sentenceParts.add(negative);
			}			
		}
		databank.saveSentenceParts(sentenceParts);
	}
}
