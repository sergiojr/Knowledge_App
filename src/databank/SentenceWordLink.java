package databank;

import java.util.ArrayList;

public class SentenceWordLink {
	int sentenceID;
	int wordPos;
	int linkWordPos;
	int conjunctionWordPos;
	int type;
	int subtype;
	int wcase;
	int gender;
	int person;
	int sing_pl;

	public SentenceWordLink(SentenceWordform prevWordform, SentenceWord conjunctionWord,
			SentenceWordform nextWordform) {
		this.sentenceID = prevWordform.sentenceID;
		this.wordPos = prevWordform.wordPos;
		this.linkWordPos = nextWordform.wordPos;
		this.conjunctionWordPos = conjunctionWord.wordPos;

		// существительные и местоимения существительные
		if ((prevWordform.wcase > 0) & (prevWordform.person > 0)) {
			this.wcase = prevWordform.wcase;
		}

		// прилагательные и местоимения прилагательные
		if ((prevWordform.wcase > 0) & (prevWordform.person == 0)) {
			this.wcase = prevWordform.wcase;
			this.gender = prevWordform.gender;
			this.person = prevWordform.person;
			this.sing_pl = prevWordform.sing_pl;
		}
		// прочие
		if (prevWordform.wcase == 0) {
			this.type = prevWordform.type;
			this.subtype = prevWordform.subtype;
			this.wcase = prevWordform.wcase;
			this.gender = prevWordform.gender;
			this.person = prevWordform.person;
			this.sing_pl = prevWordform.sing_pl;
		}
	}

	public boolean exists(ArrayList<SentenceWordLink> wordLinkList) {
		for (SentenceWordLink wordLink : wordLinkList)
			if ((sentenceID == wordLink.sentenceID)
					&& ((wordPos == wordLink.wordPos) && (linkWordPos == wordLink.linkWordPos))
							| ((wordPos == wordLink.linkWordPos) && (linkWordPos == wordLink.wordPos))
					&& (conjunctionWordPos == wordLink.conjunctionWordPos)) {
				// существительные и местоимения существительные
				if ((wcase > 0) && (person > 0))
					if ((wcase == wordLink.wcase) && (wordLink.person > 0)) {
						return true;
					}

				// прилагательные и местоимения прилагательные
				if ((wcase > 0) && (person == 0))
					if ((wordLink.person == 0)
							&& (wcase == wordLink.wcase)
							&& ((gender == wordLink.gender) | (gender == 0) | (wordLink.gender == 0))
							&& ((sing_pl == wordLink.sing_pl) | (sing_pl == 0) | (wordLink.sing_pl == 0))) {
						return true;
					}

				// прочие
				if (wcase == 0)
					if ((wordLink.person == person) && (wcase == wordLink.wcase)
							&& (gender == wordLink.gender) && (sing_pl == wordLink.sing_pl)
							&& (type == wordLink.type) && (subtype == wordLink.subtype)) {
						return true;
					}
			}
		return false;
	}
}
