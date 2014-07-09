-- View: simple_sentences

-- DROP VIEW simple_sentences;

CREATE OR REPLACE VIEW simple_sentences AS 
 SELECT sw.sentence_id, sw.subsentence_id, sw.word_pos, sw.sw_type, sw.word, sw.nominal_word, sw.nominal_ending, sw.rating, sw.rule_no, sw.type, sw.dep_word_pos
   FROM sentence_wordform sw
   JOIN sentences ON sentences.id = sw.sentence_id
  WHERE sentences.type = 1
  ORDER BY sw.sentence_id, sw.word_pos;

ALTER TABLE simple_sentences
  OWNER TO knowledge;

