# Guía de pruebas pendientes (ronda 2)

Cada bloque se prueba por separado, en orden. Solo incluye lo que falta: lo aprobado en la ronda 1 (bloques 2, 3, 4 y 5, y los pasos 7.1, 7.5 y 7.6) no se vuelve a probar. Los números de paso son los mismos de la guía completa.

## Antes de empezar

1. Reiniciar el backend (cerrar con Ctrl+C y volver a abrir) para que aplique las migraciones nuevas V010, V011 y V012:

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

## Bloque 15: Documentos del cliente y baja

| # | Paso | Resultado esperado |
|---|---|---|
| 15.1 | **Clientes** → abrir (editar) a una persona que ya tenga documentos | Aparece “Documentos de este cliente (N)” con la lista “N.º X · título”, la fecha y su papel (por ejemplo “como Poderdante”) |
| 15.2 | Usar los botones de descarga junto a uno de esos documentos | Se descarga ese documento |
| 15.3 | Abrir a una persona sin documentos | “Todavía no participa en ningún documento.” |
| 15.4 | Como **admin**: botón **Dar de baja** → Aceptar | La persona deja de aparecer en Clientes; en **Historial** sus documentos siguen ahí |
| 15.5 | Ingresar como **operador** y abrir un cliente | **No** aparece el botón Dar de baja |

## Bloque 16: Panel de Inicio con números del negocio

| # | Paso | Resultado esperado |
|---|---|---|
| 16.1 | Menú **Inicio** | Cuadros: Documentos hoy, Documentos este mes, Clientes registrados y Consultas DNI/RUC este mes |
| 16.2 | Generar un documento nuevo y volver a **Inicio** | “Documentos hoy” y “Documentos este mes” suben en 1 |
| 16.3 | Mirar el gráfico **Documentos por mes** y pasar el mouse por una barra | Se ven los últimos 6 meses; al pasar el mouse aparece la cantidad |
| 16.4 | Mirar **Plantillas más usadas este mes** | Aparecen las plantillas usadas, de la más usada a la menos |

## Bloque 17: Portal de clientes (pedidos por internet)

| # | Paso | Resultado esperado |
|---|---|---|
| 17.1 | **Plantillas** → abrir una → activar **En el portal** → **Guardar cambios** | El interruptor queda en “En el portal: los clientes pueden pedirla desde internet” |
| 17.2 | Menú **Solicitudes** | Aparece el enlace del portal (http://localhost:5173/portal) y el botón **Copiar enlace** |
| 17.3 | Abrir ese enlace en una ventana **de incógnito** (sin iniciar sesión) | Se ve “¿Qué documento necesita?” con solo las plantillas marcadas en 17.1; no hay menú de la oficina |
| 17.4 | Elegir el documento → completar datos de la persona y del documento → “Su nombre” | No hay botón **Buscar** (el cliente no gasta consultas) |
| 17.5 | Intentar enviar sin marcar la casilla de la Ley 29733 | El botón **Enviar solicitud** está desactivado |
| 17.6 | Marcar la casilla → **Enviar solicitud** | “Solicitud enviada” con un código tipo **S-XXXXXX**. Anotarlo |
| 17.7 | Enviar otra solicitud dejando vacío un dato obligatorio (por ejemplo Lugar) | Mensaje que indica qué falta completar; no se envía |
| 17.8 | **Consultar estado** (o “Consultar mi solicitud”) con el código | Estado **Pendiente** y el nombre del documento; no muestra datos personales |
| 17.9 | Volver a la oficina (ventana normal) | El menú **Solicitudes** muestra un número rojo con las pendientes (puede tardar hasta 1 minuto o recargar la página) |
| 17.10 | **Solicitudes** → pestaña Pendientes → **Atender** | Se abre Nuevo documento con un aviso azul “Atendiendo la solicitud S-…” y los datos del cliente ya completados |
| 17.11 | Revisar los datos → **Generar documento** | Se genera; en **Solicitudes** pasa a la pestaña **Atendidas** y el número rojo baja |
| 17.12 | En la ventana de incógnito, consultar de nuevo el código | Estado **Atendida** |
| 17.13 | Enviar otra solicitud → en la oficina **Rechazar** con un motivo | Pasa a **Rechazadas**; al consultar el código el cliente ve el motivo |
| 17.14 | En el portal, consultar un código inventado (S-ZZZZZZ) | “No existe una solicitud con ese código.” |
| 17.15 | Seguir enviando solicitudes seguidas (más de 5 en 10 minutos) | Aparece “Se enviaron demasiadas solicitudes desde esta conexión. Intente de nuevo en unos minutos.” (se libera solo a los 10 minutos) |
| 17.16 | Desactivar **En el portal** en la plantilla y recargar el portal | Esa plantilla ya no aparece para los clientes |

## Opcional: PDF

| # | Paso | Resultado esperado |
|---|---|---|
| 5.10 | Instalar LibreOffice (libreoffice.org), reiniciar el backend y generar un documento | Aparecen **Ver PDF** y **Descargar PDF**, idénticos al Word |

---

## Si algo falla

Anote el número del paso y copie el mensaje que aparece (o el error de la terminal del backend).
