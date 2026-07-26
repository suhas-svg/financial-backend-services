[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet("Create", "Status", "InstallTrust", "VerifyTrustedEndpoint", "RemoveTrust", "Destroy")]
    [string]$Action,

    [string]$TlsDirectory = (Join-Path $PSScriptRoot "..\.sandbox\tls"),

    [string]$Authorization,

    [uri]$Endpoint = "https://localhost:8443/healthz"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$sandboxRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ".sandbox"))
$tlsRoot = [System.IO.Path]::GetFullPath($TlsDirectory)
$sandboxPrefix = $sandboxRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar) +
    [System.IO.Path]::DirectorySeparatorChar
if (-not $tlsRoot.StartsWith($sandboxPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "TLS artifacts must stay under the repository's ignored .sandbox directory."
}

$caPath = Join-Path $tlsRoot "localhost-ca.cer"
$certificatePath = Join-Path $tlsRoot "tls.crt"
$privateKeyPath = Join-Path $tlsRoot "tls.key"
$manifestPath = Join-Path $tlsRoot "manifest.json"
$expectedCaSubject = "CN=Financial Synthetic Sandbox Localhost CA"
$authorizationPhrase = "AUTHORIZE LOCALHOST TRUST"

function Read-Manifest {
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "TLS manifest not found. Run Create first."
    }
    Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
}

function Read-ValidatedCa {
    if (-not (Test-Path -LiteralPath $caPath -PathType Leaf)) {
        throw "Localhost CA certificate not found. Run Create first."
    }
    $certificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($caPath)
    if ($certificate.Subject -ne $expectedCaSubject) {
        throw "Refusing unexpected CA subject: $($certificate.Subject)"
    }
    if ($certificate.NotAfter.ToUniversalTime() -gt [DateTime]::UtcNow.AddDays(4)) {
        throw "Refusing a CA whose lifetime exceeds the short-lived localhost policy."
    }
    if ($certificate.NotAfter.ToUniversalTime() -le [DateTime]::UtcNow) {
        throw "The localhost CA has expired."
    }
    $basicConstraints = @($certificate.Extensions | Where-Object {
        $_ -is [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]
    })
    if ($basicConstraints.Count -ne 1 -or -not $basicConstraints[0].CertificateAuthority) {
        throw "The localhost trust artifact is not a CA certificate."
    }
    $certificate
}

function Find-InstalledCa([string]$Thumbprint) {
    @(Get-ChildItem -LiteralPath Cert:\CurrentUser\Root | Where-Object {
        $_.Thumbprint -eq $Thumbprint -and $_.Subject -eq $expectedCaSubject
    })
}

