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
import java.util.Iterator;
import java.util.Set;

import knowledge_app.EndingRule;
import knowledge_app.Sentence;
import knowledge_app.SentencePart;
import knowledge_app.WordForm;
import knowledge_app.WordProcessor;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.CustomCondition;
import com.healthmarketscience.sqlbuilder.CustomSql;
import com.healthmarketscience.sqlbuilder.InCondition;
import com.healthmarketscience.sqlbuilder.OrderObject;
import com.healthmarketscience.sqlbuilder.SelectQuery;

public class DataBank {
	private Setup setup;
	private HashSet<Prefix> prefixes;
	private HashMap<Integer, Prefix> prefixesById;
	private HashMap<String, Prefix> prefixesByPrefix;
	private HashSet<Word> words;
	private HashMap<Integer, Word> wordsById;
	private HashMap<String, HashSet<Word>> wordsByBase;
	private HashMap<Integer, Integer> ruleDiversity;
	private HashMap<Integer, EndingRule> zeroEndingruleByRuleNo;
	private HashSet<Transformation> transformations;
	private HashMap<Integer, HashSet<Transformation>> transformationsById;
	private HashSet<ComplexWordTemplate> complexWordTemplates;
	private HashMap<String, HashSet<WordForm>> wordformsByWordformstring;
	private HashSet<Numeral> numerals;
	private HashMap<String, Numeral> numeralsByNumeral;
	private HashSet<WordForm> delayedSaveWordforms;

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

	public int saveSentence(int type, String sentence, ArrayList<WordProcessor> words)
			throws SQLException {
		Iterator<WordProcessor> iterator;
		WordProcessor word;
		int i = 0;
		if (sentence.isEmpty())
			return -1;

		sentenceCount++;
		establishConnection();
		Statement stat = conn.createStatement();
		stat.executeUpdate("insert into sentences " + "values (" + sentenceCount + ",'" + sentence
				+ "'," + type + ");");
		stat.close();
		PreparedStatement sent_word_insert = conn.prepareStatement("insert "
				+ "into sentence_word values (?,?,?,?,0,0,0,0,0,0,?);");
		iterator = words.iterator();
		while (iterator.hasNext()) {
			i++;
			word = iterator.next();
			word.id = i;
			sent_word_insert.setInt(1, sentenceCount);
			sent_word_insert.setInt(2, i);
			sent_word_insert.setString(3, word.getWord());
			sent_word_insert.setBoolean(4, word.isPunctuation());
			sent_word_insert.setBoolean(5, word.isName());
			sent_word_insert.addBatch();
		}
		conn.setAutoCommit(false);
		sent_word_insert.executeBatch();
		conn.setAutoCommit(true);
		sent_word_insert.close();
		return sentenceCount;
	}

