$base = "d:\projects\azure_booking\src\main\java\com\booking\azure"

$replacements = @{
    "AgenturVerwaltung" = "AgencyManagement"
    "DienstVerwaltung" = "ServiceManagement"
    "MitarbeiterVerwaltung" = "StaffManagement"
    "TerminVerwaltung" = "AppointmentManagement"
    "GraphApiAnfrage" = "GraphApiRequest"
    "SlotReservierung" = "SlotReservationPort"
    "SlotWiederherstellungService" = "SlotRecoveryService"
    "ZeitzonenUmrechnung" = "TimeZoneConverter"
    "SlotAnfrage" = "SlotRequest"
    "VerwaisteReservierung" = "OrphanedReservation"
    "GraphAntwortException" = "GraphResponseException"
    "GraphUnbekanntException" = "GraphUnknownException"
    
    "terminErstellen" = "createAppointment"
    "terminAktualisieren" = "updateAppointment"
    "terminStornieren" = "cancelAppointment"
    "termineAuflisten" = "listAppointments"
    "diensteAuflisten" = "listServices"
    "dienstErstellen" = "createService"
    "mitarbeiterAuflisten" = "listStaffMembers"
    "mitarbeiterErstellen" = "createStaffMember"
    "agenturErstellen" = "createAgency"
    "agenturAbrufen" = "getAgency"
    "umbuchen" = "reschedule"
    "zuInstant" = "toInstant"
    "zuOffsetDateTime" = "toOffsetDateTime"
    "reservieren" = "reserve"
    "freigeben" = "release"
    "wiederherstellen" = "recover"
    "bestaetigen" = "confirm"
    "sucheVerwaiste" = "findOrphaned"
    "betriebId" = "businessId"
    "anfrage" = "request"
    "terminId" = "appointmentId"
    "dienstId" = "serviceId"
}

# Rename Files First
Get-ChildItem -Path $base -Filter *.java -Recurse | ForEach-Object {
    $newName = $_.Name
    foreach ($key in $replacements.Keys) {
        if ($newName -match $key -and $key -match "^[A-Z]") {
            $newName = $newName -replace $key, $replacements[$key]
        }
    }
    if ($newName -ne $_.Name) {
        Rename-Item -Path $_.FullName -NewName $newName
    }
}

# Replace contents
Get-ChildItem -Path $base -Filter *.java -Recurse | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    foreach ($key in $replacements.Keys) {
        # use word boundaries to avoid replacing substrings incorrectly
        $content = [regex]::Replace($content, "\b$key\b", $replacements[$key])
    }
    Set-Content -Path $_.FullName -Value $content
}

echo "Translation complete"
