-- Módulo consultas: credenciales de proveedores DNI/RUC y registro de cada consulta (control de costo).

create table credencial_api (
    id              uuid          primary key,
    proveedor       varchar(15)   not null check (proveedor in ('FACTILIZA', 'APISPERU')),
    nombre          varchar(80)   not null,
    token_cifrado   varchar(2000) not null,
    token_final     varchar(8)    not null,
    prioridad       integer       not null default 1,
    activo          boolean       not null default true,
    ultimo_estado   varchar(200),
    ultimo_uso      timestamptz,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null
);

create table consulta_externa (
    id              uuid          primary key,
    tipo            varchar(5)    not null check (tipo in ('DNI', 'RUC')),
    numero          varchar(15)   not null,
    proveedor       varchar(15)   not null,
    exitosa         boolean       not null,
    mensaje         varchar(300),
    usuario_id      uuid          references usuario (id),
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null
);
create index ix_consulta_externa_creado_en on consulta_externa (creado_en desc);

comment on column credencial_api.token_cifrado is 'Token cifrado con AES-GCM; la clave maestra viene de APP_CLAVE_MAESTRA, nunca de la base';
comment on column credencial_api.token_final is 'Últimos caracteres del token, para reconocerlo sin mostrarlo';
comment on table consulta_externa is 'Cada llamada a un proveedor externo; las consultas resueltas con Clientes no se registran aquí';
