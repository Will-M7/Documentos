# CONTEXTO MAESTRO — Estudio Documental v1.0.0

## 1. Stack Tecnológico y Versiones
- **Tipo:** Aplicación de escritorio nativa 100% Offline-First (`com.ejemplo:estudio-documental:1.0.0`) para gestión, edición WYSIWYG y generación de documentos Word (`.docx`) y PDF.
- **Lenguaje y Runtime:** Java 17+ (`records`, `switch expressions`, `pattern matching`). Host: Ubuntu 26.04.1 LTS (OpenJDK 25.0.5); empaquetado Windows (`package-windows.cmd`) con JDK 21 LTS + `jpackage`.
- **Build:** Apache Maven 3.9.16 (`./mvnw`). `spring-boot-starter-parent:3.3.4` gestiona versiones (sin levantar Spring en runtime).
- **UI:** JavaFX 21.0.4 (`javafx-controls`, `javafx-web`) + **AtlantaFX 2.0.1** (`PrimerLight` / `PrimerDark`) + **Ikonli 12.3.1** (`ikonli-feather-pack`).
- **Persistencia:** SQLite 3.45.3.0 (`sqlite-jdbc`) vía JDBC directo sin ORM en `~/.document-studio/studio.db` (Linux) o `%APPDATA%\DocumentStudio\studio.db` (Windows).
- **Documentos y JSON:** Apache POI 5.2.5 (`poi-ooxml`), OpenPDF 1.3.43, Jackson 2.19.4, JUnit 5 + AssertJ.

## 2. Estructura del Proyecto
```text
/home/willian/Desarrollo/Documentos/
├── pom.xml / mvnw / package-windows.cmd / CONTEXTO.md
└── src/
    ├── main/
    │   ├── java/com/ejemplo/crudpersona/
    │   │   ├── Launcher.java                   # Entrada main()
    │   │   ├── JavaFxApplication.java          # Stage principal (1280x800) y hojas de estilo
    │   │   └── studio/
    │   │       ├── StudioWindow.java           # Controlador maestro UI (8 vistas)
    │   │       ├── DocumentStore.java          # SQLite: template, template_group, variable, kind
    │   │       ├── StudioData.java             # SQLite: contact, revision, history, bindings
    │   │       ├── DocumentEngine.java         # POI: detección y sustitución de {{tokens}}
    │   │       ├── ContinuousDocxEditor.java   # Bridge HTML (WebView) <-> OOXML DOCX
    │   │       ├── DocxPdfExporter.java        # Exportador DOCX -> PDF (OpenPDF)
    │   │       ├── VariableInput.java          # Validación y normalización tipada
    │   │       ├── ContactReuse.java           # Deduplicación y merge automático de clientes
    │   │       ├── StudioTransfer.java         # Exportación/Importación ZIP (plantillas y clientes)
    │   │       ├── ApiCredentialStore.java     # Metadatos y uso mensual de credenciales API
    │   │       ├── LocalSecret.java            # Cifrado de tokens fuera de BD (AES-GCM / DPAPI)
    │   │       ├── LookupRouter.java           # Enrutador DNI/RUC con failover (Factiliza / APIsPERU)
    │   │       ├── FactilizaClient.java / FactilizaProvider.java / FactilizaFields.java
    │   │       ├── ApisPeruProvider.java / LookupProvider.java
    │   │       └── ApiFieldCatalog.java        # Catálogo neutral de campos DNI/RUC
    │   └── resources/
    │       ├── css/app.css & studio.css        # Temas AtlantaFX Primer y estilos de módulos
    │       └── editor/document-editor.html     # Editor WYSIWYG contentEditable
    └── test/java/com/ejemplo/crudpersona/studio/
        ├── ContinuousDocxEditorTest.java / DocumentWorkflowTest.java
        ├── LocalModulesTest.java / MultiApiWorkflowTest.java / TemplateCatalogUiTest.java
        └── ContactReuseTest.java / StudioTransferTest.java
```

## 3. Reglas de Arquitectura
1. **Offline-First & Cache-First:** Toda consulta de DNI/RUC busca primero en la tabla local `contact` de SQLite antes de llamar a `LookupRouter`. Toda consulta API exitosa o ingreso manual de cliente se guarda/actualiza automáticamente en `contact` sin pedir confirmación.
2. **Conexiones SQLite Efímeras:** Cada método abre y cierra su conexión JDBC con `PRAGMA foreign_keys=ON` dentro de `try-with-resources`.
3. **Aislamiento de Secretos:** Los tokens API nunca se incluyen en SQLite ni en exportaciones ZIP; viven cifrados en `credential-<id>.secret`.
4. **Identidad Visual:** Conservar estrictamente la paleta de colores AtlantaFX (`PrimerLight` / `PrimerDark`) y las animaciones del menú lateral (`moveIndicator`, `collapseMenu`).

