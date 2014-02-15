package knowledge_app;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;

import databank.CharacterSetup;
import databank.DataBank;
import databank.DataSource;
import databank.PostgresDataBank;
import databank.Sentence;
import databank.SentenceWord;
import databank.Vocabulary;

public class Knowledge_App {
	PostgresDataBank databank;
	Vocabulary vocabulary;

	Knowledge_App() {
		long startTime;
		long curTime;
		long endTime;
		startTime = System.currentTimeMillis();
		curTime = startTime;
		databank = new PostgresDataBank("knowledge");
		databank.initDB();

		System.out.print("Loading Vocabulary from DB...");
		vocabulary = new Vocabulary(databank);
		endTime = System.currentTimeMillis();
		System.out.println("Complete in " + minutes(endTime - curTime) + " minutes.");
		curTime = endTime;

		System.out.print("Parsing Text...");
		parseSources();
		endTime = System.currentTimeMillis();
		System.out.println("Complete in " + minutes(endTime - curTime) + " minutes.");
		curTime = endTime;

		System.out.print("Saving Vocabulary to DB...");
		vocabulary.save();
		endTime = System.currentTimeMillis();
		System.out.println("Complete in " + minutes(endTime - curTime) + " minutes.");
		curTime = endTime;

		System.out.print("Parse Sentences...");
		parseSentences();
		databank.saveWordformRelationStats();
		databank.saveSentenceWordRelationHistory();
		endTime = System.currentTimeMillis();
		System.out.println("Complete in " + minutes(endTime - curTime) + " minutes.");
		curTime = endTime;
		System.out.println("Total time: " + minutes(endTime - startTime) + " minutes.");
	}

	public static void main(String[] args) {
		new Knowledge_App();
	}

	private float minutes(long l) {
		return ((float) (l / 600)) / 100;
	}

	private void parseSources() {
		boolean save;
		DataSource dataSource;
		dataSource = databank.getNextDataSource();
		while (dataSource != null) {
			save = dataSource.getAction() > 1;
			if (save)
				databank.cleanDataSource(dataSource.getID());
			parseText(dataSource.getID(), dataSource.getFilePath(), save);
			dataSource = databank.getNextDataSource();
		}
	}

	private void parseText(int sourceID, String filePath, boolean save) {
		int bufferSize = 65536;
		char[] cbuf = new char[bufferSize];
		String sentenceText;
		ArrayList<SentenceWord> sentenceWordList;
		ArrayList<CharacterSetup> characterList;
		CharacterSetup characterSetup;
		char newline = '\n';
		char dash = '-';
		char apostroph = '\'';
		int newlinecount = 0;
		char[] endSentenceMarks;
		char newchar;
		int result;
		// isPunctuation is true: current block consist of punctuation characters
		// isPunctuation is false: current block consist of letters
		boolean isPunctuation = false;
		int chameleonCharacter; // <0 - chameleon letter, >0 chameleon punctuation, 0 - not
								// chameleon
		boolean isName = false;
		boolean isSentenceEnd = false;
		boolean isFirstWord = true;
		String curInput = new String();
		sentenceText = new String();
		sentenceWordList = new ArrayList<SentenceWord>();
		try {
			characterList = databank.getCharacterList();
			endSentenceMarks = databank.getEndSentenceMarks().toCharArray();
			InputStreamReader in = new InputStreamReader(new FileInputStream(filePath), "UTF-8");
			while (in.ready()) {
				result = in.read(cbuf, 0, bufferSize);
				for (int j = 0; j < result; j++) {
					// read next character
					newchar = cbuf[j];
					// track newlines to detect paragraphs
					if (newchar == apostroph)
						newchar = ' ';
					if (newchar == newline)
						newlinecount++;

					characterSetup = getCharacterSetup(characterList, newchar);

					// chameleonCharacter contains positive number if newchar is chameleon and 0 if
					// it's not
					if (characterSetup.isChameleon())
						chameleonCharacter = 1;
					else
						chameleonCharacter = 0;
					// chameleon character '-' belongs to punctuation marks after a punctuation and
					// to words after a word
					if (!isPunctuation)
						chameleonCharacter *= -1;
					// if new character is punctuation and current block is letter
					// or character is letter and current block is punctuation then
					if ((Character.isWhitespace(newchar) || (characterSetup.isPunctuation()) || (chameleonCharacter > 0)) != isPunctuation) {
						curInput = curInput.trim();
						// add current block to sentence
						if (!curInput.isEmpty()) {
							sentenceText = sentenceText + " " + curInput;
							sentenceWordList.add(new SentenceWord(sourceID, 0, 0, 0, 0,
									new WordProcessor(curInput, isPunctuation, isName, databank,
											vocabulary).getWord(), 0, 0, 0, 0, isPunctuation,
									isName, false, "", "", "", "", ""));
						}
						isName = false;
						if (!isPunctuation)
							isFirstWord = false;
						// if current block is punctuation and one of the following
						// a. new character is a capital letter
						// b. new character is '-' and current block contains newline character
						// c. current block contains more than one newline character
						// and current block contains a character that indicates end of sentence
						// then save current sentence and initialize new one
						if (((isPunctuation) & (Character.isUpperCase(newchar) | ((newchar == dash) & (newlinecount > 0))))
								| (newlinecount > 1)) {
							for (int i = 0; i < endSentenceMarks.length; i++)
								if (curInput.indexOf(endSentenceMarks[i]) >= 0)
									isSentenceEnd = true;
							if ((isSentenceEnd) | (newlinecount > 1)) {
								if (save)
									new Sentence(databank, vocabulary, sourceID, 0, sentenceText,
											sentenceWordList).save();
								sentenceWordList = new ArrayList<SentenceWord>();
								sentenceText = new String();
								isSentenceEnd = false;
								isFirstWord = true;
							}

						}
						if (isPunctuation & Character.isUpperCase(newchar) & !isFirstWord)
							isName = true;
						// clear current block
						curInput = new String();
						newlinecount = 0;
						isPunctuation = !isPunctuation;
					}
					// add current character to current block
					curInput = curInput + Character.toString(newchar);
				}
			}
			curInput = curInput.trim();
			// add current block to sentence
			if (!curInput.isEmpty()) {
				sentenceText = sentenceText + " " + curInput;
				sentenceWordList.add(new SentenceWord(sourceID, 0, 0, 0, 0, new WordProcessor(
						curInput, isPunctuation, isName, databank, vocabulary).getWord(), 0, 0, 0,
						0, isPunctuation, isName, false, "", "", "", "", ""));
			}
			if (!sentenceText.isEmpty()) {
				if (save)
					new Sentence(databank, vocabulary, sourceID, 0, sentenceText, sentenceWordList)
							.save();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void parseSentences() {
		ArrayList<Sentence> sentences;
		Sentence sentence;
		sentences = databank.getSentences(vocabulary);
		Iterator<Sentence> iterator;
		iterator = sentences.iterator();
		while (iterator.hasNext()) {
			sentence = iterator.next();
			sentence.parse();
		}
	}

	private CharacterSetup getCharacterSetup(ArrayList<CharacterSetup> characterList, char character) {
		for (CharacterSetup characterSetup : characterList)
			if (characterSetup.equals(character))
				return characterSetup;
		return new CharacterSetup(character, 1, 0, 0, 0, 0);
	}
}