import Download from '@mui/icons-material/Download'
import PictureAsPdf from '@mui/icons-material/PictureAsPdf'
import { Box, Button, Stack, Typography } from '@mui/material'
import { useState } from 'react'
import { descargarDocx, descargarPdf, verPdf, type DocumentoResumen } from '../api'

/** Botones para descargar el Word y ver o descargar el PDF. */
export function AccionesDocumento({
  documento,
  compacto = false,
}: {
  documento: DocumentoResumen
  compacto?: boolean
}) {
  const [error, setError] = useState<string | null>(null)
  const intentar = (accion: () => Promise<void>) => () => {
    setError(null)
    accion().catch((e: Error) => setError(e.message))
  }
  const tamano = compacto ? 'small' : 'medium'
  return (
    <Box>
      <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
        <Button
          size={tamano}
          variant="outlined"
          startIcon={<Download />}
          onClick={intentar(() => descargarDocx(documento))}
        >
          Word
        </Button>
        {documento.tienePdf && (
          <Button
            size={tamano}
            variant="outlined"
            startIcon={<PictureAsPdf />}
            onClick={intentar(() => verPdf(documento))}
          >
            Ver PDF
          </Button>
        )}
        {documento.tienePdf && !compacto && (
          <Button size={tamano} startIcon={<Download />} onClick={intentar(() => descargarPdf(documento))}>
            Descargar PDF
          </Button>
        )}
      </Stack>
      {error && (
        <Typography variant="body2" color="error" sx={{ mt: 0.5 }}>
          {error}
        </Typography>
      )}
    </Box>
  )
}
