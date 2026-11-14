import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { App } from './App'

function respuestaJson(status: number, cuerpo: unknown) {
  return new Response(JSON.stringify(cuerpo), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('Inicio de sesión', () => {
  it('sin sesión muestra el formulario de ingreso', () => {
    render(<App />)
    expect(screen.getByRole('heading', { name: 'Estudio Documental' })).toBeInTheDocument()
    expect(screen.getByLabelText(/usuario/i)).toBeInTheDocument()
  })

  it('valida los campos vacíos sin llamar al servidor', async () => {
    const fetchSimulado = vi.spyOn(globalThis, 'fetch')
    render(<App />)

    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Escriba el usuario')).toBeInTheDocument()
    expect(fetchSimulado).not.toHaveBeenCalled()
  })

  it('muestra el error del servidor si las credenciales son incorrectas', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      respuestaJson(401, { status: 401, detail: 'Usuario o contraseña incorrectos.' }),
    )
    render(<App />)

    await userEvent.type(screen.getByLabelText(/usuario/i), 'admin')
    await userEvent.type(screen.getByLabelText(/contraseña/i), 'mala')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Usuario o contraseña incorrectos.')).toBeInTheDocument()
  })

  it('con credenciales correctas entra al inicio', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      respuestaJson(200, {
        token: 'token-de-prueba',
        expiraEn: new Date(Date.now() + 60_000).toISOString(),
        usuario: { id: '1', usuario: 'admin', nombre: 'Ana Pérez', rol: 'ADMIN' },
      }),
    )
    render(<App />)

    await userEvent.type(screen.getByLabelText(/usuario/i), 'admin')
    await userEvent.type(screen.getByLabelText(/contraseña/i), 'correcta')
    await userEvent.click(screen.getByRole('button', { name: 'Ingresar' }))

    expect(await screen.findByText('Hola, Ana Pérez')).toBeInTheDocument()
  })
})
