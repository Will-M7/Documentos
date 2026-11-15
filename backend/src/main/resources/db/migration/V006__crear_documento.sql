-- Módulo documentos: cada documento generado, con los valores exactos que se usaron.

create table documento (
    id                   uuid          primary key,
    numero               bigint        generated always as identity unique,
    plantilla_id         uuid          not null references plantilla (id),
    plantilla_version_id uuid          not null references plantilla_version (id),
    plantilla_nombre     varchar(150)  not null,
    usuario_id           uuid          not null references usuario (id),
    titulo               varchar(250)  not null,
    valores              text          not null,
    docx_archivo_id      uuid          not null references archivo (id),
    pdf_archivo_id       uuid          references archivo (id),
    creado_en            timestamptz   not null,
    actualizado_en       timestamptz   not null
);
create index ix_documento_creado_en on documento (creado_en desc);

create table documento_participante (
    id              uuid          primary key,
    documento_id    uuid          not null references documento (id) on delete cascade,
    participante    varchar(60)   not null,
    etiqueta        varchar(120)  not null,
    cliente_id      uuid          not null references cliente (id),
    nombre          varchar(250)  not null,
    documento_identidad varchar(30) not null,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null,
    constraint uk_documento_participante unique (documento_id, participante)
);
create index ix_documento_participante_cliente on documento_participante (cliente_id);

comment on column documento.numero is 'Número correlativo visible (n.º 1, 2, 3...)';
comment on column documento.valores is 'JSON con el texto puesto en cada marca, para auditar o regenerar';
comment on column documento_participante.nombre is 'Nombre del cliente al momento de generar (no cambia si luego se edita el cliente)';
