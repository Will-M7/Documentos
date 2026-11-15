import { InputAdornment, TextField } from '@mui/material'
import type { VariablePlantilla } from '../../plantillas/api'

/** Campo del formulario según el tipo de la variable. */
export function CampoVariable({
  variable,
  valor,
  onCambiar,
}: {
  variable: VariablePlantilla
  valor: string
  onCambiar: (valor: string) => void
}) {
  const comun = {
    fullWidth: true,
    size: 'small' as const,
    label: variable.etiqueta,
    value: valor,
    helperText: variable.descripcion ?? `En Word: {{${variable.clave}}}`,
    onChange: (e: React.ChangeEvent<HTMLInputElement>) => onCambiar(e.target.value),
  }
  switch (variable.tipo) {
    case 'FECHA':
      return <TextField {...comun} type="date" slotProps={{ inputLabel: { shrink: true } }} />
    case 'NUMERO':
      return <TextField {...comun} type="number" />
    case 'MONEDA':
      return (
        <TextField
          {...comun}
          type="number"
          slotProps={{
            input: { startAdornment: <InputAdornment position="start">S/</InputAdornment> },
            htmlInput: { step: '0.01', min: 0 },
          }}
        />
      )
    case 'TEXTO_LARGO':
      return <TextField {...comun} multiline minRows={3} />
    default:
      return <TextField {...comun} />
  }
}
