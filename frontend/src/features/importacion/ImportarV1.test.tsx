import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, respuestaJson, simularApi } from '../../test/utilidades'

describe('Importar desde la versión anterior', () => {
  it('sube el ZIP y muestra el resumen con avisos', async () => {
    let enviado: FormDataEntryValue | null = null
    simularApi({
      'GET /credenciales': () => respuestaJson(200, { consultasDelMes: 0, credenciales: [] }),
      'GET /documentos/estado-pdf': () => respuestaJson(200, { disponible: true, mensaje: 'PDF disponible' }),
      'POST /importacion/v1': (_, init) => {
        enviado = (init!.body as FormData).get('archivo')
        return respuestaJson(200, {
          clientesNuevos: 12,
          clientesCompletados: 3,
          clientesOmitidos: 1,
          plantillasCreadas: 4,
          plantillasOmitidas: 0,
          avisos: ['Cliente 7 (DNI 123): El DNI debe tener 8 dígitos.'],
        })
      },
    })
    const usuario = userEvent.setup()
    abrirComoAdmin('/configuracion')

    const importar = await screen.findByRole('button', { name: 'Importar' })
    expect(importar).toBeDisabled()
    const zip = new File(['PK'], 'exportacion-v1.zip', { type: 'application/zip' })
    await usuario.upload(screen.getByLabelText('Archivo ZIP de la versión anterior'), zip)
    expect(screen.getByText('exportacion-v1.zip')).toBeInTheDocument()
    await usuario.click(importar)

    expect(await screen.findByText(/12 nuevos/)).toBeInTheDocument()
    expect(screen.getByText(/4 creadas/)).toBeInTheDocument()
    expect(screen.getByText('Cliente 7 (DNI 123): El DNI debe tener 8 dígitos.')).toBeInTheDocument()
    expect((enviado as File | null)?.name).toBe('exportacion-v1.zip')
  })
})
