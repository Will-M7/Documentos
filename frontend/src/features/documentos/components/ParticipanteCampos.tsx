import Search from '@mui/icons-material/Search'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Grid,
  MenuItem,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import {
  buscarPorDocumento,
  ETIQUETA_DOCUMENTO,
  type DatosCliente,
  type TipoCliente,
  type TipoDocumento,
} from '../../clientes/api'
import type { Participante } from '../../plantillas/api'
import { PARTICIPANTE_VACIO, sinNulos } from '../participante'

const DOCUMENTOS_PERSONA: TipoDocumento[] = ['DNI', 'CE', 'PASAPORTE', 'RUC']

/** Datos de una persona o empresa para un rol de la plantilla, con búsqueda por documento. */
export function ParticipanteCampos({
  participante,
  valor,
  onCambiar,
}: {
  participante: Participante
  valor: DatosCliente
  onCambiar: (datos: DatosCliente) => void
}) {
  const [aviso, setAviso] = useState<{ tipo: 'success' | 'info'; texto: string; local: boolean } | null>(null)
  const usa = (campo: string) => participante.campos.some((c) => c.clave === campo)
  const tipoFijo: TipoCliente | null =
    participante.tipoCliente === 'CUALQUIERA' ? null : participante.tipoCliente
  const tipo = tipoFijo ?? valor.tipo
  const cambiar = (cambios: Partial<DatosCliente>) => onCambiar({ ...valor, ...cambios })

  const buscar = useMutation({
    mutationFn: (actualizar: boolean) =>
      buscarPorDocumento(valor.tipoDocumento, valor.numeroDocumento, actualizar),
    onSuccess: ({ cliente, mensaje, origen }) => {
      if (cliente) {
        onCambiar({ ...PARTICIPANTE_VACIO, ...sinNulos(cliente) } as DatosCliente)
      }
      setAviso({ tipo: cliente ? 'success' : 'info', texto: mensaje, local: origen === 'CLIENTES' })
    },
  })
  const puedeActualizar = aviso?.local && (valor.tipoDocumento === 'DNI' || valor.tipoDocumento === 'RUC')

  return (
    <Card variant="outlined">
      <CardContent>
        <Typography variant="h6" gutterBottom>
          {participante.etiqueta}
        </Typography>
        <Grid container spacing={2}>
          {!tipoFijo && (
            <Grid size={12}>
              <ToggleButtonGroup
                exclusive
                size="small"
                color="primary"
                value={tipo}
                onChange={(_, nuevo: TipoCliente | null) =>
                  nuevo && cambiar({ tipo: nuevo, tipoDocumento: nuevo === 'EMPRESA' ? 'RUC' : 'DNI' })
                }
              >
                <ToggleButton value="PERSONA">Persona</ToggleButton>
                <ToggleButton value="EMPRESA">Empresa</ToggleButton>
              </ToggleButtonGroup>
            </Grid>
          )}
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              select
              fullWidth
              size="small"
              label="Documento"
              value={tipo === 'EMPRESA' ? 'RUC' : valor.tipoDocumento}
              disabled={tipo === 'EMPRESA'}
              onChange={(e) => cambiar({ tipoDocumento: e.target.value as TipoDocumento })}
            >
              {(tipo === 'EMPRESA' ? (['RUC'] as TipoDocumento[]) : DOCUMENTOS_PERSONA).map((t) => (
                <MenuItem key={t} value={t}>
                  {ETIQUETA_DOCUMENTO[t]}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <TextField
                fullWidth
                size="small"
                label="Número"
                required
                value={valor.numeroDocumento}
                onChange={(e) => cambiar({ numeroDocumento: e.target.value })}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    e.preventDefault()
                    buscar.mutate(false)
                  }
                }}
              />
              <Button
                variant="outlined"
                startIcon={<Search />}
                loading={buscar.isPending}
                disabled={!valor.numeroDocumento.trim()}
                onClick={() => buscar.mutate(false)}
              >
                Buscar
              </Button>
            </Box>
          </Grid>
          {(aviso || buscar.error) && (
            <Grid size={12}>
              <Alert
                severity={buscar.error ? 'error' : aviso!.tipo}
                onClose={() => setAviso(null)}
                action={
                  puedeActualizar && !buscar.error ? (
                    <Button color="inherit" size="small" onClick={() => buscar.mutate(true)}>
                      Consultar de nuevo
                    </Button>
                  ) : undefined
                }
              >
                {buscar.error ? buscar.error.message : aviso!.texto}
              </Alert>
            </Grid>
          )}
          {tipo === 'EMPRESA' ? (
            <Grid size={12}>
              <TextField
                fullWidth
                size="small"
                required
                label="Razón social"
                value={valor.razonSocial ?? ''}
                onChange={(e) => cambiar({ razonSocial: e.target.value })}
              />
            </Grid>
          ) : (
            <>
              <Grid size={{ xs: 12, sm: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  required
                  label="Nombres"
                  value={valor.nombres ?? ''}
                  onChange={(e) => cambiar({ nombres: e.target.value })}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  required
                  label="Apellido paterno"
                  value={valor.apellidoPaterno ?? ''}
                  onChange={(e) => cambiar({ apellidoPaterno: e.target.value })}
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 4 }}>
                <TextField
                  fullWidth
                  size="small"
                  label="Apellido materno"
                  value={valor.apellidoMaterno ?? ''}
                  onChange={(e) => cambiar({ apellidoMaterno: e.target.value })}
                />
              </Grid>
            </>
          )}
          {usa('direccion') && (
            <Grid size={12}>
              <TextField
                fullWidth
                size="small"
                label={tipo === 'EMPRESA' ? 'Domicilio fiscal' : 'Dirección'}
                value={valor.direccion ?? ''}
                onChange={(e) => cambiar({ direccion: e.target.value })}
              />
            </Grid>
          )}
          {usa('telefono') && (
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                size="small"
                label="Teléfono"
                value={valor.telefono ?? ''}
                onChange={(e) => cambiar({ telefono: e.target.value })}
              />
            </Grid>
          )}
          {usa('correo') && (
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                fullWidth
                size="small"
                label="Correo"
                value={valor.correo ?? ''}
                onChange={(e) => cambiar({ correo: e.target.value })}
              />
            </Grid>
          )}
        </Grid>
      </CardContent>
    </Card>
  )
}
