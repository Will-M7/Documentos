# Guía de pruebas pendientes (ronda 2)

Cada bloque se prueba por separado, en orden. Solo incluye lo que falta: lo aprobado en la ronda 1 (bloques 2, 3, 4 y 5, y los pasos 7.1, 7.5 y 7.6) no se vuelve a probar. Los números de paso son los mismos de la guía completa.

## Antes de empezar

1. Reiniciar el backend (cerrar con Ctrl+C y volver a abrir) para que aplique las migraciones nuevas V010 y V011:

   ```bash
   cd backend
   mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=local
   ```

2. Web (otra terminal):

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

3. Abrir http://localhost:5173 e ingresar con `admin` / `admin-local-123`.
4. Para el bloque 6: crear una cuenta gratis en https://decolecta.com/profile (100 consultas al mes) y copiar el token. Opcional: otra cuenta gratis en https://apisperu.com para probar el cambio automático.

---

## Bloque 6: Consultas DNI/RUC con APIs gratuitas

| # | Paso | Resultado esperado |
|---|---|---|
| 6.1 | Menú **Configuración** | Estado del PDF y “Consultas a proveedores este mes: 0” |
| 6.2 | **Agregar credencial** → Proveedor **Decolecta** (viene elegido), pegar el token → Guardar | Aparece con prioridad 1, token `…abcd`, uso “0 de 100” |
| 6.3 | Nuevo documento → DNI de una persona real que **no** esté en Clientes → Buscar | “Datos obtenidos de Decolecta y guardados en Clientes”; en Configuración el uso pasa a “1 de 100” |
| 6.4 | Buscar el mismo DNI otra vez | “Datos cargados desde Clientes (sin costo)”; el uso no sube |
| 6.5 | Pulsar **Consultar de nuevo** | Vuelve a preguntar a Decolecta (uso +1) |
| 6.6 | Buscar un RUC real (por ejemplo, de una empresa conocida) | Se completa la razón social y la dirección fiscal |
| 6.7 | *(Cambio automático, opcional)* Agregar APIsPERU con prioridad 2. Editar Decolecta y poner “Consultas gratis al mes” = el uso actual (por ejemplo 3). Buscar otro DNI | “Datos obtenidos de APIsPERU…”: Decolecta se saltó porque llegó a su límite |
| 6.8 | Volver a poner 100 en Decolecta | Decolecta vuelve a usarse primero |
| 6.9 | Editar una credencial y pegar un token falso → buscar un DNI nuevo | Pasa a la siguiente; si no hay otra: “El proveedor rechazó el token…” |
| 6.10 | En pgAdmin: tabla `credencial_api` | El token está cifrado (texto ilegible), nunca en claro |
| 6.11 | Menú **Clientes** → Nuevo cliente → DNI real no registrado → **Buscar** | Se completan nombres y apellidos y el formulario pasa a “Editar cliente”; agregar teléfono y Guardar |

## Bloque 7: Usuarios (pasos que faltaron)

El usuario `operador1` ya existe. Para ingresar se usa el **usuario** `operador1`, no el nombre completo “Operador de Prueba”. Si no recuerda su contraseña, como admin: Usuarios → `operador1` → Restablecer contraseña → `operador-123`.

| # | Paso | Resultado esperado |
|---|---|---|
| 7.2 | Cerrar sesión e ingresar con usuario `operador1` y contraseña `operador-123` | Entra; el menú **no** muestra Administración |
| 7.3 | Cerrar sesión. Ingresar 5 veces con usuario `operador1` y contraseña equivocada; luego con la correcta | “Usuario bloqueado por varios intentos fallidos…” |
| 7.4 | Como `admin`: Usuarios → `operador1` aparece “Bloqueado” → Restablecer contraseña | `operador1` puede volver a ingresar con la contraseña nueva |

## Bloque 8: Auditoría (solo administrador)

| # | Paso | Resultado esperado |
|---|---|---|
| 8.1 | Hacer algunas acciones: ingresar con contraseña equivocada, crear un cliente, generar y descargar un documento | — |
| 8.2 | Menú **Auditoría** | Aparecen, de la más reciente a la más antigua: “Descargó un documento”, “Generó un documento”, “Registró un cliente”, “Contraseña incorrecta”, con usuario y fecha |
| 8.3 | Buscar el DNI del cliente o el n.º del documento | Solo los registros relacionados |
| 8.4 | Ingresar como `operador1` | No ve el menú Auditoría |

## Bloque 12: Sesión que se renueva sola y límite de consultas

| # | Paso | Resultado esperado |
|---|---|---|
| 12.1 | Ingresar y dejar la página abierta sin usarla más de 15 minutos; luego ir a **Clientes** | La lista carga sin pedir contraseña (la sesión se renovó sola) |
| 12.2 | Cerrar la pestaña, volver a abrir http://localhost:5173 (dentro de las 12 horas) | Sigue con la sesión iniciada |
| 12.3 | **Cerrar sesión** (ícono de salida) y luego pulsar “Atrás” en el navegador | Vuelve a la pantalla de ingreso; no muestra datos |
| 12.4 | *(Opcional, gasta consultas)* Hacer más de 10 consultas a proveedores en un minuto con **Consultar de nuevo** | “Se alcanzó el límite de 10 consultas por minuto. Espere … segundos o complete los datos a mano.” |

## Bloque 14: Vista previa antes de generar

| # | Paso | Resultado esperado |
|---|---|---|
| 14.1 | Nuevo documento → completar todo → **Vista previa** | Se abre una ventana con el Word tal como quedará (montos en letras, fecha larga, sin `{{…}}`) |
| 14.2 | Cerrar con **Volver a editar** → menú **Historial** | El documento **no** aparece: la vista previa no gasta número ni guarda nada |
| 14.3 | Usar un DNI que no esté en Clientes → Vista previa → ir a **Clientes** | Esa persona **no** se registró todavía |
| 14.4 | Vista previa sin completar Lugar | Mensaje “Complete «Lugar».” (mismas validaciones que al generar) |
| 14.5 | Vista previa → **Generar documento** desde la ventana | “Documento n.º X generado”; ahora sí aparece en Historial y la persona en Clientes |

## Opcional: PDF

| # | Paso | Resultado esperado |
|---|---|---|
| 5.10 | Instalar LibreOffice (libreoffice.org), reiniciar el backend y generar un documento | Aparecen **Ver PDF** y **Descargar PDF**, idénticos al Word |

---

## Si algo falla

Anote el número del paso y copie el mensaje que aparece (o el error de la terminal del backend).
