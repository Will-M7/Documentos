import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
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

  it('al dar de alta, Buscar trae los datos del DNI y pasa a editar ese cliente', async () => {
    let actualizado: unknown
    simularApi({
      'GET /clientes': () => respuestaJson(200, paginaVacia),
      'POST /consultas/DNI/41234567': () =>
        respuestaJson(200, {
          encontrado: true,
          origen: 'Factiliza',
          cliente: ana,
          mensaje: 'Datos obtenidos de Factiliza y guardados en Clientes.',
        }),
      'PUT /clientes/c1': (_, init) => {
        actualizado = JSON.parse(String(init?.body))
        return respuestaJson(200, ana)
      },
    })
    abrirComoAdmin('/clientes')

    await userEvent.click(await screen.findByRole('button', { name: 'Nuevo cliente' }))
    const dialogo = await screen.findByRole('dialog')
    await userEvent.type(within(dialogo).getByLabelText(/número/i), '41234567')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Buscar' }))

    expect(await within(dialogo).findByText(/Datos obtenidos de Factiliza/)).toBeInTheDocument()
    expect(within(dialogo).getByText('Editar cliente')).toBeInTheDocument()
    expect(within(dialogo).getByLabelText(/^nombres/i)).toHaveValue('Ana María')

    await userEvent.type(within(dialogo).getByLabelText(/teléfono/i), '984111222')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))
    await screen.findByText('Todavía no hay clientes registrados.')
    expect(actualizado).toMatchObject({ numeroDocumento: '41234567', telefono: '984111222' })
  })

  it('la ficha muestra los documentos del cliente y el admin puede darlo de baja', async () => {
    let bajas = 0
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    simularApi({
      'GET /clientes': () => respuestaJson(200, { ...paginaVacia, contenido: [ana], totalElementos: 1 }),
      'GET /documentos': (url) => {
        expect(url).toContain('clienteId=c1')
        return respuestaJson(200, {
          ...paginaVacia,
          totalElementos: 1,
          contenido: [
            {
              id: 'd1',
              numero: 12,
              titulo: 'Carta poder simple - Ana',
              plantillaId: 'p1',
              plantillaNombre: 'Carta poder simple',
              usuarioNombre: 'Ana',
              creadoEn: '2026-11-14T10:00:00Z',
              tienePdf: false,
              participantes: [
                {
                  participante: 'otorgante',
                  etiqueta: 'Poderdante',
                  clienteId: 'c1',
                  nombre: 'Ana',
                  documentoIdentidad: 'DNI 41234567',
                },
              ],
            },
          ],
        })
      },
      'DELETE /clientes/c1': () => {
        bajas++
        return respuestaJson(204, null)
      },
    })
    abrirComoAdmin('/clientes')

    await userEvent.click(await screen.findByText('Ana María Quispe Rojas'))
    const dialogo = await screen.findByRole('dialog')
    expect(await within(dialogo).findByText('N.º 12 · Carta poder simple - Ana')).toBeInTheDocument()
    expect(within(dialogo).getByText(/como Poderdante/)).toBeInTheDocument()

    await userEvent.click(within(dialogo).getByRole('button', { name: 'Dar de baja' }))
    await waitFor(() => expect(bajas).toBe(1))
  })
})
