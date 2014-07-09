-- View: prepositions_stat2

-- DROP VIEW prepositions_stat2;

CREATE OR REPLACE VIEW prepositions_stat2 AS 
 SELECT prep_rel.word, prep_rel.word1_wcase, sum(prep_rel.weight) AS sum
   FROM ( SELECT swr.id, swr.sentence_id, swr.word1_pos, swr.word2_pos, sw.word_id, words.word, swr.word1_wcase, 1.00 / (( SELECT count(*) AS count
                   FROM sentence_word_relation swr2
                  WHERE swr2.sentence_id = swr.sentence_id AND swr2.word1_pos = swr.word1_pos AND swr2.relation_type = 102))::numeric AS weight
           FROM sentence_word_relation swr
      JOIN sentence_word sw ON sw.sentence_id = swr.sentence_id AND sw.word_pos = swr.word2_pos
   JOIN words ON words.id = sw.word_id
  WHERE swr.relation_type = 102 AND swr.word2_pos > 0) prep_rel
  GROUP BY prep_rel.word, prep_rel.word1_wcase
  ORDER BY prep_rel.word, sum(prep_rel.weight) DESC;

ALTER TABLE prepositions_stat2
  OWNER TO postgres;

