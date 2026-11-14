-- Módulo variables: catálogo compartido. Una variable {{fecha}} significa lo mismo en todas las plantillas.

create table variable (
    id              uuid         primary key,
    clave           varchar(60)  not null unique check (clave ~ '^[a-z][a-z0-9_]*$'),
    etiqueta        varchar(120) not null,
    tipo            varchar(15)  not null check (tipo in ('TEXTO', 'TEXTO_LARGO', 'NUMERO', 'FECHA', 'MONEDA')),
    valor_defecto   varchar(500),
    descripcion     varchar(300),
    creado_en       timestamptz  not null,
    actualizado_en  timestamptz  not null
);

comment on table variable is 'Catálogo de variables {{clave}} usadas en las plantillas';
comment on column variable.clave is 'Identificador estable en minúsculas; no cambia después de creado';

-- Variables frecuentes en documentos notariales, para empezar con algo útil
insert into variable (id, clave, etiqueta, tipo, valor_defecto, descripcion, creado_en, actualizado_en) values
    (gen_random_uuid(), 'fecha', 'Fecha del documento', 'FECHA', null, 'Si se deja vacía se usa la fecha de hoy', now(), now()),
    (gen_random_uuid(), 'lugar', 'Lugar', 'TEXTO', null, 'Ciudad donde se firma', now(), now()),
    (gen_random_uuid(), 'monto', 'Monto', 'MONEDA', null, null, now(), now()),
    (gen_random_uuid(), 'plazo', 'Plazo', 'TEXTO', null, 'Por ejemplo: 12 meses', now(), now()),
    (gen_random_uuid(), 'motivo', 'Motivo', 'TEXTO_LARGO', null, null, now(), now());
