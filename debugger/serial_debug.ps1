[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Port = 'COM3',

    [Parameter(Position = 1)]
    [int]$Baud = 115200
)

if (-not ('System.IO.Ports.SerialPort' -as [type])) {
    try {
        Add-Type -AssemblyName System.IO.Ports -ErrorAction Stop
    }
    catch {
        # ignoresd
    }
}

if ([string]::IsNullOrWhiteSpace($Port)) {
    Write-Host "forgot com port"
    exit 1
}

Write-Host "opening $Port @ $Baud baued..."

try {
    $serial = [System.IO.Ports.SerialPort]::new($Port, $Baud)
    $serial.ReadTimeout = 1000
    $serial.Open()
}
catch {
    $errMsg = if ($_.Exception.GetBaseException()) { $_.Exception.GetBaseException().Message } else { $_.Exception.Message }
    Write-Host "cant not open ${Port}: $errMsg"
    exit 1
}

Write-Host "Running`n"

$byteCount = 0
$lastByteTime = [DateTime]::UtcNow
$silenceWarned = $false

try {
    while ($true) {
        try {
            $byteVal = $serial.ReadByte()
            if ($byteVal -ge 0) {
                $byteCount++
                $lastByteTime = [DateTime]::UtcNow
                $silenceWarned = $false
                $marker = if ($byteVal -eq 0xAA) { " <- init frame" } else { "" }
                $printable = if ($byteVal -ge 32 -and $byteVal -lt 127) { [char]$byteVal } else { "." }
                Write-Host ("[{0,5}] 0x{1:X2}  ({2}){3}" -f $byteCount, $byteVal, $printable, $marker)
            }
        }
        catch [System.TimeoutException] {
            # No byte received within timeout (1 sec)
            if (-not $silenceWarned -and ([DateTime]::UtcNow - $lastByteTime).TotalSeconds -gt 5) {
                Write-Host "... no data for 5+ seconds."
                $silenceWarned = $true
            }
        }
        catch [System.IO.IOException] {
            Write-Host "`ndevice disconnected: $($_.Exception.Message)"
            break
        }
    }
}
finally {
    if ($null -ne $serial) {
        if ($serial.IsOpen) {
            $serial.Close()
        }
        $serial.Dispose()
    }
    Write-Host "`end! bytes: $byteCount"
}
