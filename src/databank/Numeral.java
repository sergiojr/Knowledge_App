package databank;

public class Numeral {
	String numeral;
	int sing_pl;
	int base_wcase;
	int base_sing_pl;
	int value;

	public Numeral(String numeral, int sing_pl, int base_wcase, int base_sing_pl, int value) {
		this.numeral = numeral;
		this.sing_pl = sing_pl;
		this.base_wcase = base_wcase;
		this.base_sing_pl = base_sing_pl;
		this.value = value;
	}

	public int getBaseWcase() {
		return base_wcase;
	}

	public int getBaseSingPl() {
		return base_sing_pl;
	}
}
