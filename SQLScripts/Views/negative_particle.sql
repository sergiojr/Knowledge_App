-- View: negative_particle

-- DROP VIEW negative_particle;

CREATE OR REPLACE VIEW negative_particle AS 
 SELECT dep_words_pair.sentence_id, dep_words_pair.dep_word_pos, dep_words_pair.dep_word AS adjective, dep_words_pair.word AS substantive
   FROM dep_words_pair
  WHERE dep_words_pair.dep_type = 97
  ORDER BY dep_words_pair.sentence_id, dep_words_pair.dep_word_pos;

ALTER TABLE negative_particle
  OWNER TO knowledge;

