import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, paginaVacia, respuestaJson, simularApi } from '../../test/utilidades'

const atrasado = {
  configurado: true,
  directorio: 'D:/respaldos',
  puedeRespaldarAhora: true,
  alDia: false,
  ultimo: '2026-11-10T02:00:00Z',
  respaldos: [
    { nombre: '20261113_2100', fecha: '2026-11-14T02:00:00Z', tamanoBytes: 2048, completo: false },
    { nombre: '20261109_2100', fecha: '2026-11-10T02:00:00Z', tamanoBytes: 5 * 1024 * 1024, completo: true },
  ],
}

describe('Respaldos', () => {
  it('muestra los respaldos, avisa que están atrasados y permite respaldar ahora', async () => {
    let respaldado = false
    simularApi({
      'GET /credenciales': () => respuestaJson(200, { consultasDelMes: 0, credenciales: [] }),
      'GET /documentos/estado-pdf': () => respuestaJson(200, { disponible: true, mensaje: 'PDF disponible' }),
      'GET /respaldos': () =>
        respuestaJson(
          200,
          respaldado ? { ...atrasado, alDia: true, ultimo: '2026-11-14T22:00:00Z' } : atrasado,
        ),
      'POST /respaldos': () => {
        respaldado = true
        return respuestaJson(200, { exito: true, salida: 'Respaldo listo: D:/respaldos/20261114_1700' })
      },
    })
    const usuario = userEvent.setup()
    abrirComoAdmin('/configuracion')

    expect(await screen.findByText(/Hace más de 2 días que no se respalda/)).toBeInTheDocument()
    expect(screen.getByText('Incompleto')).toBeInTheDocument()
    expect(screen.getByText('5.0 MB')).toBeInTheDocument()

    await usuario.click(screen.getByRole('button', { name: 'Respaldar ahora' }))
    expect(await screen.findByText('Respaldo terminado.')).toBeInTheDocument()
    expect(await screen.findByText(/Último respaldo completo/)).toBeInTheDocument()
    expect(screen.queryByText(/Hace más de 2 días/)).not.toBeInTheDocument()
  })

  it('en Inicio avisa al administrador si no hay respaldos recientes', async () => {
    simularApi({
      'GET /documentos': () => respuestaJson(200, paginaVacia),
      'GET /respaldos': () => respuestaJson(200, { ...atrasado, ultimo: null, respaldos: [] }),
    })
    abrirComoAdmin('/')
    expect(
      await screen.findByText('Todavía no se ha hecho ningún respaldo de la información.'),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver respaldos' })).toHaveAttribute('href', '/configuracion')
  })
})
