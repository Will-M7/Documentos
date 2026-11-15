import { Alert, Box, Card, CardContent, Grid, Skeleton, Tooltip, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { api } from '../../../api/cliente'

interface Cantidad {
  nombre: string
  cantidad: number
}

interface Resumen {
  documentosHoy: number
  documentosMes: number
  documentosMesAnterior: number
  clientesTotal: number
  clientesNuevosMes: number
  consultasMes: number
  documentosPorMes: Cantidad[]
  plantillasDelMes: Cantidad[]
  usuariosDelMes: Cantidad[]
}

const MESES = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']

/** "2026-11" → "nov 2026" */
function nombreMes(clave: string) {
  const [anio, mes] = clave.split('-')
  return `${MESES[Number(mes) - 1]} ${anio}`
}

/** Números del negocio: un dato por tarjeta y dos gráficos de barras de una sola serie. */
export function Panel() {
  const consulta = useQuery({ queryKey: ['estadisticas'], queryFn: () => api<Resumen>('/estadisticas') })

  if (consulta.error) return <Alert severity="error">{consulta.error.message}</Alert>
  const r = consulta.data
  const diferencia = r ? r.documentosMes - r.documentosMesAnterior : 0

  return (
    <Grid container spacing={2}>
      <Dato titulo="Documentos hoy" valor={r?.documentosHoy} />
      <Dato
        titulo="Documentos este mes"
        valor={r?.documentosMes}
        detalle={
          r &&
          `${diferencia >= 0 ? '+' : '−'}${Math.abs(diferencia)} respecto al mes anterior (${r.documentosMesAnterior})`
        }
      />
      <Dato
        titulo="Clientes registrados"
        valor={r?.clientesTotal}
        detalle={r && `${r.clientesNuevosMes} nuevos este mes`}
      />
      <Dato titulo="Consultas DNI/RUC este mes" valor={r?.consultasMes} detalle="A proveedores externos" />

      <Grid size={{ xs: 12, md: 6 }}>
        <Card sx={{ height: '100%' }}>
          <CardContent>
            <Typography variant="h6">Documentos por mes</Typography>
            {r ? (
              <BarrasVerticales
                datos={r.documentosPorMes.map((d) => ({ ...d, nombre: nombreMes(d.nombre) }))}
              />
            ) : (
              <Skeleton variant="rounded" height={180} />
            )}
          </CardContent>
        </Card>
      </Grid>
      <Grid size={{ xs: 12, md: 6 }}>
        <Card sx={{ height: '100%' }}>
          <CardContent>
            <Typography variant="h6">Plantillas más usadas este mes</Typography>
            {r ? (
              <BarrasHorizontales datos={r.plantillasDelMes} unidad="documentos" />
            ) : (
              <Skeleton height={180} />
            )}
            {r && r.usuariosDelMes.length > 1 && (
              <>
                <Typography variant="h6" sx={{ mt: 2 }}>
                  Documentos por usuario este mes
                </Typography>
                <BarrasHorizontales datos={r.usuariosDelMes} unidad="documentos" />
              </>
            )}
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  )
}

function Dato({ titulo, valor, detalle }: { titulo: string; valor?: number; detalle?: string | false }) {
  return (
    <Grid size={{ xs: 6, md: 3 }}>
      <Card sx={{ height: '100%' }}>
        <CardContent>
          <Typography variant="body2" color="text.secondary">
            {titulo}
          </Typography>
          <Typography variant="h4" component="p" sx={{ fontWeight: 600, my: 0.5 }}>
            {valor ?? <Skeleton width={60} />}
          </Typography>
          {detalle && (
            <Typography variant="body2" color="text.secondary">
              {detalle}
            </Typography>
          )}
        </CardContent>
      </Card>
    </Grid>
  )
}

const ALTO = 150

/** Columnas finas con el valor encima; el detalle exacto aparece al pasar el mouse. */
function BarrasVerticales({ datos }: { datos: Cantidad[] }) {
  const maximo = Math.max(1, ...datos.map((d) => d.cantidad))
  return (
    <Box
      role="img"
      aria-label={datos.map((d) => `${d.nombre}: ${d.cantidad}`).join(', ')}
      sx={{ display: 'flex', alignItems: 'flex-end', gap: 1, height: ALTO + 40, pt: 2 }}
    >
      {datos.map((d) => (
        <Tooltip key={d.nombre} title={`${d.nombre}: ${d.cantidad} documentos`} placement="top">
          <Box
            sx={{
              flex: 1,
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-end',
              alignItems: 'center',
              cursor: 'default',
            }}
          >
            <Typography variant="caption" color="text.secondary">
              {d.cantidad}
            </Typography>
            <Box
              sx={{
                width: '60%',
                maxWidth: 36,
                height: Math.max(2, (d.cantidad / maximo) * ALTO),
                bgcolor: 'primary.main',
                borderRadius: '4px 4px 0 0',
              }}
            />
            <Box sx={{ width: '100%', borderTop: 1, borderColor: 'divider' }} />
            <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5, whiteSpace: 'nowrap' }}>
              {d.nombre}
            </Typography>
          </Box>
        </Tooltip>
      ))}
    </Box>
  )
}

/** Barras horizontales con nombre y valor escritos (sin leyenda: es una sola serie). */
function BarrasHorizontales({ datos, unidad }: { datos: Cantidad[]; unidad: string }) {
  if (datos.length === 0) {
    return (
      <Typography color="text.secondary" sx={{ py: 2 }}>
        Todavía no hay datos este mes.
      </Typography>
    )
  }
  const maximo = Math.max(1, ...datos.map((d) => d.cantidad))
  return (
    <Box sx={{ display: 'grid', gap: 1, mt: 1 }}>
      {datos.map((d) => (
        <Tooltip key={d.nombre} title={`${d.nombre}: ${d.cantidad} ${unidad}`} placement="top-start">
          <Box>
            <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 1 }}>
              <Typography variant="body2" noWrap>
                {d.nombre}
              </Typography>
              <Typography variant="body2" color="text.secondary">
                {d.cantidad}
              </Typography>
            </Box>
            <Box sx={{ height: 8, bgcolor: 'action.hover', borderRadius: '4px' }}>
              <Box
                sx={{
                  height: '100%',
                  width: `${(d.cantidad / maximo) * 100}%`,
                  bgcolor: 'primary.main',
                  borderRadius: '4px',
                }}
              />
            </Box>
          </Box>
        </Tooltip>
      ))}
    </Box>
  )
}
