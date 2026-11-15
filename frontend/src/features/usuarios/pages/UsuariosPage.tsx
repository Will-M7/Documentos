import Add from '@mui/icons-material/Add'
import {
  Alert,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  MenuItem,
  Paper,
  Stack,
  Switch,
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
import { useState } from 'react'
import { EncabezadoPagina } from '../../../components/EncabezadoPagina'
import type { Rol } from '../../auth/api'
import { useSesion } from '../../auth/sesionContext'
import {
  crearUsuario,
  editarUsuario,
  ETIQUETA_ROL,
  listarUsuarios,
  restablecerClave,
  type UsuarioAdmin,
} from '../api'

export function UsuariosPage() {
  const consulta = useQuery({ queryKey: ['usuarios'], queryFn: listarUsuarios })
  const [dialogo, setDialogo] = useState<{ abierto: boolean; usuario: UsuarioAdmin | null }>({
    abierto: false,
    usuario: null,
  })

  return (
    <>
      <EncabezadoPagina
        titulo="Usuarios"
        descripcion="Quién puede entrar al sistema. Los operadores generan documentos; los administradores además configuran."
        acciones={
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => setDialogo({ abierto: true, usuario: null })}
          >
            Nuevo usuario
          </Button>
        }
      />
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Nombre</TableCell>
              <TableCell>Usuario</TableCell>
              <TableCell>Rol</TableCell>
              <TableCell>Estado</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {consulta.data?.map((u) => (
              <TableRow
                key={u.id}
                hover
                sx={{ cursor: 'pointer', opacity: u.activo ? 1 : 0.6 }}
                onClick={() => setDialogo({ abierto: true, usuario: u })}
              >
                <TableCell>{u.nombre}</TableCell>
                <TableCell sx={{ fontFamily: 'monospace' }}>{u.usuario}</TableCell>
                <TableCell>{ETIQUETA_ROL[u.rol]}</TableCell>
                <TableCell>
                  {u.bloqueadoHasta ? (
                    <Chip size="small" color="warning" label="Bloqueado por intentos fallidos" />
                  ) : (
                    <Chip
                      size="small"
                      variant="outlined"
                      color={u.activo ? 'success' : 'default'}
                      label={u.activo ? 'Activo' : 'Desactivado'}
                    />
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <UsuarioDialog
        key={dialogo.usuario?.id ?? (dialogo.abierto ? 'nuevo' : 'cerrado')}
        abierto={dialogo.abierto}
        usuario={dialogo.usuario}
        onCerrar={() => setDialogo({ abierto: false, usuario: null })}
      />
    </>
  )
}

function UsuarioDialog({
  abierto,
  usuario,
  onCerrar,
}: {
  abierto: boolean
  usuario: UsuarioAdmin | null
  onCerrar: () => void
}) {
  const queryClient = useQueryClient()
  const { sesion } = useSesion()
  const esYo = usuario?.id === sesion?.usuario.id
  const [nombreUsuario, setNombreUsuario] = useState('')
  const [nombre, setNombre] = useState(usuario?.nombre ?? '')
  const [rol, setRol] = useState<Rol>(usuario?.rol ?? 'OPERADOR')
  const [activo, setActivo] = useState(usuario?.activo ?? true)
  const [clave, setClave] = useState('')
  const [claveCambiada, setClaveCambiada] = useState(false)

  const refrescar = () => queryClient.invalidateQueries({ queryKey: ['usuarios'] })
  const guardar = useMutation({
    mutationFn: () =>
      usuario
        ? editarUsuario(usuario.id, { nombre, rol, activo })
        : crearUsuario({ usuario: nombreUsuario, nombre, clave, rol }),
    onSuccess: () => {
      refrescar()
      onCerrar()
    },
  })
  const nuevaClave = useMutation({
    mutationFn: () => restablecerClave(usuario!.id, clave),
    onSuccess: () => {
      setClave('')
      setClaveCambiada(true)
      refrescar()
    },
  })

  return (
    <Dialog open={abierto} onClose={onCerrar} maxWidth="sm" fullWidth>
      <DialogTitle>{usuario ? `Editar a ${usuario.nombre}` : 'Nuevo usuario'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {guardar.error && <Alert severity="error">{guardar.error.message}</Alert>}
          {!usuario && (
            <TextField
              label="Usuario para ingresar"
              helperText="Sin espacios; por ejemplo: mtorres"
              value={nombreUsuario}
              onChange={(e) => setNombreUsuario(e.target.value)}
            />
          )}
          <TextField label="Nombre completo" value={nombre} onChange={(e) => setNombre(e.target.value)} />
          <TextField
            select
            label="Rol"
            value={rol}
            disabled={esYo}
            onChange={(e) => setRol(e.target.value as Rol)}
          >
            {(Object.keys(ETIQUETA_ROL) as Rol[]).map((r) => (
              <MenuItem key={r} value={r}>
                {ETIQUETA_ROL[r]}
              </MenuItem>
            ))}
          </TextField>
          {!usuario && (
            <TextField
              label="Contraseña inicial"
              type="password"
              autoComplete="new-password"
              helperText="Mínimo 10 caracteres. Entréguela a la persona para que la cambie."
              value={clave}
              onChange={(e) => setClave(e.target.value)}
            />
          )}
          {usuario && (
            <FormControlLabel
              control={
                <Switch checked={activo} disabled={esYo} onChange={(e) => setActivo(e.target.checked)} />
              }
              label={activo ? 'Activo: puede ingresar' : 'Desactivado: no puede ingresar'}
            />
          )}
          {usuario && (
            <>
              <Divider />
              <Typography variant="subtitle2">Restablecer contraseña</Typography>
              <Typography variant="body2" color="text.secondary">
                Para quien la olvidó o quedó bloqueado. También quita el bloqueo.
              </Typography>
              {nuevaClave.error && <Alert severity="error">{nuevaClave.error.message}</Alert>}
              {claveCambiada && <Alert severity="success">Contraseña cambiada.</Alert>}
              <Stack direction="row" spacing={1}>
                <TextField
                  fullWidth
                  size="small"
                  label="Contraseña nueva"
                  type="password"
                  autoComplete="new-password"
                  value={clave}
                  onChange={(e) => setClave(e.target.value)}
                />
                <Button
                  variant="outlined"
                  disabled={!clave}
                  loading={nuevaClave.isPending}
                  onClick={() => nuevaClave.mutate()}
                >
                  Cambiar
                </Button>
              </Stack>
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onCerrar}>Cancelar</Button>
        <Button variant="contained" loading={guardar.isPending} onClick={() => guardar.mutate()}>
          Guardar
        </Button>
      </DialogActions>
    </Dialog>
  )
}
