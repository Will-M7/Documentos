-- Sesiones renovables: el token de acceso dura poco (15 min) y se renueva con un token de sesión
-- que se guarda aquí solo como hash. Cerrar sesión, desactivar al usuario o cambiarle la clave las revoca.

create table sesion (
    id              uuid          primary key,
    usuario_id      uuid          not null references usuario (id),
    hash_token      varchar(64)   not null unique,
    expira_en       timestamptz   not null,
    revocada        boolean       not null default false,
    creado_en       timestamptz   not null,
    actualizado_en  timestamptz   not null
);
create index ix_sesion_usuario on sesion (usuario_id) where not revocada;

comment on column sesion.hash_token is 'SHA-256 del token de sesión; el token en claro solo lo tiene el navegador';
