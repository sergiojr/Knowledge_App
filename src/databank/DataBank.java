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

import knowledge_app.WordProcessor;

import com.healthmarketscience.sqlbuilder.BinaryCondition;
import com.healthmarketscience.sqlbuilder.BinaryCondition.Op;
import com.healthmarketscience.sqlbuilder.ComboCondition;
import com.healthmarketscience.sqlbuilder.CustomCondition;
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
	private HashMap<Integer, HashSet<Integer>> ruleVarianceByRuleNo;
	private HashMap<Integer, EndingRule> zeroEndingruleByRuleNo;
	private HashSet<Transformation> transformations;
	private HashMap<Integer, HashSet<Transformation>> transformationsById;
	private HashSet<ComplexWordTemplate> complexWordTemplates;
	private HashSet<Numeral> numerals;
	private HashMap<String, Numeral> numeralsByNumeral;

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

	public int saveSentence(int type, String sentence, ArrayList<SentenceWord> sentenceWordList)
			throws SQLException {
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
		for (SentenceWord word : sentenceWordList) {
			i++;
			word.sentenceID = sentenceCount;
			word.wordPos = i;
			sent_word_insert.setInt(1, sentenceCount);
			sent_word_insert.setInt(2, i);
			sent_word_insert.setString(3, word.word);
			sent_word_insert.setBoolean(4, word.isPunctuation);
			sent_word_insert.setBoolean(5, word.isName);
			sent_word_insert.addBatch();
		}
		conn.setAutoCommit(false);
		sent_word_insert.executeBatch();
		conn.setAutoCommit(true);
		sent_word_insert.close();
		return sentenceCount;
	}

	public HashSet<Word> getWords(String baseForm, int type) {
		HashSet<Word> wordsList = new HashSet<Word>();
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
		if (words == null)
			return;
		try {
			establishConnection();
			PreparedStatement readWord = conn.prepareStatement("select 1 from words where id=?");
			PreparedStatement saveWord = conn.prepareStatement("insert into words "
					+ "values (?,?,?,?,?,?,?);");
			PreparedStatement saveComplexWord = conn.prepareStatement("insert into complex_word "
					+ "values (?,?,?)");
			PreparedStatement updateWord = conn.prepareStatement("update words "
					+ "set rule_variance = ?, rating=? where id=?");
			for (Word word : words) {
				readWord.setInt(1, word.id);
				ResultSet rs = readWord.executeQuery();
				if (rs.next()) {
					updateWord.setInt(1, word.rule_variance);
					updateWord.setInt(2, word.rating);
					updateWord.setInt(3, word.id);
					updateWord.addBatch();
				} else {
					saveWord.setInt(1, word.id);
					saveWord.setString(2, word.word);
					saveWord.setInt(3, word.type);
					saveWord.setInt(4, word.rule_no);
					saveWord.setInt(5, word.rating);
					saveWord.setBoolean(6, word.complex);
					saveWord.setInt(7, word.rule_variance);
					saveWord.addBatch();
					if (word.complex) {
						saveComplexWord.setInt(1, word.id);
						saveComplexWord.setInt(2, word.word1ID);
						saveComplexWord.setInt(3, word.word2ID);
						saveComplexWord.addBatch();
					}
				}
			}
			readWord.close();
			conn.setAutoCommit(false);
			updateWord.executeBatch();
			saveWord.executeBatch();
			saveComplexWord.executeBatch();
			conn.setAutoCommit(true);
			updateWord.close();
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
		if (delayedSaveWordforms == null)
			return;
		Iterator<WordForm> iterator = delayedSaveWordforms.iterator();
		WordForm wordform;
		try {
			establishConnection();
			PreparedStatement readWordform = conn.prepareStatement("select 1 from wordforms "
					+ "where word_id=? and rule_id=? and postfix_id=?");
			PreparedStatement saveWordform = conn.prepareStatement("insert into wordforms "
					+ "values (?,?,?,?);");
			while (iterator.hasNext()) {
				wordform = iterator.next();
				readWordform.setInt(1, wordform.wordID);
				readWordform.setInt(2, wordform.getRuleID());
				readWordform.setInt(3, wordform.postfix_id);
				ResultSet rs = readWordform.executeQuery();
				if (!rs.next()) {
					saveWordform.setInt(1, wordform.wordID);
					saveWordform.setString(2, wordform.wordForm);
					saveWordform.setInt(3, wordform.getRuleID());
					saveWordform.setInt(4, wordform.postfix_id);
					saveWordform.addBatch();
				}
			}
			readWordform.close();
			conn.setAutoCommit(false);
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

	public HashSet<WordForm> getFixedWordForms(Vocabulary vocabulary, String wordform,
			Postfix postfix) throws SQLException {
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

	public Set<Postfix> getPostfixes() {
		if (postfixes == null) {
			postfixes = new HashSet<Postfix>();
			ResultSet rs;
			try{
			establishConnection();
			Statement stat = conn.createStatement();
			rs = stat.executeQuery("select * from postfixes");
			while (rs.next())
				postfixes.add(new Postfix(rs.getInt("id"), rs.getString("postfix"), rs
						.getInt("reflexive"), rs.getInt("type"), rs.getInt("tense"), rs
						.getInt("rule_no")));
			rs.close();
			stat.close();
			}catch(SQLException e){
				e.printStackTrace();
			}
		}
		return postfixes;
	}

	public Postfix getPostfix(int id) {
		if (postfixes==null)
			getPostfixes();
		for (Postfix postfix : postfixes)
			if (postfix.id==id)
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
							.getInt("word1_type"), rs.getInt("word1_subtype"), rs
							.getInt("word1_wcase"), rs.getInt("word1_sing_pl"), rs
							.getInt("word2_type"), rs.getInt("word2_subtype"), rs
							.getInt("word2_wcase"), rs.getInt("word2_sing_pl"), rs
							.getString("delimiter")));
				}
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
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return ruleCount.intValue();
	}

	public HashSet<Integer> getRuleVariance(int rule_no) {
		HashSet<Integer> ruleVariance;
		String query;
		if (rule_no <= 0)
			return null;
		if (ruleVarianceByRuleNo == null)
			ruleVarianceByRuleNo = new HashMap<Integer, HashSet<Integer>>();
		ruleVariance = ruleVarianceByRuleNo.get(new Integer(rule_no));
		if (ruleVariance == null) {
			try {
				query = MessageFormat.format("select DISTINCT rule_variance from ending_rules "
						+ "where rule_no={0,number,#} and rule_variance>0", rule_no);
				establishConnection();
				Statement stat = conn.createStatement();
				ResultSet rs = stat.executeQuery(query);
				ruleVariance = new HashSet<Integer>();
				while (rs.next())
					ruleVariance.add(new Integer(rs.getInt(1)));
				ruleVarianceByRuleNo.put(new Integer(rule_no), ruleVariance);
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

	public String getCapitalLetters() {
		return getCharacters(1, 1, -1, -1);
	}

	public String getChameleonMarks() {
		return getCharacters(2, -1, -1, -1);
	}

	public String getPunctuationMarks() {
		return getCharacters(0, -1, -1, -1);
	}

	public String getEndSentenceMarks() {
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

	public EndingRule getEndingRule(boolean fixed, int rule) {
		String query;
		if (fixed)
			query = "select base_form as ending,wcase,gender,person,type,-type as rule_no,0 as rule_variance "
					+ "from fixed_words where rule_id=" + rule;
		else
			query = "select ending,wcase,gender,person,type,rule_no,rule_variance "
					+ "from ending_rules where rule_id=" + rule;
		EndingRule endingRule = null;
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			if (rs.next())
				endingRule = new EndingRule(rs.getString("ending"),rule, rs.getInt("wcase"), rs.getInt("gender"),
						rs.getInt("person"), rs.getInt("type"), rs.getInt("rule_no"),
						rs.getInt("rule_variance"));

			rs.close();
			stat.close();

		} catch (SQLException e) {
			e.printStackTrace();
		}
		return endingRule;
	}

	public ArrayList<SentenceWordform> getSentencePartList(int sentence_id,
			String subsentenceFilter, int wordPos, String wcaseFilter, String personFilter,
			int gender, int sing_pl, String typeFilter, String subtypeFilter,
			double rating_tolerance) {
		ArrayList<SentenceWordform> sentenceParts = new ArrayList<SentenceWordform>();
		String ratingToleranceCondition = MessageFormat
				.format("(100-rating)<=(100-maxrating)*{0,number,#.##} and rating*{0,number,#.##}>=maxrating",
						rating_tolerance);

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
					"sentence_id"), sentence_id));
			applyFilter(query, "subsentence_id", subsentenceFilter);
			if (wordPos > 0)
				query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
						"word_pos"), wordPos));
			// query.addCondition(new BinaryCondition(BinaryCondition.Op.EQUAL_TO, new CustomSql(
			// "rating"), new CustomSql("maxrating")));
			query.addCondition(new CustomCondition(ratingToleranceCondition));
			query.addCustomOrdering(new CustomSql("rating"), OrderObject.Dir.DESCENDING);
			ResultSet rs = stat.executeQuery(query.validate().toString());
			while (rs.next()) {
				if (DataBank.checkFilter(rs.getInt("type"), rs.getString("word_type_filter"))
						& DataBank.checkFilter(rs.getInt("wcase"), rs.getString("wcase_filter"))
						& DataBank.checkFilter(rs.getInt("gender"), rs.getString("gender_filter"))
						& DataBank
								.checkFilter(rs.getInt("sing_pl"), rs.getString("sing_pl_filter")))
					sentenceParts.add(new SentenceWordform(sentence_id,
							rs.getInt("subsentence_id"), rs.getInt("word_pos"), rs.getInt("type"),
							rs.getInt("subtype"), rs.getInt("wcase"), rs.getInt("gender"), rs
									.getInt("person"), rs.getInt("sing_pl"), rs.getInt("word_id"),
							rs.getInt("rule_id"), rs.getInt("dep_word_pos"), rs
									.getInt("preposition_id"), rs.getString("word_type_filter"), rs
									.getString("wcase_filter"), rs.getString("gender_filter"), rs
									.getString("sing_pl_filter"), rs.getInt("rating"), rs
									.getInt("maxrating")));
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

	static public boolean checkFilter(int value, String filter) {
		boolean result = false;
		if (filter == null)
			return true;
		if (filter.isEmpty())
			return true;
		String[] splitFilter = filter.split("\\|");
		for (int i = 0; i < splitFilter.length; i++) {
			result = result | checkBinaryFilter(value, splitFilter[i]);
		}
		return result;
	}

	static private boolean checkBinaryFilter(int value, String filter) {
		String part = filter;
		if (filter.startsWith("<>")) {
			part = filter.substring(2);
			return (value != Integer.valueOf(part));
		}
		if (filter.startsWith("<")) {
			part = filter.substring(1);
			return (value < Integer.valueOf(part));
		}
		if (filter.startsWith(">")) {
			part = filter.substring(1);
			return (value > Integer.valueOf(part));
		}
		return (value == Integer.valueOf(part));
	}

	public void saveSentenceParts(ArrayList<SentenceWord> sentenceParts) {
		try {
			establishConnection();
			PreparedStatement prep = conn.prepareStatement("UPDATE sentence_word "
					+ "SET type=?,word_id=?,rule_id=?,dep_word_pos=?,preposition_id=?,"
					+ "word_type_filter=?, wcase_filter=?, gender_filter=?, sing_pl_filter=?, "
					+ "subsentence_id=?, internal=? " + "WHERE sentence_id=? and word_pos=?");
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
				prep.setString(6, sentencePart.word_type_filter);
				prep.setString(7, sentencePart.wcase_filter);
				prep.setString(8, sentencePart.gender_filter);
				prep.setString(9, sentencePart.sing_pl_filter);
				prep.setInt(10, sentencePart.subsentenceID);
				prep.setBoolean(11, sentencePart.internal);
				prep.setInt(12, sentencePart.sentenceID);
				prep.setInt(13, sentencePart.wordPos);
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
					+ "(?,?,?,?,?,?,?,?,?)");
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

	public void saveSentenceWordRelationList(ArrayList<SentenceWordRelation> wordRelationList) {
		try {
			establishConnection();
			PreparedStatement prep = conn
					.prepareStatement("insert into sentence_word_relation values "
							+ "(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
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
		int sentence_id;
		ArrayList<Sentence> sentences = new ArrayList<Sentence>();
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery("select * from sentences order by id");
			while (rs.next()) {
				sentence_id = rs.getInt("id");
				sentences.add(new Sentence(this, vocabulary, sentence_id, rs.getString("sentence"),
						getSentenceWordList(sentence_id)));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentences;
	}

	private ArrayList<SentenceWord> getSentenceWordList(int sentence_id) {
		ArrayList<SentenceWord> sentenceWords = new ArrayList<SentenceWord>();
		String query = MessageFormat.format("select * from sentence_word "
				+ "where sentence_id = {0,number,#} order by sentence_id, word_pos", sentence_id);
		try {
			establishConnection();
			Statement stat = conn.createStatement();
			ResultSet rs = stat.executeQuery(query);
			while (rs.next()) {
				sentenceWords.add(new SentenceWord(rs.getInt("sentence_id"), rs
						.getInt("subsentence_id"), rs.getInt("word_pos"), rs.getString("word"), rs
						.getInt("type"), rs.getInt("dep_word_pos"), rs.getInt("preposition_id"), rs
						.getBoolean("punctuation"), rs.getBoolean("name"), rs
						.getBoolean("internal"), rs.getString("word_type_filter"), rs
						.getString("wcase_filter"), rs.getString("gender_filter"), rs
						.getString("sing_pl_filter")));
			}
			rs.close();
			stat.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return sentenceWords;
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
}
