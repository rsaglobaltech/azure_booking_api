$base = "d:\projects\azure_booking\src\main\java\com\booking\azure"

$replacements = @{
    "mitarbeiterVerwaltung" = "staffManagement"
    "mitarbeiterAbrufen" = "getStaffMember"
    "mitarbeiterAktualisieren" = "updateStaffMember"
    "mitarbeiterLoeschen" = "deleteStaffMember"
    "mitarbeiterVerfuegbarkeitAbrufen" = "getStaffMemberAvailability"
    "dienstVerwaltung" = "serviceManagement"
    "dienstAbrufen" = "getService"
    "dienstAktualisieren" = "updateService"
    "dienstLoeschen" = "deleteService"
    "agenturVerwaltung" = "agencyManagement"
    "betriebeAuflisten" = "listBusinesses"
    "betriebAbrufen" = "getBusiness"
    "betriebErstellen" = "createBusiness"
    "betriebAktualisieren" = "updateBusiness"
    "betriebLoeschen" = "deleteBusiness"
    "betriebVeroeffentlichen" = "publishBusiness"
    "betriebDeaktivieren" = "deactivateBusiness"
    "terminVerwaltung" = "appointmentManagement"
    "terminAktualisieren" = "updateAppointment"
    "terminStornieren" = "cancelAppointment"
    "termineAuflisten" = "listAppointments"
    "terminErstellen" = "createAppointment"
    "terminAbrufen" = "getAppointment"
    "graphApiAnfrage" = "graphApiRequest"
    "slotReservierung" = "slotReservation"
    "slotAnfrage" = "slotRequest"
    "koerper" = "body"
    "pfad" = "path"
    "terminePfad" = "appointmentsPath"
    "agentur" = "agency"
}

Get-ChildItem -Path $base -Filter *.java -Recurse | ForEach-Object {
    $content = Get-Content $_.FullName -Raw
    foreach ($key in $replacements.Keys) {
        $content = [regex]::Replace($content, "\b$key\b", $replacements[$key])
    }
    Set-Content -Path $_.FullName -Value $content
}

echo "Method translation complete"
