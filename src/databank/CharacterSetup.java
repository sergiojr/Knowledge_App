package databank;

public class CharacterSetup {
	char character;
	int type;
	int capital;
	int sentence_end;
	int ready;
	int elevation;

	public CharacterSetup(char character, int type, int capital, int sentence_end, int ready,
			int elevation) {
		this.character = character;
		this.type = type;
		this.capital = capital;
		this.sentence_end = sentence_end;
		this.ready = ready;
		this.elevation = elevation;
	}
	
	public boolean equals(char character){
		return this.character==character;
	}
	
	public boolean isPunctuation(){
		return type==0;
	}
	
	public boolean isChameleon(){
		return type==2;
	}
}
