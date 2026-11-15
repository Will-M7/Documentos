-- Módulo archivos: los archivos (DOCX, PDF) se guardan en disco; aquí solo sus datos.

create table archivo (
    id              uuid          primary key,
    nombre_original varchar(255)  not null,
    tipo_mime       varchar(120)  not null,
    tamano          bigint        not null check (tamano >= 0),
    sha256          varchar(64)   not null,
    ruta            varchar(500)  not null unique,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null
);

comment on table archivo is 'Archivos guardados en disco (app.archivos.directorio); ruta relativa a ese directorio';
comment on column archivo.sha256 is 'Huella del contenido para verificar que el archivo no cambió';
