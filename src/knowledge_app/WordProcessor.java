package knowledge_app;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import databank.ComplexWordTemplate;
import databank.DataBank;
import databank.EndingRule;
import databank.Postfix;
import databank.Vocabulary;
import databank.Word;
import databank.WordForm;

public class WordProcessor {
	private HashSet<WordForm> wordforms;
	private DataBank databank;
	private Vocabulary vocabulary;
	private boolean isPunctuation;
	private boolean isName;
	private String word;
	public int id;

	public static int substantive = 1; // существительное
	public static int verb = 2; // глагол
	public static int adjective = 3; // прилагательное
	public static int numeral = 4; // числительное
	public static int pronoun = 50; // местоимение
	public static int particle = 97; // частица
	public static int adverb = 98; // наречие
	public static int conjunction = 99; // союз
	public static int preposition = 100; // предлог
	public static int punctuation = 200; // знак препинания

	public WordProcessor(String word, boolean isPunctuation, boolean isName, DataBank databank, Vocabulary vocabulary)
			throws Exception {
		this.databank = databank;
		this.vocabulary = vocabulary;
		this.isPunctuation = isPunctuation;
		this.isName = isName;
		this.word = word.toLowerCase().intern();
		if (!isPunctuation)
			parseBaseForm(word);
	}

	private void parseBaseForm(String word) throws SQLException {
		Set<Postfix> postfixes;
		Postfix postfix;
		Word emptyWord;
		String lcWord = word.toLowerCase().intern();
		String tempWord;
		wordforms = vocabulary.getWordformsByWordformstring(lcWord);
		if (wordforms == null) {
			wordforms = databank.getFixedWordForms(vocabulary, lcWord, databank.getPostfix(0));
			if (!wordforms.isEmpty())
				if (databank.isOnlyFixedForm(lcWord))
					return;
			postfixes = databank.getPostfixes();
			Iterator<Postfix> iterator = postfixes.iterator();
			while (iterator.hasNext()) {
				postfix = iterator.next();
				if (lcWord.endsWith(postfix.getPostfix())) {
					tempWord = lcWord.substring(0, lcWord.length() - postfix.getPostfix().length());
					wordforms.addAll(databank.getFixedWordForms(vocabulary,tempWord, postfix));
					if (databank.isOnlyFixedForm(tempWord))
						return;
					wordforms.addAll(parseEnding(tempWord, postfix, 0, null));
				}
			}
			wordforms.addAll(parseEnding(lcWord, databank.getPostfix(0), 0, null));
			vocabulary.putWordformsByWordformstring(lcWord, wordforms);
		}
		if (wordforms.isEmpty()) {
			emptyWord = vocabulary.getWord(lcWord, 0, 0, 0, false, 0, 0, true);
			wordforms.add(vocabulary.createWordform(emptyWord,lcWord, null, 0));
		}
	}

	private Set<WordForm> parseEnding(String lcWord, Postfix postfix, int complexWordIndex,
			ComplexWordTemplate complexWordTemplate) throws SQLException {
		Set<WordForm> wordforms = new HashSet<WordForm>();
		// parse simple words
		for (int i = 0; i <= 5; i++)
			if (lcWord.length() > i)
				wordforms.addAll(getEndingWordForms(lcWord.substring(lcWord.length() - i),
						lcWord.substring(0, lcWord.length() - i), postfix, complexWordIndex,
						complexWordTemplate));

		// parse complex words
		HashSet<ComplexWordTemplate> complexWordTemplates = databank.getComplexWordTemplates();
		ComplexWordTemplate curComplexWordTemplate;
		Iterator<ComplexWordTemplate> iterator = complexWordTemplates.iterator();
		String[] subWords;
		Set<WordForm> word1Wordforms = null;
		Set<WordForm> word2Wordforms = null;
		Iterator<WordForm> word1WordformsIterator;
		Iterator<WordForm> word2WordformsIterator;
		WordForm word1Wordform;
		WordForm word2Wordform;
		Word word1;
		Word word2;
		Word word;
		while (iterator.hasNext()) {
			curComplexWordTemplate = iterator.next();
			if (lcWord.contains(curComplexWordTemplate.getDelimiter())) {
				subWords = lcWord.split(curComplexWordTemplate.getDelimiter(), 2);
				word1Wordforms = parseEnding(subWords[0], postfix, 1, curComplexWordTemplate);
				if (word1Wordforms != null)
					if (!word1Wordforms.isEmpty())
						word2Wordforms = parseEnding(subWords[1], postfix, 2,
								curComplexWordTemplate);
				if (word2Wordforms != null) {
					word2WordformsIterator = word2Wordforms.iterator();
					while (word2WordformsIterator.hasNext()) {
						word2Wordform = word2WordformsIterator.next();
						word1WordformsIterator = word1Wordforms.iterator();
						while (word1WordformsIterator.hasNext()) {
							word1Wordform = word1WordformsIterator.next();
							word1 = vocabulary.getWord(word1Wordform.wordID);
							word2 = vocabulary.getWord(word2Wordform.wordID);
							word = vocabulary.getWord(
									word1.getWord() + curComplexWordTemplate.getDelimiter()
											+ word2.getWord(), word2Wordform.getEndingRule(), true, word1.getId(),
									word2.getId(), true);
							wordforms.add(vocabulary.createWordform(word,lcWord + postfix.getPostfix(),
									word2Wordform.getEndingRule(), postfix.getId()));
						}
					}
				}
			}
		}
		return wordforms;
	}

	private Set<WordForm> getEndingWordForms(String ending, String base, Postfix postfix,
			int complexWordIndex, ComplexWordTemplate complexWordTemplate) throws SQLException {
		EndingRule endingrule;
		EndingRule zeroEndingrule;
		boolean valid;
		String modbase;
		Set<String> modbases;
		Word word;
		Set<WordForm> wordforms = new HashSet<WordForm>();
		Set<EndingRule> endingrules = databank.getEndingRules(ending, postfix, complexWordIndex,
				complexWordTemplate);
		Iterator<EndingRule> iterator = endingrules.iterator();
		Iterator<String> basesIterator;
		while (iterator.hasNext()) {
			Set<String> bases = new HashSet<String>();
			valid = true;
			endingrule = iterator.next();
			zeroEndingrule = databank.getZeroEndingrule(endingrule);
			if (base.length() < endingrule.getMinLength())
				valid = false;
			if (valid)
				valid=endingrule.checkBase(base);
			if (valid) {
				modbases = endingrule.getZeroForms(base, zeroEndingrule);
				bases.addAll(modbases);

				modbase = endingrule.dropCharacterE(base);
				if (modbase != null) {
					modbases = endingrule.getZeroForms(modbase, zeroEndingrule);
					bases.addAll(modbases);
				}

				modbase = endingrule.dropCharacterO(base);
				if (modbase != null) {
					modbases = endingrule.getZeroForms(modbase, zeroEndingrule);
					bases.addAll(modbases);
				}

				basesIterator = bases.iterator();
				while (basesIterator.hasNext()) {
					modbase = basesIterator.next();
					if (databank.checkBase(modbase, endingrule)) {
						word = vocabulary.getWord(modbase, endingrule, false, 0, 0, true);
						wordforms.add(vocabulary.createWordform(word,base + ending + postfix.getPostfix(),
								endingrule, postfix.getId()));
					}
				}
			}
		}
		return wordforms;
	}

	public String getWord() {
		return word;
	}

	public boolean isName() {
		return isName;
	}
}
