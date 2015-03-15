package databank;

public class Transformation {
	public Transformation(DataBank databank, int id, int line, String sourcePrefix,
			String sourceSuffix, int sourceType, String targetPrefix, String targetSuffix,
			int targetType, boolean keepRule, int sourceRule, int targetRule, int type) {
		this.databank = databank;
		this.id = id;
		this.line = line;
		this.sourcePrefix = sourcePrefix;
		this.sourceSuffix = sourceSuffix;
		this.sourceType = sourceType;
		this.targetPrefix = targetPrefix;
		this.targetSuffix = targetSuffix;
		this.targetType = targetType;
		this.keepRule = keepRule;
		this.sourceRule = sourceRule;
		this.targetRule = targetRule;
		this.type = type;
	}

	public int getId() {
		return id;
	}

	public int getLine() {
		return line;
	}

	int id;
	int line;
	private String sourcePrefix;
	String sourceSuffix;
	int sourceType;
	private String targetPrefix;
	String targetSuffix;
	int targetType;
	boolean keepRule;
	int sourceRule;
	int targetRule;
	int type;
	DataBank databank;

	public Word forwardTransformation(Word word) {
		String oldWord = word.word.intern();
		String newWord = oldWord;
		int newType = 0;
		int newRule = targetRule;
		int newRuleVariance = 0;

		if (sourceType != 0)
			if (word.type != sourceType)
				return null;

		if (targetType != 0)
			newType = targetType;
		else
			newType = word.type;

		if (sourceRule != 0)
			if (word.rule_no != sourceRule)
				return null;

		if (keepRule) {
			newRule = word.rule_no;
			newRuleVariance = word.rule_variance;
		}

		Prefix sourcePrefix = databank.getPrefix(this.sourcePrefix);
		if (!this.sourcePrefix.isEmpty() && sourcePrefix == null)
			return null;

		if (sourcePrefix != null) {
			newWord = sourcePrefix.dropPrefix(newWord);
			if (newWord == null)
				return null;
		}

		Prefix targetPrefix = databank.getPrefix(this.targetPrefix);
		if (!this.targetPrefix.isEmpty() && targetPrefix == null)
			return null;

		if (targetPrefix != null) {
			newWord = targetPrefix.addPrefix(newWord);
			if (newWord == null)
				return null;
		}

		newWord = dropSuffix(newWord, sourceSuffix);
		newWord = addSuffix(newWord, targetSuffix);

		if (newWord != oldWord)
			return new Word(null, 0, newWord, newType, newRule, newRuleVariance, false, 0, 0, 0);
		return null;
	}

	public Word backwardTransformation(Word word) {
		String oldWord = word.word.intern();
		String newWord = oldWord;
		int newType = 0;
		int newRule = sourceRule;
		int newRuleVariance = 0;

		if (targetType != 0)
			if (word.type != targetType)
				return null;

		if (sourceType != 0)
			newType = sourceType;
		else
			newType = word.type;

		if (targetRule != 0)
			if (word.rule_no != targetRule)
				return null;

		if (keepRule) {
			newRule = word.rule_no;
			newRuleVariance = word.rule_variance;
		}

		Prefix targetPrefix = databank.getPrefix(this.targetPrefix);
		if (!this.targetPrefix.isEmpty() && targetPrefix == null)
			return null;

		if (targetPrefix != null) {
			newWord = targetPrefix.dropPrefix(newWord);
			if (newWord == null)
				return null;
		}

		Prefix sourcePrefix = databank.getPrefix(this.sourcePrefix);
		if (!this.sourcePrefix.isEmpty() && sourcePrefix == null)
			return null;

		if (sourcePrefix != null) {
			newWord = sourcePrefix.addPrefix(newWord);
			if (newWord == null)
				return null;
		}

		newWord = dropSuffix(newWord, targetSuffix);
		if (newWord == null)
			return null;

		newWord = addSuffix(newWord, sourceSuffix);

		if (newWord != oldWord)
			return new Word(null, 0, newWord, newType, newRule, newRuleVariance, false, 0, 0, 0);

		return null;
	}

	private String addSuffix(String oldWord, String suffix) {
		if (suffix.isEmpty())
			return oldWord;

		return oldWord + suffix;
	}

	private String dropSuffix(String oldWord, String suffix) {
		if (suffix.isEmpty())
			return oldWord;

		if (oldWord.endsWith(suffix))
			return oldWord.substring(0, oldWord.length() - suffix.length());
		else
			return null;
	}
}
