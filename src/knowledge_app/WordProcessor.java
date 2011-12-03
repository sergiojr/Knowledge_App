package knowledge_app;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import databank.ComplexWordTemplate;
import databank.DataBank;
import databank.Postfix;
import databank.Word;

public class WordProcessor {
	private HashSet<WordForm> wordforms;
	private DataBank databank;
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

	public WordProcessor(String word, boolean isPunctuation, boolean isName, DataBank databank)
			throws Exception {
		this.databank = databank;
		this.isPunctuation = isPunctuation;
		this.isName = isName;
		this.word = word.toLowerCase().intern();
		if (!isPunctuation)
			parseBaseForm(word);
	}

	public boolean isPunctuation() {
		return isPunctuation;
	}

	private void parseBaseForm(String word) throws SQLException {
		Set<Postfix> postfixes;
		Postfix postfix;
		Word emptyword;
		String lcWord = word.toLowerCase().intern();
		String tempWord;
		wordforms = databank.getWordformsByWordformstring(lcWord);
		if (wordforms == null) {
			wordforms = databank.getFixedWordForms(lcWord, databank.getPostfix(0));
			if (!wordforms.isEmpty())
				if (databank.isOnlyFixedForm(lcWord))
					return;
			postfixes = databank.getPostfixes();
			Iterator<Postfix> iterator = postfixes.iterator();
			while (iterator.hasNext()) {
				postfix = iterator.next();
				if (lcWord.endsWith(postfix.getPostfix())) {
					tempWord = lcWord.substring(0, lcWord.length() - postfix.getPostfix().length());
					wordforms.addAll(databank.getFixedWordForms(tempWord, postfix));
					if (databank.isOnlyFixedForm(tempWord))
						return;
					wordforms.addAll(parseEnding(tempWord, postfix, 0, null));
				}
			}
			wordforms.addAll(parseEnding(lcWord, databank.getPostfix(0), 0, null));
			databank.putWorformsByWordformstring(lcWord, wordforms);
		}
		if (wordforms.isEmpty()) {
			emptyword = databank.getWord(lcWord, 0, 0, false, 0, 0, true);
			wordforms.add(emptyword.createWordform(lcWord, 0, 0));
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
							word1 = databank.getWord(word1Wordform.wordID);
							word2 = databank.getWord(word2Wordform.wordID);
							word = databank.getWord(
									word1.getWord() + curComplexWordTemplate.getDelimiter()
											+ word2.getWord(), word2Wordform.endingRule.type,
									word2Wordform.endingRule.rule_no, true, word1.getId(),
									word2.getId(), true);
							wordforms.add(word.createWordform(lcWord + postfix.getPostfix(),
									word2Wordform.endingRule.rule_id, postfix.getId()));
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
		int i;
		String[] baseEnd;
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
			zeroEndingrule = databank.getZeroEndingrule(endingrule.rule_no);
			if (base.length() < endingrule.min_length)
				valid = false;
			if (endingrule.allow_after != null)
				if (valid & !endingrule.allow_after.isEmpty()) {
					baseEnd = endingrule.allow_after.split(";");
					valid = false;
					i = 0;
					while ((!valid) & (i < baseEnd.length)) {
						valid = base.endsWith(baseEnd[i]);
						i++;
					}
				}
			if (endingrule.deny_after != null)
				if (valid & !endingrule.deny_after.isEmpty()) {
					baseEnd = endingrule.deny_after.split(";");
					i = 0;
					while (valid & (i < baseEnd.length)) {
						valid = !base.endsWith(baseEnd[i]);
						i++;
					}
				}
			if (valid) {
				modbases = getZeroForms(base, endingrule, zeroEndingrule);
				bases.addAll(modbases);

				modbase = dropCharacter(base, endingrule.e_before, 'е', 'ь', "л");
				if (modbase != null) {
					modbases = getZeroForms(modbase, endingrule, zeroEndingrule);
					bases.addAll(modbases);
				}

				modbase = dropCharacter(base, endingrule.o_before, 'о', 'ь', "");
				if (modbase != null) {
					modbases = getZeroForms(modbase, endingrule, zeroEndingrule);
					bases.addAll(modbases);
				}

				basesIterator = bases.iterator();
				while (basesIterator.hasNext()) {
					modbase = basesIterator.next();
					if (checkBase(modbase, endingrule.type)) {
						word = databank.getWord(modbase, endingrule.type, endingrule.rule_no,
								false, 0, 0, true);
						wordforms.add(word.createWordform(base + ending + postfix.getPostfix(),
								endingrule.rule_id, postfix.getId()));
					}
				}
			}
		}
		return wordforms;
	}

	private boolean checkBase(String base, int type) {
		if (type == numeral) {
			return databank.existNumeral(base);
		}
		return true;
	}

	/**
	 * Changes alternating letters at the end of the base
	 * 
	 * @param base
	 *            - current base
	 * @param endingrule
	 *            - current ending rule
	 * @param zeroEndingrule
	 *            - ending rule of base word form
	 * @return list of bases
	 */
	private Set<String> getZeroForms(String base, EndingRule endingrule, EndingRule zeroEndingrule) {
		String[] baseEnd;
		String[] modEnd;
		int matchLength = 0;
		String modbase;
		Set<String> modbases = new HashSet<String>();

		if (endingrule.rule_id == zeroEndingrule.rule_id) {
			modbases.add(base);
			return modbases;
		}

		if ((endingrule.allow_after == null) | (zeroEndingrule.allow_after == null)) {
			modbases.add(base);
			return modbases;
		}

		if ((endingrule.allow_after.isEmpty()) | (zeroEndingrule.allow_after.isEmpty())) {
			modbases.add(base);
			return modbases;
		}

		if (endingrule.allow_after.intern() == zeroEndingrule.allow_after.intern()) {
			modbases.add(base);
			return modbases;
		}

		baseEnd = endingrule.allow_after.split(";");
		modEnd = zeroEndingrule.allow_after.split(";");

		for (int i = 0; i < baseEnd.length; i++)
			if (baseEnd[i].length() >= matchLength)
				if (base.endsWith(baseEnd[i]))
					if (baseEnd[i].length() == matchLength) {
						modbase = base.substring(0, base.length() - baseEnd[i].length())
								+ modEnd[i];
						modbases.add(modbase);
					} else {
						matchLength = baseEnd[i].length();
						modbases = new HashSet<String>();
						modbase = base.substring(0, base.length() - baseEnd[i].length())
								+ modEnd[i];
						modbases.add(modbase);
					}
		return modbases;
	}

	private String dropCharacter(String base, String checkList, char character, char change,
			String change_after) {
		String[] baseEnd;
		String checkEnd;
		String modbase;
		if ((checkList != null) & (base.length() > 2))
			if (!checkList.isEmpty()) {
				baseEnd = checkList.split(";");
				for (int i = 0; i < baseEnd.length; i++) {
					checkEnd = character + baseEnd[i];
					if (base.endsWith(checkEnd)) {
						modbase = base.substring(0, base.length() - checkEnd.length());
						if (!change_after.isEmpty())
							if (modbase.endsWith(change_after))
								modbase = modbase + change;
						modbase = modbase + baseEnd[i];
						return modbase;
					}
				}
			}
		return null;
	}

	public String getWord() {
		return word;
	}

	public boolean isName() {
		return isName;
	}

}
