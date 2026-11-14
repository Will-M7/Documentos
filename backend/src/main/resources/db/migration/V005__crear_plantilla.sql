-- Módulo plantillas: documentos Word con marcas {{...}}, versionados.

create table plantilla (
    id              uuid          primary key,
    nombre          varchar(150)  not null,
    descripcion     varchar(500),
    categoria       varchar(80),
    version_actual  integer       not null check (version_actual >= 1),
    activo          boolean       not null default true,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null
);
create unique index uk_plantilla_nombre on plantilla (lower(nombre));

-- Cada DOCX subido es una versión nueva e inmutable; los documentos guardan la versión exacta que usaron
create table plantilla_version (
    id              uuid         primary key,
    plantilla_id    uuid         not null references plantilla (id),
    numero          integer      not null check (numero >= 1),
    archivo_id      uuid         not null references archivo (id),
    marcas          varchar(4000) not null,
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null,
    constraint uk_plantilla_version unique (plantilla_id, numero)
);

-- Roles de las personas dentro de la plantilla: otorgante, apoderado, arrendador...
create table plantilla_participante (
    id              uuid          primary key,
    plantilla_id    uuid          not null references plantilla (id) on delete cascade,
    clave           varchar(60)   not null,
    etiqueta        varchar(120)  not null,
    tipo_cliente    varchar(12)   not null check (tipo_cliente in ('PERSONA', 'EMPRESA', 'CUALQUIERA')),
    campos          varchar(500)  not null,
    orden           integer       not null,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null,
    constraint uk_plantilla_participante unique (plantilla_id, clave)
);

-- Variables del catálogo que usa la versión actual
create table plantilla_variable (
    id              uuid          primary key,
    plantilla_id    uuid          not null references plantilla (id) on delete cascade,
    variable_id     uuid          not null references variable (id),
    orden           integer       not null,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null,
    constraint uk_plantilla_variable unique (plantilla_id, variable_id)
);

comment on column plantilla_version.marcas is 'Marcas detectadas en el DOCX, separadas por salto de línea';
comment on column plantilla_participante.campos is 'Campos usados en Word, separados por coma: nombres,numero_documento';
