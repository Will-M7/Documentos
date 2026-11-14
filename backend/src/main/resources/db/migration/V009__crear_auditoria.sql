-- Módulo auditoría: quién hizo qué y cuándo (Ley 29733: trazabilidad del uso de datos personales).

create table auditoria (
    id              uuid          primary key,
    usuario_id      uuid          references usuario (id),
    usuario_nombre  varchar(120),
    accion          varchar(40)   not null,
    entidad         varchar(40)   not null,
    entidad_id      uuid,
    detalle         varchar(500),
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null
);
create index ix_auditoria_creado_en on auditoria (creado_en desc);
create index ix_auditoria_entidad on auditoria (entidad, entidad_id);

comment on table auditoria is 'Registro inmutable de acciones; no se edita ni se borra desde la aplicación';
