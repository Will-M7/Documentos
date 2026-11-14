-- Módulo clientes: personas y empresas que participan en los documentos.

create table cliente (
    id                uuid         primary key,
    tipo              varchar(10)  not null check (tipo in ('PERSONA', 'EMPRESA')),
    tipo_documento    varchar(10)  not null check (tipo_documento in ('DNI', 'RUC', 'CE', 'PASAPORTE')),
    numero_documento  varchar(15)  not null,
    nombres           varchar(120),
    apellido_paterno  varchar(80),
    apellido_materno  varchar(80),
    razon_social      varchar(200),
    direccion         varchar(250),
    telefono          varchar(30),
    correo            varchar(120),
    origen            varchar(15)  not null check (origen in ('MANUAL', 'FACTILIZA', 'APISPERU')),
    activo            boolean      not null default true,
    creado_en         timestamptz  not null,
    actualizado_en    timestamptz  not null,
    constraint uk_cliente_documento unique (tipo_documento, numero_documento),
    constraint ck_cliente_persona check (tipo <> 'PERSONA' or (nombres is not null and apellido_paterno is not null)),
    constraint ck_cliente_empresa check (tipo <> 'EMPRESA' or (razon_social is not null and tipo_documento = 'RUC'))
);

-- Búsqueda por nombre sin distinguir mayúsculas
create index ix_cliente_nombres on cliente (lower(coalesce(nombres, '') || ' ' || coalesce(apellido_paterno, '') || ' ' || coalesce(apellido_materno, '') || ' ' || coalesce(razon_social, '')));

comment on table cliente is 'Personas y empresas; únicas por tipo y número de documento';
comment on column cliente.origen is 'De dónde salieron los datos: ingreso manual o proveedor de consulta';
