package databank;

public class DataSource {
	private int id;
	private String filePath;
	// action:
	// 0 - skip
	// 1 - parse words/wordforms
	// 2 - parse sentences
	private int action;

	public DataSource(int id, String filePath, int action) {
		this.id = id;
		this.filePath = filePath;
		this.action = action;
	}

	public String getFilePath() {
		return filePath;
	}

	public int getAction() {
		return action;
	}
}
