-- View: prepositions_stat

-- DROP VIEW prepositions_stat;

CREATE OR REPLACE VIEW prepositions_stat AS 
 SELECT sentence_wordform_detailed.preposition_id, words.word, sentence_wordform_detailed.wcase, sum(sentence_wordform_detailed.rating) AS rating
   FROM sentence_wordform_detailed
   JOIN words ON sentence_wordform_detailed.preposition_id = words.id
  WHERE sentence_wordform_detailed.rating = sentence_wordform_detailed.maxrating
  GROUP BY sentence_wordform_detailed.preposition_id, words.word, sentence_wordform_detailed.wcase
 HAVING sentence_wordform_detailed.preposition_id > 0 AND sentence_wordform_detailed.wcase > 1
  ORDER BY words.word, sum(sentence_wordform_detailed.rating) DESC;

ALTER TABLE prepositions_stat
  OWNER TO knowledge;

