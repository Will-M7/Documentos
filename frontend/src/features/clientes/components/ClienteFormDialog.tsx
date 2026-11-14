import Search from '@mui/icons-material/Search'
import {
  Alert,
  Box,
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
import { useEffect, useState } from 'react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import {
  actualizarCliente,
  buscarPorDocumento,
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
  const tipoDocumento = useWatch({ control, name: 'tipoDocumento' })
  const numeroDocumento = useWatch({ control, name: 'numeroDocumento' })
  // Si al buscar el documento resulta que ya existe, el formulario pasa a editar ese cliente
  const [encontrado, setEncontrado] = useState<Cliente | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const actual = cliente ?? encontrado

  const guardar = useMutation({
    mutationFn: (datos: DatosCliente) => (actual ? actualizarCliente(actual.id, datos) : crearCliente(datos)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['clientes'] })
      cerrar()
    },
  })

  const buscar = useMutation({
    mutationFn: () => buscarPorDocumento(tipoDocumento, numeroDocumento),
    onSuccess: ({ cliente: hallado, mensaje }) => {
      setAviso(mensaje)
      if (hallado) {
        setEncontrado(hallado)
        reset({ ...VACIO, ...sinNulos(hallado) })
        queryClient.invalidateQueries({ queryKey: ['clientes'] })
      }
    },
  })

  const { reset: limpiarError } = guardar
  const { reset: limpiarBusqueda } = buscar
  useEffect(() => {
    if (abierto) {
      reset(cliente ? { ...VACIO, ...sinNulos(cliente) } : VACIO)
      limpiarError()
      limpiarBusqueda()
    }
  }, [abierto, cliente, reset, limpiarError, limpiarBusqueda])

  const cerrar = () => {
    setEncontrado(null)
    setAviso(null)
    onCerrar()
  }

  return (
    <Dialog open={abierto} onClose={cerrar} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit((datos) => guardar.mutate(datos))} noValidate>
        <DialogTitle>{actual ? 'Editar cliente' : 'Nuevo cliente'}</DialogTitle>
        <DialogContent>
          {(aviso || buscar.error) && (
            <Alert severity={buscar.error ? 'error' : encontrado ? 'success' : 'info'} sx={{ mb: 2 }}>
              {buscar.error ? buscar.error.message : aviso}
            </Alert>
          )}
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
              <Box sx={{ display: 'flex', gap: 1 }}>
                <TextField fullWidth required label="Número" {...register('numeroDocumento')} />
                {!cliente && (tipoDocumento === 'DNI' || tipoDocumento === 'RUC') && (
                  <Button
                    variant="outlined"
                    startIcon={<Search />}
                    loading={buscar.isPending}
                    disabled={!numeroDocumento?.trim()}
                    onClick={() => buscar.mutate()}
                  >
                    Buscar
                  </Button>
                )}
              </Box>
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
          <Button onClick={cerrar}>Cancelar</Button>
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
