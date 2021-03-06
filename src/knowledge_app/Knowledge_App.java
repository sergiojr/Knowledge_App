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
		ArrayList<SentenceWord> sentenceWordList;
		ArrayList<CharacterSetup> characterList;
		CharacterSetup characterSetup;
		char newline = '\n';
		char apostroph = '\'';
		int newlinecount = 0;
		char newchar;
		int result;
		// isPunctuation is true: current block consist of punctuation characters
		// isPunctuation is false: current block consist of letters
		boolean isPunctuation = false;
		int chameleonCharacter; // <0 - chameleon letter, >0 chameleon punctuation, 0 - not
								// chameleon
		boolean isName = false;
		boolean isSentenceEnd = false;
		int sentenceEndIndex = -1;
		boolean stickToEnd = false;
		boolean isExpectCapitalLetter = true;
		boolean separateMode = false;
		int curElevation = 0;
		String curInput = new String();
		sentenceWordList = new ArrayList<SentenceWord>();
		try {
			characterList = databank.getCharacterList();
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

					separateMode |= characterSetup.isSeparate();
					// if new character is punctuation and current block is letter
					// or character is letter and current block is punctuation then
					if (Character.isWhitespace(newchar)
							|| separateMode
							|| (characterSetup.isPunctuation() || (chameleonCharacter > 0)) != isPunctuation) {
						curInput = curInput.trim();

						// add current block to sentence
						if (!curInput.isEmpty()) {
							sentenceWordList.add(new SentenceWord(sourceID, 0, 0, 0, 0,
									new WordProcessor(curInput, isPunctuation, isName, databank,
											vocabulary).getWord(), 0, 0, 0, curElevation,
									isPunctuation, isName, false, "", "", "", "", ""));
							if (isSentenceEnd && ((sentenceEndIndex < 0) | stickToEnd)) {
								isSentenceEnd = true;
								sentenceEndIndex = sentenceWordList.size();
								stickToEnd = true;
							}

						}

						isName = false;
						if (!isPunctuation)
							isExpectCapitalLetter = false;

						// if current block is punctuation and one of the following
						// a. new character is a capital letter
						// b. current block contains more than one newline character
						// and isSentenceEnd flag is set
						// then save current sentence and initialize new one
						if ((isPunctuation & Character.isUpperCase(newchar)) | (newlinecount > 1)) {
							if ((isSentenceEnd) | (newlinecount > 1)) {

								int wordCount = sentenceWordList.size();
								if (sentenceEndIndex < 0)
									sentenceEndIndex = wordCount;

								ArrayList<SentenceWord> tempSentenceWordList = new ArrayList<SentenceWord>();
								tempSentenceWordList.addAll(sentenceWordList.subList(0,
										sentenceEndIndex));
								if (save)
									new Sentence(databank, vocabulary, sourceID, 0,
											tempSentenceWordList).save();

								tempSentenceWordList = new ArrayList<SentenceWord>();
								tempSentenceWordList.addAll(sentenceWordList.subList(
										sentenceEndIndex, wordCount));
								sentenceWordList = tempSentenceWordList;

								isSentenceEnd = false;
								stickToEnd = false;
								sentenceEndIndex = -1;
								isExpectCapitalLetter = true;
								newlinecount = 0;
							}

						}
						if (isPunctuation & Character.isUpperCase(newchar) & !isExpectCapitalLetter)
							isName = true;
						// clear current block
						curInput = new String();
						if (!characterSetup.isChameleon())
							isPunctuation = characterSetup.isPunctuation()
									|| Character.isWhitespace(newchar);
					}
					// set isSentenceEnd flag if any character in the block is "Sentence End"
					// character
					isSentenceEnd |= characterSetup.isSentenceEnd();

					// letter block resets newlinecount and isSentenceEnd flag
					if (!Character.isWhitespace(newchar) && !characterSetup.isPunctuation()
							&& !characterSetup.isChameleon()) {
						newlinecount = 0;
						isSentenceEnd = false;
						stickToEnd = false;
						sentenceEndIndex = -1;
					}
					curElevation = characterSetup.getElevation();
					separateMode = characterSetup.isSeparate();
					isExpectCapitalLetter |= characterSetup.isExpectCapitalLetter();
					if (Character.isWhitespace(newchar))
						stickToEnd = false;							
					// add current character to current block
					curInput = curInput + Character.toString(newchar);
				}
			}
			curInput = curInput.trim();
			// add current block to sentence
			if (!curInput.isEmpty()) {
				sentenceWordList.add(new SentenceWord(sourceID, 0, 0, 0, 0, new WordProcessor(
						curInput, isPunctuation, isName, databank, vocabulary).getWord(), 0, 0, 0,
						0, isPunctuation, isName, false, "", "", "", "", ""));
			}
			if (!sentenceWordList.isEmpty()) {
				if (save)
					new Sentence(databank, vocabulary, sourceID, 0, sentenceWordList).save();
			}
			in.close();
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
		return new CharacterSetup(character, 1, 0, 0, 0, 0, false, false);
	}
}