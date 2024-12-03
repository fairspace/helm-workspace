create table if not exists fairspace.attribute
(
    attribute_id   Int64,
    attribute_name String,
    attribute_type String
)
    engine = MergeTree
    ORDER BY (`attribute_type`, `attribute_name`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view
(
    view_id   Int64,
    view_name String
)
    engine = MergeTree
    ORDER BY (`view_name`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view_attribute
(
    view_id      Int64,
    attribute_id Int64,
    entity_id    String,
    value_int    Nullable(Decimal(24, 6)),
    value_text   Nullable(String),
    value_date   Nullable(DateTime64(6))
)
    engine = MergeTree
    ORDER BY (`entity_id`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.label
(
    id          String,
    type        String,
    label       String
)
    engine = MergeTree
    ORDER BY (`id`, `label`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view_to_view
(
    parent_view_type String,
    parent_view_name String,
    child_view_type  String,
    child_view_name  String,
    self_reference   BOOLEAN
)
    engine = MergeTree
    ORDER BY (`parent_view_type`, `child_view_name`, `child_view_type`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view_to_string_attribute
(
    view_type String,
    view_name String,
    attribute_id String, -- e.g. https://fairspace.nl/domain#ClinicalStudyPhase
    attribute_name String, -- e.g. clinicalstudyphase
    value_id String, -- e.g. https://fairspace.nl/domain#clinical_study_phase_0009
    value String -- e.g. PHASE II
)
    engine = MergeTree
    ORDER BY (`view_type`, `view_name`, `attribute_name`, `value`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view_to_numeric_attribute
(
    view_type String,
    view_name String,
    attribute_name String, -- e.g. plannednumberofsubjects
    value Float64
)
    engine = MergeTree
    ORDER BY (`view_type`, `view_name`, `attribute_name`, `value`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view_to_date_attribute
(
    view_type String,
    view_name String,
    attribute_name String, -- e.g. plannedrecruitmentstartdate
    value DATETIME64
)
    engine = MergeTree
    ORDER BY (`view_type`, `view_name`, `attribute_name`, `value`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.view_to_boolean_attribute
(
    view_type String,
    view_name String,
    attribute_name String, -- e.g. medicalhistoryavailable
    value Bool
)
    engine = MergeTree
    ORDER BY (`view_type`, `view_name`, `attribute_name`, `value`)
    SETTINGS index_granularity = 8192;

create table if not exists fairspace.date_attribute_min_max
(
    view_type String,
    attribute_name String,
    min DATETIME64,
    max DATETIME64
)
    engine = MergeTree
    ORDER BY (`view_type`, `attribute_name`)
    SETTINGS index_granularity = 8192;

create materialized view date_attribute_min_max_mv to fairspace.date_attribute_min_max as
SELECT view_type,
       attribute_name,
       min(value) AS min,
       max(value) AS max
FROM fairspace.view_to_date_attribute
GROUP BY view_type, attribute_name;

create table if not exists fairspace.numeric_attribute_min_max
(
    view_type String,
    attribute_name String,
    min Float64,
    max Float64
)
    engine = MergeTree
    ORDER BY (`view_type`, `attribute_name`)
    SETTINGS index_granularity = 8192;

create materialized view numeric_attribute_min_max_mv to fairspace.numeric_attribute_min_max as
SELECT view_type,
       attribute_name,
       min(value) AS min,
       max(value) AS max
FROM fairspace.view_to_numeric_attribute
GROUP BY view_type, attribute_name;