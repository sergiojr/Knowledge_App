-- View: prepositions_stat3

-- DROP VIEW prepositions_stat3;

CREATE OR REPLACE VIEW prepositions_stat3 AS 
 SELECT prep_rel.word, prep_rel.word1_wcase, sum(prep_rel.weight) AS sum
   FROM ( SELECT swr.id, swr.sentence_id, swr.word1_pos, swr.word2_pos, sw.word_id, words.word, swr.word1_wcase, 1.00 * (( SELECT max(swd.rating) AS rating
                   FROM sentence_wordform_detailed swd
                  WHERE swd.sentence_id = swr.sentence_id AND swd.word_pos = swr.word1_pos AND swd.wcase = swr.word1_wcase AND swd.type = swr.word1_type AND swd.gender = swr.word1_gender AND swd.sing_pl = swr.word1_sing_pl))::numeric / (( SELECT count(*) AS count
                   FROM sentence_word_relation swr2
                  WHERE swr2.sentence_id = swr.sentence_id AND swr2.word1_pos = swr.word1_pos AND swr2.relation_type = 102))::numeric AS weight
           FROM sentence_word_relation swr
      JOIN sentence_word sw ON sw.sentence_id = swr.sentence_id AND sw.word_pos = swr.word2_pos
   JOIN words ON words.id = sw.word_id
  WHERE swr.relation_type = 102 AND swr.word2_pos > 0) prep_rel
  GROUP BY prep_rel.word, prep_rel.word1_wcase
  ORDER BY prep_rel.word, sum(prep_rel.weight) DESC;

ALTER TABLE prepositions_stat3
  OWNER TO postgres;

