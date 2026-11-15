-- Módulo identidad: usuarios del sistema.
-- Regla: una migración aplicada nunca se modifica; los cambios van en una nueva (V002__...).

create table usuario (
    id             uuid         primary key,
    usuario        varchar(50)  not null unique,
    nombre         varchar(120) not null,
    hash_clave     varchar(100) not null,
    rol            varchar(20)  not null check (rol in ('ADMIN', 'OPERADOR')),
    activo         boolean      not null default true,
    creado_en      timestamptz  not null,
    actualizado_en timestamptz  not null
);

comment on table usuario is 'Personas que entran al sistema';
comment on column usuario.usuario is 'Nombre de usuario en minúsculas';
comment on column usuario.hash_clave is 'Hash BCrypt; la contraseña nunca se guarda en claro';
