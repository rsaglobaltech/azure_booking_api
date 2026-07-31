-- ============================================================================
-- Slot-Reservierungen: Schiedsrichter gegen Doppelbuchungen
--
-- Microsoft Graph bietet keine atomaren Primitive: keine Sperren, keine
-- Transaktionen, kein ETag/If-Match auf bookingAppointment. Der verwendete
-- Endpunkt POST /solutions/bookingBusinesses/{id}/appointments ist der
-- administrative Endpunkt und laesst Ueberbuchung bewusst zu.
--
-- Daher entscheidet diese Tabelle, welche von mehreren gleichzeitigen
-- Anfragen den Slot bekommt. Siehe docs/PLAN-COLISION-RESERVAS.md §2.
-- ============================================================================

-- Liefert die btree-Operatorklassen (=) fuer GiST-Indizes. Ohne diese
-- Erweiterung laesst sich business_id/staff_member_id nicht mit WITH =
-- in die EXCLUDE-Bedingung aufnehmen.
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE slot_reservation (
    id                   BIGSERIAL     PRIMARY KEY,
    business_id          VARCHAR(255)  NOT NULL,
    service_id           VARCHAR(255)  NOT NULL,
    staff_member_id      VARCHAR(255)  NOT NULL,

    -- IMMER UTC. Die API empfaengt lokale Zeit + Zonenangabe; ohne
    -- Normalisierung wuerden '10:00 Europe/Berlin' und '08:00 UTC' als
    -- verschiedene Slots gelten und die Kollision bliebe unerkannt.
    start_utc            TIMESTAMPTZ   NOT NULL,
    end_utc              TIMESTAMPTZ   NOT NULL,

    graph_appointment_id VARCHAR(255),
    state                VARCHAR(20)   NOT NULL,
    expires_at           TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT ck_slot_state    CHECK (state IN ('PENDING', 'CONFIRMED', 'RELEASED')),
    CONSTRAINT ck_slot_zeitraum CHECK (end_utc > start_utc)
);

-- Der eigentliche Schiedsrichter.
--
-- Erfasst auch TEILWEISE Ueberschneidungen (10:00-11:00 gegen 10:30-11:30),
-- die ein reiner UNIQUE-Index auf start_utc uebersehen wuerde.
-- '[)' = Startzeit inklusive, Endzeit exklusiv: ein Termin 10:00-11:00 und
-- ein Folgetermin 11:00-12:00 kollidieren also NICHT.
--
-- RELEASED-Zeilen sind ausgenommen: freigegebene Slots duerfen neu belegt
-- werden, die Zeile bleibt aber als Pruefspur erhalten.
ALTER TABLE slot_reservation
    ADD CONSTRAINT ex_slot_overlap
    EXCLUDE USING gist (
        business_id     WITH =,
        staff_member_id WITH =,
        tstzrange(start_utc, end_utc, '[)') WITH &&
    ) WHERE (state IN ('PENDING', 'CONFIRMED'));

-- Fuer den Wiederherstellungsjob: verwaiste PENDING-Zeilen finden, die nach
-- einem Absturz zwischen Reservierung und Graph-Aufruf haengengeblieben sind.
CREATE INDEX ix_slot_pending_abgelaufen
    ON slot_reservation (expires_at)
    WHERE state = 'PENDING';

-- Fuer Stornierung und Umbuchung: Reservierung ueber die Graph-Termin-ID finden.
CREATE INDEX ix_slot_graph_termin
    ON slot_reservation (graph_appointment_id)
    WHERE graph_appointment_id IS NOT NULL;
