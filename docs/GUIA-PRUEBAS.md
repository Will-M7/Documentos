# Guía de pruebas por bloque

Cada bloque se prueba por separado, en orden. Marque ✅ o anote lo que falló.

## Antes de empezar

1. PostgreSQL encendido, con el usuario `estudio` y las bases `estudio_documental` y `estudio_documental_test` creadas (ver [DESARROLLO.md](DESARROLLO.md), sección 2).
2. Backend (una terminal):

   ```bash
   cd backend
   mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
   ```

   Al arrancar aplica solo las migraciones nuevas (V002 a V009). Si ya tenía el backend abierto, ciérrelo (Ctrl+C) y vuelva a abrirlo.
3. Web (otra terminal):

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

4. Abrir http://localhost:5173 e ingresar con `admin` / `admin-local-123`.

**Pruebas automáticas** (opcional; deben pasar todas):

```bash
cd backend
mvnw.cmd verify
```

```bash
cd frontend
npm test
```

---

## Bloque 2: Clientes

| # | Paso | Resultado esperado |
|---|---|---|
| 2.1 | Menú **Clientes** → **Nuevo cliente** → Persona, DNI `41234567`, nombres `Ana María`, apellido paterno `Quispe` → Guardar | Aparece en la tabla como “Ana María Quispe”, origen “Manual” |
| 2.2 | Repetir con el mismo DNI | Mensaje: “Ya existe un cliente con DNI 41234567…” |
| 2.3 | Nuevo cliente con DNI `123` | Mensaje: “El DNI debe tener 8 dígitos.” |
| 2.4 | Nuevo cliente → **Empresa**, RUC `20123456789`, razón social `Inversiones Andinas S.A.C.` | Se guarda; el selector de documento queda fijo en RUC |
| 2.5 | Escribir `andinas` en el buscador | Solo aparece la empresa |
| 2.6 | Clic en una fila → cambiar la dirección → Guardar | La tabla muestra la dirección nueva |
| 2.7 | En pgAdmin: `estudio_documental` → Tables → `cliente` → View Data | Las mismas filas, con el número sin espacios ni guiones |

## Bloque 3: Variables (solo administrador)

| # | Paso | Resultado esperado |
|---|---|---|
| 3.1 | Menú **Variables** | Lista con `{{fecha}}`, `{{lugar}}`, `{{monto}}`, `{{motivo}}`, `{{plazo}}` |
| 3.2 | **Nueva variable**: clave `numero_partida`, etiqueta `N.º de partida`, tipo Texto | Aparece como `{{numero_partida}}` |
| 3.3 | Abrir `{{fecha}}` | El campo Clave está bloqueado; se puede cambiar la etiqueta |
| 3.4 | Nueva variable con clave `1-mala` | Mensaje de clave no válida |

## Bloque 4: Plantillas (solo administrador)

| # | Paso | Resultado esperado |
|---|---|---|
| 4.1 | Menú **Plantillas** → **Subir plantilla** → elegir `docs/ejemplos/carta-poder-simple.docx`, categoría `Poderes` → Subir | Abre el detalle: participantes **Otorgante** y **Apoderado**; variables Motivo, Monto, Lugar, Fecha |
| 4.2 | Cambiar “Otorgante” por `Poderdante`, “Puede ser” = Persona → **Guardar cambios** | “Cambios guardados.” |
| 4.3 | Descargar la versión 1 (ícono ⬇) | Se descarga el Word original |
| 4.4 | Abrir el Word en Microsoft Word, escribir en algún lugar `{{otorgante.dni}}` → guardar → **Subir nueva versión** | Error: “'dni' no es un dato de cliente (use: …)”. La plantilla sigue en v1 |
| 4.5 | Cambiar por `{{otorgante.numero_documento}}` → subir de nuevo | Pasa a **v2**; el participante sigue llamándose “Poderdante” |
| 4.6 | Crear su propia plantilla en Word con la guía de marcas de la pantalla y subirla | Detecta sus participantes y variables |

## Bloque 5: Generar documentos e historial

| # | Paso | Resultado esperado |
|---|---|---|
| 5.1 | Menú **Nuevo documento** → elegir “Carta poder simple” | Formulario con un bloque por participante y “Datos del documento” |
| 5.2 | Poderdante: DNI `41234567` → **Buscar** | “Datos cargados desde Clientes”; nombres y apellidos se completan |
| 5.3 | Apoderado: DNI `47000001` → Buscar | “No está registrado y no hay credenciales…”; completar nombres y apellido paterno a mano |
| 5.4 | Motivo, Monto `1250.50`, Lugar `Cusco` → **Generar documento** | “Documento n.º X generado”, botón **Word** y aviso de que falta LibreOffice para el PDF |
| 5.5 | Descargar el Word y abrirlo | Montos “S/ 1,250.50” y “MIL DOSCIENTOS CINCUENTA CON 50/100 SOLES”, fecha larga, nombre en mayúsculas, sin ninguna `{{…}}` |
| 5.6 | Menú **Clientes** | Aparece el apoderado nuevo (se guardó solo) |
| 5.7 | Generar sin completar Lugar | Mensaje: “Complete «Lugar».” |
| 5.8 | Menú **Historial** → buscar `47000001` | Aparece el documento; **Usar de nuevo** abre el formulario ya lleno |
| 5.9 | Menú **Inicio** | “Últimos documentos” muestra el generado |
| 5.10 | *(Opcional)* Instalar LibreOffice (libreoffice.org), reiniciar el backend y generar otro | Ahora aparecen **Ver PDF** y **Descargar PDF**, idénticos al Word |

