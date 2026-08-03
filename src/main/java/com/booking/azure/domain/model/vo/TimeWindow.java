package com.booking.azure.domain.model.vo;

import java.time.Duration;
import java.time.Instant;

/**
 * Un intervalo temporal semiabierto {@code [inicio, fin)}, siempre en UTC.
 *
 * <h2>Por qué existe este objeto de valor</h2>
 *
 * La regla de solape es <b>la invariante central del sistema</b>: dos reservas
 * para el mismo empleado nunca deben solaparse. Antes de que existiera esta
 * clase, la regla estaba escrita <b>tres veces por separado</b> —en el servicio
 * de recuperación, en la consulta SQL y en la petición de reserva— y por tanto
 * podían divergir sin que nadie se diera cuenta. Ahora esta clase es la única
 * fuente de verdad y todo lo demás delega en ella.
 *
 * <h2>Por qué el intervalo es semiabierto</h2>
 *
 * Una cita que termina a las 11:00 y otra que empieza a las 11:00 <b>no</b> se
 * solapan. Tratar el intervalo como cerrado rechazaría ese par y haría imposible
 * encadenar citas seguidas — justo lo que una agenda necesita hacer todo el día.
 *
 * <h2>Por qué UTC</h2>
 *
 * La conversión desde hora local más zona ocurre en la capa de aplicación, antes
 * de construir este objeto. Sin esa normalización, {@code 10:00 Europe/Madrid} y
 * {@code 08:00 UTC} contarían como ventanas distintas siendo el mismo instante,
 * y la colisión pasaría inadvertida.
 */
public record TimeWindow(Instant start, Instant end) {

    public TimeWindow {
        if (start == null || end == null) {
            throw new IllegalArgumentException("start and end are required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "end must be after start (start=%s, end=%s)".formatted(start, end));
        }
    }

    public static TimeWindow of(Instant start, Instant end) {
        return new TimeWindow(start, end);
    }

    /**
     * Si esta ventana se solapa con otra.
     *
     * <p>Comparación semiabierta: las ventanas que solo se tocan no se solapan.
     * La consulta {@code countOverlappingReservations} implementa esta misma
     * condición en SQL; si una de las dos cambia, la otra debe cambiar con ella.
     */
    public boolean overlaps(TimeWindow other) {
        return other.start.isBefore(this.end) && this.start.isBefore(other.end);
    }

    /** Whether {@code moment} falls inside this window (start inclusive, end exclusive). */
    public boolean contains(Instant moment) {
        return !moment.isBefore(start) && moment.isBefore(end);
    }

    public Duration duration() {
        return Duration.between(start, end);
    }

    /**
     * Widens the window by {@code padding} on both sides.
     *
     * Used when querying Microsoft Bookings, which may add buffer time before
     * and after an appointment, so the returned times do not match the
     * requested ones exactly.
     */
    public TimeWindow paddedBy(Duration padding) {
        return new TimeWindow(start.minus(padding), end.plus(padding));
    }

    @Override
    public String toString() {
        return start + " – " + end;
    }
}
