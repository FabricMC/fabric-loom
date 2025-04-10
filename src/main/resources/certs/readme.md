# Minecraft certificate chain

Exported from the vanilla jar by extracting MOJANGCS.RSA from the jar and then running:

`openssl pkcs7 -inform DER -in MOJANGCS.RSA -print_certs -out cert.pem`