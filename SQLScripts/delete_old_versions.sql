delete from sentence_word_relation_history where version_id>0 and version_id<=10
update sentence_word_relation_history set version_id=version_id-10 where version_id>10