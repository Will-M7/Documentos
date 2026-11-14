import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { cambiarMiClave } from '../api'

/** Cada usuario cambia su propia contraseña. */
export function CambiarClaveDialog({ abierto, onCerrar }: { abierto: boolean; onCerrar: () => void }) {
  const [actual, setActual] = useState('')
  const [nueva, setNueva] = useState('')
  const [repetida, setRepetida] = useState('')
  const noCoinciden = repetida.length > 0 && nueva !== repetida

  const cambiar = useMutation({ mutationFn: () => cambiarMiClave(actual, nueva) })

  const cerrar = () => {
    setActual('')
    setNueva('')
    setRepetida('')
    cambiar.reset()
    onCerrar()
  }

  return (
    <Dialog open={abierto} onClose={cerrar} maxWidth="xs" fullWidth>
      <DialogTitle>Cambiar mi contraseña</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 1 }}>
          {cambiar.error && <Alert severity="error">{cambiar.error.message}</Alert>}
          {cambiar.isSuccess ? (
            <Alert severity="success">Contraseña cambiada. Úsela la próxima vez que ingrese.</Alert>
          ) : (
            <>
              <TextField
                label="Contraseña actual"
                type="password"
                autoComplete="current-password"
                value={actual}
                onChange={(e) => setActual(e.target.value)}
              />
              <TextField
                label="Contraseña nueva"
                type="password"
                autoComplete="new-password"
                helperText="Mínimo 10 caracteres."
                value={nueva}
                onChange={(e) => setNueva(e.target.value)}
              />
              <TextField
                label="Repita la contraseña nueva"
                type="password"
                autoComplete="new-password"
                error={noCoinciden}
                helperText={noCoinciden ? 'No coincide.' : ' '}
                value={repetida}
                onChange={(e) => setRepetida(e.target.value)}
              />
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={cerrar}>{cambiar.isSuccess ? 'Cerrar' : 'Cancelar'}</Button>
        {!cambiar.isSuccess && (
          <Button
            variant="contained"
            disabled={!actual || !nueva || nueva !== repetida}
            loading={cambiar.isPending}
            onClick={() => cambiar.mutate()}
          >
            Cambiar
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
