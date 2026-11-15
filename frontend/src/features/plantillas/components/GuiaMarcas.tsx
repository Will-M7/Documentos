import { Alert, AlertTitle, Box, Typography } from '@mui/material'

/** Explica cómo escribir las marcas en Word. */
export function GuiaMarcas() {
  return (
    <Alert severity="info" sx={{ mb: 3 }}>
      <AlertTitle>Cómo preparar la plantilla en Word</AlertTitle>
      <Typography variant="body2" component="div">
        Escriba entre llaves dobles lo que se debe completar:
        <Box component="ul" sx={{ my: 1, pl: 3 }}>
          <li>
            <code>{'{{fecha}}'}</code>, <code>{'{{lugar}}'}</code>, <code>{'{{monto}}'}</code>: datos del
            documento (catálogo de Variables).
          </li>
          <li>
            <code>{'{{otorgante.nombres}}'}</code>, <code>{'{{apoderado.documento}}'}</code>: datos de una
            persona o empresa, que se llenan desde Clientes. Campos: nombres, apellidos, apellido_paterno,
            apellido_materno, nombre_completo, razon_social, documento, tipo_documento, numero_documento,
            direccion, telefono, correo.
          </li>
          <li>
            Formatos opcionales: <code>{'{{monto | letras}}'}</code> (en palabras),{' '}
            <code>{'{{monto | moneda}}'}</code>, <code>{'{{fecha | fecha_larga}}'}</code>,{' '}
            <code>{'{{otorgante.nombre_completo | mayusculas}}'}</code>, minusculas, titulo.
          </li>
        </Box>
        El formato del Word (fuente, negrita, tablas, encabezados) se conserva tal cual.
      </Typography>
    </Alert>
  )
}
