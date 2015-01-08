package databank;

public class CharacterSetup {
	char character;
	int type;
	int capital;
	int sentence_end;
	int ready;
	int elevation;
	boolean separate;
	boolean expectedCapitalLetter;

	public CharacterSetup(char character, int type, int capital, int sentence_end, int ready,
			int elevation,boolean separate, boolean expectedCapitalLetter) {
		this.character = character;
		this.type = type;
		this.capital = capital;
		this.sentence_end = sentence_end;
		this.ready = ready;
		this.elevation = elevation;
		this.separate = separate;
		this.expectedCapitalLetter = expectedCapitalLetter;
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
	
	public boolean isSentenceEnd(){
		return sentence_end>0;
	}
	
	public int getElevation(){
		return elevation;
	}
	
	public boolean isSeparate(){
		return separate;
	}

	public boolean isExpectCapitalLetter() {
		return expectedCapitalLetter;
	}
}
