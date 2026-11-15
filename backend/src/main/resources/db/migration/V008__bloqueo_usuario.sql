-- Bloqueo temporal tras varios intentos de ingreso fallidos (protección contra adivinar contraseñas).

alter table usuario add column intentos_fallidos integer not null default 0;
alter table usuario add column bloqueado_hasta timestamptz;

comment on column usuario.intentos_fallidos is 'Intentos fallidos seguidos; vuelve a 0 al ingresar bien';
comment on column usuario.bloqueado_hasta is 'Si tiene fecha futura, el usuario no puede ingresar hasta entonces';
