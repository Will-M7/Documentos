import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { abrirComoAdmin, respuestaJson, simularApi } from '../../../test/utilidades'

const fecha = {
  id: 'v1',
  clave: 'fecha',
  etiqueta: 'Fecha del documento',
  tipo: 'FECHA',
  valorDefecto: null,
  descripcion: null,
}

describe('Pantalla de variables', () => {
  it('lista el catálogo con la marca que se escribe en Word', async () => {
    simularApi({ 'GET /variables': () => respuestaJson(200, [fecha]) })
    abrirComoAdmin('/variables')

    expect(await screen.findByText('{{fecha}}')).toBeInTheDocument()
    expect(screen.getByText('Fecha del documento')).toBeInTheDocument()
  })

  it('al editar no deja cambiar la clave y envía solo lo editable', async () => {
    let enviado: unknown
    simularApi({
      'GET /variables': () => respuestaJson(200, [fecha]),
      'PUT /variables/v1': (_, init) => {
        enviado = JSON.parse(String(init?.body))
        return respuestaJson(200, fecha)
      },
    })
    abrirComoAdmin('/variables')

    await userEvent.click(await screen.findByText('{{fecha}}'))
    const dialogo = await screen.findByRole('dialog')
    expect(within(dialogo).getByLabelText(/clave/i)).toBeDisabled()
    const etiqueta = within(dialogo).getByLabelText(/etiqueta/i)
    await userEvent.clear(etiqueta)
    await userEvent.type(etiqueta, 'Fecha de firma')
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Guardar' }))

    await screen.findByText('{{fecha}}')
    expect(enviado).toEqual({ etiqueta: 'Fecha de firma', tipo: 'FECHA', valorDefecto: '', descripcion: '' })
  })
})
