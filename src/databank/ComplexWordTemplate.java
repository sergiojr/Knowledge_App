package databank;

public class ComplexWordTemplate {
	int id;

	int word1_type;
	int word1_subtype;
	int word1_wcase;
	int word1_sing_pl;

	int word2_type;
	int word2_subtype;
	int word2_wcase;
	int word2_sing_pl;

	String delimiter;

	public String getDelimiter() {
		return delimiter;
	}

	public ComplexWordTemplate(int id, int word1_type, int word1_subtype, int word1_wcase,
			int word1_sing_pl, int word2_type, int word2_subtype, int word2_wcase, int word2_sing_pl,
			String delimiter) {
		super();
		this.id = id;
		this.word1_type = word1_type;
		this.word1_subtype = word1_subtype;
		this.word1_wcase = word1_wcase;
		this.word1_sing_pl = word1_sing_pl;
		this.word2_type = word2_type;
		this.word2_subtype = word2_subtype;
		this.word2_wcase = word2_wcase;
		this.word2_sing_pl = word2_sing_pl;
		this.delimiter = delimiter;
	}

}
