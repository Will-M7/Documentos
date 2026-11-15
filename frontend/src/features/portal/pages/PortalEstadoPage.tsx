import { Alert, Box, Button, Card, CardContent, Chip, Stack, TextField, Typography } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { estadoPublico, ETIQUETA_ESTADO } from '../../solicitudes/api'

const COLOR = { PENDIENTE: 'warning', ATENDIDA: 'success', RECHAZADA: 'error' } as const

/** El cliente consulta su solicitud con el código. Solo se muestra el estado, nunca datos personales. */
export function PortalEstadoPage() {
  const [parametros] = useSearchParams()
  const [codigo, setCodigo] = useState(parametros.get('codigo') ?? '')
  const consulta = useMutation({ mutationFn: () => estadoPublico(codigo) })
  const { mutate: consultar } = consulta

  useEffect(() => {
    if (parametros.get('codigo')) consultar()
  }, [parametros, consultar])

  return (
    <>
      <Typography variant="h5" component="h1" gutterBottom>
        Consultar mi solicitud
      </Typography>
      <Box
        component="form"
        sx={{ display: 'flex', gap: 1, mb: 3, maxWidth: 420 }}
        onSubmit={(e) => {
          e.preventDefault()
          consulta.mutate()
        }}
      >
        <TextField
          fullWidth
          label="Código"
          placeholder="S-XXXXXX"
          value={codigo}
          onChange={(e) => setCodigo(e.target.value.toUpperCase())}
        />
        <Button type="submit" variant="contained" disabled={!codigo.trim()} loading={consulta.isPending}>
          Consultar
        </Button>
      </Box>
      {consulta.error && <Alert severity="error">{consulta.error.message}</Alert>}
      {consulta.data && (
        <Card>
          <CardContent>
            <Stack spacing={1}>
              <Typography variant="body2" color="text.secondary">
                {consulta.data.codigo} · enviada el{' '}
                {new Date(consulta.data.enviadaEn).toLocaleDateString('es-PE')}
              </Typography>
              <Typography variant="h6">{consulta.data.documento}</Typography>
              <Box>
                <Chip color={COLOR[consulta.data.estado]} label={ETIQUETA_ESTADO[consulta.data.estado]} />
              </Box>
              <Typography>{consulta.data.mensaje}</Typography>
            </Stack>
          </CardContent>
        </Card>
      )}
    </>
  )
}
