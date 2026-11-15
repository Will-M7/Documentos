import Add from '@mui/icons-material/Add'
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import {
  crearVariable,
  editarVariable,
  ETIQUETA_TIPO,
  listarVariables,
  type DatosVariable,
  type TipoVariable,
  type Variable,
} from '../api'

const VACIA: DatosVariable = { clave: '', etiqueta: '', tipo: 'TEXTO', valorDefecto: '', descripcion: '' }

export function VariablesPage() {
  const [dialogo, setDialogo] = useState<{ abierto: boolean; variable: Variable | null }>({
    abierto: false,
    variable: null,
  })
  const consulta = useQuery({ queryKey: ['variables'], queryFn: listarVariables })

  return (
    <>
      <EncabezadoPagina
        titulo="Variables"
        descripcion="Catálogo compartido: {{fecha}} significa lo mismo en todas las plantillas. Las nuevas se crean solas al subir una plantilla."
        acciones={
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => setDialogo({ abierto: true, variable: null })}
          >
            Nueva variable
          </Button>
        }
      />
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Marca en Word</TableCell>
              <TableCell>Etiqueta en el formulario</TableCell>
              <TableCell>Tipo</TableCell>
              <TableCell>Valor por defecto</TableCell>
              <TableCell>Descripción</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.map((v) => (
              <TableRow
                key={v.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => setDialogo({ abierto: true, variable: v })}
              >
                <TableCell sx={{ fontFamily: 'monospace' }}>{`{{${v.clave}}}`}</TableCell>
                <TableCell>{v.etiqueta}</TableCell>
                <TableCell>{ETIQUETA_TIPO[v.tipo]}</TableCell>
                <TableCell>{v.valorDefecto}</TableCell>
                <TableCell>{v.descripcion}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <VariableDialog
        abierto={dialogo.abierto}
        variable={dialogo.variable}
        onCerrar={() => setDialogo({ abierto: false, variable: null })}
      />
    </>
  )
}

function VariableDialog({
  abierto,
  variable,
  onCerrar,
}: {
  abierto: boolean
  variable: Variable | null
  onCerrar: () => void
}) {
  const queryClient = useQueryClient()
  const { control, register, handleSubmit, reset } = useForm<DatosVariable>({ defaultValues: VACIA })

  const guardar = useMutation({
    mutationFn: (d: DatosVariable) =>
      variable
        ? editarVariable(variable.id, {
            etiqueta: d.etiqueta,
            tipo: d.tipo,
            valorDefecto: d.valorDefecto,
            descripcion: d.descripcion,
          })
        : crearVariable(d),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['variables'] })
      onCerrar()
    },
  })
  const { reset: limpiarError } = guardar

  useEffect(() => {
    if (abierto) {
      reset(
        variable
          ? {
              ...variable,
              valorDefecto: variable.valorDefecto ?? '',
              descripcion: variable.descripcion ?? '',
            }
          : VACIA,
      )
      limpiarError()
    }
  }, [abierto, variable, reset, limpiarError])

  return (
    <Dialog open={abierto} onClose={onCerrar} maxWidth="sm" fullWidth>
      <form onSubmit={handleSubmit((d) => guardar.mutate(d))} noValidate>
        <DialogTitle>{variable ? 'Editar variable' : 'Nueva variable'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 1 }}>
            {guardar.error && <Alert severity="error">{guardar.error.message}</Alert>}
            <TextField
              label="Clave"
              required
              disabled={!!variable}
              helperText={
                variable
                  ? 'La clave no se puede cambiar porque las plantillas la usan.'
                  : 'Minúsculas y guion bajo. En Word se escribe entre llaves: {{clave}}'
              }
              {...register('clave')}
            />
            <TextField label="Etiqueta en el formulario" required {...register('etiqueta')} />
            <Controller
              control={control}
              name="tipo"
              render={({ field }) => (
                <TextField select label="Tipo" {...field}>
                  {(Object.keys(ETIQUETA_TIPO) as TipoVariable[]).map((t) => (
                    <MenuItem key={t} value={t}>
                      {ETIQUETA_TIPO[t]}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            <TextField label="Valor por defecto" {...register('valorDefecto')} />
            <TextField label="Descripción o ayuda" multiline minRows={2} {...register('descripcion')} />
            <Typography variant="body2" color="text.secondary">
              Los datos de las personas (nombres, DNI, dirección…) no van aquí: se escriben en Word como{' '}
              {'{{otorgante.nombres}}'} y se llenan desde Clientes.
            </Typography>
          </Stack>
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
