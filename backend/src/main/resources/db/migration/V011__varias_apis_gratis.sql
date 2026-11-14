-- Varias APIs de consulta (incluida Decolecta, gratuita) con límite mensual por credencial:
-- cuando una se agota, se usa la siguiente automáticamente.

alter table credencial_api drop constraint credencial_api_proveedor_check;
alter table credencial_api add constraint credencial_api_proveedor_check
    check (proveedor in ('FACTILIZA', 'APISPERU', 'DECOLECTA'));
alter table credencial_api add column limite_mensual integer check (limite_mensual is null or limite_mensual > 0);
alter table credencial_api add column agotada_hasta timestamptz;

alter table cliente drop constraint cliente_origen_check;
alter table cliente add constraint cliente_origen_check
    check (origen in ('MANUAL', 'FACTILIZA', 'APISPERU', 'DECOLECTA'));

alter table consulta_externa add column credencial_id uuid references credencial_api (id) on delete set null;
create index ix_consulta_externa_credencial on consulta_externa (credencial_id, creado_en);

comment on column credencial_api.limite_mensual is 'Consultas que permite el plan por mes (gratis: Decolecta 100). Vacío = sin límite';
comment on column credencial_api.agotada_hasta is 'No se usa hasta esta fecha (el proveedor respondió que se acabó el cupo)';