## Bloque 6: Consultas DNI/RUC con APIs gratuitas (solo administrador configura)

**Antes:** cree una cuenta gratis en https://decolecta.com/profile (100 consultas al mes) y copie su token. Si quiere probar el cambio automático entre APIs, cree también una cuenta gratis en https://apisperu.com.

| # | Paso | Resultado esperado |
|---|---|---|
| 6.1 | Menú **Configuración** | Estado del PDF y “Consultas a proveedores este mes: 0” |
| 6.2 | **Agregar credencial** → Proveedor **Decolecta** (viene elegido), pegar el token → Guardar | Aparece con prioridad 1, token `…abcd`, uso “0 de 100” |
| 6.3 | Nuevo documento → DNI de una persona real que **no** esté en Clientes → Buscar | “Datos obtenidos de Decolecta y guardados en Clientes”; en Configuración el uso pasa a “1 de 100” |
| 6.4 | Buscar el mismo DNI otra vez | “Datos cargados desde Clientes (sin costo)”; el uso no sube |
| 6.5 | Pulsar **Consultar de nuevo** | Vuelve a preguntar a Decolecta (uso +1) |
| 6.6 | Buscar un RUC real (por ejemplo, de una empresa conocida) | Se completa la razón social y la dirección fiscal |
| 6.7 | *(Cambio automático)* Agregar APIsPERU con prioridad 2. Editar Decolecta y poner “Consultas gratis al mes” = el uso actual (por ejemplo 2). Buscar otro DNI | “Datos obtenidos de APIsPERU…”: Decolecta se saltó porque llegó a su límite |
| 6.8 | Volver a poner 100 en Decolecta | Decolecta vuelve a usarse primero |
| 6.9 | Editar una credencial y pegar un token falso → buscar un DNI nuevo | Pasa a la siguiente; si no hay otra: “El proveedor rechazó el token…” |
| 6.10 | En pgAdmin: tabla `credencial_api` | El token está cifrado (texto ilegible), nunca en claro |
| 6.11 | Menú **Clientes** → Nuevo cliente → DNI real no registrado → **Buscar** | Se completan nombres y apellidos y el formulario pasa a “Editar cliente”; agregar teléfono y Guardar |

## Bloque 7: Usuarios

| # | Paso | Resultado esperado |
|---|---|---|
| 7.1 | Menú **Usuarios** → **Nuevo usuario** → *Usuario para ingresar*: `operador1` · *Nombre completo*: `Operador de Prueba` · *Rol*: Operador · *Contraseña inicial*: `operador-123` | Aparece activo, con `operador1` en la columna Usuario |
| 7.2 | Cerrar sesión e ingresar con **usuario** `operador1` (no el nombre completo) y contraseña `operador-123` | Entra; el menú **no** muestra Administración |
| 7.3 | Ingresar 5 veces con contraseña equivocada y luego con la correcta | “Usuario bloqueado por varios intentos fallidos…” |
| 7.4 | Como `admin`: Usuarios → `operador1` aparece “Bloqueado” → Restablecer contraseña | Puede volver a ingresar con la contraseña nueva |
| 7.5 | Ícono 🔑 (barra superior) → cambiar la propia contraseña | “Contraseña cambiada.” |
| 7.6 | Abrir su propio usuario admin | No permite quitarse el rol ni desactivarse |

## Bloque 8: Auditoría (solo administrador)

| # | Paso | Resultado esperado |
|---|---|---|
| 8.1 | Hacer algunas acciones: ingresar con contraseña equivocada, crear un cliente, generar y descargar un documento | — |
| 8.2 | Menú **Auditoría** | Aparecen, de la más reciente a la más antigua: “Descargó un documento”, “Generó un documento”, “Registró un cliente”, “Contraseña incorrecta”, con usuario y fecha |
| 8.3 | Buscar el DNI del cliente o el n.º del documento | Solo los registros relacionados |
| 8.4 | Ingresar como operador | No ve el menú Auditoría |

---

## Si algo falla

Anote el número del paso y copie el mensaje que aparece (o el error de la terminal del backend). Ver también [DESARROLLO.md](DESARROLLO.md), sección “Problemas comunes”.
