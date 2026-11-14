import Add from '@mui/icons-material/Add'
import DeleteOutlined from '@mui/icons-material/DeleteOutlined'
import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  MenuItem,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import {
  crearCredencial,
  editarCredencial,
  eliminarCredencial,
  estadoPdf,
  listarCredenciales,
  NOMBRE_PROVEEDOR,
  type Credencial,
  type Proveedor,
} from '../api'

export function ConfiguracionPage() {
  const queryClient = useQueryClient()
  const credenciales = useQuery({ queryKey: ['credenciales'], queryFn: listarCredenciales })
  const pdf = useQuery({ queryKey: ['estado-pdf'], queryFn: estadoPdf })
  const [dialogo, setDialogo] = useState<{ abierto: boolean; credencial: Credencial | null }>({
    abierto: false,
    credencial: null,
  })
  const eliminar = useMutation({
    mutationFn: eliminarCredencial,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['credenciales'] }),
  })

  return (
    <>
      <EncabezadoPagina titulo="Configuración" descripcion="Conexiones externas y estado del servidor." />

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>
            PDF
          </Typography>
          {pdf.data && (
            <Alert severity={pdf.data.disponible ? 'success' : 'warning'}>
              {pdf.data.mensaje}
              {!pdf.data.disponible && (
                <>
                  {' '}
                  Descárguelo gratis de libreoffice.org, instálelo en la PC servidor y reinicie el backend.
                </>
              )}
            </Alert>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Stack direction="row" sx={{ alignItems: 'flex-start', mb: 1, gap: 2, flexWrap: 'wrap' }}>
            <div style={{ flexGrow: 1 }}>
              <Typography variant="h6">Consultas de DNI y RUC</Typography>
              <Typography variant="body2" color="text.secondary">
                Al buscar un documento se revisa primero Clientes (sin costo). Si no está, se consulta a estos
                proveedores en orden de prioridad (1 primero); si uno falla se usa el siguiente.
              </Typography>
            </div>
            <Button
              variant="contained"
              startIcon={<Add />}
              onClick={() => setDialogo({ abierto: true, credencial: null })}
            >
              Agregar credencial
            </Button>
          </Stack>
          {credenciales.data && (
            <Typography variant="body2" sx={{ mb: 2 }}>
              Consultas pagadas este mes: <strong>{credenciales.data.consultasDelMes}</strong>
            </Typography>
          )}
          {credenciales.error && <Alert severity="error">{credenciales.error.message}</Alert>}
          {eliminar.error && <Alert severity="error">{eliminar.error.message}</Alert>}
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Prioridad</TableCell>
                <TableCell>Proveedor</TableCell>
                <TableCell>Token</TableCell>
                <TableCell>Estado</TableCell>
                <TableCell>Último uso</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {credenciales.data?.credenciales.map((c) => (
                <TableRow
                  key={c.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => setDialogo({ abierto: true, credencial: c })}
                >
                  <TableCell>{c.prioridad}</TableCell>
                  <TableCell>
                    {NOMBRE_PROVEEDOR[c.proveedor]}
                    <Typography variant="body2" color="text.secondary">
                      {c.nombre}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ fontFamily: 'monospace' }}>{c.tokenFinal}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      variant="outlined"
                      label={c.activo ? 'Activa' : 'Pausada'}
                      color={c.activo ? 'success' : 'default'}
                    />
                  </TableCell>
                  <TableCell>
                    {c.ultimoUso ? new Date(c.ultimoUso).toLocaleString('es-PE') : 'Nunca'}
                    {c.ultimoEstado && (
                      <Typography variant="body2" color="text.secondary">
                        {c.ultimoEstado}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    <Tooltip title="Eliminar">
                      <IconButton
                        aria-label={`Eliminar ${c.nombre}`}
                        onClick={(e) => {
                          e.stopPropagation()
                          if (window.confirm(`¿Eliminar la credencial "${c.nombre}"?`)) eliminar.mutate(c.id)
                        }}
                      >
                        <DeleteOutlined />
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
              {credenciales.data?.credenciales.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 3, color: 'text.secondary' }}>
                    Sin credenciales: los datos de clientes nuevos se escriben a mano.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <CredencialDialog
        key={dialogo.credencial?.id ?? (dialogo.abierto ? 'nueva' : 'cerrado')}
        abierto={dialogo.abierto}
        credencial={dialogo.credencial}
        onCerrar={() => setDialogo({ abierto: false, credencial: null })}
      />
    </>
  )
}

function CredencialDialog({
  abierto,
  credencial,
  onCerrar,
}: {
  abierto: boolean
  credencial: Credencial | null
  onCerrar: () => void
}) {
  const queryClient = useQueryClient()
  const [proveedor, setProveedor] = useState<Proveedor>(credencial?.proveedor ?? 'FACTILIZA')
  const [nombre, setNombre] = useState(credencial?.nombre ?? '')
  const [token, setToken] = useState('')
  const [prioridad, setPrioridad] = useState(credencial?.prioridad ?? 1)
  const [activo, setActivo] = useState(credencial?.activo ?? true)

  const guardar = useMutation({
    mutationFn: () =>
      credencial
        ? editarCredencial(credencial.id, { nombre, token, prioridad, activo })
        : crearCredencial({ proveedor, nombre, token, prioridad }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['credenciales'] })
      onCerrar()
    },
  })

  return (
    <Dialog open={abierto} onClose={onCerrar} maxWidth="sm" fullWidth>
      <DialogTitle>{credencial ? 'Editar credencial' : 'Agregar credencial'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {guardar.error && <Alert severity="error">{guardar.error.message}</Alert>}
          <TextField
            select
            label="Proveedor"
            value={proveedor}
            disabled={!!credencial}
            onChange={(e) => setProveedor(e.target.value as Proveedor)}
          >
            {(Object.keys(NOMBRE_PROVEEDOR) as Proveedor[]).map((p) => (
              <MenuItem key={p} value={p}>
                {NOMBRE_PROVEEDOR[p]}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            label="Nombre"
            placeholder="Cuenta principal"
            value={nombre}
            onChange={(e) => setNombre(e.target.value)}
          />
          <TextField
            label={credencial ? 'Token nuevo (dejar vacío para no cambiarlo)' : 'Token'}
            value={token}
            onChange={(e) => setToken(e.target.value)}
            type="password"
            autoComplete="off"
            helperText="Se guarda cifrado. Nunca se vuelve a mostrar completo."
          />
          <TextField
            label="Prioridad"
            type="number"
            value={prioridad}
            onChange={(e) => setPrioridad(Number(e.target.value))}
            slotProps={{ htmlInput: { min: 1, max: 99 } }}
            helperText="1 se usa primero."
          />
          {credencial && (
            <FormControlLabel
              control={<Switch checked={activo} onChange={(e) => setActivo(e.target.checked)} />}
              label={activo ? 'Activa' : 'Pausada'}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar}>Cancelar</Button>
        <Button
          variant="contained"
          loading={guardar.isPending}
          disabled={!credencial && !token.trim()}
          onClick={() => guardar.mutate()}
        >
          Guardar
        </Button>
      </DialogActions>
    </Dialog>
  )
}
