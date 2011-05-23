package databank;

public class Setup {
	private String splitDelimiter;
	private String hardSign;
	private String iSign;
	private String negativeParticle;

	public Setup(String splitDelimiter, String hardSign, String iSign, String negativeParticle) {
		this.splitDelimiter = splitDelimiter;
		this.hardSign = hardSign;
		this.iSign = iSign;
		this.negativeParticle = negativeParticle;
	}

	public String getSplitDelimiter() {
		return splitDelimiter;
	}

	public String getISign() {
		return iSign;
	}

	public String getHardSign() {
		return hardSign;
	}

	public String getNegative() {
		return negativeParticle;
	}
}
