import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, respuestaJson, simularApi } from '../../../test/utilidades'

const operador = {
  id: 'u2',
  usuario: 'mtorres',
  nombre: 'María Torres',
  rol: 'OPERADOR',
  activo: true,
  bloqueadoHasta: '2099-01-01T00:00:00Z',
  creadoEn: '2026-11-14T10:00:00Z',
}

describe('Usuarios', () => {
  it('muestra el bloqueo y permite restablecer la contraseña', async () => {
    let clave: unknown
    simularApi({
      'GET /usuarios': () => respuestaJson(200, [operador]),
      'PUT /usuarios/u2/clave': (_, init) => {
        clave = JSON.parse(String(init?.body))
        return respuestaJson(204, null)
      },
    })
    abrirComoAdmin('/usuarios')

    expect(await screen.findByText('Bloqueado por intentos fallidos')).toBeInTheDocument()
    await userEvent.click(screen.getByText('María Torres'))
    const dialogo = await screen.findByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText('Contraseña nueva'), 'clave-nueva-123')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Cambiar' }))

    expect(await within(dialogo).findByText('Contraseña cambiada.')).toBeInTheDocument()
    expect(clave).toEqual({ clave: 'clave-nueva-123' })
  })
})
