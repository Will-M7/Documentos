import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  MenuItem,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
} from '@mui/material'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import {
  actualizarCliente,
  crearCliente,
  ETIQUETA_DOCUMENTO,
  type Cliente,
  type DatosCliente,
  type TipoDocumento,
} from '../api'

const VACIO: DatosCliente = {
  tipo: 'PERSONA',
  tipoDocumento: 'DNI',
  numeroDocumento: '',
  nombres: '',
  apellidoPaterno: '',
  apellidoMaterno: '',
  razonSocial: '',
  direccion: '',
  telefono: '',
  correo: '',
}

const DOCUMENTOS_PERSONA: TipoDocumento[] = ['DNI', 'CE', 'PASAPORTE', 'RUC']

/** Crear (sin `cliente`) o editar (con `cliente`) un cliente. Las reglas las valida el backend. */
export function ClienteFormDialog({
  abierto,
  cliente,
  onCerrar,
}: {
  abierto: boolean
  cliente: Cliente | null
  onCerrar: () => void
}) {
  const queryClient = useQueryClient()
  const { control, register, handleSubmit, reset, setValue } = useForm<DatosCliente>({
    defaultValues: VACIO,
  })
  const tipo = useWatch({ control, name: 'tipo' })

  const guardar = useMutation({
    mutationFn: (datos: DatosCliente) =>
      cliente ? actualizarCliente(cliente.id, datos) : crearCliente(datos),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clientes'] })
      onCerrar()
    },
  })

  const { reset: limpiarError } = guardar
  useEffect(() => {
    if (abierto) {
      reset(cliente ? { ...VACIO, ...sinNulos(cliente) } : VACIO)
      limpiarError()
    }
  }, [abierto, cliente, reset, limpiarError])

  return (
    <Dialog open={abierto} onClose={onCerrar} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit((datos) => guardar.mutate(datos))} noValidate>
        <DialogTitle>{cliente ? 'Editar cliente' : 'Nuevo cliente'}</DialogTitle>
        <DialogContent>
          {guardar.error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {guardar.error.message}
            </Alert>
          )}
          <Grid container spacing={2} sx={{ pt: 1 }}>
            <Grid size={12}>
              <Controller
                control={control}
                name="tipo"
                render={({ field }) => (
                  <ToggleButtonGroup
                    exclusive
                    color="primary"
                    value={field.value}
                    onChange={(_, valor) => {
                      if (!valor) return
                      field.onChange(valor)
                      if (valor === 'EMPRESA') setValue('tipoDocumento', 'RUC')
                    }}
                  >
                    <ToggleButton value="PERSONA">Persona</ToggleButton>
                    <ToggleButton value="EMPRESA">Empresa</ToggleButton>
                  </ToggleButtonGroup>
                )}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              <Controller
                control={control}
                name="tipoDocumento"
                render={({ field }) => (
                  <TextField select fullWidth label="Documento" {...field} disabled={tipo === 'EMPRESA'}>
                    {(tipo === 'EMPRESA' ? (['RUC'] as TipoDocumento[]) : DOCUMENTOS_PERSONA).map((t) => (
                      <MenuItem key={t} value={t}>
                        {ETIQUETA_DOCUMENTO[t]}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 7 }}>
              <TextField fullWidth required label="Número" {...register('numeroDocumento')} />
            </Grid>
            {tipo === 'EMPRESA' ? (
              <Grid size={12}>
                <TextField fullWidth required label="Razón social" {...register('razonSocial')} />
              </Grid>
            ) : (
              <>
                <Grid size={12}>
                  <TextField fullWidth required label="Nombres" {...register('nombres')} />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField fullWidth required label="Apellido paterno" {...register('apellidoPaterno')} />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField fullWidth label="Apellido materno" {...register('apellidoMaterno')} />
                </Grid>
              </>
            )}
            <Grid size={12}>
              <TextField
                fullWidth
                label={tipo === 'EMPRESA' ? 'Domicilio fiscal' : 'Dirección'}
                {...register('direccion')}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              <TextField fullWidth label="Teléfono" {...register('telefono')} />
            </Grid>
            <Grid size={{ xs: 12, sm: 7 }}>
              <TextField fullWidth type="email" label="Correo" {...register('correo')} />
            </Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={onCerrar}>Cancelar</Button>
          <Button type="submit" variant="contained" loading={guardar.isPending}>
            Guardar
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}

function sinNulos(cliente: Cliente): DatosCliente {
  return Object.fromEntries(
    Object.entries(cliente).map(([clave, valor]) => [clave, valor ?? '']),
  ) as unknown as DatosCliente
}
