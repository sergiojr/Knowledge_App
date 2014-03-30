package databank;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import knowledge_app.WordProcessor;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.BinaryCondition.Op;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;

public class DataBank {
	private Setup setup;
	private HashSet<Prefix> prefixes;
	private HashSet<Postfix> postfixes;
	private HashMap<Integer, Prefix> prefixesById;
	private HashMap<String, Prefix> prefixesByPrefix;
	private HashMap<Integer, Integer> ruleDiversity;
	private HashMap<Integer, ArrayList<Integer>> ruleVarianceByRuleNo;
	private HashMap<Integer, EndingRule> zeroEndingruleByRuleNo;
	private HashMap<Integer, EndingRule> endingRulesByRuleNo;

	private HashSet<ComplexWordTemplate> complexWordTemplates;
	private HashSet<Numeral> numerals;
	private HashMap<String, Numeral> numeralsByNumeral;
	private static int insertBatchLimit = 10000;

	String DBName;
	String DBFileName;
	int sentenceCount;
	Connection conn;

	public DataBank(String DBName) {
		sentenceCount = 0;
		this.DBName = DBName;
		DBFileName = DBName + ".sqlite";
	}

	public void initDB() {
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.executeUpdate("drop table if exists sentences;");
			stat.executeUpdate("drop table if exists words;");
			stat.executeUpdate("drop table if exists wordforms;");
			stat.executeUpdate("drop table if exists sentence_word;");
			stat.executeUpdate("create table if not exists sentences "
					+ "(id INTEGER PRIMARY KEY,sentence text,type int);");
			stat.executeUpdate("create table if not exists words "
					+ "(id INTEGER PRIMARY KEY,word text,type int,rule_no int,rating int);");
			stat.executeUpdate("CREATE INDEX IF NOT EXISTS index01 " + "ON words ( type, word )");
			stat.executeUpdate("create table if not exists wordforms "
					+ "(word_id,wordform,rule_id,postfix_id);");
			stat.executeUpdate("CREATE INDEX IF NOT EXISTS index02 "
					+ "ON wordforms ( word_id,rule_id, postfix_id )");
			stat.executeUpdate("CREATE INDEX IF NOT EXISTS index03 " + "ON wordforms ( wordform )");
			stat.executeUpdate("create table if not exists sentence_word "
					+ "(sentence_id,word_pos,word,punctuation,word_id,"
					+ "rule_id,postfix_id,type INTEGER,preposition_id INTEGER, dep_word_pos INTEGER);");
			stat.executeUpdate("CREATE INDEX IF NOT EXISTS index04 "
					+ "ON sentence_word ( word_id )");
			stat.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void cleanDataSource(int id) {
		String query = MessageFormat.format("delete from sentences where source_id={0,number,#}",
				id);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.executeUpdate(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	void establishConnection() throws SQLException {
		try {
			if (conn == null) {
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection("jdbc:sqlite:" + DBFileName);
			}
			if (conn.isClosed()) {
				Class.forName("org.sqlite.JDBC");
				conn = DriverManager.getConnection("jdbc:sqlite:" + DBFileName);
			}
		} catch (ClassNotFoundException e) {
			System.out.println(e.toString());
			System.exit(0);
		}
	}

	public int saveSentence(int sourceID, int type, String sentence,
			ArrayList<SentenceWord> sentenceWordList) throws SQLException {
		int i = 0;
		if (sentence.isEmpty())
			return -1;

		sentenceCount++;
		establishConnection();
		Statement stat = conn.createStatement();
		stat.executeUpdate("insert into sentences " + "values (" + sentenceCount + ",'" + sentence
				+ "'," + type + "," + sourceID + ");");
		stat.close();
		PreparedStatement sent_word_insert = conn.prepareStatement("insert into "
				+ "sentence_word (source_id,sentence_id,word_pos,word,punctuation,name,elevation_dif) "
				+ "values (?,?,?,?,?,?,?);");
		for (SentenceWord word : sentenceWordList) {
			i++;
			word.sentenceID = sentenceCount;
			word.wordPos = i;
			sent_word_insert.setInt(1, sourceID);
			sent_word_insert.setInt(2, sentenceCount);
			sent_word_insert.setInt(3, i);
			sent_word_insert.setString(4, word.word);
			sent_word_insert.setBoolean(5, word.isPunctuation);
			sent_word_insert.setBoolean(6, word.isName);
			sent_word_insert.setInt(7, word.elevation_dif);
			sent_word_insert.addBatch();
		}
		conn.setAutoCommit(false);
		sent_word_insert.executeBatch();
		conn.setAutoCommit(true);
		sent_word_insert.close();
		return sentenceCount;
	}

	public ArrayList<Word> getWords(String baseForm, int type) {
		ArrayList<Word> wordsList = new ArrayList<Word>();
		Word word;

		if (baseForm == null)
			return wordsList;

		SelectQuery query;
		query = new SelectQuery();
		query.addAllColumns();
		query.addCustomFromTable(new CustomSql("words"));
		query.addCustomJoin(new CustomSql(
				" left join complex_word on words.id=complex_word.word_id"));
		if (type > 0)
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"type"), type));
		if (baseForm != "")
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"word"), baseForm));

		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				word = new Word(this, rs.getInt("id"), rs.getString("word"), rs.getInt("type"),
						rs.getInt("rule_no"), rs.getInt("rule_variance"), rs.getBoolean("complex"),
						rs.getInt("word1"), rs.getInt("word2"), rs.getInt("rating"));
				wordsList.add(word);
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordsList;
	}

	public void saveWord(HashSet<Word> words) {
		int minWordId = 0;
		int maxWordId = 0;
		int batchCounter = 0;
		ArrayList<Word> orderedWords;
		ArrayList<Word> updateWords;

		if (words == null)
			return;

		for (Word word : words) {
			if ((minWordId == 0) || (word.id < minWordId))
				minWordId = word.id;

			if (word.id > maxWordId)
				maxWordId = word.id;
		}

		orderedWords = new ArrayList<Word>();
		orderedWords.ensureCapacity(maxWordId - minWordId + 1);
		while (orderedWords.size() < maxWordId - minWordId + 1)
			orderedWords.add(null);

		for (Word word : words)
			orderedWords.set(word.id - minWordId, word);

		updateWords = new ArrayList<Word>();

		try {
			establishConnection();
			conn.setAutoCommit(false);
			PreparedStatement readWord = conn
					.prepareStatement("select id from words where id>=? and id<=?");
			readWord.setInt(1, minWordId);
			readWord.setInt(2, maxWordId);
			ResultSet rs = readWord.executeQuery();
			while (rs.next()) {
				int curId = rs.getInt("id") - minWordId;
				Word curWord = orderedWords.get(curId);
				if (curWord != null) {
					updateWords.add(curWord);
					orderedWords.set(curId, null);
				}
			}
			rs.close();
			readWord.close();

			PreparedStatement updateWord = conn.prepareStatement("update words "
					+ "set rule_variance = ?, rating=? where id=?");

			for (Word word : updateWords) {
				if (batchCounter > insertBatchLimit) {
					updateWord.executeBatch();
					batchCounter = 0;
				}
				updateWord.setInt(1, word.rule_variance);
				updateWord.setInt(2, word.rating);
				updateWord.setInt(3, word.id);
				updateWord.addBatch();
				batchCounter++;
			}
			updateWord.executeBatch();
			updateWord.close();

			PreparedStatement saveWord = conn.prepareStatement("insert into words "
					+ "values (?,?,?,?,?,?,?);");
			PreparedStatement saveComplexWord = conn.prepareStatement("insert into complex_word "
					+ "values (?,?,?)");

			for (Word word : orderedWords) {
				if (batchCounter > insertBatchLimit) {
					saveWord.executeBatch();
					batchCounter = 0;
				}
				if (word != null) {
					saveWord.setInt(1, word.id);
					saveWord.setString(2, word.word);
					saveWord.setInt(3, word.type);
					saveWord.setInt(4, word.rule_no);
					saveWord.setInt(5, word.rating);
					saveWord.setBoolean(6, word.complex);
					saveWord.setInt(7, word.rule_variance);
					saveWord.addBatch();
					batchCounter++;
					if (word.complex) {
						saveComplexWord.setInt(1, word.id);
						saveComplexWord.setInt(2, word.word1ID);
						saveComplexWord.setInt(3, word.word2ID);
						saveComplexWord.addBatch();
					}
				}
			}

			saveWord.executeBatch();
			saveComplexWord.executeBatch();
			conn.setAutoCommit(true);
			saveWord.close();
			saveComplexWord.close();
		} catch (SQLException e) {
			e.printStackTrace();
			e.getNextException().printStackTrace();
		}
	}

	public HashSet<WordForm> getWordforms(Word word) {
		EndingRule endingrule;
		HashSet<WordForm> wordforms = new HashSet<WordForm>();
		SelectQuery query = new SelectQuery();
		query.addAllColumns();
		query.addCustomFromTable(new CustomSql("wordforms"));
		query.addCustomJoin(new CustomSql(" join words on words.id=wordforms.word_id"));
		if (word != null)
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"word_id"), word.id));

		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				endingrule = getEndingRule(rs.getInt("rule_no") < 0, rs.getInt("rule_id"));
				wordforms.add(new WordForm(rs.getString("wordform"), rs.getInt("id"), endingrule,
						rs.getInt("postfix_id")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordforms;
	}

	public void saveWordforms(HashSet<WordForm> delayedSaveWordforms) {
		int batchCounter = 0;
		if (delayedSaveWordforms == null)
			return;
		// transform HashSet to ArrayList (by Word ID) of ArrayLists
		int minWordID = 0;
		int maxWordID = 0;
		for (WordForm wordform : delayedSaveWordforms) {
			if ((minWordID == 0) || (wordform.wordID < minWordID))
				minWordID = wordform.wordID;

			if (wordform.wordID > maxWordID)
				maxWordID = wordform.wordID;
		}

		// initialize orderedArrayList
		ArrayList<ArrayList<WordForm>> orderedWordFormList = new ArrayList<ArrayList<WordForm>>();
		orderedWordFormList.ensureCapacity(maxWordID - minWordID + 1);
		for (int i = 0; i < maxWordID - minWordID + 1; i++)
			orderedWordFormList.add(null);

		for (WordForm wordform : delayedSaveWordforms) {
			ArrayList<WordForm> wordFormList = orderedWordFormList.get(wordform.wordID - minWordID);
			if (wordFormList == null) {
				wordFormList = new ArrayList<WordForm>();
				orderedWordFormList.set(wordform.wordID - minWordID, wordFormList);
			}
			wordFormList.add(wordform);
		}

		try {
			establishConnection();
			PreparedStatement readWordform = conn.prepareStatement("select rule_id,postfix_id "
					+ "from wordforms where word_id=?");
			PreparedStatement saveWordform = conn.prepareStatement("insert into wordforms "
					+ "values (?,?,?,?);");
			conn.setAutoCommit(false);
			for (int i = 0; i < maxWordID - minWordID + 1; i++) {
				if (batchCounter > insertBatchLimit) {
					saveWordform.executeBatch();
					batchCounter = 0;
				}
				ArrayList<WordForm> wordFormList = orderedWordFormList.get(i);
				if (wordFormList != null) {
					readWordform.setInt(1, i + minWordID);
					ResultSet rs = readWordform.executeQuery();
					while (rs.next()) {
						HashSet<WordForm> removeWordFormList = new HashSet<WordForm>();
						int rule_id = rs.getInt("rule_id");
						int postfix_id = rs.getInt("postfix_id");
						for (WordForm wordform : wordFormList)
							if ((wordform.getRuleID() == rule_id)
									&& (wordform.postfix_id == postfix_id))
								removeWordFormList.add(wordform);
						wordFormList.removeAll(removeWordFormList);
					}
					rs.close();

					for (WordForm wordform : wordFormList) {
						saveWordform.setInt(1, wordform.wordID);
						saveWordform.setString(2, wordform.wordForm);
						saveWordform.setInt(3, wordform.getRuleID());
						saveWordform.setInt(4, wordform.postfix_id);
						saveWordform.addBatch();
						batchCounter++;
					}
				}
			}
			readWordform.close();
			saveWordform.executeBatch();
			conn.setAutoCommit(true);
			saveWordform.close();
		} catch (SQLException e) {
			e.printStackTrace();
			e.getNextException().printStackTrace();
		}
	}

	public HashSet<WordWordRelation> getWordWordRelation(int wordID, int relationType) {
		HashSet<WordWordRelation> result = new HashSet<WordWordRelation>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("word_word_relation"));
			if (relationType >= 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"relation_type"), relationType));
			if (wordID > 0) {
				ComboCondition cc = new ComboCondition(ComboCondition.Op.OR);
				cc.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_id"), wordID));
				cc.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"parent_word_id"), wordID));
				query.addCondition(cc);
			}
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next())
				result.add(new WordWordRelation(rs.getInt("word_id"), rs.getInt("parent_word_id"),
						rs.getInt("relation_type"), rs.getInt("relation_ref_id"), rs
								.getInt("relation_ref_line")));
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public void saveWordWordRelation(HashSet<WordWordRelation> wordRelations) {
		if (wordRelations == null)
			return;
		try {
			establishConnection();
			PreparedStatement readWordWordRelation = conn
					.prepareStatement("select 1 from word_word_relation "
							+ "where word_id=? and parent_word_id=? and relation_type=?");
			PreparedStatement saveWordWordRelation = conn
					.prepareStatement("insert into word_word_relation " + "values (?,?,?,?,?);");
			for (WordWordRelation wordRelation : wordRelations) {
				readWordWordRelation.setInt(1, wordRelation.wordID);
				readWordWordRelation.setInt(2, wordRelation.parentWordID);
				readWordWordRelation.setInt(3, wordRelation.relationType);
				ResultSet rs = readWordWordRelation.executeQuery();
				if (!rs.next()) {
					saveWordWordRelation.setInt(1, wordRelation.wordID);
					saveWordWordRelation.setInt(2, wordRelation.parentWordID);
					saveWordWordRelation.setInt(3, wordRelation.relationType);
					saveWordWordRelation.setInt(4, wordRelation.relationRefID);
					saveWordWordRelation.setInt(5, wordRelation.relationRefLine);
					saveWordWordRelation.addBatch();
				}
			}
			readWordWordRelation.close();
			conn.setAutoCommit(false);
			saveWordWordRelation.executeBatch();
			conn.setAutoCommit(true);
			saveWordWordRelation.close();
		} catch (SQLException e) {
			e.printStackTrace();
			e.getNextException().printStackTrace();
		}
	}

	public void saveWordformRelationStats() {
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.execute("delete from wordform_relation_stats;");
			stat.execute("insert into wordform_relation_stats select * from sentence_word_relation_stats;");
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void saveSentenceWordRelationHistory() {
		int max_version_id = 0;
		String query;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			// copy error flag to all versions of relation
			stat.execute("update sentence_word_relation_history set error = true where exists "
					+ "(select 1 from sentence_word_relation_history l_history "
					+ "where sentence_word_relation_history.source_id=l_history.source_id and "
					+ "sentence_word_relation_history.relation_type=l_history.relation_type and "
					+ "sentence_word_relation_history.sentence_id=l_history.sentence_id and "
					+ "sentence_word_relation_history.word_pos = l_history.word_pos and "
					+ "sentence_word_relation_history.dep_word_pos=l_history.dep_word_pos and l_history.error=true);");

			// clean error relations from reference version (id=0)
			stat.execute("delete from sentence_word_relation_history where version_id=0 and error=true");

			// copy non-error entries from last version to reference version (id=0)
			stat.execute("insert into sentence_word_relation_history "
					+ "select 0, source_id, sentence_id, relation_type, word_pos, preposition, "
					+ "word, type, dep_word_pos, dep_preposition, dep_word, dep_type, error "
					+ "from sentence_word_relation_history history "
					+ "where version_id=(select max(version_id) from sentence_word_relation_history) and "
					+ "error=false and not exists(select 1 from sentence_word_relation_history ref_history "
					+ "where ref_history.version_id=0 and ref_history.source_id=history.source_id and "
					+ "ref_history.sentence_id=history.sentence_id and ref_history.relation_type=history.relation_type and "
					+ "ref_history.word_pos=history.word_pos and ref_history.dep_word_pos=history.dep_word_pos)");

			// get last version id
			ResultSet rs = stat
					.executeQuery("select max(version_id)  max_version_id from sentence_word_relation_history");
			if (rs.next()) {
				max_version_id = rs.getInt("max_version_id");
			}

			// copy current version to history
			query = MessageFormat
					.format("INSERT INTO sentence_word_relation_history "
							+ "SELECT {0,number,#}, source_id, sentence_id, relation_type, word_pos, preposition,"
							+ "word, type, dep_word_pos, dep_preposition, dep_word, dep_type, "
							+ "exists (select 1 from sentence_word_relation_history l_history "
							+ "where dep_words_pair2.source_id=l_history.source_id and "
							+ "dep_words_pair2.relation_type=l_history.relation_type and "
							+ "dep_words_pair2.sentence_id = l_history.sentence_id and "
							+ "dep_words_pair2.word_pos = l_history.word_pos and "
							+ "dep_words_pair2.dep_word_pos=l_history.dep_word_pos and l_history.error=true) error "
							+ "FROM dep_words_pair2;", max_version_id + 1);
			stat.execute(query);
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public HashMap<String, ArrayList<EndingRuleStat>> getWordformRelationStats() {
		HashMap<String, ArrayList<EndingRuleStat>> result = new HashMap<String, ArrayList<EndingRuleStat>>();
		String curWordform;
		ArrayList<EndingRuleStat> curRelationStats;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat
					.executeQuery("select * from wordform_relation_stats order by wordform");
			while (rs.next()) {
				curWordform = rs.getString("wordform").intern();
				curRelationStats = result.get(curWordform);
				if (curRelationStats == null) {
					curRelationStats = new ArrayList<EndingRuleStat>();
					result.put(curWordform, curRelationStats);
				}
				curRelationStats.add(new EndingRuleStat(rs.getInt("type"), rs.getInt("wcase"), rs
						.getInt("gender"), rs.getInt("sing_pl"), rs.getInt("wordform_count")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public HashSet<String> getFixesOnlyForms() {
		HashSet<String> result = new HashSet<String>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String query = "select word from fixed_words where only_form=1";
			ResultSet rs = stat.executeQuery(query);
			while (rs.next())
				result.add(rs.getString("word").intern());
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public ArrayList<WordForm> getFixedWordForms(Vocabulary vocabulary, String wordform,
			Postfix postfix) throws SQLException {
		Word word;
		ArrayList<WordForm> wordforms = new ArrayList<WordForm>();
		ResultSet rs;
		establishConnection();
		Statement stat = conn.createStatement();

		SelectQuery query = new SelectQuery();
		query.addAllColumns();
		query.addCustomFromTable(new CustomSql("fixed_words"));
		query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql("word"),
				wordform));

		if (postfix != null) {
			if (postfix.type > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"type"), postfix.type));
			// if (postfix.tense > 0)
			// query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
			// "tense"), postfix.tense));
		}

		rs = stat.executeQuery(query.validate().toString());

		while (rs.next()) {
			word = vocabulary.getWord(rs.getString("base_form"), rs.getInt("type"),
					-rs.getInt("type"), 0, false, 0, 0, true);
			EndingRule endingRule = getEndingRule(true, rs.getInt("rule_id"));
			wordforms.add(vocabulary.createWordform(word, wordform + postfix.postfix, endingRule,
					postfix.id));
		}

		rs.close();
		stat.close();
		return wordforms;
	}

	public ArrayList<EndingRule> getEndingRules(String ending, Postfix postfix,
			int complexWordIndex, ComplexWordTemplate complexWordTemplate) throws SQLException {
		ArrayList<EndingRule> endingrules = new ArrayList<EndingRule>();
		ResultSet rs;
		establishConnection();
		Statement stat = conn.createStatement();

		SelectQuery query = new SelectQuery();
		query.addAllColumns();
		query.addCustomFromTable(new CustomSql("ending_rules"));
		query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO,
				new CustomSql("ending"), ending));
		if (postfix != null) {
			if (postfix.type > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"type"), postfix.type));
			if (postfix.tense > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"tense"), postfix.tense));
			if (postfix.rule_no > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"rule_no"), postfix.rule_no));
		}

		if ((complexWordIndex == 1) & (complexWordTemplate != null)) {
			if (complexWordTemplate.word1_type > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"type"), complexWordTemplate.word1_type));
			if (complexWordTemplate.word1_subtype > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"subtype"), complexWordTemplate.word1_subtype));
			if (complexWordTemplate.word1_wcase > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"wcase"), complexWordTemplate.word1_wcase));
			if (complexWordTemplate.word1_sing_pl > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"sing_pl"), complexWordTemplate.word1_sing_pl));
		}

		if ((complexWordIndex == 2) & (complexWordTemplate != null)) {
			if (complexWordTemplate.word2_type > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"type"), complexWordTemplate.word2_type));
			if (complexWordTemplate.word2_subtype > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"subtype"), complexWordTemplate.word2_subtype));
			if (complexWordTemplate.word2_wcase > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"wcase"), complexWordTemplate.word2_wcase));
			if (complexWordTemplate.word2_sing_pl > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"sing_pl"), complexWordTemplate.word2_sing_pl));
		}

		rs = stat.executeQuery(query.validate().toString());

		while (rs.next()) {
			endingrules.add(new EndingRule(ending, rs.getInt("rule_no"),
					rs.getInt("rule_variance"), rs.getInt("type"), rs.getInt("subtype"), rs
							.getInt("rule_id"), rs.getInt("wcase"), rs.getInt("gender"), rs
							.getInt("sing_pl"), rs.getInt("tense"), rs.getInt("animate"), rs
							.getInt("person"), rs.getString("allow_after"), rs
							.getString("deny_after"), rs.getString("e_before"), rs
							.getString("o_before"), rs.getInt("min_length")));
		}
		rs.close();
		stat.close();
		return endingrules;
	}

	/**
	 * *
	 * 
	 * @param endingrule
	 *            - integer value of rule no
	 * @return ending rule of a base word form
	 */
	public EndingRule getZeroEndingrule(EndingRule endingrule) {
		int ruleNo = endingrule.rule_no;
		EndingRule zeroEndingrule = null;
		if (zeroEndingruleByRuleNo == null)
			zeroEndingruleByRuleNo = new HashMap<Integer, EndingRule>();
		zeroEndingrule = zeroEndingruleByRuleNo.get(new Integer(ruleNo));
		if (zeroEndingrule != null)
			return zeroEndingrule;
		try {
			ResultSet rs;
			establishConnection();
			Statement stat = conn.createStatement();

			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("ending_rules"));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"rule_no"), ruleNo));
			query.addCustomOrderings(new CustomSql("tense, sing_pl, person, gender, wcase"));
			rs = stat.executeQuery(query.validate().toString());
			if (rs.next()) {
				zeroEndingrule = new EndingRule(rs.getString("ending"), rs.getInt("rule_no"),
						rs.getInt("rule_variance"), rs.getInt("type"), rs.getInt("subtype"),
						rs.getInt("rule_id"), rs.getInt("wcase"), rs.getInt("gender"),
						rs.getInt("sing_pl"), rs.getInt("tense"), rs.getInt("animate"),
						rs.getInt("person"), rs.getString("allow_after"),
						rs.getString("deny_after"), rs.getString("e_before"),
						rs.getString("o_before"), rs.getInt("min_length"));
				zeroEndingruleByRuleNo.put(new Integer(ruleNo), zeroEndingrule);
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return zeroEndingrule;
	}

	private EndingRule getEndingRule(boolean fixed, int rule) {
		int ruleNo;
		EndingRule endingRule = null;

		if (fixed)
			ruleNo = -rule;
		else
			ruleNo = rule;

		if (endingRulesByRuleNo == null)
			endingRulesByRuleNo = new HashMap<Integer, EndingRule>();
		endingRule = endingRulesByRuleNo.get(new Integer(ruleNo));
		if (endingRule != null)
			return endingRule;

		String query;
		if (fixed)
			query = "select base_form as ending,wcase,gender,person,sing_pl, animate, type,-type as rule_no,0 as rule_variance "
					+ "from fixed_words where rule_id=" + rule;
		else
			query = "select ending,wcase,gender,person,sing_pl,animate, type,rule_no,rule_variance "
					+ "from ending_rules where rule_id=" + rule;

		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			if (rs.next()) {
				endingRule = new EndingRule(rs.getString("ending"), rule, rs.getInt("wcase"),
						rs.getInt("gender"), rs.getInt("sing_pl"), rs.getInt("animate"),
						rs.getInt("person"), rs.getInt("type"), rs.getInt("rule_no"),
						rs.getInt("rule_variance"));
				endingRulesByRuleNo.put(new Integer(ruleNo), endingRule);
			}
			rs.close();
			stat.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return endingRule;
	}

	public Set<Postfix> getPostfixes() {
		if (postfixes == null) {
			postfixes = new HashSet<Postfix>();
			ResultSet rs;
			try {
				establishConnection();
				Statement stat = conn.createStatement();
				rs = stat.executeQuery("select * from postfixes");
				while (rs.next())
					postfixes.add(new Postfix(rs.getInt("id"), rs.getString("postfix"), rs
							.getInt("reflexive"), rs.getInt("type"), rs.getInt("tense"), rs
							.getInt("rule_no")));
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return postfixes;
	}

	public Postfix getPostfix(int id) {
		if (postfixes == null)
			getPostfixes();
		for (Postfix postfix : postfixes)
			if (postfix.id == id)
				return postfix;
		return new Postfix(id, "", 0, 0, 0, 0);
	}

	public HashSet<Prefix> getPrefixes() {
		if (prefixes == null) {
			prefixes = new HashSet<Prefix>();
			prefixesById = new HashMap<Integer, Prefix>();
			prefixesByPrefix = new HashMap<String, Prefix>();
			ResultSet rs;
			try {
				int id;
				Prefix prefix;
				String prefixString;
				establishConnection();
				Statement stat = conn.createStatement();
				rs = stat.executeQuery("select * from prefixes");
				while (rs.next()) {
					id = rs.getInt("id");
					prefixString = rs.getString("prefix");
					prefix = new Prefix(this, id, prefixString, rs.getString("allow_before"),
							rs.getString("deny_before"), rs.getString("hard_sign_before"),
							rs.getString("change_i_with"));
					prefixes.add(prefix);
					prefixesById.put(new Integer(id), prefix);
					prefixesByPrefix.put(prefixString, prefix);
				}
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return prefixes;
	}

	public Prefix getPrefix(String prefix) {
		if (prefixesByPrefix == null)
			getPrefixes();
		return prefixesByPrefix.get(prefix.intern());
	}

	public ArrayList<Transformation> getTransformations() {
		ArrayList<Transformation> transformations = new ArrayList<Transformation>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from transformations");
			while (rs.next()) {
				transformations.add(new Transformation(this, rs.getInt("id"), rs.getInt("line"), rs
						.getString("source_prefix"), rs.getString("source_suffix"), rs
						.getInt("source_type"), rs.getString("target_prefix"), rs
						.getString("target_suffix"), rs.getInt("target_type"), rs
						.getBoolean("keep_rule"), rs.getInt("source_rule"), rs
						.getInt("target_rule"), rs.getInt("type")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return transformations;
	}

	public HashSet<ComplexWordTemplate> getComplexWordTemplates() {
		if (complexWordTemplates == null) {
			complexWordTemplates = new HashSet<ComplexWordTemplate>();

			String query = "select * from complex_word_template";
			try {
				establishConnection();
				Statement stat = conn.createStatement();
				ResultSet rs = stat.executeQuery(query);
				while (rs.next()) {
					complexWordTemplates.add(new ComplexWordTemplate(rs.getInt("id"), rs
							.getInt("word1_type"), rs.getInt("word1_subtype"), rs
							.getInt("word1_wcase"), rs.getInt("word1_sing_pl"), rs
							.getInt("word2_type"), rs.getInt("word2_subtype"), rs
							.getInt("word2_wcase"), rs.getInt("word2_sing_pl"), rs
							.getString("delimiter")));
				}
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return complexWordTemplates;
	}

	public DataSource getNextDataSource() {
		DataSource dataSource = null;
		try {
			int id = 0;
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from sources where action > 0");
			if (rs.next()) {
				id = rs.getInt("id");
				dataSource = new DataSource(rs.getInt("id"), rs.getString("file_path"),
						rs.getInt("action"));
			}
			rs.close();
			if (id > 0)
				stat.executeUpdate("update sources set action=0 where id=" + id);
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return dataSource;
	}

	public Setup getSetup() {
		if (setup == null) {
			ResultSet rs;
			try {
				establishConnection();
				Statement stat = conn.createStatement();
				rs = stat.executeQuery("select * from setup where id=0");
				if (rs.next())
					setup = new Setup(rs.getString("split_delimiter"), rs.getString("hard_sign"),
							rs.getString("i"), rs.getString("negative"));
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return setup;
	}

	public int getRuleDiversity(int rule_no) {
		Integer ruleCount;
		String query;
		if (rule_no <= 0)
			return -1;
		if (ruleDiversity == null)
			ruleDiversity = new HashMap<Integer, Integer>();
		ruleCount = ruleDiversity.get(new Integer(rule_no));
		if (ruleCount == null) {
			try {
				query = MessageFormat.format("select count(DISTINCT ending) from ending_rules "
						+ "where ending_rules.rule_no={0,number,#} and "
						+ "(rule_variance=0 or rule_variance=1)", rule_no);
				establishConnection();
				Statement stat = conn.createStatement();
				ResultSet rs = stat.executeQuery(query);
				if (rs.next())
					ruleCount = new Integer(rs.getInt(1));
				else
					ruleCount = new Integer(-1);
				ruleDiversity.put(new Integer(rule_no), ruleCount);
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return ruleCount.intValue();
	}

	public ArrayList<Integer> getRuleVariance(int rule_no) {
		ArrayList<Integer> ruleVariance;
		String query;
		if (rule_no <= 0)
			return null;
		if (ruleVarianceByRuleNo == null)
			ruleVarianceByRuleNo = new HashMap<Integer, ArrayList<Integer>>();
		ruleVariance = ruleVarianceByRuleNo.get(new Integer(rule_no));
		if (ruleVariance == null) {
			try {
				query = MessageFormat.format("select DISTINCT rule_variance from ending_rules "
						+ "where rule_no={0,number,#} and rule_variance>0", rule_no);
				establishConnection();
				Statement stat = conn.createStatement();
				ResultSet rs = stat.executeQuery(query);
				ruleVariance = new ArrayList<Integer>();
				while (rs.next())
					ruleVariance.add(new Integer(rs.getInt(1)));
				ruleVarianceByRuleNo.put(new Integer(rule_no), ruleVariance);
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return ruleVariance;
	}

	public int getMaxWordID() {
		int result = 0;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select id from words order by id desc limit 1");
			if (rs.next())
				result = rs.getInt(1);
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	private String getEndSentenceMarks() {
		return getCharacters(0, -1, 1, -1);
	}

	public String getPunctuationMarksNotReady() {
		return getCharacters(0, -1, -1, 0);
	}

	private String getCharacters(int type, int capital, int sentence_end, int ready) {
		String result = new String("");
		String whereStatement = new String("");
		if (type >= 0)
			whereStatement = "type=" + type;
		if (capital >= 0) {
			if (!whereStatement.isEmpty())
				whereStatement = whereStatement + " AND ";
			whereStatement += "capital=" + capital;
		}
		if (sentence_end >= 0) {
			if (!whereStatement.isEmpty())
				whereStatement = whereStatement + " AND ";
			whereStatement += "sentence_end=" + sentence_end;
		}
		if (ready >= 0) {
			if (!whereStatement.isEmpty())
				whereStatement = whereStatement + " AND ";
			whereStatement += "ready=" + ready;
		}

		if (!whereStatement.isEmpty())
			whereStatement = "where " + whereStatement;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select character from characters " + whereStatement);
			while (rs.next()) {
				result = result + rs.getString(1);
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public ArrayList<CharacterSetup> getCharacterList() {
		ArrayList<CharacterSetup> result = new ArrayList<CharacterSetup>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from characters");
			while (rs.next()) {
				result.add(new CharacterSetup(rs.getString("character").charAt(0), rs
						.getInt("type"), rs.getInt("capital"), rs.getInt("sentence_end"), rs
						.getInt("ready"), rs.getInt("elevation")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;

	}

	public ArrayList<SentenceWordform> getSentencePartList(int source_id, int sentence_id,
			String subsentenceFilter, int wordPos, String wcaseFilter, String personFilter,
			int gender, int sing_pl, String typeFilter, String subtypeFilter) {
		ArrayList<SentenceWordform> sentenceParts = new ArrayList<SentenceWordform>();

		if (wordPos < 0)
			return sentenceParts;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
			applyFilter(query, "wcase", wcaseFilter);
			applyFilter(query, "person", personFilter);

			if (gender > 0)
				query.addCondition(new InCondition(new CustomSql("gender"), 0, gender));
			if (sing_pl > 0)
				query.addCondition(new InCondition(new CustomSql("sing_pl"), 0, sing_pl));

			applyFilter(query, "type", typeFilter);
			applyFilter(query, "subtype", subtypeFilter);

			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"source_id"), source_id));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"sentence_id"), sentence_id));
			applyFilter(query, "subsentence_id", subsentenceFilter);
			if (wordPos > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_pos"), wordPos));
			query.addCustomOrdering(new CustomSql("rating"), OrderObject.Dir.DESCENDING);
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				sentenceParts.add(new SentenceWordform(source_id, sentence_id, rs
						.getInt("subsentence_id"), rs.getInt("elevation"), rs.getInt("word_pos"),
						rs.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
								.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"), rs
								.getInt("animate"), rs.getInt("word_id"), rs.getInt("rule_id"), rs
								.getInt("dep_word_pos"), rs.getInt("preposition_id"), rs
								.getString("word_type_filter"), rs.getString("wcase_filter"), rs
								.getString("gender_filter"), rs.getString("sing_pl_filter"), rs
								.getInt("rating"), rs.getInt("maxrating")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentenceParts;
	}

	private void applyFilter(SelectQuery query, String fieldName, String filter) {
		if (filter == null)
			return;
		if (filter.isEmpty())
			return;
		ComboCondition cc = new ComboCondition(ComboCondition.Op.OR);
		String[] splitFilter = filter.split("\\|");
		for (int i = 0; i < splitFilter.length; i++) {
			cc.addCondition(getBinaryFilter(fieldName, splitFilter[i]));
		}
		query.addCondition(cc);
	}

	private BinaryCondition getBinaryFilter(String fieldName, String filter) {
		BinaryCondition bc = null;
		Op bcOp;
		String part = filter;
		if (filter.startsWith("<>")) {
			part = filter.substring(2);
			bcOp = BinaryCondition.Op.NOT_EQUAL_TO;
		} else if (filter.startsWith("<")) {
			part = filter.substring(1);
			bcOp = BinaryCondition.Op.LESS_THAN;
		} else if (filter.startsWith(">")) {
			part = filter.substring(1);
			bcOp = BinaryCondition.Op.GREATER_THAN;
		} else
			bcOp = BinaryCondition.Op.EQUAL_TO;
		if (bcOp != null)
			bc = new BinaryCondition(bcOp, new CustomSql(fieldName), Integer.valueOf(part));
		return bc;
	}

	public void saveSentenceParts(ArrayList<SentenceWord> sentenceParts) {
		try {
			establishConnection();
			PreparedStatement prep = conn.prepareStatement("UPDATE sentence_word "
					+ "SET type=?,word_id=?,rule_id=?,dep_word_pos=?,preposition_id=?, "
					+ "elevation=?, elevation_dif=?, "
					+ "word_type_filter=?, wcase_filter=?, gender_filter=?, sing_pl_filter=?, "
					+ "animate_filter=?, subsentence_id=?, internal=? "
					+ "WHERE source_id=? and sentence_id=? and word_pos=?");
			for (SentenceWord sentencePart : sentenceParts) {
				int word_id;
				int rule_id;
				if (sentencePart.sentenceWordform == null) {
					word_id = 0;
					rule_id = 0;
				} else {
					word_id = sentencePart.sentenceWordform.word_id;
					rule_id = sentencePart.sentenceWordform.rule_id;
				}
				prep.setInt(1, sentencePart.part);
				prep.setInt(2, word_id);
				prep.setInt(3, rule_id);
				prep.setInt(4, sentencePart.dep_word_pos);
				prep.setInt(5, sentencePart.preposition_id);
				prep.setInt(6, sentencePart.elevation);
				prep.setInt(7, sentencePart.elevation_dif);
				prep.setString(8, sentencePart.word_type_filter);
				prep.setString(9, sentencePart.wcase_filter);
				prep.setString(10, sentencePart.gender_filter);
				prep.setString(11, sentencePart.sing_pl_filter);
				prep.setString(12, sentencePart.animate_filter);
				prep.setInt(13, sentencePart.subsentenceID);
				prep.setBoolean(14, sentencePart.internal);
				prep.setInt(15, sentencePart.sourceID);
				prep.setInt(16, sentencePart.sentenceID);
				prep.setInt(17, sentencePart.wordPos);
				prep.addBatch();
			}
			conn.setAutoCommit(false);
			prep.executeBatch();
			conn.setAutoCommit(true);
			prep.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void saveSentenceWordLinkList(ArrayList<SentenceWordLink> wordLinkList) {
		try {
			establishConnection();
			PreparedStatement prep = conn.prepareStatement("insert into sentence_word_link values "
					+ "(?,?,?,?,?,?,?,?,?,?,?)");
			for (SentenceWordLink wordLink : wordLinkList) {
				prep.setInt(1, wordLink.sentenceID);
				prep.setInt(2, wordLink.wordPos);
				prep.setInt(3, wordLink.linkWordPos);
				prep.setInt(4, wordLink.type);
				prep.setInt(5, wordLink.wcase);
				prep.setInt(6, wordLink.gender);
				prep.setInt(7, wordLink.person);
				prep.setInt(8, wordLink.sing_pl);
				prep.setInt(9, wordLink.subtype);
				prep.setInt(10, wordLink.conjunctionWordPos);
				prep.setInt(11, wordLink.sourceID);
				prep.addBatch();
			}
			conn.setAutoCommit(false);
			prep.executeBatch();
			conn.setAutoCommit(true);
			prep.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void saveSentenceWordRelationList(HashSet<SentenceWordRelation> wordRelationList) {
		try {
			establishConnection();
			PreparedStatement prep = conn
					.prepareStatement("insert into sentence_word_relation values "
							+ "(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
			for (SentenceWordRelation wordRelation : wordRelationList)
				if (wordRelation.status == 2) {
					prep.setInt(1, wordRelation.id);
					prep.setInt(2, wordRelation.depID);
					prep.setInt(3, wordRelation.sentenceID);
					prep.setInt(4, wordRelation.word1Pos);
					prep.setInt(5, wordRelation.word1Type);
					prep.setInt(6, wordRelation.word1Case);
					prep.setInt(7, wordRelation.word1Gender);
					prep.setInt(8, wordRelation.word1Sing_Pl);
					prep.setInt(9, wordRelation.word2Pos);
					prep.setInt(10, wordRelation.word2Type);
					prep.setInt(11, wordRelation.word2Case);
					prep.setInt(12, wordRelation.word2Gender);
					prep.setInt(13, wordRelation.word2Sing_Pl);
					prep.setInt(14, wordRelation.relationType);
					prep.setInt(15, wordRelation.word1Animate);
					prep.setInt(16, wordRelation.word2Animate);
					prep.setInt(17, wordRelation.sourceID);
					prep.addBatch();
				}
			conn.setAutoCommit(false);
			prep.executeBatch();
			conn.setAutoCommit(true);
			prep.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<Sentence> getSentences(Vocabulary vocabulary) {
		int source_id;
		int sentence_id;
		ArrayList<Sentence> sentences = new ArrayList<Sentence>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat
					.executeQuery("select * from sentences where processed=false order by source_id, id");
			while (rs.next()) {
				source_id = rs.getInt("source_id");
				sentence_id = rs.getInt("id");
				sentences.add(new Sentence(this, vocabulary, source_id, sentence_id,
						getSentenceWordList(source_id, sentence_id)));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentences;
	}

	private ArrayList<SentenceWord> getSentenceWordList(int source_id, int sentence_id) {
		ArrayList<SentenceWord> sentenceWords = new ArrayList<SentenceWord>();
		String query = MessageFormat
				.format("select * from sentence_word "
						+ "where source_id = {0,number,#} and sentence_id = {1,number,#} order by sentence_id, word_pos",
						source_id, sentence_id);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				sentenceWords.add(new SentenceWord(rs.getInt("source_id"),
						rs.getInt("sentence_id"), rs.getInt("subsentence_id"), rs
								.getInt("elevation"), rs.getInt("word_pos"), rs.getString("word"),
						rs.getInt("type"), rs.getInt("dep_word_pos"), rs.getInt("preposition_id"),
						rs.getInt("elevation_dif"), rs.getBoolean("punctuation"), rs
								.getBoolean("name"), rs.getBoolean("internal"), rs
								.getString("word_type_filter"), rs.getString("wcase_filter"), rs
								.getString("gender_filter"), rs.getString("sing_pl_filter"), rs
								.getString("animate_filter")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentenceWords;
	}

	public void setSentenceType(int source_id, int id, int type) {
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String query = MessageFormat
					.format("update sentences set type={2} where source_id = {0,number,#} and id={1,number,#}",
							source_id, id, type);
			stat.executeUpdate(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public HashSet<Numeral> getNumerals() {
		if (numerals == null) {
			numerals = new HashSet<Numeral>();
			numeralsByNumeral = new HashMap<String, Numeral>();
			ResultSet rs;
			try {
				Numeral numeral;
				String numeralString;
				establishConnection();
				Statement stat = conn.createStatement();
				rs = stat.executeQuery("select * from numberals");
				while (rs.next()) {
					numeralString = rs.getString("numeral").intern();
					numeral = new Numeral(numeralString, rs.getInt("sing_pl"),
							rs.getInt("base_wcase"), rs.getInt("base_sing_pl"), rs.getInt("value"));
					numerals.add(numeral);
					numeralsByNumeral.put(numeralString, numeral);
				}
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return numerals;
	}

	public boolean existNumeral(String base) {
		if (numerals == null)
			getNumerals();
		if (numeralsByNumeral.get(base.intern()) == null)
			return false;
		return true;
	}

	public Numeral getNumeralByWordID(Vocabulary vocabulary, int word_id) {
		if (numerals == null)
			getNumerals();
		return numeralsByNumeral.get(vocabulary.getWord(word_id).word);
	}

	public boolean isNumeralBaseForm(Vocabulary vocabulary, int word_id, int rule_id) {
		String query;
		Word word = vocabulary.getWord(word_id);
		if (word.rule_no > 0)
			query = MessageFormat
					.format("select 1 from ending_rules join ending_rules as base_ending "
							+ "on ending_rules.rule_no = base_ending.rule_no and "
							+ "ending_rules.gender = base_ending.gender and base_ending.wcase=1 "
							+ "where ending_rules.rule_id={0,number,#} and ending_rules.ending=base_ending.ending and "
							+ "ending_rules.rule_id={0,number,#}", rule_id);
		else {
			query = MessageFormat.format("select 1 from fixed_words "
					+ "where word = base_form and rule_id = {0,number,#}", rule_id);
		}
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			if (rs.next()) {
				rs.close();
				stat.close();
				return true;
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean checkBase(String base, EndingRule endingRule) {
		if (endingRule.type == WordProcessor.numeral) {
			return existNumeral(base);
		}
		return true;
	}

	public void setSentenceProcessed(int sourceID, int id) {
		String query = MessageFormat.format("update sentences set processed = true "
				+ "where source_id={0,number,#} and id={1,number,#}", sourceID, id);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.execute(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}
}
