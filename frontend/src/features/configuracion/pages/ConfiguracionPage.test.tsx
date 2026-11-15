import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, respuestaJson, simularApi } from '../../../test/utilidades'

const decolecta = {
  id: 'k1',
  proveedor: 'DECOLECTA',
  nombre: 'Decolecta gratis',
  tokenFinal: '…abcd',
  prioridad: 1,
  activo: true,
  ultimoEstado: 'Correcta',
  ultimoUso: '2026-11-14T10:00:00Z',
  limiteMensual: 100,
  usadasEsteMes: 100,
  agotadaHasta: '2026-12-01T05:00:00Z',
}

describe('Configuración de APIs', () => {
  it('muestra el uso del mes y si la credencial está agotada', async () => {
    simularApi({
      'GET /credenciales': () => respuestaJson(200, { consultasDelMes: 100, credenciales: [decolecta] }),
      'GET /documentos/estado-pdf': () =>
        respuestaJson(200, { disponible: false, mensaje: 'PDF no disponible' }),
    })
    abrirComoAdmin('/configuracion')

    expect(await screen.findByText('100 de 100')).toBeInTheDocument()
    expect(screen.getByText(/Agotada hasta/)).toBeInTheDocument()
  })

  it('al agregar, propone Decolecta con 100 consultas gratis y las envía', async () => {
    let enviado: unknown
    simularApi({
      'GET /credenciales': () => respuestaJson(200, { consultasDelMes: 0, credenciales: [] }),
      'GET /documentos/estado-pdf': () =>
        respuestaJson(200, { disponible: false, mensaje: 'PDF no disponible' }),
      'POST /credenciales': (_, init) => {
        enviado = JSON.parse(String(init?.body))
        return respuestaJson(201, decolecta)
      },
    })
    abrirComoAdmin('/configuracion')

    await userEvent.click(await screen.findByRole('button', { name: 'Agregar credencial' }))
    const dialogo = await screen.findByRole('dialog')
    expect(within(dialogo).getByLabelText(/consultas gratis al mes/i)).toHaveValue(100)
    expect(within(dialogo).getByRole('link', { name: 'Obtener token' })).toHaveAttribute(
      'href',
      'https://decolecta.com/profile',
    )
    await userEvent.type(within(dialogo).getByLabelText(/^token/i), 'token-de-prueba-123')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    await screen.findByText(/Sin credenciales/)
    expect(enviado).toMatchObject({
      proveedor: 'DECOLECTA',
      token: 'token-de-prueba-123',
      limiteMensual: 100,
    })
  })
})
