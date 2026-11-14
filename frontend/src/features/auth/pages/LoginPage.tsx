import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { z } from 'zod'
import { iniciarSesion } from '../api'
import { useSesion } from '../sesionContext'

const esquema = z.object({
  usuario: z.string().trim().min(1, 'Escriba el usuario'),
  clave: z.string().min(1, 'Escriba la contraseña'),
})

type Formulario = z.infer<typeof esquema>

export function LoginPage() {
  const { sesion, iniciar } = useSesion()
  const navegar = useNavigate()
  const ubicacion = useLocation()
  const destino = (ubicacion.state as { desde?: string } | null)?.desde ?? '/'

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<Formulario>({ resolver: zodResolver(esquema) })

  const login = useMutation({
    mutationFn: (datos: Formulario) => iniciarSesion(datos.usuario, datos.clave),
    onSuccess: (nueva) => {
      iniciar(nueva)
      navegar(destino, { replace: true })
    },
  })

  if (sesion) {
    return <Navigate to={destino} replace />
  }

  return (
    <Box
      sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', bgcolor: 'background.default', p: 2 }}
    >
      <Paper sx={{ width: '100%', maxWidth: 380, p: 4 }} elevation={2}>
        <Stack
          component="form"
          spacing={2.5}
          noValidate
          onSubmit={handleSubmit((datos) => login.mutate(datos))}
        >
          <Box>
            <Typography variant="h5" component="h1" sx={{ fontWeight: 600 }}>
              Estudio Documental
            </Typography>
            <Typography color="text.secondary">Ingrese con su usuario</Typography>
          </Box>

          {login.error && <Alert severity="error">{login.error.message}</Alert>}

          <TextField
            label="Usuario"
            autoComplete="username"
            autoFocus
            {...register('usuario')}
            error={!!errors.usuario}
            helperText={errors.usuario?.message}
          />
          <TextField
            label="Contraseña"
            type="password"
            autoComplete="current-password"
            {...register('clave')}
            error={!!errors.clave}
            helperText={errors.clave?.message}
          />
          <Button type="submit" variant="contained" size="large" loading={login.isPending}>
            Ingresar
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
