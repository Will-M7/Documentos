-- Portal de clientes: el cliente final pide un documento desde internet y la oficina lo revisa y genera.

alter table plantilla add column publica boolean not null default false;
comment on column plantilla.publica is 'Si se ofrece en el portal de clientes';

create table solicitud (
    id                uuid          primary key,
    codigo            varchar(12)   not null unique,
    plantilla_id      uuid          not null references plantilla (id),
    plantilla_nombre  varchar(150)  not null,
    estado            varchar(12)   not null check (estado in ('PENDIENTE', 'ATENDIDA', 'RECHAZADA')),
    datos             text          not null,
    contacto_nombre   varchar(150)  not null,
    contacto_telefono varchar(30),
    contacto_correo   varchar(120),
    acepta_datos      boolean       not null check (acepta_datos),
    motivo_rechazo    varchar(300),
    documento_id      uuid          references documento (id),
    atendida_por      uuid          references usuario (id),
    atendida_en       timestamptz,
    creado_en         timestamptz   not null,
    actualizado_en    timestamptz   not null
);
create index ix_solicitud_estado on solicitud (estado, creado_en);

comment on column solicitud.codigo is 'Código que recibe el cliente para consultar el estado (no revela datos personales)';
comment on column solicitud.datos is 'JSON con variables y participantes tal como los envió el cliente';
comment on column solicitud.acepta_datos is 'El cliente autorizó el tratamiento de sus datos personales (Ley 29733)';
