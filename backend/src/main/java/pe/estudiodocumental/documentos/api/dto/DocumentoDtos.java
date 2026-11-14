package pe.estudiodocumental.documentos.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import pe.estudiodocumental.clientes.api.dto.ClienteRequest;

/** Entrada y salida de la API de documentos. */
public final class DocumentoDtos {

    private DocumentoDtos() {}

    /**
     * Pedido para generar un documento.
     *
     * @param variables valor de cada variable por su clave; fechas como AAAA-MM-DD, números con punto decimal.
     * @param participantes datos de la persona o empresa para cada rol de la plantilla, por su clave.
     */
    public record GenerarRequest(
            @NotNull(message = "Elija la plantilla") UUID plantillaId,
            @Size(max = 250) String titulo,
            Map<String, String> variables,
            Map<String, @Valid ClienteRequest> participantes) {}

    public record ParticipanteDto(
            String participante, String etiqueta, UUID clienteId, String nombre, String documentoIdentidad) {}

    public record DocumentoResumen(
            UUID id,
            long numero,
            String titulo,
            UUID plantillaId,
            String plantillaNombre,
            String usuarioNombre,
            Instant creadoEn,
            boolean tienePdf,
            List<ParticipanteDto> participantes) {}

    /**
     * Documento con todo lo necesario para revisarlo o generarlo de nuevo.
     *
     * @param variables valores tal como se ingresaron.
     * @param marcas texto que se puso en cada marca del Word.
     * @param avisoPdf motivo por el que no hay PDF, o null.
     */
    public record DocumentoDetalle(
            DocumentoResumen resumen, Map<String, String> variables, Map<String, String> marcas, String avisoPdf) {}
}
