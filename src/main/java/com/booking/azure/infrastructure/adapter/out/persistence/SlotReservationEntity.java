package com.booking.azure.infrastructure.adapter.out.persistence;

import com.booking.azure.domain.model.SlotStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA-Abbildung der Tabelle {@code slot_reservation}.
 *
 * Onion-Architektur – Infrastrukturschicht: bleibt bewusst hier und wird
 * niemals in die Domänen- oder Anwendungsschicht durchgereicht.
 *
 * Das Schema verwaltet Flyway ({@code db/migration/V1__slot_reservation.sql}),
 * nicht Hibernate. Diese Klasse muss zum Schema passen, nicht umgekehrt –
 * daher {@code ddl-auto: validate}.
 */
@Entity
@Table(name = "slot_reservation")
@Getter
@Setter
@NoArgsConstructor
public class SlotReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private String businessId;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "staff_member_id", nullable = false)
    private String staffMemberId;

    /** Immer UTC – siehe Hinweis in der Migration. */
    @Column(name = "start_utc", nullable = false)
    private Instant startUtc;

    /** Immer UTC, exklusiv. */
    @Column(name = "end_utc", nullable = false)
    private Instant endUtc;

    @Column(name = "graph_appointment_id")
    private String graphAppointmentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private SlotStatus state;

    /**
     * Zeitpunkt, ab dem eine {@code PENDING}-Zeile als verwaist gilt.
     *
     * <p><b>Nicht Teil der Korrektheit.</b> Den gegenseitigen Ausschluss
     * garantiert die {@code EXCLUDE}-Bedingung, nicht dieser Wert. Er dient
     * ausschließlich dazu, nach einem Absturz zwischen Reservierung und
     * Graph-Aufruf hängengebliebene Zeilen wiederzufinden.
     */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}