switch ($Action) {
    "Create" {
        if (Test-Path -LiteralPath $tlsRoot) {
            $existing = @(Get-ChildItem -LiteralPath $tlsRoot -Force)
            if ($existing.Count -gt 0) {
                throw "TLS directory is not empty. Remove trust and run Destroy before creating a new lifecycle."
            }
        } else {
            New-Item -ItemType Directory -Path $tlsRoot -Force | Out-Null
        }

        & docker version --format "{{.Server.Version}}" | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Docker is required to create the local certificate without host key tooling." }

        $toolImage = "financial-sandbox-tls-tool:phase3"
        & docker build --quiet --tag $toolImage (Join-Path $repoRoot "infrastructure\synthetic-sandbox\gateway") | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Unable to build the repository-pinned TLS tool image." }

        $containerScript = @'
set -eu
umask 077
openssl genrsa -out /out/ca.key 3072
openssl req -x509 -new -sha256 -key /out/ca.key -days 3 \
  -subj "/CN=Financial Synthetic Sandbox Localhost CA" \
  -addext "basicConstraints=critical,CA:TRUE,pathlen:0" \
  -addext "keyUsage=critical,keyCertSign,cRLSign" \
  -out /out/localhost-ca.pem
openssl genrsa -out /out/tls.key 3072
openssl req -new -sha256 -key /out/tls.key \
  -subj "/CN=localhost/O=Financial Synthetic Sandbox" \
  -out /out/localhost.csr
printf '%s\n' \
  'basicConstraints=critical,CA:FALSE' \
  'keyUsage=critical,digitalSignature,keyEncipherment' \
  'extendedKeyUsage=serverAuth' \
  'subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1' > /tmp/localhost.ext
openssl x509 -req -sha256 -days 2 \
  -in /out/localhost.csr -CA /out/localhost-ca.pem -CAkey /out/ca.key \
  -CAcreateserial -extfile /tmp/localhost.ext -out /out/tls.crt
openssl x509 -in /out/localhost-ca.pem -outform der -out /out/localhost-ca.cer
rm -f /out/ca.key /out/localhost-ca.pem /out/localhost.csr /out/localhost-ca.srl
chmod 0600 /out/tls.key
'@
        $generatorPath = Join-Path $tlsRoot "create-certificates.sh"
        [System.IO.File]::WriteAllText(
            $generatorPath,
            $containerScript.Replace("`r`n", "`n"),
            (New-Object System.Text.UTF8Encoding($false))
        )
        try {
            & docker run --rm --entrypoint sh --mount "type=bind,source=$tlsRoot,target=/out" $toolImage /out/create-certificates.sh
            if ($LASTEXITCODE -ne 0) { throw "Localhost certificate creation failed." }
        } finally {
            Remove-Item -LiteralPath $generatorPath -Force -ErrorAction SilentlyContinue
        }

        $ca = Read-ValidatedCa
        $manifest = [ordered]@{
            purpose = "localhost-only synthetic sandbox browser trust"
            createdAtUtc = [DateTime]::UtcNow.ToString("o")
            caSubject = $ca.Subject
            caThumbprint = $ca.Thumbprint
            caNotAfterUtc = $ca.NotAfter.ToUniversalTime().ToString("o")
            allowedDnsNames = @("localhost")
            allowedIpAddresses = @("127.0.0.1", "::1")
            privateKeyTracked = $false
        }
        $manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
        Write-Host "Created a short-lived localhost-only certificate lifecycle under ignored .sandbox/tls."
        Write-Host "CurrentUser trust was not changed."
    }

    "Status" {
        $manifest = Read-Manifest
        $installed = @(Find-InstalledCa $manifest.caThumbprint)
        [pscustomobject]@{
            TlsDirectory = $tlsRoot
            Subject = $manifest.caSubject
            ExpiresUtc = $manifest.caNotAfterUtc
            CurrentUserRootInstalled = ($installed.Count -eq 1)
            CertificatePresent = (Test-Path -LiteralPath $certificatePath -PathType Leaf)
            PrivateKeyPresent = (Test-Path -LiteralPath $privateKeyPath -PathType Leaf)
        }
    }

    "InstallTrust" {
        if ($Authorization -cne $authorizationPhrase) {
            throw "CurrentUser trust mutation requires -Authorization '$authorizationPhrase'."
        }
        $ca = Read-ValidatedCa
        $manifest = Read-Manifest
        if ($manifest.caThumbprint -ne $ca.Thumbprint) {
            throw "Manifest thumbprint does not match the localhost CA."
        }
        $installed = @(Find-InstalledCa $ca.Thumbprint)
        if ($installed.Count -eq 0) {
            Import-Certificate -FilePath $caPath -CertStoreLocation Cert:\CurrentUser\Root | Out-Null
        }
        $verified = @(Find-InstalledCa $ca.Thumbprint)
        if ($verified.Count -ne 1) { throw "CurrentUser localhost CA installation could not be verified." }
        Write-Host "Installed exactly one short-lived localhost sandbox CA in CurrentUser Root."
    }

    "VerifyTrustedEndpoint" {
        if ($Endpoint.Scheme -ne "https" -or $Endpoint.Host -ne "localhost") {
            throw "Trusted endpoint verification is restricted to https://localhost."
        }
        $manifest = Read-Manifest
        if (@(Find-InstalledCa $manifest.caThumbprint).Count -ne 1) {
            throw "The expected localhost CA is not installed in CurrentUser Root."
        }
        $port = if ($Endpoint.IsDefaultPort) { 443 } else { $Endpoint.Port }
        $client = [System.Net.Sockets.TcpClient]::new()
        $tlsStream = $null
        $reader = $null
        $writer = $null
        try {
            $client.Connect("localhost", $port)
            # No validation callback is supplied: SslStream must accept the CurrentUser chain and localhost name.
            $tlsStream = [System.Net.Security.SslStream]::new($client.GetStream(), $false)
            $tlsStream.AuthenticateAsClient("localhost")
            if (-not $tlsStream.IsAuthenticated -or -not $tlsStream.IsEncrypted) {
                throw "The localhost connection was not authenticated and encrypted."
            }
            $writer = [System.IO.StreamWriter]::new(
                $tlsStream,
                [System.Text.Encoding]::ASCII,
                1024,
                $true
            )
            $writer.NewLine = [Environment]::NewLine
            $writer.WriteLine("GET $($Endpoint.PathAndQuery) HTTP/1.1")
            $writer.WriteLine("Host: localhost:$port")
            $writer.WriteLine("Connection: close")
            $writer.WriteLine()
            $writer.Flush()
            $reader = [System.IO.StreamReader]::new($tlsStream, [System.Text.Encoding]::ASCII)
            $statusLine = $reader.ReadLine()
            if ($statusLine -notmatch '^HTTP/[0-9.]+ 200(?: |$)') {
                throw "Trusted localhost endpoint did not return HTTP 200: $statusLine"
            }
        } finally {
            if ($null -ne $reader) { $reader.Dispose() }
            if ($null -ne $writer) { $writer.Dispose() }
            if ($null -ne $tlsStream) { $tlsStream.Dispose() }
            $client.Dispose()
        }
        Write-Host "Trusted TLS verification passed without a certificate bypass: $Endpoint"
    }

    "RemoveTrust" {
        $manifest = Read-Manifest
        $installed = @(Find-InstalledCa $manifest.caThumbprint)
        foreach ($certificate in $installed) {
            Remove-Item -LiteralPath ("Cert:\CurrentUser\Root\" + $certificate.Thumbprint)
        }
        if (@(Find-InstalledCa $manifest.caThumbprint).Count -ne 0) {
            throw "Localhost CA removal could not be verified."
        }
        Write-Host "Verified removal of the localhost sandbox CA from CurrentUser Root."
    }

    "Destroy" {
        $manifest = Read-Manifest
        if (@(Find-InstalledCa $manifest.caThumbprint).Count -ne 0) {
            throw "Refusing to delete lifecycle evidence while the CA is trusted. Run RemoveTrust first."
        }
        Remove-Item -LiteralPath $tlsRoot -Recurse -Force
        if (Test-Path -LiteralPath $tlsRoot) { throw "TLS artifact removal could not be verified." }
        Write-Host "Verified removal of ignored localhost certificate and private-key artifacts."
    }
}
