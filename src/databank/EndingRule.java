package databank;

import java.util.HashSet;
import java.util.Set;

public class EndingRule {
	String ending;
	int rule_no;
	int rule_variance;
	int type;
	int subtype;
	int rule_id;
	int person;
	int wcase;
	int gender;
	int sing_pl;
	String allow_after;
	String deny_after;
	String e_before;
	String o_before;
	int min_length;

	public EndingRule(String ending, int rule_no, int rule_variance, int type, int subtype,
			int rule_id, int wcase, int gender, int sing_pl, int person, String allow_after, String deny_after,
			String e_before, String o_before, int min_length) {
		this.ending = ending;
		this.rule_no = rule_no;
		this.rule_variance = rule_variance;
		this.type = type;
		this.subtype = subtype;
		this.rule_id = rule_id;
		this.wcase = wcase;
		this.gender = gender;
		this.sing_pl = sing_pl;
		this.person = person;
		this.allow_after = allow_after;
		this.deny_after = deny_after;
		this.e_before = e_before;
		this.o_before = o_before;
		this.min_length = min_length;
	}

	public EndingRule(String ending, int rule_id, int wcase, int gender, int sing_pl, int person, int type, int rule_no,
			int rule_variance) {
		this.ending=ending;
		this.rule_id = rule_id;
		this.type = type;
		this.rule_no = rule_no;
		this.rule_variance = rule_variance;
		this.wcase = wcase;
		this.gender = gender;
		this.sing_pl = sing_pl;
		this.person = person;
	}

	public boolean isZeroVarience() {
		if (rule_variance == 0)
			return true;
		return false;
	}

	/**
	 * @param base - original base (could contain variable suffixes)
	 * @param zeroEndingrule - EndingRule for base form
	 * @return possible base forms from non-base form
	 */
	public Set<String> getBaseForms(String base, EndingRule zeroEndingrule) {
		String modbase;
		Set<String> modbases;
		Set<String> bases = new HashSet<String>();
		if (checkBase(base)) {
			modbases = getZeroForms(base, zeroEndingrule);
			bases.addAll(modbases);
	
			modbase = dropCharacterE(base);
			if (modbase != null) {
				modbases = getZeroForms(modbase, zeroEndingrule);
				bases.addAll(modbases);
			}
	
			modbase = dropCharacterO(base);
			if (modbase != null) {
				modbases = getZeroForms(modbase, zeroEndingrule);
				bases.addAll(modbases);
			}
		}
		return bases;
	}

	private Set<String> getZeroForms(String base, EndingRule zeroEndingrule) {
		String[] baseEnd;
		String[] modEnd;
		int matchLength = 0;
		String modbase;
		Set<String> modbases = new HashSet<String>();

		if (rule_id == zeroEndingrule.rule_id) {
			if (base.length()>=zeroEndingrule.min_length)
				modbases.add(base);			
			return modbases;
		}

		if ((allow_after == null) | (zeroEndingrule.allow_after == null)) {
			if (base.length()>=zeroEndingrule.min_length)
				modbases.add(base);
			return modbases;
		}

		if ((allow_after.isEmpty()) | (zeroEndingrule.allow_after.isEmpty())) {
			if (base.length()>=zeroEndingrule.min_length)
				modbases.add(base);
			return modbases;
		}

		if (allow_after.intern() == zeroEndingrule.allow_after.intern()) {
			if (base.length()>=zeroEndingrule.min_length)
				modbases.add(base);
			return modbases;
		}

		baseEnd = allow_after.split(";");
		modEnd = zeroEndingrule.allow_after.split(";");

		for (int i = 0; i < baseEnd.length; i++)
			if (baseEnd[i].length() >= matchLength)
				if (base.endsWith(baseEnd[i]))
					if (baseEnd[i].length() == matchLength) {
						modbase = base.substring(0, base.length() - baseEnd[i].length())
								+ modEnd[i];
						if (modbase.length()>=zeroEndingrule.min_length)						
							modbases.add(modbase);
					} else {
						matchLength = baseEnd[i].length();
						modbases = new HashSet<String>();
						modbase = base.substring(0, base.length() - baseEnd[i].length())
								+ modEnd[i];
						if (modbase.length()>=zeroEndingrule.min_length)
							modbases.add(modbase);
					}
		return modbases;
	}

	/**Check base against "minimal length", "allow after" and "deny after"
	 * @param base
	 * @return if base is valid according to ending rule
	 */
	private boolean checkBase(String base) {
		int i;
		String[] baseEnd;
		boolean valid = true;
		
		if (base.length() < min_length)
			return false;
		
		if (allow_after != null)
			if (valid & !allow_after.isEmpty()) {
				baseEnd = allow_after.split(";");
				valid = false;
				i = 0;
				while ((!valid) & (i < baseEnd.length)) {
					valid = base.endsWith(baseEnd[i]);
					i++;
				}
			}
		if (deny_after != null)
			if (valid & !deny_after.isEmpty()) {
				baseEnd = deny_after.split(";");
				i = 0;
				while (valid & (i < baseEnd.length)) {
					valid = !base.endsWith(baseEnd[i]);
					i++;
				}
			}
		return valid;
	}

	private String dropCharacterE(String base) {
		return dropCharacter(base, e_before, 'е', 'ь', "л");
	}

	private String dropCharacterO(String base) {
		return dropCharacter(base, o_before, 'о', 'ь', "");
	}

	/**
	 * Changes alternating letters at the end of the base
	 * 
	 * @param base
	 *            - current base
	 * @param endingrule
	 *            - current ending rule
	 * @param zeroEndingrule
	 *            - ending rule of base word form
	 * @return list of bases
	 */
	private String dropCharacter(String base, String checkList, char character, char change,
			String change_after) {
		String[] baseEnd;
		String checkEnd;
		String modbase;
		if ((checkList != null) & (base.length() > 2))
			if (!checkList.isEmpty()) {
				baseEnd = checkList.split(";");
				for (int i = 0; i < baseEnd.length; i++) {
					checkEnd = character + baseEnd[i];
					if (base.endsWith(checkEnd)) {
						modbase = base.substring(0, base.length() - checkEnd.length());
						if (!change_after.isEmpty())
							if (modbase.endsWith(change_after))
								modbase = modbase + change;
						modbase = modbase + baseEnd[i];
						return modbase;
					}
				}
			}
		return null;
	}
}
