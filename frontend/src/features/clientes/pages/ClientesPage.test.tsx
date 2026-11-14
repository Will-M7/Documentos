import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, paginaVacia, respuestaJson, simularApi } from '../../../test/utilidades'

const ana = {
  id: 'c1',
  tipo: 'PERSONA',
  tipoDocumento: 'DNI',
  numeroDocumento: '41234567',
  nombres: 'Ana María',
  apellidoPaterno: 'Quispe',
  apellidoMaterno: 'Rojas',
  nombreCompleto: 'Ana María Quispe Rojas',
  direccion: 'Av. Sol 123',
  origen: 'MANUAL',
  actualizadoEn: '2026-11-14T10:00:00Z',
}

describe('Pantalla de clientes', () => {
  it('lista los clientes del backend', async () => {
    simularApi({
      'GET /clientes': () => respuestaJson(200, { ...paginaVacia, contenido: [ana], totalElementos: 1 }),
    })
    abrirComoAdmin('/clientes')

    expect(await screen.findByText('Ana María Quispe Rojas')).toBeInTheDocument()
    expect(screen.getByText('DNI 41234567')).toBeInTheDocument()
  })

  it('crea un cliente y envía los datos del formulario', async () => {
    let enviado: unknown
    simularApi({
      'GET /clientes': () => respuestaJson(200, paginaVacia),
      'POST /clientes': (_, init) => {
        enviado = JSON.parse(String(init?.body))
        return respuestaJson(201, ana)
      },
    })
    abrirComoAdmin('/clientes')

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cliente' }))
    const dialogo = await screen.findByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText(/número/i), '41234567')
    await userEvent.type(within(dialogo).getByLabelText(/^nombres/i), 'Ana María')
    await userEvent.type(within(dialogo).getByLabelText(/apellido paterno/i), 'Quispe')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    await screen.findByText('Todavía no hay clientes registrados.')
    expect(enviado).toMatchObject({
      tipo: 'PERSONA',
      tipoDocumento: 'DNI',
      numeroDocumento: '41234567',
      nombres: 'Ana María',
      apellidoPaterno: 'Quispe',
    })
  })

  it('muestra el error del backend dentro del formulario', async () => {
    simularApi({
      'GET /clientes': () => respuestaJson(200, paginaVacia),
      'POST /clientes': () =>
        respuestaJson(409, { status: 409, detail: 'Ya existe un cliente con DNI 41234567.' }),
    })
    abrirComoAdmin('/clientes')

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cliente' }))
    const dialogo = await screen.findByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText(/número/i), '41234567')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    expect(await within(dialogo).findByText('Ya existe un cliente con DNI 41234567.')).toBeInTheDocument()
  })
})
