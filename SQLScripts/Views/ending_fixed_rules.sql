-- View: ending_fixed_rules

-- DROP VIEW ending_fixed_rules;

CREATE OR REPLACE VIEW ending_fixed_rules AS 
         SELECT - fixed_words.type AS rule_no, fixed_words.rule_id, fixed_words.type, fixed_words.wcase, fixed_words.gender, fixed_words.sing_pl, fixed_words.person, fixed_words.animate, 0 AS tense, fixed_words.subtype
           FROM fixed_words
UNION 
         SELECT ending_rules.rule_no, ending_rules.rule_id, ending_rules.type, ending_rules.wcase, ending_rules.gender, ending_rules.sing_pl, ending_rules.person, ending_rules.animate, ending_rules.tense, ending_rules.subtype
           FROM ending_rules;

ALTER TABLE ending_fixed_rules
  OWNER TO knowledge;

