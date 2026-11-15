package pe.estudiodocumental.identidad.dominio;

/** Roles del sistema. Ver docs/ARQUITECTURA.md, sección 2. */
public enum Rol {
    /** Dueño o encargado: todo lo del operador más usuarios, plantillas y configuración. */
    ADMIN,
    /** Personal de atención: clientes y generación de documentos. */
    OPERADOR
}
