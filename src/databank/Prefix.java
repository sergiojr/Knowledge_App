package databank;

public class Prefix {
	public Prefix(DataBank databank, int id, String prefix, String allow_before,
			String deny_before, String hard_sign_before, String change_i_with) {
		String splitDelimiter;
		splitDelimiter = databank.getSetup().getSplitDelimiter();
		hardSign = databank.getSetup().getHardSign();
		i = databank.getSetup().getISign();
		this.id = id;
		this.prefix = prefix;
		this.allow_before = allow_before.split(splitDelimiter);
		this.deny_before = deny_before.split(splitDelimiter);
		this.hard_sign_before = hard_sign_before.split(splitDelimiter);
		this.change_i_with = change_i_with;
	}

	private int id;
	private String prefix;
	private String[] allow_before;
	private String[] deny_before;
	private String hardSign;
	private String[] hard_sign_before;
	private String i;
	private String change_i_with;

	public int getId() {
		return id;
	}

	public String addPrefix(String wordForm) {
		String newWordForm = null;
		String modPrefix = prefix;
		for (int i = 0; i < deny_before.length; i++)
			if (!deny_before[i].isEmpty())
				if (wordForm.startsWith(deny_before[i]))
					return null;
		for (int i = 0; i < hard_sign_before.length; i++)
			if (!hard_sign_before[i].isEmpty())
				if (wordForm.startsWith(hard_sign_before[i]))
					modPrefix = prefix + hardSign;
		if (!change_i_with.isEmpty())
			if (wordForm.startsWith(i))
				wordForm = change_i_with + wordForm.substring(i.length());
		newWordForm = modPrefix + wordForm;
		return newWordForm;
	}

	public String dropPrefix(String wordForm) {
		String newWordForm = null;
		String tempWordForm;
		if (wordForm.startsWith(prefix)) {
			newWordForm = wordForm.substring(prefix.length());
			// word without prefix cannot start from character that require hard sign ("ú")
			for (int i = 0; i < hard_sign_before.length; i++)
				if (!hard_sign_before[i].isEmpty())
					if (newWordForm.startsWith(hard_sign_before[i]))
						return null;
			// if word without prefix start from hard sign ("ú") that strip it and check allowed
			// beginning
			if (newWordForm.startsWith(hardSign)) {
				tempWordForm = newWordForm.substring(hardSign.length());
				for (int i = 0; i < hard_sign_before.length; i++)
					if (!hard_sign_before[i].isEmpty())
						if (tempWordForm.startsWith(hard_sign_before[i]))
							newWordForm = tempWordForm;
			}
			// if prefix change "i" in word then "i" is denied in the beginning of word
			// and "i" should be restored in the beginning of the word
			if (!change_i_with.isEmpty()) {
				if (newWordForm.startsWith(i))
					return null;
				if (newWordForm.startsWith(change_i_with))
					newWordForm = i + newWordForm.substring(change_i_with.length());
			}

			// check that word without prefix starts from character that is not denied
			for (int i = 0; i < deny_before.length; i++)
				if (!deny_before[i].isEmpty())
					if (newWordForm.startsWith(deny_before[i]))
						return null;

		}
		return newWordForm;
	}
}
