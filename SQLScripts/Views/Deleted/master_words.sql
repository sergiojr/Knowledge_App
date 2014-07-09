-- View: master_words

-- DROP VIEW master_words;

CREATE OR REPLACE VIEW master_words AS 
 SELECT sentence_wordform.nominal_word, sentence_wordform.nominal_ending, sentence_wordform.rule_no, sentence_wordform.rating, count(*) AS word_count
   FROM sentence_wordform
   JOIN sentence_wordform sw2 ON sentence_wordform.sentence_id = sw2.sentence_id AND sentence_wordform.word_pos = sw2.dep_word_pos AND sw2.type <> 100
  WHERE sentence_wordform.type = 1
  GROUP BY sentence_wordform.nominal_word, sentence_wordform.nominal_ending, sentence_wordform.rule_no, sentence_wordform.rating
  ORDER BY sentence_wordform.nominal_word;

ALTER TABLE master_words
  OWNER TO knowledge;

