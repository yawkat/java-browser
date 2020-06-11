-- ARTIFACTS

create table artifacts
(
    id                 varchar not null primary key,
    lastCompileVersion integer not null default 0,
    metadata           bytea   not null default '{}'
);

-- SOURCE FILES

create table sourceFiles
(
    realm       smallint not null,
    artifactId  varchar  not null,
    path        varchar  not null,
    text        bytea    not null,
    annotations bytea    not null
)
    partition by list (realm);

create table _sourceFiles0 partition of sourceFiles for values in (0);
create table _sourceFiles1 partition of sourceFiles for values in (1);

-- own table because for large source files there may be multiple of these (tsvector is limited to 16k positions)
-- todo: partition by realm
create table sourceFileLexemesBase
(
    realm      smallint not null,
    artifactId varchar  not null,
    sourceFile varchar  not null,
    lexemes    tsvector not null,
    starts     int4[]   not null,
    lengths    int4[]   not null,
    primary key (realm, artifactId, sourceFile)
);

-- separate tables for the separate indices
create table sourceFileLexemes
(
) inherits (sourceFileLexemesBase);
create table sourceFileLexemesNoSymbols
(
) inherits (sourceFileLexemesBase);

-- BINDINGS

create table bindings
(
    realm       smallint not null,
    artifactId  varchar  not null,
    binding     varchar  not null,
    description bytea    null, -- json
    parent      varchar  null,
    sourceFile  varchar  not null,
    isType      bool     not null,
    modifiers   int4     not null
)
    partition by list (realm);

create table _bindings0 partition of bindings for values in (0);
create table _bindings1 partition of bindings for values in (1);

-- BINDING REFERENCES

create table binding_references
(
    realm            smallint not null,
    targetBinding    varchar  not null,
    type             int      not null,
    sourceArtifactId varchar  not null,
    sourceFile       varchar  not null,
    sourceFileLine   int      not null,
    sourceFileId     int      not null
)
    partition by list (realm);

create table _binding_references0 partition of binding_references for values in (0);
create table _binding_references1 partition of binding_references for values in (1);

-- DEPENDENCIES

create table dependencies
(
    fromArtifactId varchar not null references artifacts,
    toArtifactId   varchar not null,

    primary key (fromArtifactId, toArtifactId)
);

-- ALIASES

create table artifactAliases
(
    artifactId varchar not null references artifacts,
    alias      varchar not null,

    primary key (artifactId, alias)
);

-- BINDING REFERENCE COUNTS

create materialized view binding_references_count_view as
select realm, targetBinding, type, sourceArtifactId, count(*) as count
from binding_references
group by (realm, targetBinding, type, sourceArtifactId);

-- PACKAGES

create function count_dots(text)
    returns int
as
'select char_length($1) - char_length(replace($1, ''.'', ''''));'
    language sql
    immutable
    returns null on null input ;

create function until_last_dot(text)
    returns text
as
'select array_to_string((string_to_array($1, ''.''))[1:array_length(string_to_array($1, ''.''), 1) - 1], ''.'');'
    language sql
    immutable
    returns null on null input ;

create materialized view packages_view as
with recursive rec (artifactId, name) as (
    select artifactId, until_last_dot(binding) as name
    from bindings
    where parent is null
      and position('.' in binding) != 0
    union
    distinct
    select artifactId, null
    from bindings
    union
    distinct
    select artifactId, until_last_dot(name)
    from rec
    where position('.' in name) != 0
)
select *
from rec
order by artifactId, name;

-- TYPE COUNT BY PACKAGE

-- This view counts the number of direct and indirect members of all packages.
-- The package column may be NULL to denote the "top-level" package.
create materialized view type_count_by_depth_view as
with types_and_packages (artifactId, name) as (
    select artifactId, binding as name
    from bindings
    where parent is null
      and realm = 0
    union
    distinct
    select artifactId, name
    from packages_view
)
select outer_.artifactId,
       outer_.name                                                     package,
       count_dots(inner_.name) - coalesce(count_dots(outer_.name), -1) depth,
       count(inner_.name)                                              typeCount
from types_and_packages as outer_
         inner join types_and_packages as inner_ on outer_.artifactId = inner_.artifactId and
                                                    (outer_.name is null or inner_.name like outer_.name || '.%')
group by outer_.artifactId, outer_.name, count_dots(inner_.name);