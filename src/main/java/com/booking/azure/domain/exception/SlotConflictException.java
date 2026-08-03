package com.booking.azure.domain.exception;

/**
 * La ventana solicitada ya está ocupada para al menos uno de los empleados.
 *
 * <p>Capa de dominio: es un fallo de negocio, independiente de HTTP. Su
 * traducción a {@code 409 Conflict} ocurre en la capa de presentación, en
 * {@code GlobalExceptionHandler}.
 *
 * <p>Se lanza cuando la comprobación de solape rechaza la inserción — es decir,
 * exactamente cuando otra petición, simultánea o anterior, ya se ha llevado el
 * hueco.
 *
 * <p><b>No es un error del sistema.</b> Con peticiones concurrentes sobre la
 * misma agenda, que una pierda es el funcionamiento normal y esperado; por eso
 * se registra a nivel {@code INFO} y no como fallo.
 */
public class SlotConflictException extends DomainException {

    public SlotConflictException(String message) {
        super(message);
    }

    public SlotConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
