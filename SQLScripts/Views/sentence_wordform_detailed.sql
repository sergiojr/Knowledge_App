-- View: sentence_wordform_detailed

-- DROP VIEW sentence_wordform_detailed;

CREATE OR REPLACE VIEW sentence_wordform_detailed AS 
 SELECT sentence_word.sentence_id, sentence_word.word_pos, sentence_word.word, words.type, words.rating, ending_fixed_rules.wcase, ending_fixed_rules.person, ending_fixed_rules.gender, ending_fixed_rules.sing_pl, ending_fixed_rules.animate, ending_fixed_rules.tense, wordforms.word_id, wordforms.rule_id, sentence_word.preposition_id, ( SELECT max(words.rating) AS max
           FROM wordforms
      JOIN words ON wordforms.word_id = words.id
     WHERE wordforms.wordform = sentence_word.word) AS maxrating, sentence_word.dep_word_pos, ending_fixed_rules.subtype, wordforms.postfix_id, sentence_word.word_type_filter, sentence_word.subsentence_id, sentence_word.wcase_filter, sentence_word.gender_filter, sentence_word.sing_pl_filter
   FROM sentence_word
   JOIN wordforms ON sentence_word.word = wordforms.wordform
   JOIN words ON wordforms.word_id = words.id
   JOIN ending_fixed_rules ON words.rule_no = ending_fixed_rules.rule_no AND wordforms.rule_id = ending_fixed_rules.rule_id
  ORDER BY sentence_word.sentence_id, sentence_word.word_pos;

ALTER TABLE sentence_wordform_detailed
  OWNER TO knowledge;

