package knowledge_app;

public class SentencePart {
public SentencePart(int sentenceID, int wordPos, int part, int type, int subtype, int wcase,
			int gender, int person, int sing_pl, int word_id, int rule_id,int dep_word_pos,int preposition_id) {
		this.sentenceID = sentenceID;
		this.wordPos = wordPos;
		this.part = part;
		this.type=type;
		this.subtype=subtype;
		this.wcase = wcase;
		this.gender = gender;
		this.person = person;
		this.sing_pl= sing_pl;
		this.word_id=word_id;
		this.rule_id=rule_id;
		this.dep_word_pos=dep_word_pos;
		this.preposition_id=preposition_id;
	}
public int sentenceID;
public int wordPos;
public int part;
public static int subject = 1;
public static int predicate = 2;
public int type;
public int subtype;
public int wcase;
public int gender;
public int person;
public int sing_pl;
public int word_id;
public int rule_id;
public int dep_word_pos;
public int preposition_id;
}