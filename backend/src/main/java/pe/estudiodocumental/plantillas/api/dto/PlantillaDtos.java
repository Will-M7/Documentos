package pe.estudiodocumental.plantillas.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pe.estudiodocumental.plantillas.dominio.CampoParticipante;
import pe.estudiodocumental.plantillas.dominio.ParticipantePlantilla;
import pe.estudiodocumental.plantillas.dominio.Plantilla;
import pe.estudiodocumental.plantillas.dominio.PlantillaVersion;
import pe.estudiodocumental.plantillas.dominio.TipoParticipante;
import pe.estudiodocumental.variables.dominio.TipoVariable;
import pe.estudiodocumental.variables.dominio.Variable;

/** Entrada y salida de la API de plantillas. */
public final class PlantillaDtos {

    private PlantillaDtos() {}

    public record PlantillaResumen(
            UUID id,
            String nombre,
            String descripcion,
            String categoria,
            int versionActual,
            boolean activo,
            Instant actualizadoEn) {

        public static PlantillaResumen desde(Plantilla p) {
            return new PlantillaResumen(
                    p.getId(),
                    p.getNombre(),
                    p.getDescripcion(),
                    p.getCategoria(),
                    p.getVersionActual(),
                    p.isActivo(),
                    p.getActualizadoEn());
        }
    }

    public record CampoDto(String clave, String etiqueta) {}

    public record ParticipanteDto(String clave, String etiqueta, TipoParticipante tipoCliente, List<CampoDto> campos) {

        public static ParticipanteDto desde(ParticipantePlantilla p) {
            List<CampoDto> campos = p.getCampos().stream()
                    .map(c -> new CampoDto(
                            c,
                            CampoParticipante.desdeClave(c)
                                    .map(CampoParticipante::etiqueta)
                                    .orElse(c)))
                    .toList();
            return new ParticipanteDto(p.getClave(), p.getEtiqueta(), p.getTipoCliente(), campos);
        }
    }

    public record VariableDto(
            UUID id, String clave, String etiqueta, TipoVariable tipo, String valorDefecto, String descripcion) {

        public static VariableDto desde(Variable v) {
            return new VariableDto(
                    v.getId(), v.getClave(), v.getEtiqueta(), v.getTipo(), v.getValorDefecto(), v.getDescripcion());
        }
    }

    public record VersionDto(int numero, Instant creadoEn, List<String> marcas) {

        public static VersionDto desde(PlantillaVersion v) {
            return new VersionDto(v.getNumero(), v.getCreadoEn(), v.getMarcas());
        }
    }

    public record PlantillaDetalle(
            UUID id,
            String nombre,
            String descripcion,
            String categoria,
            int versionActual,
            boolean activo,
            List<ParticipanteDto> participantes,
            List<VariableDto> variables,
            List<VersionDto> versiones) {}

    public record ConfigParticipanteRequest(
            @NotBlank String clave,

            @NotBlank(message = "Escriba la etiqueta del participante") @Size(max = 120)
            String etiqueta,

            @NotNull TipoParticipante tipoCliente) {}

    public record EditarPlantillaRequest(
            @NotBlank(message = "Escriba el nombre") @Size(max = 150)
            String nombre,

            @Size(max = 500) String descripcion,
            @Size(max = 80) String categoria,
            boolean activo,
            @NotNull List<@Valid ConfigParticipanteRequest> participantes) {}
}
