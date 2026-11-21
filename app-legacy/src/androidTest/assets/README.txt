open-meteo-chain.pem is a public certificate fixture, not a product trust store.
Captured 2026-11-16 from api.open-meteo.com:443 with OpenSSL s_client
-servername api.open-meteo.com -verify_hostname api.open-meteo.com
-verify_return_error -showcerts. Normal trust and hostname verification succeeded.
Four certificates; SHA256 7b00708bd78d3b164795541c7e2f3abbd96a797b22b76e025cc5e22bead1af93.
Offline PKIX uses the fixed capture date; live TLS controls use real system time.
Never add this chain or its leaf to the product trust anchors.