## 4. Estado implementado (13 de noviembre de 2026)

- **Plantillas:** `GRUPOS` contiene exclusivamente `DocumentStore.groups()`. Se conserva el grupo seleccionado o se selecciona el primero disponible; el catálogo filtra por pertenencia real. Sin grupos muestra una invitación a crear uno. Crear/importar plantillas y añadir plantillas existentes (incluidas las todavía no asignadas) siguen disponibles; las nuevas se asocian al grupo seleccionado.
- **Configuración y respaldos:** la interfaz tiene únicamente tres tarjetas: **Importar**, **Exportar Plantillas** y **Exportar Clientes**. La resolución de duplicados se elige en la misma vista (`Conservar existentes` / `Reemplazar existentes`). La exportación de plantillas ofrece exactamente los alcances individual, grupo completo y todas.
- **Transferencia:** `StudioTransfer.exportConfiguration(Path, Collection<Long>)` filtra el grafo relacionado con las plantillas seleccionadas. Incluye grupos, variables explícitas y definiciones usadas por marcadores OOXML, vínculos API, asociaciones de clientes, contextos de consulta, correspondencias y tipos documentales con sus reglas. Mantiene los bytes DOCX originales y editados. Las definiciones ajenas al alcance quedan fuera.
- **Formatos ZIP:** `configuration.json` conserva el formato `document-studio-config`, versión 6 (importación compatible con versiones 1–6). `clients.json` usa `document-studio-clients`, versión 1, e incluye toda la tabla `contact`, con `extrasJson`. Un ZIP puede contener cualquiera de las dos secciones o ambas; se validan los clientes antes de aplicar la configuración. Se rechazan entradas desconocidas, duplicadas y paquetes de más de 200 MB descomprimidos.
- **Clientes:** identidad por `kind` + `documentType` (normalizado) + `number` (sin espacios exteriores), sin búsqueda alternativa por número que mezcle tipos documentales. La importación conserva o reemplaza coincidencias según la selección; reimportar no crea duplicados. El guardado automático fusiona campos no vacíos y datos adicionales, preservando valores ausentes en respuestas parciales.
- **API y generación:** toda respuesta utilizable de `runApiQuery` se guarda antes de aplicar los datos al formulario; la consulta rápida muestra **✓ Guardado automáticamente en Clientes**. `lookupForField` valida el identificador, consulta primero SQLite y recupera campos base más los valores canónicos y rutas de `extrasJson`, sin consumir API si encuentra el contacto. El estado local es **Datos cargados desde Clientes locales**. Una respuesta tardía no rellena otro formulario ni un identificador que haya cambiado.
- **Ingreso manual sin conexión:** generar la vista previa y guardar DOCX/PDF guardan automáticamente clientes con identificador válido y nombre o dirección. `ContactReuse.formBindings` combina asociaciones explícitas, contextos API, vinculaciones de variables y tipos DNI/RUC; los campos manuales reconocibles se asocian únicamente cuando el grupo es inequívoco. No hay callbacks ni diálogos de confirmación para fusionar conflictos. Los grupos independientes no intercambian datos.
- **Secretos:** ni los ZIP ni los nuevos respaldos cifrados incluyen tokens. La API de respaldo de base de datos se conserva para compatibilidad, fuera de la vista de configuración; los respaldos antiguos DSBK2 se pueden leer sin restaurar sus secretos.
- **Interfaz:** menú expandido de 242 px con las animaciones existentes; descripciones por sección; botones con clases `primary-btn`, `secondary-btn` y `danger-btn` que conservan `.button`. Todos los colores usan variables semánticas AtlantaFX. Generador 56/44 con formulario de dos columnas y acciones inferiores alineadas; Variables 340 px, Plantillas 240/centro/270 px, Historial y API 380 px. Clientes mantiene Guardar cliente fuera del área desplazable. Las ocho vistas se revisaron mediante capturas JavaFX con PrimerLight y PrimerDark.

## 5. Verificación

- `./mvnw test`: **30 pruebas, 0 fallos, 0 errores, 0 omitidas**, con JDK 21 y compilación para Java 17.
- Cobertura en `TemplateCatalogUiTest`, `StudioTransferTest`, `ContactReuseTest`, `LocalModulesTest`, `MultiApiWorkflowTest`, `DocumentWorkflowTest` y `ContinuousDocxEditorTest`: grupos reales/estado vacío, selectores de exportación, estilos de ambos temas, reutilización local sin llamadas API, guardado manual y de resultados API, identidades independientes, respuestas parciales, exportación filtrada/autocontenida, ZIP combinado y preservación OOXML.
- Las pruebas JavaFX necesitan acceso a una pantalla gráfica y usan una caché nativa en el directorio temporal. Para revisar capturas opcionales: `./mvnw test -DargLine="-Dstudio.ui.snapshots=/tmp/documentos-ui-review"`.
