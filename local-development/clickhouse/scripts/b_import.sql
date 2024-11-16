INSERT INTO `fairspace`.`attribute` FROM INFILE '/data/attribute.csv' FORMAT CSV;
INSERT INTO `fairspace`.`view` FROM INFILE '/data/view.csv' FORMAT CSV;
INSERT INTO `fairspace`.`label` FROM INFILE '/data/label.csv' FORMAT CSV;
INSERT INTO `fairspace`.`view_attribute` FROM INFILE '/data/view_attribute.csv' FORMAT CSV;
INSERT INTO `fairspace`.`view_to_boolean_attribute` FROM INFILE '/data/view_boolean_attribute.csv' FORMAT CSV;

-- Add relationships between views
INSERT INTO `fairspace`.`view_to_view` SELECT 'study', va.entity_id, 'datafile', va.value_text, false
FROM fairspace.view_attribute va
         INNER JOIN fairspace.view v ON va.view_id = v.view_id
         INNER JOIN fairspace.attribute a ON va.attribute_id = a.attribute_id
WHERE v.view_id = 1 -- study
  AND a.attribute_name = 'datafile_id';
INSERT INTO `fairspace`.`view_to_view` SELECT 'study', va.entity_id, 'datafile', va.value_text, true
FROM fairspace.view_attribute va
         INNER JOIN fairspace.view v ON va.view_id = v.view_id
         INNER JOIN fairspace.attribute a ON va.attribute_id = a.attribute_id
WHERE v.view_id = 1 -- study
  AND a.attribute_name = 'study_id';

INSERT INTO `fairspace`.`view_to_view` SELECT 'datafile', va.entity_id, 'study', va.value_text, false
FROM `fairspace`.view_attribute va
         INNER JOIN `fairspace`.view v ON va.view_id = v.view_id
         INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
WHERE v.view_id = 2 -- datafile
  AND a.attribute_name = 'study_id';
INSERT INTO `fairspace`.`view_to_view` SELECT 'datafile', va.entity_id, 'study', va.value_text, true
FROM `fairspace`.view_attribute va
         INNER JOIN `fairspace`.view v ON va.view_id = v.view_id
         INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
WHERE v.view_id = 2 -- datafile
  AND a.attribute_name = 'datafile_id';

INSERT INTO `fairspace`.`view_to_string_attribute`
SELECT 'study', va.entity_id, l.type, a.attribute_name, l.id, va.value_text FROM fairspace.view_attribute va
    INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
    INNER JOIN `fairspace`.label l ON va.value_text = l.label
    WHERE va.view_id = 1 AND a.attribute_name != 'study_id' AND a.attribute_name != 'datafile_id' AND va.value_text IS NOT null;
INSERT INTO `fairspace`.`view_to_string_attribute`
SELECT 'datafile', va.entity_id, l.type, a.attribute_name, l.id, va.value_text FROM fairspace.view_attribute va
    INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
    INNER JOIN `fairspace`.label l ON va.value_text = l.label
    WHERE va.view_id = 2 AND a.attribute_name != 'datafile_id' AND a.attribute_name != 'datafile_id' AND va.value_text IS NOT null;

INSERT INTO `fairspace`.`view_to_int_attribute`
SELECT 'study', va.entity_id, a.attribute_name, va.value_int FROM fairspace.view_attribute va
    INNER JOIN`fairspace`. attribute a ON va.attribute_id = a.attribute_id
    WHERE va.view_id = 1 AND a.attribute_name != 'study_id' AND a.attribute_name != 'datafile_id' AND va.value_int IS NOT null;
INSERT INTO `fairspace`.`view_to_int_attribute`
SELECT 'datafile', va.entity_id, a.attribute_name, va.value_int FROM fairspace.view_attribute va
    INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
    WHERE va.view_id = 2 AND a.attribute_name != 'datafile_id' AND a.attribute_name != 'datafile_id' AND va.value_int IS NOT null;

INSERT INTO `fairspace`.`view_to_date_attribute`
SELECT 'study', va.entity_id, a.attribute_name, va.value_date
FROM fairspace.view_attribute va
    INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
    WHERE va.view_id = 1 AND a.attribute_name != 'study_id' AND a.attribute_name != 'datafile_id' AND va.value_date IS NOT null;
INSERT INTO `fairspace`.`view_to_date_attribute`
SELECT 'datafile', va.entity_id, a.attribute_name, va.value_date FROM fairspace.view_attribute va
    INNER JOIN `fairspace`.attribute a ON va.attribute_id = a.attribute_id
    WHERE va.view_id = 2 AND a.attribute_name != 'datafile_id' AND a.attribute_name != 'datafile_id' AND va.value_date IS NOT null;

DROP TABLE `fairspace`.`attribute`;
DROP TABLE `fairspace`.`view`;
DROP TABLE `fairspace`.`view_attribute`;