	public void saveWord(Word word) {
		String query;
		try {
			word.id = getMaxWordID() + 1;
			establishConnection();
			Statement stat = conn.createStatement();
			query = MessageFormat.format("insert into words "
					+ "values ({0,number,#},''{1}'',{2,number,#}, {3,number,#},{4,number,#},{5})",
					word.id, word.word, word.type, word.rule_no, word.rating, word.complex);
			stat.executeUpdate(query);
			if (word.complex) {
				query = MessageFormat.format(
						"insert into complex_word values ({0,number,#},{1,number,#},{2,number,#})",
						word.id, word.word1ID, word.word2ID);
				stat.executeUpdate(query);
			}
			stat.close();
			updateWordCache(word);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public void saveWordForm(WordForm wordform) {
		String query;
		WordWordRelation transformRelation;
		Word word;

		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select 1 from wordforms " + "where word_id="
					+ wordform.wordID + " AND rule_id=" + wordform.rule + "" + " AND postfix_id="
					+ wordform.postfix_id + ";");
			if (!rs.next()) {
				word = getWord(wordform.wordID);
				rs.close();
				// check word for transformation
				query = MessageFormat.format(
						"select word_id,parent_word_id,relation_ref_id,relation_ref_line from word_word_relation "
								+ "where (word_id={0,number,#} and relation_type=1) "
								+ "or (parent_word_id={0,number,#} and relation_type=1)",
						wordform.wordID);
				rs = stat.executeQuery(query);
				while (rs.next()) {
					transformRelation = new WordWordRelation(rs.getInt("word_id"),
							rs.getInt("parent_word_id"), 1, rs.getInt("relation_ref_id"),
							rs.getInt("relation_ref_line"));
					word.copyWordForm(transformRelation, wordform.rule, wordform.postfix_id);
				}
				rs.close();
				delayedSave(wordform);
			}
			rs.close();
			// Transformation transformation;
			// check transformations with type=0 if they need to be restored to their own type
			/*
			 * query = MessageFormat.format(
			 * "select parent_word_id,relation_ref_id from word_word_relation " +
			 * "where word_id={0,number,#} and relation_type=0", wordform.wordID); rs =
			 * stat.executeQuery(query); while (rs.next()) { if
			 * (!checkSimilarWordforms(wordform.wordID, rs.getInt("parent_word_id"))) {
			 * transformation = transformationsById.get(new Integer(rs.getInt("relation_ref_id")));
			 * updateWordWordRelationType
			 * (wordform.wordID,rs.getInt("parent_word_id"),transformation.type,transformation.id);
			 * } } rs.close(); // check other words for prefixes to this query =
			 * MessageFormat.format("select word_id,relation_ref_id from word_word_relation " +
			 * "where parent_word_id={0,number,#} and relation_type=0", wordform.wordID); rs =
			 * stat.executeQuery(query); while (rs.next()) { if
			 * (!checkSimilarWordforms(wordform.wordID, rs.getInt("word_id"))) { transformation =
			 * transformationsById.get(new Integer(rs.getInt("relation_ref_id")));
			 * updateWordWordRelationType
			 * (rs.getInt("word_id"),wordform.wordID,transformation.type,transformation.id); } }
			 */
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void delayedSave(WordForm wordform) {
		if (delayedSaveWordforms == null)
			delayedSaveWordforms = new HashSet<WordForm>();

		delayedSaveWordforms.add(wordform);

		if (delayedSaveWordforms.size() >= 1000)
			flushWordforms();

	}

	public void flushWordforms() {
		if (delayedSaveWordforms == null)
			return;
		Iterator<WordForm> iterator = delayedSaveWordforms.iterator();
		WordForm wordform;
		HashSet<Word> updatedWordSet = new HashSet<Word>();
		try {
			establishConnection();
			PreparedStatement readWordform = conn.prepareStatement("select 1 from wordforms "
					+ "where word_id=? and rule_id=? and postfix_id=?");
			PreparedStatement saveWordform = conn.prepareStatement("insert into wordforms "
					+ "values (?,?,?,?);");
			while (iterator.hasNext()) {
				wordform = iterator.next();
				readWordform.setInt(1, wordform.wordID);
				readWordform.setInt(2, wordform.rule);
				readWordform.setInt(3, wordform.postfix_id);
				ResultSet rs = readWordform.executeQuery();
				if (!rs.next()) {
					updatedWordSet.add(getWord(wordform.wordID));
					saveWordform.setInt(1, wordform.wordID);
					saveWordform.setString(2, wordform.wordForm);
					saveWordform.setInt(3, wordform.rule);
					saveWordform.setInt(4, wordform.postfix_id);
					saveWordform.addBatch();
				}
			}
			readWordform.close();
			conn.setAutoCommit(false);
			saveWordform.executeBatch();
			conn.setAutoCommit(true);
			saveWordform.close();
			UpdateWordRating(updatedWordSet);
		} catch (SQLException e) {
			e.printStackTrace();
			e.getNextException().printStackTrace();
		}
		delayedSaveWordforms.clear();
	}

	public void saveWordWordRelation(WordWordRelation prefixRelation) {
		String query = MessageFormat.format("insert into word_word_relation "
				+ "values ({0,number,#},{1,number,#},{2,number,#},{3,number,#},{4,number,#}) ",
				prefixRelation.wordID, prefixRelation.parentWordID, prefixRelation.relationType,
				prefixRelation.relationRefID, prefixRelation.relationRefLine);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.executeUpdate(query);
			stat.close();
			HashSet<Word> wordSet = new HashSet<Word>();
			wordSet.add(getWord(prefixRelation.wordID));
			wordSet.add(getWord(prefixRelation.parentWordID));
			UpdateWordRating(wordSet);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	private void updateWordWordRelationType(int wordID, int parentWordID, int type, int refID) {
		String query = MessageFormat.format(
				"update word_word_relation set relation_type = {2,number,#} "
						+ "where word_id = {0,number,#} and parent_word_id = {1,number,#} and "
						+ "relation_ref_id = {3,number,#}", wordID, parentWordID, type, refID);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			stat.executeUpdate(query);
			stat.close();
			HashSet<Word> wordSet = new HashSet<Word>();
			wordSet.add(getWord(wordID));
			wordSet.add(getWord(parentWordID));
			UpdateWordRating(wordSet);
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public int getWordID(String baseForm, int type, int rule, boolean complex, int word1, int word2) {
		HashSet<Word> wordSet = wordsByBase.get(baseForm);
		if (wordSet != null) {
			Iterator<Word> iterator = wordSet.iterator();
			Word word;
			while (iterator.hasNext()) {
				word = iterator.next();
				if ((word.type == type) & (word.rule_no == rule) & (word.complex == complex)
						& (word.word1ID == word1) & (word.word2ID == word2))
					return word.id;
			}
		}
		String query = MessageFormat.format(
				"select id from words left join complex_word on words.id = complex_word.word_id "
						+ "where word=''{0}'' AND type={1,number,#} AND rule_no={2,number,#} and "
						+ "complex = {3} and word1 = {4,number,#} and word2 = {5,number,#};",
				baseForm, type, rule, complex, word1, word2);
		int wordID = 0;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			if (rs.next())
				wordID = rs.getInt("id");
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordID;
	}

	public boolean isOnlyFixedForm(String lcWord) {
		boolean result = false;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String query = MessageFormat.format(
					"select 1 from fixed_words where word=''{0}'' and only_form=1", lcWord);
			ResultSet rs = stat.executeQuery(query);
			result = rs.next();
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public HashSet<WordForm> getFixedWordForms(String wordform, Postfix postfix)
			throws SQLException {
		Word word;
		HashSet<WordForm> wordforms = new HashSet<WordForm>();
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
			if (postfix.tense > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"tense"), postfix.tense));
		}

		rs = stat.executeQuery(query.validate().toString());

		while (rs.next()) {
			word = getWord(rs.getString("base_form"), rs.getInt("type"), -rs.getInt("type"), false,
					0, 0, true);
			wordforms.add(word.createWordform(wordform + postfix.postfix, rs.getInt("rule_id"),
					postfix.id));
		}

		rs.close();
		stat.close();
		return wordforms;
	}

	public Set<EndingRule> getEndingRules(String ending, Postfix postfix, int complexWordIndex,
			ComplexWordTemplate complexWordTemplate) throws SQLException {
		Set<EndingRule> endingrules = new HashSet<EndingRule>();
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
			if (complexWordTemplate.word1_tense > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"tense"), complexWordTemplate.word1_tense));
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
			if (complexWordTemplate.word2_tense > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"tense"), complexWordTemplate.word2_tense));
			if (complexWordTemplate.word2_wcase > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"wcase"), complexWordTemplate.word2_wcase));
			if (complexWordTemplate.word2_sing_pl > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"sing_pl"), complexWordTemplate.word2_sing_pl));
		}

		rs = stat.executeQuery(query.validate().toString());

		while (rs.next()) {
			endingrules.add(new EndingRule(ending, rs.getInt("rule_no"), rs.getInt("type"), rs
					.getInt("subtype"), rs.getInt("rule_id"), rs.getInt("wcase"), rs
					.getInt("gender"), rs.getInt("person"), rs.getString("allow_after"), rs
					.getString("deny_after"), rs.getString("e_before"), rs.getString("o_before"),
					rs.getInt("min_length")));
		}
		rs.close();
		stat.close();
		return endingrules;
	}

	/**
	 * *
	 * 
	 * @param rule_no
	 *            - integer value of rule no
	 * @return ending rule of a base word form
	 */
	public EndingRule getZeroEndingrule(int rule_no) {
		EndingRule zeroEndingrule = null;
		if (zeroEndingruleByRuleNo == null)
			zeroEndingruleByRuleNo = new HashMap<Integer, EndingRule>();
		zeroEndingrule = zeroEndingruleByRuleNo.get(new Integer(rule_no));
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
					"rule_no"), rule_no));
			query.addCustomOrderings(new CustomSql("tense, sing_pl, person, gender, wcase"));
			rs = stat.executeQuery(query.validate().toString());
			if (rs.next()) {
				zeroEndingrule = new EndingRule(rs.getString("ending"), rs.getInt("rule_no"),
						rs.getInt("type"), rs.getInt("subtype"), rs.getInt("rule_id"),
						rs.getInt("wcase"), rs.getInt("gender"), rs.getInt("person"),
						rs.getString("allow_after"), rs.getString("deny_after"),
						rs.getString("e_before"), rs.getString("o_before"), rs.getInt("min_length"));
				zeroEndingruleByRuleNo.put(new Integer(rule_no), zeroEndingrule);
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return zeroEndingrule;
	}

	public Set<Postfix> getPostfixes() throws SQLException {
		Set<Postfix> postfixes = new HashSet<Postfix>();
		ResultSet rs;
		establishConnection();
		Statement stat = conn.createStatement();
		rs = stat.executeQuery("select * from postfixes");
		while (rs.next())
			postfixes.add(new Postfix(rs.getInt("id"), rs.getString("postfix"), rs
					.getInt("reflexive"), rs.getInt("type"), rs.getInt("tense"), rs
					.getInt("rule_no")));
		rs.close();
		stat.close();
		return postfixes;
	}

	public Postfix getPostfix(int id) throws SQLException {
		Postfix postfix;
		ResultSet rs;
		establishConnection();
		Statement stat = conn.createStatement();
		rs = stat.executeQuery("select * from postfixes where id=" + id + ";");
		if (rs.next())
			postfix = new Postfix(rs.getInt("id"), rs.getString("postfix"), rs.getInt("reflexive"),
					rs.getInt("type"), rs.getInt("tense"), rs.getInt("rule_no"));
		else
			postfix = new Postfix(id, "", 0, 0, 0, 0);
		rs.close();
		stat.close();
		return postfix;
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

	public HashSet<Transformation> getTransformations() {
		HashSet<Transformation> tempTransformations;
		if (transformations == null) {
			transformations = new HashSet<Transformation>();
			transformationsById = new HashMap<Integer, HashSet<Transformation>>();
			ResultSet rs;
			try {
				int id;
				Transformation transformation;
				establishConnection();
				Statement stat = conn.createStatement();
				rs = stat.executeQuery("select * from transformations");
				while (rs.next()) {
					id = rs.getInt("id");
					transformation = new Transformation(this, id, rs.getInt("line"),
							rs.getString("source_prefix"), rs.getString("source_suffix"),
							rs.getInt("source_type"), rs.getString("target_prefix"),
							rs.getString("target_suffix"), rs.getInt("target_type"),
							rs.getBoolean("keep_rule"), rs.getInt("source_rule"),
							rs.getInt("target_rule"), rs.getInt("type"));
					transformations.add(transformation);
					tempTransformations = transformationsById.get(id);
					if (tempTransformations == null)
						tempTransformations = new HashSet<Transformation>();
					tempTransformations.add(transformation);
					transformationsById.put(new Integer(id), tempTransformations);
				}
				rs.close();
				stat.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return transformations;
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

	private Prefix getPrefix(int id) {
		if (prefixesById == null)
			getPrefixes();
		return prefixesById.get(new Integer(id));
	}

	public Prefix getPrefix(String prefix) {
		if (prefixesByPrefix == null)
			getPrefixes();
		return prefixesByPrefix.get(prefix.intern());
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

	private void UpdateWordRating(HashSet<Word> wordSet) throws SQLException {
		if (wordSet.isEmpty())
			return;
		int wordDiversity = 0;
		int ruleDiversity;
		int newrating = 0;
		boolean isChanged = false;
		HashSet<Word> updateWordSet = new HashSet<Word>();
		ResultSet rs;
		Iterator<Word> iterator = wordSet.iterator();
		Word word;
		establishConnection();
		PreparedStatement relationQuery;
		PreparedStatement wordformQuery;
		PreparedStatement complexWordQuery;
		relationQuery = conn
				.prepareStatement("select count (distinct relation_ref_id ) from word_word_relation "
						+ "where (word_id=? and relation_type=2) "
						+ "or (parent_word_id=? and relation_type=2)");
		wordformQuery = conn.prepareStatement("select count(DISTINCT ending) from wordforms "
				+ "join ending_rules on wordforms.rule_id=ending_rules.rule_id where word_id=?");
		complexWordQuery = conn
				.prepareStatement("select word_id from complex_word where word1=? or word2=?");
		PreparedStatement prep = conn.prepareStatement("UPDATE words SET rating=? WHERE id=?");
		while (iterator.hasNext()) {
			newrating = 0;
			isChanged = false;
			word = iterator.next();
			if (word.complex) {
				Word word1;
				Word word2;
				word1 = getWord(word.word1ID);
				word2 = getWord(word.word2ID);
				newrating = Math.round((float) Math.sqrt(word1.rating * word2.rating));
				if (newrating != word.rating)
					isChanged = true;
				word.rating = newrating;
			}
			if (!word.complex) {
				relationQuery.setInt(1, word.id);
				relationQuery.setInt(2, word.id);
				rs = relationQuery.executeQuery();
				if (rs.next())
					wordDiversity = rs.getInt(1);
				rs.close();
				wordformQuery.setInt(1, word.id);
				rs = wordformQuery.executeQuery();
				if (rs.next()) {
					wordDiversity = wordDiversity + rs.getInt(1);
					ruleDiversity = getRuleDiversity(word.rule_no);
					if (wordDiversity > ruleDiversity)
						wordDiversity = ruleDiversity;
					if (word.rule_no > 0)
						newrating = Math.round(100.0f * wordDiversity / ruleDiversity);
					if (word.rule_no < -10)
						newrating = 100;
					if (newrating != word.rating)
						isChanged = true;

					word.rating = newrating;
				}
				rs.close();
			}
			if (isChanged) {
				if (word.rating != 0) {
					prep.setInt(1, word.rating);
					prep.setInt(2, word.id);
					prep.addBatch();
				}
				complexWordQuery.setInt(1, word.id);
				complexWordQuery.setInt(2, word.id);
				rs = complexWordQuery.executeQuery();
				while (rs.next())
					updateWordSet.add(getWord(rs.getInt(1)));
			}
		}
		relationQuery.close();
		wordformQuery.close();
		complexWordQuery.close();
		conn.setAutoCommit(false);
		prep.executeBatch();
		conn.setAutoCommit(true);
		prep.close();
		if (!updateWordSet.isEmpty())
			UpdateWordRating(updateWordSet);
	}

	private int getRuleDiversity(int rule_no) {
		Integer ruleCount;
		if (rule_no <= 0)
			return -1;
		if (ruleDiversity == null)
			ruleDiversity = new HashMap<Integer, Integer>();
		ruleCount = ruleDiversity.get(new Integer(rule_no));
		if (ruleCount == null) {
			try {
				establishConnection();
				Statement stat = conn.createStatement();
				ResultSet rs = stat.executeQuery("select count(DISTINCT ending) from ending_rules "
						+ "where ending_rules.rule_no=" + rule_no);
				if (rs.next())
					ruleCount = new Integer(rs.getInt(1));
				else
					ruleCount = new Integer(-1);
				ruleDiversity.put(new Integer(rule_no), ruleCount);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return ruleCount.intValue();
	}

	private int getMaxWordID() throws SQLException {
		int result = 0;
		establishConnection();
		Statement stat = conn.createStatement();
		ResultSet rs = stat.executeQuery("select id from words order by id desc limit 1");
		if (rs.next())
			result = rs.getInt(1);
		rs.close();
		stat.close();
		return result;
	}

	public void FillBestMatch(int sentence_id) throws SQLException {
		int preposition_id = 0;
		String query;
		String queryBestWordform;
		establishConnection();
		Statement stat = conn.createStatement();
		Statement stat2 = conn.createStatement();
		query = MessageFormat.format("select word_pos, word "
				+ "from sentence_word where punctuation=false and sentence_id={0,number,#} "
				+ "order by word_pos", sentence_id);
		ResultSet rs = stat.executeQuery(query);
		PreparedStatement prep = conn.prepareStatement("UPDATE sentence_word "
				+ "SET word_id=?, rule_id=?, postfix_id=?, preposition_id=? "
				+ "WHERE sentence_id=? and word_pos=?");
		while (rs.next()) {
			queryBestWordform = MessageFormat.format(
					"SELECT word_id, rule_id, postfix_id,type FROM wordforms "
							+ "left join words on id=word_id "
							+ "where wordform=''{0}'' order by rating desc", rs.getString("word"));
			ResultSet rs2 = stat2.executeQuery(queryBestWordform);
			if (rs2.next()) {
				prep.setInt(1, rs2.getInt("word_id"));
				prep.setInt(2, rs2.getInt("rule_id"));
				prep.setInt(3, rs2.getInt("postfix_id"));
				prep.setInt(4, preposition_id);
				prep.setInt(5, sentence_id);
				prep.setInt(6, rs.getInt("word_pos"));
				prep.addBatch();
				preposition_id = 0;
				if (rs2.getInt("type") == WordProcessor.preposition) {
					preposition_id = rs2.getInt("word_id");
				}
			}
			rs2.close();
		}
		stat2.close();
		rs.close();
		stat.close();
		conn.setAutoCommit(false);
		prep.executeBatch();
		conn.setAutoCommit(true);
		prep.close();
	}

	public String getCapitalLetters() throws SQLException {
		return getCharacters(1, 1, -1, -1);
	}

	public String getChameleonMarks() throws SQLException {
		return getCharacters(2, -1, -1, -1);
	}

	public String getPunctuationMarks() throws SQLException {
		return getCharacters(0, -1, -1, -1);
	}

	public String getEndSentenceMarks() throws SQLException {
		return getCharacters(0, -1, 1, -1);
	}

	public String getPunctuationMarksNotReady() throws SQLException {
		return getCharacters(0, -1, -1, 0);
	}

	private String getCharacters(int type, int capital, int sentence_end, int ready)
			throws SQLException {
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
		establishConnection();
		Statement stat = conn.createStatement();
		ResultSet rs = stat.executeQuery("select character from characters " + whereStatement);
		while (rs.next()) {
			result = result + rs.getString(1);
		}
		rs.close();
		stat.close();
		return result;
	}

	public EndingRule getEndingRule(boolean fixed, int rule) {
		String query;
		if (fixed)
			query = "select wcase,gender,person,type,-type as rule_no from fixed_words where rule_id="
					+ rule;
		else
			query = "select wcase,gender,person,type,rule_no from ending_rules where rule_id="
					+ rule;
		EndingRule endingRule = null;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			if (rs.next())
				endingRule = new EndingRule(rule, rs.getInt(1), rs.getInt(2), rs.getInt(3),
						rs.getInt("type"), rs.getInt("rule_no"));

			rs.close();
			stat.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return endingRule;
	}

	public ArrayList<SentencePart> getSubjectList(int id, int predicatePos, int person, int gender,
			int sing_pl, double rating_tolerance) {
		if (person == 0)
			person = 4;
		ArrayList<SentencePart> subjects = new ArrayList<SentencePart>();
		String ratingToleranceCondition = MessageFormat.format(
				"(100-rating)<=(100-maxrating)*{0,number,#.##}", rating_tolerance);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
			query.addCondition(new CustomCondition(new CustomSql("wcase=1")));
			switch (person) {
			case 0:
			case 1:
			case 2:
			case 3:
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"person"), person));
				break;
			case 4:
				query.addCondition(new CustomCondition(new CustomSql("person>0")));
				break;
			}
			if (gender > 0)
				query.addCondition(new InCondition(new CustomSql("gender"), 0, gender));
			if (sing_pl > 0)
				query.addCondition(new InCondition(new CustomSql("sing_pl"), 0, sing_pl));
			query.addCondition(new CustomCondition(new CustomSql("preposition_id=0")));
			query.addCondition(new CustomCondition(new CustomSql("dep_word_pos=0")));
			query.addCondition(new CustomCondition(ratingToleranceCondition));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"sentence_id"), id));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.NOT_EQUAL_TO, new CustomSql(
					"word_pos"), predicatePos));
			query.addCustomOrdering(new CustomSql("rating"), OrderObject.Dir.DESCENDING);
			ResultSet rs = stat.executeQuery(query.validate().toString());

			/*
			 * String query = MessageFormat.format("SELECT * from sentence_wordform_detailed " +
			 * "where wcase=1 and person>0 and sentence_id={0,number,#} " +
			 * "and preposition_id=0 and dep_word_pos=0 " +
			 * "and (100-rating)<=(100-maxrating)*{1,number,#.##}" + "order by rating desc", id,
			 * rating_tolerance);} ResultSet rs = stat.executeQuery(query);
			 */
			while (rs.next()) {
				subjects.add(new SentencePart(id, rs.getInt("word_pos"), SentencePart.subject, rs
						.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
						.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"), rs
						.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"), rs
						.getInt("preposition_id")));

			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return subjects;
	}

	public ArrayList<SentencePart> getPredicateList(int id, int subjectPos, int person, int gender,
			int sing_pl, double rating_tolerance) {
		String ratingToleranceCondition = MessageFormat.format(
				"(100-rating)<=(100-maxrating)*{0,number,#.##}", rating_tolerance);
		ArrayList<SentencePart> predicates = new ArrayList<SentencePart>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
			query.addCondition(new CustomCondition(new CustomSql("wcase=0")));
			if (person > 0)
				query.addCondition(new InCondition(new CustomSql("person"), 0, person));
			if (gender > 0)
				query.addCondition(new InCondition(new CustomSql("gender"), 0, gender));
			if (sing_pl > 0)
				query.addCondition(new InCondition(new CustomSql("sing_pl"), 0, sing_pl));
			query.addCondition(new CustomCondition(new CustomSql("type=2")));
			query.addCondition(new CustomCondition(new CustomSql("tense>0")));
			query.addCondition(new CustomCondition(new CustomSql("tense<10")));
			query.addCondition(new CustomCondition(new CustomSql("preposition_id=0")));
			query.addCondition(new CustomCondition(ratingToleranceCondition));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"sentence_id"), id));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.NOT_EQUAL_TO, new CustomSql(
					"word_pos"), subjectPos));
			query.addCustomOrdering(new CustomSql("rating"), OrderObject.Dir.DESCENDING);
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				predicates.add(new SentencePart(rs.getInt("sentence_id"), rs.getInt("word_pos"),
						SentencePart.predicate, rs.getInt("type"), rs.getInt("subtype"), rs
								.getInt("wcase"), rs.getInt("gender"), rs.getInt("person"), rs
								.getInt("sing_pl"), rs.getInt("word_id"), rs.getInt("rule_id"), rs
								.getInt("dep_word_pos"), rs.getInt("preposition_id")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return predicates;
	}

	public ArrayList<SentencePart> getSentencePartList(int sentence_id, int wordPos,
			String wcaseFilter, int person, int gender, int sing_pl, int type) {
		ArrayList<SentencePart> sentenceParts = new ArrayList<SentencePart>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
			parseFilter(query, "wcase", wcaseFilter);

			switch (person) {
			case 0:
			case 1:
			case 2:
			case 3:
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"person"), person));
				break;
			case 4:
				query.addCondition(new CustomCondition(new CustomSql("person>0")));
				break;
			}
			if (gender > 0)
				query.addCondition(new InCondition(new CustomSql("gender"), 0, gender));
			if (sing_pl > 0)
				query.addCondition(new InCondition(new CustomSql("sing_pl"), 0, sing_pl));
			if (type > 0)
				query.addCondition(new InCondition(new CustomSql("type"), 0, type));

			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"sentence_id"), sentence_id));
			if (wordPos > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_pos"), wordPos));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"rating"), new CustomSql("maxrating")));
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				sentenceParts.add(new SentencePart(sentence_id, rs.getInt("word_pos"), 0, rs
						.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
						.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"), rs
						.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"), rs
						.getInt("preposition_id")));

			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentenceParts;
	}

	private void parseFilter(SelectQuery query, String fieldName, String wcaseFilter) {
		if (wcaseFilter.isEmpty())
			return;
		String part = wcaseFilter;
		if (wcaseFilter.startsWith("<>")) {
			part = wcaseFilter.substring(2);
			query.addCondition(new BinaryCondition(BinaryCondition.Op.NOT_EQUAL_TO, new CustomSql(
					fieldName), Integer.valueOf(part)));
			return;
		}
		if (wcaseFilter.startsWith("<")) {
			part = wcaseFilter.substring(1);
			query.addCondition(new BinaryCondition(BinaryCondition.Op.LESS_THAN, new CustomSql(
					fieldName), Integer.valueOf(part)));
			return;
		}
		if (wcaseFilter.startsWith(">")) {
			part = wcaseFilter.substring(1);
			query.addCondition(new BinaryCondition(BinaryCondition.Op.GREATER_THAN, new CustomSql(
					fieldName), Integer.valueOf(part)));
			return;
		}
		query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO,
				new CustomSql(fieldName), Integer.valueOf(part)));
	}

	public ArrayList<SentencePart> getAdjectiveList(int sentence_id, int wordPos,
			String wcaseFilter, int gender, int sing_pl) {
		return getSentencePartList(sentence_id, wordPos, wcaseFilter, 0, gender, sing_pl, 0);
	}

	public ArrayList<SentencePart> getSubstantiveList(int sentence_id, int wordPos,
			String wcaseFilter, int person, int gender, int sing_pl) {
		if (person == 0)
			person = 4;
		return getSentencePartList(sentence_id, wordPos, wcaseFilter, person, gender, sing_pl, 0);
	}

	public ArrayList<SentencePart> getNumeralList(int sentence_id) {
		return getSentencePartList(sentence_id, 0, ">0", 4, 0, 0, WordProcessor.numeral);
	}

	public ArrayList<SentencePart> getConjunctions(int sentence_id) {
		String conjunction = "и";
		String query = MessageFormat.format("select word_pos from sentence_word "
				+ "where sentence_id={0,number,#} and word=''{1}''", sentence_id, conjunction);
		ArrayList<SentencePart> conjunctions = new ArrayList<SentencePart>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			while (rs.next())
				conjunctions.add(new SentencePart(sentence_id, rs.getInt("word_pos"), 0, 0, 0, 0,
						0, 0, 0, 0, 0, 0, 0));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return conjunctions;
	}

	public ArrayList<SentencePart> getNegatives(int sentence_id) {
		String negative = getSetup().getNegative();
		String query = MessageFormat.format("select * from sentence_wordform_detailed "
				+ "where sentence_id={0,number,#} and word=''{1}'' and type=97", sentence_id,
				negative);
		ArrayList<SentencePart> negatives = new ArrayList<SentencePart>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			while (rs.next())
				negatives.add(new SentencePart(sentence_id, rs.getInt("word_pos"), 0, rs
						.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
						.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"), rs
						.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"), rs
						.getInt("preposition_id")));
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return negatives;
	}

	public ArrayList<SentencePart> getPreviousWordforms(int id, int wordPos) {
		ArrayList<SentencePart> wordforms = new ArrayList<SentencePart>();
		int prevWordPos = 0;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String prevWordPosQuery = MessageFormat
					.format("select word_pos from sentence_word "
							+ "where sentence_id={0,number,#} and word_pos<{1,number,#} and dep_word_pos=0 "
							+ "order by word_pos desc", id, wordPos);
			ResultSet rs = stat.executeQuery(prevWordPosQuery);
			if (rs.next())
				prevWordPos = rs.getInt("word_pos");
			rs.close();
			if (prevWordPos > 0) {
				String query = MessageFormat
						.format("SELECT * from sentence_wordform_detailed "
								+ "where sentence_id={0,number,#} and word_pos={1,number,#} and dep_word_pos=0 "
								+ "and rating=maxrating", id, prevWordPos);
				rs = stat.executeQuery(query);
				while (rs.next()) {
					wordforms.add(new SentencePart(id, rs.getInt("word_pos"), SentencePart.subject,
							rs.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
									.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"),
							rs.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"),
							rs.getInt("preposition_id")));
				}
				rs.close();
			}
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordforms;
	}

	public ArrayList<SentencePart> getNextWordforms(int id, int wordPos) {
		ArrayList<SentencePart> wordforms = new ArrayList<SentencePart>();
		ResultSet rs;
		int nextWordPos = 0;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			nextWordPos = getNextIndependentWordPos(id, wordPos);
			if (nextWordPos > 0) {
				String query = MessageFormat
						.format("SELECT * from sentence_wordform_detailed "
								+ "where sentence_id={0,number,#} and word_pos={1,number,#} and dep_word_pos=0 "
								+ "and rating=maxrating", id, nextWordPos);
				rs = stat.executeQuery(query);
				while (rs.next()) {
					wordforms.add(new SentencePart(id, rs.getInt("word_pos"), SentencePart.subject,
							rs.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
									.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"),
							rs.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"),
							rs.getInt("preposition_id")));
				}
				rs.close();
			}
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordforms;
	}

	public int getNextIndependentWordPos(int id, int wordPos) {
		int nextWordPos = 0;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String prevWordPosQuery = MessageFormat
					.format("select word_pos from sentence_word "
							+ "where sentence_id={0,number,#} and word_pos>{1,number,#} and dep_word_pos=0 "
							+ "order by word_pos asc", id, wordPos);
			ResultSet rs = stat.executeQuery(prevWordPosQuery);
			if (rs.next())
				nextWordPos = rs.getInt("word_pos");
			rs.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return nextWordPos;
	}

	public void saveSentenceParts(ArrayList<SentencePart> sentenceParts) {
		Iterator<SentencePart> iterator;
		iterator = sentenceParts.iterator();
		SentencePart sentencePart;
		try {
			establishConnection();
			PreparedStatement prep = conn
					.prepareStatement("UPDATE sentence_word "
							+ "SET type=?,word_id=?,rule_id=?,dep_word_pos=?,preposition_id=? WHERE sentence_id=? and word_pos=?");
			while (iterator.hasNext()) {
				sentencePart = iterator.next();
				prep.setInt(1, sentencePart.part);
				prep.setInt(2, sentencePart.word_id);
				prep.setInt(3, sentencePart.rule_id);
				prep.setInt(4, sentencePart.dep_word_pos);
				prep.setInt(5, sentencePart.preposition_id);
				prep.setInt(6, sentencePart.sentenceID);
				prep.setInt(7, sentencePart.wordPos);
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

	public ArrayList<Sentence> getSentences() {
		ArrayList<Sentence> sentences = new ArrayList<Sentence>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from sentences");
			while (rs.next())
				sentences.add(new Sentence(this, rs.getInt("id"), rs.getString("sentence")));
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentences;
	}

	public void setSentenceType(int id, int type) {
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String query = MessageFormat.format(
					"update sentences set type={1} where id={0,number,#}", id, type);
			stat.executeUpdate(query);
		} catch (SQLException e) {
			e.printStackTrace();
		}

	}

	public Word getWord(int id) {
		if (wordsById == null)
			wordsById = new HashMap<Integer, Word>();

		Word word = wordsById.get(new Integer(id));
		if (word == null) {
			String query = MessageFormat
					.format("select * from words left join complex_word on words.id = complex_word.word_id where id={0,number,#}",
							id);
			try {
				establishConnection();
				Statement stat = conn.createStatement();
				ResultSet rs = stat.executeQuery(query);
				if (rs.next()) {
					word = new Word(this, id, rs.getString("word"), rs.getInt("type"),
							rs.getInt("rule_no"), rs.getBoolean("complex"), rs.getInt("word1"),
							rs.getInt("word2"), rs.getInt("rating"));
					updateWordCache(word);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return word;
	}

	public Word getWord(String baseForm, int type, int rule_no, boolean complex, int word1ID,
			int word2ID, boolean save) {
		String query;

		if (wordsByBase == null)
			wordsByBase = new HashMap<String, HashSet<Word>>();
		HashSet<Word> wordSet = wordsByBase.get(baseForm);
		Word word = null;
		if (wordSet != null) {
			Iterator<Word> iterator = wordSet.iterator();
			while (iterator.hasNext()) {
				word = iterator.next();
				if ((word.type == type) & (word.rule_no == rule_no) & (word.complex == complex)
						& (word.word1ID == word1ID) & (word.word2ID == word2ID))
					return word;
			}
		}

		word = null;

		if (complex)
			query = MessageFormat
					.format("select id,rating from words left join complex_word on words.id = complex_word.word_id "
							+ "where word=''{0}'' AND type={1,number,#} AND rule_no={2,number,#} AND "
							+ "complex = {3} and word1 = {4,number,#} AND word2 = {5,number,#};",
							baseForm, type, rule_no, complex, word1ID, word2ID);
		else
			query = MessageFormat
					.format("select id,rating from words where word=''{0}'' AND type={1,number,#} AND rule_no={2,number,#}",
							baseForm, type, rule_no);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			if (rs.next()) {
				word = new Word(this, rs.getInt("id"), baseForm, type, rule_no, complex, word1ID,
						word2ID, rs.getInt("rating"));
				updateWordCache(word);
			} else if (save) {
				word = new Word(this, 0, baseForm, type, rule_no, complex, word1ID, word2ID, 0);
				word.save();
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return word;
	}

	public ArrayList<Word> getWords(String baseForm, int type, int rule_no) {
		ArrayList<Word> wordsList = new ArrayList<Word>();
		Word word;

		if (baseForm == null)
			return wordsList;

		if (type > 0 && rule_no > 0) {
			word = getWord(baseForm, type, rule_no, false, 0, 0, false);
			if (word != null)
				wordsList.add(word);
			return wordsList;
		}

		SelectQuery query;
		query = new SelectQuery();
		query.addAllColumns();
		query.addCustomFromTable(new CustomSql("words"));
		if (type > 0)
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"type"), type));
		if (rule_no > 0)
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"rule_no"), rule_no));
		query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql("word"),
				baseForm));

		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				word = getWord(rs.getInt("id"));
				wordsList.add(word);
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordsList;
	}

	private void updateWordCache(Word word) {
		if (words == null)
			words = new HashSet<Word>();
		if (wordsById == null)
			wordsById = new HashMap<Integer, Word>();
		if (wordsByBase == null)
			wordsByBase = new HashMap<String, HashSet<Word>>();

		HashSet<Word> wordSet;
		words.add(word);
		wordsById.put(new Integer(word.id), word);
		wordSet = wordsByBase.get(word.word);
		if (wordSet == null) {
			wordSet = new HashSet<Word>();
			wordSet.add(word);
			wordsByBase.put(word.word, wordSet);
		} else
			wordSet.add(word);
	}

	public void putWorformsByWordformstring(String wordform, HashSet<WordForm> wordforms) {
		if (wordformsByWordformstring == null)
			wordformsByWordformstring = new HashMap<String, HashSet<WordForm>>();

		wordformsByWordformstring.put(wordform.intern(), wordforms);
	}

	public HashSet<WordForm> getWordformsByWordformstring(String wordform) {
		if (wordformsByWordformstring == null)
			wordformsByWordformstring = new HashMap<String, HashSet<WordForm>>();

		return wordformsByWordformstring.get(wordform.intern());
	}

	public HashSet<WordForm> getWordforms(int wordID) {
		EndingRule endingrule;
		HashSet<WordForm> wordforms = new HashSet<WordForm>();
		String query = MessageFormat.format("select * from wordforms where word_id={0,number,#}",
				wordID);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				endingrule = getEndingRule(getWord(wordID).fixed, rs.getInt("rule_id"));
				wordforms.add(new WordForm(rs.getString("wordform"), wordID, endingrule, rs
						.getInt("postfix_id")));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return wordforms;
	}

	public void saveSentenceWordLink(SentencePart prevWordform, SentencePart conjunction,
			SentencePart nextWordform) {
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String existLinkQuery = MessageFormat
					.format("select 1 from sentence_word_link "
							+ "where sentence_id={0,number,#} and word_pos={1,number,#} and "
							+ "link_word_pos={2,number,#} and type={3,number,#} and wcase={4,number,#} and "
							+ "gender={5,number,#} and person={6,number,#} and sing_pl={7,number,#} and "
							+ "subtype={8,number,#}", prevWordform.sentenceID,
							prevWordform.wordPos, nextWordform.wordPos, prevWordform.type,
							prevWordform.wcase, prevWordform.gender, prevWordform.person,
							prevWordform.sing_pl, prevWordform.subtype);
			ResultSet rs = stat.executeQuery(existLinkQuery);
			if (rs.next()) {
				rs.close();
				stat.close();
				return;
			}
			rs.close();
			String insertLinkQuery = MessageFormat
					.format("insert into sentence_word_link values "
							+ "({0,number,#},{1,number,#},{2,number,#},{3,number,#},{4,number,#},{5,number,#},"
							+ "{6,number,#},{7,number,#},{8,number,#},{9,number,#})",
							prevWordform.sentenceID, prevWordform.wordPos, nextWordform.wordPos,
							prevWordform.type, prevWordform.wcase, prevWordform.gender,
							prevWordform.person, prevWordform.sing_pl, prevWordform.subtype,
							conjunction.wordPos);
			stat.executeUpdate(insertLinkQuery);
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public ArrayList<SentencePart> getLinkedWordList(int sentence_id, int wordPos, int type,
			int subtype, int wcase, int person, int gender, int sing_pl) {
		ArrayList<SentencePart> linkedWords = new ArrayList<SentencePart>();
		ArrayList<Integer> linkedWordPosition = new ArrayList<Integer>();
		Iterator<Integer> linkedWordPositionIterator;
		Integer tempWordPos;
		SelectQuery query;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String wordlinkQuery = MessageFormat
					.format("select link_word_pos as result from sentence_word_link "
							+ "where sentence_id={0,number,#} and word_pos={1,number,#} and "
							+ "type={2,number,#} and subtype = {3,number,#} and wcase = {4,number,#} "
							+ "union "
							+ "select word_pos as result from sentence_word_link "
							+ "where sentence_id={0,number,#} and link_word_pos={1,number,#} and "
							+ "type={2,number,#} and subtype = {3,number,#} and wcase = {4,number,#}",
							sentence_id, wordPos, type, subtype, wcase);
			ResultSet rs = stat.executeQuery(wordlinkQuery);
			while (rs.next()) {
				tempWordPos = new Integer(rs.getInt("result"));
				if (!linkedWordPosition.contains(tempWordPos))
					linkedWordPosition.add(tempWordPos);
			}
			rs.close();
			linkedWordPositionIterator = linkedWordPosition.iterator();
			while (linkedWordPositionIterator.hasNext()) {
				tempWordPos = linkedWordPositionIterator.next();
				query = new SelectQuery();
				query.addAllColumns();
				query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
				if (wcase > 0)
					query.addCondition(new InCondition(new CustomSql("wcase"), 0, wcase));
				if (person > 0)
					query.addCondition(new InCondition(new CustomSql("person"), 0, person));
				if (gender > 0)
					query.addCondition(new InCondition(new CustomSql("gender"), 0, gender));
				if (sing_pl > 0)
					query.addCondition(new InCondition(new CustomSql("sing_pl"), 0, sing_pl));
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"type"), type));
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"sentence_id"), sentence_id));
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_pos"), tempWordPos.intValue()));
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"rating"), new CustomSql("maxrating")));
				rs = stat.executeQuery(query.validate().toString());
				while (rs.next()) {
					linkedWords.add(new SentencePart(rs.getInt("sentence_id"), rs
							.getInt("word_pos"), 0, rs.getInt("type"), rs.getInt("subtype"), rs
							.getInt("wcase"), rs.getInt("gender"), rs.getInt("person"), rs
							.getInt("sing_pl"), rs.getInt("word_id"), rs.getInt("rule_id"), rs
							.getInt("dep_word_pos"), rs.getInt("preposition_id")));
				}
				rs.close();
			}
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return linkedWords;
	}

	public SentencePart getConjunction(int sentence_id, int wordPos, int wordPos2) {
		boolean found = false;
		ArrayList<SentencePart> conjunctions;
		Iterator<SentencePart> conjunctionsIterator;
		SentencePart conjunction = null;
		conjunctions = getConjunctions(sentence_id);
		int conjunctionWordPos = 0;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String query = MessageFormat
					.format("select conjunction_word_pos from sentence_word_link "
							+ "where sentence_id={0,number,#} and word_pos={1,number,#} and link_word_pos = {2,number,#} ",
							sentence_id, wordPos, wordPos2);
			ResultSet rs = stat.executeQuery(query);
			if (rs.next())
				conjunctionWordPos = rs.getInt("conjunction_word_pos");
			if (conjunctionWordPos == 0) {
				rs.close();
				query = MessageFormat
						.format("select conjunction_word_pos from sentence_word_link "
								+ "where sentence_id={0,number,#} and word_pos={1,number,#} and link_word_pos = {2,number,#} ",
								sentence_id, wordPos2, wordPos);
				rs = stat.executeQuery(query);
				if (rs.next())
					conjunctionWordPos = rs.getInt("conjunction_word_pos");
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if (conjunctionWordPos != 0) {
			conjunctionsIterator = conjunctions.iterator();
			while (conjunctionsIterator.hasNext() & !found) {
				conjunction = conjunctionsIterator.next();
				if (conjunction.wordPos == conjunctionWordPos)
					found = true;
			}
		}
		if (found)
			return conjunction;
		else
			return null;
	}

	public ArrayList<SentencePart> getVerbList(int id, int subtype, int wordPos) {
		ArrayList<SentencePart> verbs = new ArrayList<SentencePart>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
			query.addCondition(new CustomCondition(new CustomSql("type=2")));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"sentence_id"), id));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"subtype"), subtype));
			if (wordPos > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_pos"), wordPos));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"rating"), new CustomSql("maxrating")));
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				verbs.add(new SentencePart(rs.getInt("sentence_id"), rs.getInt("word_pos"), 0, rs
						.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
						.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"), rs
						.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"), rs
						.getInt("preposition_id")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return verbs;
	}

	public ArrayList<SentencePart> getAdverbList(int id, int tense, int wordPos) {
		ArrayList<SentencePart> verbs = new ArrayList<SentencePart>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			SelectQuery query = new SelectQuery();
			query.addAllColumns();
			query.addCustomFromTable(new CustomSql("sentence_wordform_detailed"));
			query.addCondition(new CustomCondition(new CustomSql("type=3")));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"sentence_id"), id));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"tense"), tense));
			if (wordPos > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_pos"), wordPos));
			query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
					"rating"), new CustomSql("maxrating")));
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				verbs.add(new SentencePart(rs.getInt("sentence_id"), rs.getInt("word_pos"), 0, rs
						.getInt("type"), rs.getInt("subtype"), rs.getInt("wcase"), rs
						.getInt("gender"), rs.getInt("person"), rs.getInt("sing_pl"), rs
						.getInt("word_id"), rs.getInt("rule_id"), rs.getInt("dep_word_pos"), rs
						.getInt("preposition_id")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return verbs;
	}

	public boolean checkSimilarWordforms(int wordID, int transformedWordID) {
		String query;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			String queryTemplate = "select wordform from wordforms where word_id = {0,number,#} "
					+ "except select wordform from wordforms where word_id={1,number,#}";
			query = MessageFormat.format(queryTemplate, wordID, transformedWordID);
			ResultSet rs = stat.executeQuery(query);
			if (!rs.next()) {
				rs.close();
				stat.close();
				return true;
			}
			rs.close();
			query = MessageFormat.format(queryTemplate, transformedWordID, wordID);
			rs = stat.executeQuery(query);
			if (!rs.next()) {
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

	public Numeral getNumeralByWordID(int word_id) {
		if (numerals == null)
			getNumerals();
		return numeralsByNumeral.get(getWord(word_id).word);
	}

	public boolean isNumeralBaseForm(int word_id, int rule_id) {
		String query;
		Word word = getWord(word_id);
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

	public Transformation getTransformation(int relationRefID, int relationRefLine) {
		Iterator<Transformation> iterator;
		Transformation transformation;
		if (transformations == null)
			getTransformations();
		iterator = transformationsById.get(relationRefID).iterator();
		while (iterator.hasNext()) {
			transformation = iterator.next();
			if (transformation.line == relationRefLine)
				return transformation;
		}
		return null;
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
							.getInt("word1_type"), rs.getInt("word1_tense"), rs
							.getInt("word1_wcase"), rs.getInt("word1_sing_pl"), rs
							.getInt("word2_type"), rs.getInt("word2_tense"), rs
							.getInt("word2_wcase"), rs.getInt("word2_sing_pl"), rs
							.getString("delimiter")));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return complexWordTemplates;
	}
}
