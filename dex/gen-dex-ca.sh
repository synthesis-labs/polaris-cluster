#!/bin/bash

ROOT=$( dirname "${BASH_SOURCE[0]}" )
FOLDER=$ROOT/ca

echo Will create CA in $FOLDER

mkdir -p $FOLDER
cd $FOLDER

cat << EOF > req.cnf
[req]
req_extensions = v3_req
distinguished_name = req_distinguished_name

[req_distinguished_name]

[ v3_req ]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = dex.example.cluster.k8s
EOF

# CA Private and Self-signed Certificate
#
openssl genrsa -out dex-ca-key.pem 2048
openssl req -x509 -new -nodes -key dex-ca-key.pem -days 9999 -out dex-ca-cert.pem -subj "/CN=dex-kube-ca"

# Issuer private key and signed by CA
#
openssl genrsa -out dex-issuer-key.pem 2048
openssl req -new -key dex-issuer-key.pem -out dex-issuer-csr.pem -subj "/CN=dex-kube-issuer" -config req.cnf
openssl x509 -req -in dex-issuer-csr.pem -CA dex-ca-cert.pem -CAkey dex-ca-key.pem -CAcreateserial -out dex-issuer-cert.pem -days 9999 -extensions v3_req -extfile req.cnf

cd -
